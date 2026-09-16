package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.ConnectionTestResult
import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.dto.LocateTarget

/** Locale-neutral state formatted only when it reaches the main UI. */
sealed interface MainStatus {
    data object Disconnected : MainStatus
    data object Connected : MainStatus
    data object Testing : MainStatus
    data class TestProgress(val progress: String) : MainStatus
    data class ConnectionTest(val result: ConnectionTestResult) : MainStatus
}

/**
 * Main UI state
 */
data class MainUiState(
    val routingVpn: String = "",
    val routingDirect: String = "",
    val routingError: Boolean = false,
    val routingSaved: Int = 0,
    val appUpdate: com.v2ray.ang.dto.CheckUpdateResult? = null,
    val checkingAppUpdate: Boolean = false,
    val preferences: FilvlessPreferences = FilvlessPreferences(),
    val preferencesLoaded: Boolean = false,
    val connectionHealth: com.v2ray.ang.service.ConnectionHealth = com.v2ray.ang.service.ConnectionHealth.IDLE,
    val connectionPending: Boolean = false,
    val connectionFailed: Boolean = false,
    val subscriptionExpiresAt: Long? = null,
    val subscriptionUpdatedAt: Long = -1,
    val subscriptionUpdateFailed: Boolean = false,
    val groups: List<GroupMapItem> = emptyList(),
    val selectedGroupId: String = "",
    val selectedGuid: String? = null,
    val isRunning: Boolean = false,
    val elapsedSeconds: Long = 0L,
    val isTesting: Boolean = false,
    val status: MainStatus = MainStatus.Disconnected,
    val locateTarget: LocateTarget? = null,
    val confirmRemove: Boolean = false,
    val doubleColumnDisplay: Boolean = false,
    val shareQRCodeBitmap: android.graphics.Bitmap? = null
)

/**
 * All possible user interaction intents
 */
sealed interface MainAction {
    data class SetUpdateDownloadPolicy(val policy: com.v2ray.ang.handler.AppUpdatePolicy) : MainAction
    data class SetPreference(val key: FilvlessPreference, val enabled: Boolean) : MainAction
    data class SetLanguage(val code: String) : MainAction
    data object ForgetSubscriptions : MainAction
    data object OpenSupport : MainAction
    data object OpenReview : MainAction
    data class SaveRouting(val vpn: String, val direct: String) : MainAction
    data object CheckAppUpdate : MainAction
    data object Initialize : MainAction
    data object RefreshGroups : MainAction
    data object ToggleService : MainAction
    data object TestCurrentServer : MainAction
    data object TestAllServers : MainAction
    data object TestRealAllServers : MainAction
    data object CancelTesting : MainAction
    data object RemoveAllServers : MainAction
    data object RemoveDuplicateServers : MainAction
    data object RemoveInvalidServers : MainAction
    data object SortByTestResults : MainAction
    data object UpdateSubscriptions : MainAction
    data object ExportAll : MainAction

    data object ImportQRcode : MainAction
    data object ImportClipboard : MainAction
    data object ImportConfigLocal : MainAction
    data class ImportManually(val type: Int) : MainAction
    data object RestartService : MainAction
    data object LocateSelectedServer : MainAction

    data class SelectGroup(val groupId: String) : MainAction
    data class SelectServer(val guid: String) : MainAction
    data class RemoveServer(val guid: String) : MainAction
    data class EditServer(val guid: String, val profile: com.v2ray.ang.dto.entities.ProfileItem) : MainAction
    data class Search(val query: String) : MainAction
    data class ShareQRCode(val guid: String) : MainAction
    data class ShareClipboard(val guid: String) : MainAction
    data class ShareFullContent(val guid: String) : MainAction
    data object DismissQRCodeDialog : MainAction

    data class ImportBatchConfig(val configText: String) : MainAction

    data object LocateHandled : MainAction
}
