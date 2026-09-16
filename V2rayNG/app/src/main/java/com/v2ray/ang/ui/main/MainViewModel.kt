package com.v2ray.ang.ui.main

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.ConnectionTestResult
import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.dto.LocateTarget
import com.v2ray.ang.dto.RealPingResult
import com.v2ray.ang.dto.TestServiceMessage
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.extension.delay
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.matchesPattern
import com.v2ray.ang.extension.moveItem
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.PatternSyntaxException

private fun applyTestDelayResults(
    servers: List<ServersCache>,
    updates: Map<String, Long>,
): List<ServersCache> = servers.map { server ->
    val delayMillis = updates[server.guid]
    if (delayMillis == null || delayMillis == server.testDelayMillis) {
        server
    } else {
        server.copy(testDelayMillis = delayMillis)
    }
}

private fun applyTestDelayResultsToRows(
    rows: List<ServerRowUiModel>,
    updates: Map<String, Long>,
): List<ServerRowUiModel> = rows.map { row ->
    val delayMillis = updates[row.guid]
    if (delayMillis == null || delayMillis == row.testDelayMillis) {
        row
    } else {
        row.copy(testDelayMillis = delayMillis)
    }
}

class MainViewModel(
    application: Application,
    private val dataSource: MainDataSource
) : BaseViewModel(application) {

    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
    private val preloadDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)

    // ---------- UI state ----------
    private val _uiState = MutableStateFlow(
        MainUiState(
            selectedGroupId = dataSource.getSelectedSubscriptionId(),
            selectedGuid = dataSource.getSelectServer(),
            confirmRemove = dataSource.getConfirmRemove(),
            doubleColumnDisplay = dataSource.getDoubleColumnDisplay()
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    // ---------- Keyword filtering ----------
    @Volatile
    private var keywordFilter: String = ""
    private var filterJob: Job? = null

    // ---------- Groups & cache ----------
    private val cacheMutex = Mutex()
    private val groupDataCache = mutableMapOf<String, List<ServersCache>>()
    private val groupUiFlows = ConcurrentHashMap<String, MutableStateFlow<ServerGroupUiState>>()
    private val groupServerFlows = ConcurrentHashMap<String, StateFlow<List<ServersCache>>>()
    private val groupLoadMutexes = ConcurrentHashMap<String, Mutex>()
    private val serverOrderPersistenceJobs = mutableMapOf<String, Job>()

    private var setupGroupJob: Job? = null
    private var preloadJob: Job? = null
    private var selectedGroupLoadJob: Job? = null
    private var reloadJob: Job? = null
    private var sessionTimerJob: Job? = null
    private var connectionTimeoutJob: Job? = null
    private var initializationJob: Job? = null
    private val preferenceMutex = Mutex()
    private var coldLaunchHandled = false
    private var testResultFlushJob: Job? = null
    private val pendingTestResults = linkedMapOf<String, Long>()

    private val testRequests = MainTestRequests()
    private var bulkTestJob: Job? = null

    private val initialPageReady = CompletableDeferred<Unit>()

    // ---------- Service events ----------
    init {
        refreshFilvlessPreferences()
        collectServiceEvents()
        setupGroupTab()
    }

    private fun collectServiceEvents() {
        viewModelScope.launch {
            dataSource.mainServiceEvent.collect { event ->
                handleServiceEvent(event)
            }
        }
    }

    private fun handleServiceEvent(event: MainServiceEvent) {
        when (event) {
            is MainServiceEvent.HealthChanged -> {
                if (uiState.value.isRunning) _uiState.update { it.copy(connectionHealth = event.health) }
            }
            MainServiceEvent.SubscriptionsUpdated -> setupGroupTab(forceRefresh = true)
            MainServiceEvent.StateRunning -> updateRunningState(true, clearTestingText = false)
            MainServiceEvent.StateNotRunning -> updateRunningState(false, clearTestingText = false)
            MainServiceEvent.StateStartSuccess -> {
                toastSuccess(R.string.toast_services_success)
                updateRunningState(true)
            }

            MainServiceEvent.StateStartFailure -> {
                toastError(R.string.toast_services_failure)
                updateRunningState(false)
                _uiState.update { it.copy(connectionFailed = true) }
            }

            MainServiceEvent.StateStopSuccess -> updateRunningState(false)
            is MainServiceEvent.MeasureDelayResult -> {
                if (!uiState.value.isRunning || !testRequests.completeCurrent(event.requestId)) return
                _uiState.update { it.copy(isTesting = testRequests.isTesting, status = MainStatus.ConnectionTest(event.result)) }
            }

            is MainServiceEvent.MeasureConfigSuccess -> {
                val request = testRequests.bulk?.takeIf { it.id == event.requestId } ?: return
                queueTestResult(event.result, request)
            }

            is MainServiceEvent.MeasureConfigNotify -> {
                if (event.requestId == testRequests.bulk?.id) {
                    _uiState.update { it.copy(status = MainStatus.TestProgress(event.progress)) }
                }
            }

            is MainServiceEvent.MeasureConfigFinish -> {
                val request = testRequests.bulk?.takeIf { it.id == event.requestId } ?: return
                val scheduledFlush = testResultFlushJob
                testResultFlushJob = viewModelScope.launch {
                    scheduledFlush?.cancelAndJoin()
                    if (testRequests.bulk?.id != request.id) return@launch
                    flushPendingTestResults(request)
                    onTestsFinished(request.id)
                }
            }

            is MainServiceEvent.MeasureDelayCancelled -> {
                if (testRequests.completeCurrent(event.requestId)) resetTestStatus()
            }

            is MainServiceEvent.MeasureConfigCancelled -> {
                if (testRequests.completeBulk(event.requestId) != null) {
                    cancelPendingTestResults()
                    resetTestStatus()
                }
            }
        }
    }

    private fun queueTestResult(result: RealPingResult, request: MainTestRequests.Bulk) {
        pendingTestResults[result.guid] = result.delayMillis
        if (testResultFlushJob?.isActive == true) return

        testResultFlushJob = viewModelScope.launch {
            while (pendingTestResults.isNotEmpty()) {
                delay(TEST_RESULT_FLUSH_INTERVAL_MS)
                flushPendingTestResults(request)
            }
        }
    }

    private suspend fun flushPendingTestResults(request: MainTestRequests.Bulk) {
        if (pendingTestResults.isEmpty()) return
        if (testRequests.bulk?.id != request.id) return

        val updates = cacheMutex.withLock {
            val drained = pendingTestResults.toMap()
            pendingTestResults.clear()
            groupDataCache[request.groupId]?.let { cached ->
                groupDataCache[request.groupId] = applyTestDelayResults(cached, drained)
            }
            drained
        }
        if (updates.isEmpty()) return
        if (testRequests.bulk?.id != request.id) return
        mutableServerGroupState(request.groupId).update { current ->
            current.copy(
                servers = applyTestDelayResults(current.servers, updates),
                rows = applyTestDelayResultsToRows(current.rows, updates),
            )
        }
    }

    internal fun formatStatus(status: MainStatus): String = when (status) {
        MainStatus.Disconnected -> dataSource.getString(R.string.connection_not_connected)
        MainStatus.Connected -> dataSource.getString(R.string.connection_connected)
        MainStatus.Testing -> dataSource.getString(R.string.connection_test_testing)
        is MainStatus.TestProgress -> dataSource.getString(
            R.string.connection_running_task_left,
            status.progress
        )

        is MainStatus.ConnectionTest -> formatConnectionTestResult(status.result)
    }

    private fun formatConnectionTestResult(result: ConnectionTestResult): String {
        val status = if (result.delayMillis >= 0) {
            val delay = dataSource.getString(R.string.server_test_delay_value, result.delayMillis)
            dataSource.getString(R.string.connection_test_available, delay)
        } else {
            val detail = result.errorMessage.ifBlank {
                dataSource.getString(R.string.connection_test_empty_message)
            }
            dataSource.getString(R.string.connection_test_error, detail)
        }

        if (result.delayMillis < 0 || (result.country == null && result.ipAddress == null)) {
            return status
        }

        val unknown = dataSource.getString(R.string.value_unknown)
        return "$status\n(${result.country ?: unknown}) ${result.ipAddress ?: unknown}"
    }

    // ---------- Public state accessors ----------
    fun serversForGroup(groupId: String): StateFlow<List<ServersCache>> =
        groupServerFlows.computeIfAbsent(groupId) {
            val groupState = mutableServerGroupState(groupId)
            groupState
                .map { it.servers }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                    initialValue = groupState.value.servers,
                )
        }

    internal fun serverGroupState(groupId: String): StateFlow<ServerGroupUiState> =
        mutableServerGroupState(groupId).asStateFlow()

    private fun mutableServerGroupState(groupId: String): MutableStateFlow<ServerGroupUiState> =
        groupUiFlows.computeIfAbsent(groupId) { MutableStateFlow(ServerGroupUiState()) }

    private fun currentServers(): List<ServersCache> =
        mutableServerGroupState(uiState.value.selectedGroupId).value.servers

    // ---------- Action handler ----------
    fun onAction(action: MainAction) {
        when (action) {
            is MainAction.SetUpdateDownloadPolicy -> viewModelScope.launch(ioDispatcher) {
                preferenceMutex.withLock {
                    try {
                        com.v2ray.ang.handler.AppUpdateDownload.setPolicy(getApplication(), action.policy)
                        _uiState.update { it.copy(preferences = it.preferences.copy(updateDownloadPolicy = action.policy)) }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { refreshFilvlessPreferences(); toastError(R.string.toast_failure) }
                }
            }
            is MainAction.SetPreference -> setFilvlessPreference(action.key, action.enabled)
            MainAction.ForgetSubscriptions -> forgetSubscriptions()
            is MainAction.SetLanguage, MainAction.OpenSupport, MainAction.OpenReview -> Unit // Activity-owned system actions.
            is MainAction.SaveRouting -> saveRouting(action.vpn, action.direct)
            MainAction.CheckAppUpdate -> checkAppUpdate(true)
            MainAction.Initialize -> initialize()
            MainAction.RefreshGroups -> setupGroupTab(forceRefresh = true)
            MainAction.TestAllServers -> testAllRealPing(true)
            MainAction.TestRealAllServers -> testAllRealPing()
            MainAction.CancelTesting -> cancelAllPing()
            MainAction.RemoveAllServers -> removeAllServerAsync()
            MainAction.RemoveDuplicateServers -> removeDuplicateServerAsync()
            MainAction.RemoveInvalidServers -> removeInvalidServerAsync()
            MainAction.SortByTestResults -> sortByTestResultsAsync()
            MainAction.UpdateSubscriptions -> importConfigViaSub()
            MainAction.ExportAll -> exportAllAsync()
            is MainAction.SelectGroup -> subscriptionIdChanged(action.groupId)
            is MainAction.SelectServer -> updateSelectedGuid(action.guid)
            is MainAction.RemoveServer -> removeServerAndRefresh(action.guid)
            is MainAction.Search -> filterConfig(action.query)
            is MainAction.ImportBatchConfig -> importBatchConfig(action.configText)
            MainAction.LocateHandled -> consumeLocateTarget()
            is MainAction.ShareQRCode -> {
                val bitmap = dataSource.share2QRCode(action.guid)
                _uiState.update { it.copy(shareQRCodeBitmap = bitmap) }
            }

            MainAction.DismissQRCodeDialog -> {
                _uiState.update { it.copy(shareQRCodeBitmap = null) }
            }

            MainAction.ToggleService,
            MainAction.TestCurrentServer,
            MainAction.ImportQRcode,
            MainAction.ImportClipboard,
            MainAction.ImportConfigLocal,
            is MainAction.ImportManually,
            MainAction.RestartService,
            MainAction.LocateSelectedServer,
            is MainAction.EditServer,
            is MainAction.ShareClipboard,
            is MainAction.ShareFullContent -> {
                // Handled by Activity via its onAction lambda
            }
        }
    }

    // ---------- Initialization ----------
    fun refreshFilvlessPreferences() {
        viewModelScope.launch(ioDispatcher) {
            preferenceMutex.withLock {
                val preferences = dataSource.readFilvlessPreferences()
                _uiState.update { it.copy(preferences = preferences, preferencesLoaded = true) }
            }
        }
    }

    private fun setFilvlessPreference(key: FilvlessPreference, enabled: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            preferenceMutex.withLock {
                if (dataSource.writeFilvlessPreference(key, enabled)) {
                    _uiState.update { it.copy(preferences = it.preferences.withPreference(key, enabled)) }
                } else {
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    suspend fun prepareColdLaunch(): Boolean {
        if (coldLaunchHandled) return false
        coldLaunchHandled = true
        initializationJob?.join()
        return withContext(ioDispatcher) {
            val preferences = dataSource.readFilvlessPreferences()
            val guid = dataSource.getSelectServer()
            val profile = guid?.let(dataSource::decodeServerConfig)
            val denied = profile?.let {
                dataSource.getServerGuidList(it.subscriptionId).any { id ->
                    dataSource.decodeServerConfig(id)?.remarks?.let(::isUnsupportedDeviceNotice) == true
                }
            } ?: true
            shouldAutoConnect(preferences.autoConnect, uiState.value.isRunning, guid, denied)
        }
    }

    fun connectionRequested() {
        if (uiState.value.connectionPending || uiState.value.isRunning) return
        _uiState.update { it.copy(connectionPending = true, connectionFailed = false) }
        connectionTimeoutJob?.cancel()
        connectionTimeoutJob = viewModelScope.launch {
            kotlinx.coroutines.delay(30_000)
            if (uiState.value.connectionPending) {
                _uiState.update { it.copy(connectionPending = false, connectionFailed = true) }
            }
        }
    }

    fun connectionCancelled() {
        connectionTimeoutJob?.cancel()
        _uiState.update { it.copy(connectionPending = false) }
    }

    private fun forgetSubscriptions() {
        if (uiState.value.isRunning || uiState.value.connectionPending || isLoading.value) return
        launchLoading {
            withContext(ioDispatcher) {
                preferenceMutex.withLock { dataSource.forgetSubscriptions() }
            }
            refreshFilvlessPreferences()
            setupGroupTab(forceRefresh = true).join()
        }
    }

    fun initialize() {
        if (initializationJob != null) return
        checkAppUpdate(false)
        refreshUiSettings()
        initializationJob = viewModelScope.launch(preloadDispatcher) {
            try {
                initialPageReady.await()
                delay(32)
                dataSource.initAssets()
                dataSource.syncSubscriptions()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                LogUtil.e(AppConfig.TAG, "Main background initialization failed", error)
            }
        }
    }

    private fun saveRouting(vpn: String, direct: String) {
        viewModelScope.launch(ioDispatcher) {
            try {
                val routes = com.v2ray.ang.handler.FilvlessRouting(vpn, direct)
                if (com.v2ray.ang.handler.FilvlessRoutingStore.save(routes)) {
                    _uiState.update { it.copy(routingVpn = vpn, routingDirect = direct, routingError = false, routingSaved = it.routingSaved + 1) }
                } else toastError(R.string.toast_failure)
            } catch (_: IllegalArgumentException) {
                _uiState.update { it.copy(routingError = true) }
            }
        }
    }

    private var lastAutomaticUpdateCheck: Long? = null

    fun checkAppUpdateOnResume() = checkAppUpdate(false)

    private fun checkAppUpdate(manual: Boolean) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (!manual && lastAutomaticUpdateCheck?.let { now - it < 30_000L } == true) return
        if (uiState.value.checkingAppUpdate) return
        lastAutomaticUpdateCheck = now
        _uiState.update { it.copy(checkingAppUpdate = true) }
        viewModelScope.launch {
            try {
                val result = com.v2ray.ang.handler.UpdateCheckerManager.checkForUpdate(true)
                _uiState.update { it.copy(appUpdate = result.takeIf { result.hasUpdate }) }
                try { com.v2ray.ang.handler.AppUpdateDownload.enqueue(getApplication(), result) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* Update screen provides retry without blocking VPN startup. */ }
                if (manual && !result.hasUpdate) toast(dataSource.getString(R.string.update_already_latest_version))
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (manual) toastError(R.string.toast_failure)
            } finally {
                _uiState.update { it.copy(checkingAppUpdate = false) }
            }
        }
    }

    fun refreshUiSettings() {
        viewModelScope.launch(ioDispatcher) {
            val routes = com.v2ray.ang.handler.FilvlessRoutingStore.read()
            _uiState.update { it.copy(routingVpn = routes.vpn, routingDirect = routes.direct) }
        }
        refreshFilvlessPreferences()
        _uiState.update {
            it.copy(
                confirmRemove = dataSource.getConfirmRemove(),
                doubleColumnDisplay = dataSource.getDoubleColumnDisplay()
            )
        }
    }

    // ---------- Group & server loading ----------
    private suspend fun buildServersCache(guids: List<String>): List<ServersCache> =
        guids.mapNotNull { guid ->
            currentCoroutineContext().ensureActive()
            val profile = dataSource.decodeServerConfig(guid) ?: return@mapNotNull null
            val affiliation = dataSource.decodeAffiliationInfo(guid)
            ServersCache(
                guid = guid,
                profile = profile.copy(),
                testDelayMillis = affiliation?.testDelayMillis ?: 0L
            )
        }

    private suspend fun loadGroup(
        groupId: String,
        forceRefresh: Boolean = false
    ): List<ServersCache> {
        val loadMutex = groupLoadMutexes.computeIfAbsent(groupId) { Mutex() }
        return loadMutex.withLock {
            if (!forceRefresh) {
                cacheMutex.withLock { groupDataCache[groupId]?.let { return@withLock it } }
            }
            val servers = buildServersCache(dataSource.getServerGuidList(groupId))
            currentCoroutineContext().ensureActive()
            cacheMutex.withLock { groupDataCache[groupId] = servers }
            servers
        }
    }

    private fun applyKeywordFilter(servers: List<ServersCache>): List<ServersCache> {
        val keyword = keywordFilter.trim()
        if (keyword.isEmpty()) return servers
        val regex = try {
            Regex(keyword, RegexOption.IGNORE_CASE)
        } catch (_: PatternSyntaxException) {
            return servers
        }
        return servers.filter { cache ->
            val profile = cache.profile
            profile.remarks.matchesPattern(regex, keyword) ||
                    profile.description.orEmpty().matchesPattern(regex, keyword) ||
                    profile.server.orEmpty().matchesPattern(regex, keyword) ||
                    profile.configType.name.matchesPattern(regex, keyword)
        }
    }

    private fun updateGroupUi(groupId: String, servers: List<ServersCache>) {
        if (uiState.value.selectedGroupId == groupId) {
            val subscriptionId = groupId.ifEmpty {
                servers.map { it.profile.subscriptionId }.distinct().singleOrNull().orEmpty()
            }
            val subscription = dataSource.getSubscriptionItem(subscriptionId)
            _uiState.update { state ->
                if (state.selectedGroupId != groupId) state else state.copy(
                    subscriptionExpiresAt = subscription?.expiresAtSeconds,
                    subscriptionUpdatedAt = subscription?.lastUpdated ?: -1,
                )
            }
        }
        val filteredServers = applyKeywordFilter(servers)
        mutableServerGroupState(groupId).value = ServerGroupUiState(
            servers = filteredServers,
            rows = buildServerRows(groupId, filteredServers)
        )
    }

    private fun buildServerRows(groupId: String, servers: List<ServersCache>): List<ServerRowUiModel> {
        val subscriptionRemarks = if (groupId.isEmpty()) {
            servers.asSequence()
                .map { it.profile.subscriptionId }
                .filter { it.isNotEmpty() }
                .distinct()
                .associateWith { subscriptionId ->
                    dataSource.getSubscriptionItem(subscriptionId)?.remarks.orEmpty()
                }
        } else {
            emptyMap()
        }
        return servers.map { server ->
            buildServerRowUiModel(
                server = server,
                subscriptionRemarks = subscriptionRemarks[server.profile.subscriptionId].orEmpty()
            )
        }
    }

    fun getSubscriptions(): List<SubscriptionCache> = dataSource.getSubscriptions()

    private fun resolveSelectedGroup(groups: List<GroupMapItem>): String {
        val current = uiState.value.selectedGroupId
        val resolved = when {
            groups.isEmpty() -> ""
            groups.any { it.id == current } -> current
            else -> groups.first().id
        }
        if (resolved != current) {
            dataSource.setSelectedSubscriptionId(resolved)
        }
        return resolved
    }

    private fun radialPreloadOrder(groups: List<GroupMapItem>, selectedIndex: Int): List<String> {
        if (groups.isEmpty()) return emptyList()
        val result = ArrayList<String>((groups.size - 1).coerceAtLeast(0))
        for (distance in 1 until groups.size) {
            val right = selectedIndex + distance
            val left = selectedIndex - distance
            if (right in groups.indices) result += groups[right].id
            if (left in groups.indices) result += groups[left].id
        }
        return result
    }

    fun setupGroupTab(forceRefresh: Boolean = false): Job {
        setupGroupJob?.cancel()
        preloadJob?.cancel()
        selectedGroupLoadJob?.cancel()

        return viewModelScope.launch(ioDispatcher) {
            try {
                if (forceRefresh) {
                    cacheMutex.withLock { groupDataCache.clear() }
                }
                val groups = dataSource.getSubscriptions().map {
                    GroupMapItem(id = it.guid, remarks = it.subscription.remarks)
                }
                val selectedGroup = resolveSelectedGroup(groups)
                val validIds = groups.mapTo(HashSet()) { it.id }
                groupUiFlows.keys.removeAll { it !in validIds }
                groupServerFlows.keys.removeAll { it !in validIds }
                groupLoadMutexes.keys.removeAll { it !in validIds }

                _uiState.update {
                    it.copy(
                        groups = groups,
                        selectedGroupId = selectedGroup,
                        selectedGuid = dataSource.getSelectServer(),
                    )
                }
                groups.forEach { mutableServerGroupState(it.id) }

                if (groups.isEmpty()) {
                    cacheMutex.withLock { groupDataCache.clear() }
                    return@launch
                }

                val selectedServers = loadGroup(selectedGroup, forceRefresh)
                updateGroupUi(selectedGroup, selectedServers)

                if (!initialPageReady.isCompleted) {
                    initialPageReady.complete(Unit)
                }

                val selectedIndex =
                    groups.indexOfFirst { it.id == selectedGroup }.coerceAtLeast(0)
                val preloadOrder = radialPreloadOrder(groups, selectedIndex)
                preloadJob = viewModelScope.launch(preloadDispatcher) {
                    preloadOrder.forEach { groupId ->
                        ensureActive()
                        delay(32)
                        val servers = loadGroup(groupId, forceRefresh)
                        updateGroupUi(groupId, servers)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to set up group tabs", error)
            } finally {
                if (!initialPageReady.isCompleted) {
                    initialPageReady.complete(Unit)
                }
            }
        }.also { setupGroupJob = it }
    }

    // ---------- Business actions (coroutine-based) ----------
    private fun importBatchConfig(configText: String) {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    val (count, countSub) = dataSource.importBatchConfig(
                        configText, uiState.value.selectedGroupId, true
                    )
                    dataSource.syncSubscriptions()
                    when {
                        count > 0 -> {
                            toast(dataSource.getString(R.string.title_import_config_count, count))
                            setupGroupTab(forceRefresh = true)
                        }

                        countSub > 0 -> setupGroupTab(forceRefresh = true)
                        else -> toastError(R.string.toast_failure)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Failed to import batch config", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun importConfigViaSub() {
        val subId = uiState.value.selectedGroupId
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    val result = if (subId.isEmpty()) {
                        dataSource.updateConfigViaSubAll()
                    } else {
                        val item = dataSource.getSubscriptionItem(subId) ?: return@withContext
                        dataSource.updateConfigViaSub(SubscriptionCache(subId, item))
                    }
                    when {
                        result.successCount + result.failureCount + result.skipCount == 0 ->
                            toast(R.string.title_update_subscription_no_subscription)

                        result.successCount > 0 && result.failureCount + result.skipCount == 0 ->
                            toast(
                                getQuantityString(
                                    R.plurals.title_update_config_count,
                                    result.configCount,
                                    result.configCount,
                                )
                            )

                        else ->
                            toast(dataSource.getString(R.string.title_update_subscription_result, result.configCount, result.successCount, result.failureCount, result.skipCount))
                    }
                    if (result.configCount > 0) {
                        setupGroupTab(forceRefresh = true)
                        refreshSelectedGuid()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Subscription update failed", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun exportAllAsync() {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    val groupId = uiState.value.selectedGroupId
                    val list = if (groupId.isEmpty() && keywordFilter.isEmpty()) {
                        dataSource.getServerGuidList("")
                    } else {
                        currentServers().map { it.guid }
                    }
                    val ret = dataSource.shareNonCustomConfigsToClipboard(list)
                    if (ret > 0) {
                        toast(dataSource.getString(R.string.title_export_config_count, ret))
                    } else {
                        toastError(R.string.toast_failure)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Export failed", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun removeAllServerAsync() {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    val count =
                        if (uiState.value.selectedGroupId.isEmpty() && keywordFilter.isEmpty()) {
                            dataSource.removeAllServer()
                        } else {
                            val guids = currentServers().map { it.guid }
                            guids.forEach { dataSource.removeServer(it) }
                            guids.size
                        }
                    viewModelScope.launch(ioDispatcher) {
                        cacheMutex.withLock { groupDataCache.clear() }
                    }
                    setupGroupTab(forceRefresh = true)
                    toast(dataSource.getString(R.string.title_del_config_count, count))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Delete all failed", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun removeDuplicateServerAsync() {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    val seen = HashSet<ProfileItem>()
                    val duplicates = ArrayList<String>()
                    currentServers().forEach { server ->
                        val profile = server.profile
                        if (!profile.configType.isComplexType()) {
                            val identity = profile.duplicateIdentity()
                            if (!seen.add(identity)) duplicates += server.guid
                        }
                    }
                    duplicates.forEach { dataSource.removeServer(it) }
                    setupGroupTab(forceRefresh = true)
                    toast(dataSource.getString(R.string.title_del_duplicate_config_count, duplicates.size))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Delete duplicate failed", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun removeInvalidServerAsync() {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    val count = removeInvalidServerInternal()
                    viewModelScope.launch(ioDispatcher) {
                        cacheMutex.withLock { groupDataCache.clear() }
                        setupGroupTab(forceRefresh = true)
                    }
                    toast(dataSource.getString(R.string.title_del_config_count, count))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Delete invalid failed", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun removeInvalidServerInternal(): Int {
        val visibleServersOnly =
            uiState.value.selectedGroupId.isNotEmpty() || keywordFilter.isNotBlank()
        return if (visibleServersOnly) {
            currentServers().sumOf { server ->
                dataSource.removeInvalidServerByGuid(server.guid)
            }
        } else {
            dataSource.removeInvalidServersInGroup("")
        }
    }

    private fun sortByTestResultsAsync() {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    sortByTestResultsInternal()
                    cacheMutex.withLock { groupDataCache.clear() }
                    setupGroupTab(forceRefresh = true)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Sort by test results failed", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun sortByTestResultsInternal() {
        val subs = if (uiState.value.selectedGroupId.isEmpty()) {
            dataSource.getSubsList()
        } else {
            listOf(uiState.value.selectedGroupId)
        }
        subs.forEach { dataSource.sortByTestResultsForSub(it) }
    }

    fun subscriptionIdChanged(id: String) {
        if (_uiState.value.groups.none { it.id == id }) return
        mutableServerGroupState(id)
        if (uiState.value.selectedGroupId != id) {
            dataSource.setSelectedSubscriptionId(id)
            _uiState.update { it.copy(selectedGroupId = id) }
        }
        selectedGroupLoadJob?.cancel()
        selectedGroupLoadJob = viewModelScope.launch(ioDispatcher) {
            try {
                updateGroupUi(id, loadGroup(id))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to load selected group: $id", error)
            }
        }
    }

    fun reloadServerList() {
        val groupId = uiState.value.selectedGroupId
        selectedGroupLoadJob?.cancel()
        selectedGroupLoadJob = viewModelScope.launch(ioDispatcher) {
            updateGroupUi(groupId, loadGroup(groupId, forceRefresh = true))
        }
    }

    fun reloadAllGroups(groupIds: List<String>) {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch(preloadDispatcher) {
            val selected = uiState.value.selectedGroupId
            val order = buildList {
                if (selected in groupIds) add(selected)
                addAll(groupIds.filter { it != selected })
            }
            order.forEachIndexed { index, groupId ->
                ensureActive()
                if (index > 0) delay(32)
                updateGroupUi(groupId, loadGroup(groupId, forceRefresh = true))
            }
        }
    }

    fun filterConfig(keyword: String) {
        if (keyword == keywordFilter) return
        keywordFilter = keyword
        filterJob?.cancel()
        filterJob = viewModelScope.launch(defaultDispatcher) {
            delay(300)
            val snapshot = cacheMutex.withLock { groupDataCache.toMap() }
            ensureActive()
            snapshot.forEach { (groupId, servers) ->
                ensureActive()
                updateGroupUi(groupId, servers)
            }
        }
    }

    fun updateSelectedGuid(guid: String) {
        dataSource.setSelectServer(guid)
        _uiState.update { it.copy(selectedGuid = guid) }
    }

    fun refreshSelectedGuid() {
        _uiState.update { it.copy(selectedGuid = dataSource.getSelectServer()) }
    }

    fun removeServerAndRefresh(guid: String) {
        if (guid == uiState.value.selectedGuid) {
            toast(R.string.toast_action_not_allowed)
            return
        }
        viewModelScope.launch(ioDispatcher) {
            dataSource.removeServer(guid)
            cacheMutex.withLock { groupDataCache.clear() }
            setupGroupTab(forceRefresh = true).join()
        }
    }

    fun moveServer(groupId: String, fromPosition: Int, toPosition: Int) {
        val groupState = mutableServerGroupState(groupId).value
        val servers = groupState.servers.toMutableList()
        if (!servers.moveItem(fromPosition, toPosition)) return
        val rows = groupState.rows.toMutableList()
        rows.moveItem(fromPosition, toPosition)
        val guids = servers.map { it.guid }
        mutableServerGroupState(groupId).value = ServerGroupUiState(servers, rows)
        // A drag emits several moves; serialize writes so an older order cannot overwrite a newer one.
        val previousPersistenceJob = serverOrderPersistenceJobs[groupId]
        serverOrderPersistenceJobs[groupId] = viewModelScope.launch(ioDispatcher) {
            previousPersistenceJob?.join()
            dataSource.encodeServerList(guids, groupId)
            cacheMutex.withLock { groupDataCache[groupId] = servers }
        }
    }

    // ---------- Testing ----------
    fun cancelAllPing() {
        bulkTestJob?.cancel()
        bulkTestJob = null
        testRequests.cancelBulk()
        testRequests.invalidateCurrent()
        cancelPendingTestResults()
        resetTestStatus()
        dataSource.cancelAllPing()
    }

    private fun resetTestStatus() {
        _uiState.update {
            it.copy(
                isTesting = testRequests.isTesting,
                status = if (testRequests.isTesting) MainStatus.Testing
                else if (it.isRunning) MainStatus.Connected else MainStatus.Disconnected
            )
        }
    }

    fun testAllRealPing(onlyTcp: Boolean = false) {
        cancelAllPing()
        val groupId = uiState.value.selectedGroupId
        val servers = currentServers()
        if (servers.isEmpty()) {
            return
        }
        val serverGuids = servers.map { it.guid }
        mutableServerGroupState(groupId).update { current ->
            current.copy(
                servers = current.servers.map { server ->
                    if (server.testDelayMillis == 0L) server
                    else server.copy(testDelayMillis = 0L)
                },
                rows = current.rows.map { row ->
                    if (row.testDelayMillis == 0L) row
                    else row.copy(testDelayMillis = 0L)
                }
            )
        }
        val request = testRequests.beginBulk(groupId)
        val message = TestServiceMessage(
            key = AppConfig.MSG_MEASURE_CONFIG_START,
            subscriptionId = groupId,
            serverGuids = if (keywordFilter.isNotEmpty()) serverGuids else emptyList(),
            onlyTcp = onlyTcp
        )
        _uiState.update {
            it.copy(
                isTesting = true,
                status = MainStatus.Testing
            )
        }
        bulkTestJob = viewModelScope.launch {
            withContext(ioDispatcher) {
                dataSource.clearAllTestDelayResults(serverGuids)
                val resetGuids = serverGuids.toHashSet()
                cacheMutex.withLock {
                    groupDataCache[groupId]?.let { cached ->
                        groupDataCache[groupId] = cached.map { server ->
                            if (server.guid !in resetGuids || server.testDelayMillis == 0L) server
                            else server.copy(testDelayMillis = 0L)
                        }
                    }
                }
            }
            dataSource.sendMsg2TestService(message, request.id)
        }
    }

    private fun cancelPendingTestResults() {
        testResultFlushJob?.cancel()
        testResultFlushJob = null
        pendingTestResults.clear()
    }

    fun testCurrentServerRealPing() {
        if (!uiState.value.isRunning) return
        val requestId = testRequests.beginCurrent()
        _uiState.update { it.copy(isTesting = true, status = MainStatus.Testing) }
        dataSource.testCurrentServerRealPing(requestId)
    }

    private fun onTestsFinished(requestId: String) {
        if (testRequests.completeBulk(requestId) == null) return
        resetTestStatus()
        viewModelScope.launch(ioDispatcher) {
            cacheMutex.withLock { groupDataCache.clear() }
            reloadAllGroups(_uiState.value.groups.map { it.id })
        }
    }

    fun triggerLocateSelectedServer() {
        val selected = dataSource.getSelectServer() ?: return
        val profile = dataSource.decodeServerConfig(selected) ?: return
        val groupId = profile.subscriptionId
        if (_uiState.value.groups.none { it.id == groupId }) return
        viewModelScope.launch(ioDispatcher) {
            updateGroupUi(groupId, loadGroup(groupId))
            if (_uiState.value.selectedGroupId != groupId) {
                dataSource.setSelectedSubscriptionId(groupId)
            }
            val target = LocateTarget(groupId, selected)
            _uiState.update {
                it.copy(selectedGroupId = groupId, locateTarget = target)
            }
        }
    }

    private fun consumeLocateTarget() {
        _uiState.update { it.copy(locateTarget = null) }
    }

    // ---------- Running state ----------
    private fun updateRunningState(running: Boolean, clearTestingText: Boolean = true) {
        if (!running && !clearTestingText && uiState.value.connectionPending) return
        connectionTimeoutJob?.cancel()
        _uiState.update { it.copy(connectionPending = false, connectionFailed = false) }
        if (!running) {
            sessionTimerJob?.cancel()
            sessionTimerJob = null
            _uiState.update { it.copy(elapsedSeconds = 0L) }
        } else if (sessionTimerJob?.isActive != true) {
            sessionTimerJob = viewModelScope.launch {
                while (true) {
                    val startedAt = withContext(ioDispatcher) { dataSource.connectionStartedAtMillis() }
                    val elapsed = connectionElapsedSeconds(startedAt, android.os.SystemClock.elapsedRealtime())
                    _uiState.update { it.copy(elapsedSeconds = elapsed) }
                    kotlinx.coroutines.delay(1_000)
                }
            }
        }
        if (!running || clearTestingText) testRequests.invalidateCurrent()
        _uiState.update { state ->
            state.copy(
                connectionHealth = if (!running) com.v2ray.ang.service.ConnectionHealth.IDLE
                    else if (!state.isRunning) com.v2ray.ang.service.ConnectionHealth.CHECKING else state.connectionHealth,
                isRunning = running,
                isTesting = testRequests.isTesting,
                status = if (!clearTestingText && state.isRunning == running) state.status
                else if (running) MainStatus.Connected else MainStatus.Disconnected
            )
        }
    }

    override fun onCleared() {
        setupGroupJob?.cancel()
        preloadJob?.cancel()
        selectedGroupLoadJob?.cancel()
        reloadJob?.cancel()
        filterJob?.cancel()
        cancelAllPing()
        dataSource.close()
        super.onCleared()
    }

    // ---------- Factory ----------
    class Factory(private val application: Application, private val dataSource: MainDataSource) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(application, dataSource) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    private companion object {
        const val TEST_RESULT_FLUSH_INTERVAL_MS = 500L
    }
}
