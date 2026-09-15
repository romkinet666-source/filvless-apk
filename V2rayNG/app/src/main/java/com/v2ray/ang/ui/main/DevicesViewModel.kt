package com.v2ray.ang.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.handler.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class DeviceSubscription(val id: String, val title: String)
internal data class DevicesState(val subscriptions: List<DeviceSubscription> = emptyList(),
    val selectedId: String? = null, val snapshot: DeviceSnapshot? = null,
    val loading: Boolean = true, val error: String? = null, val removed: Boolean = false)

internal class DevicesViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(DevicesState())
    val state = mutableState.asStateFlow()
    private var credentials: Map<String, String> = emptyMap()

    init {
        viewModelScope.launch {
            val subscriptions = withContext(Dispatchers.IO) { MmkvManager.decodeSubscriptions().filter { it.subscription.enabled } }
            credentials = subscriptions.mapNotNull { sub -> deviceCredential(sub.subscription.url)?.let { sub.guid to it } }.toMap()
            val choices = subscriptions.filter { it.guid in credentials }.map { DeviceSubscription(it.guid, it.subscription.remarks) }
            mutableState.update { it.copy(subscriptions = choices, selectedId = choices.firstOrNull()?.id, loading = false) }
            refresh()
        }
    }

    fun select(id: String) {
        if (state.value.loading || id !in credentials || id == state.value.selectedId) return
        mutableState.update { it.copy(selectedId = id, snapshot = null, error = null, removed = false) }
        refresh()
    }

    fun refresh(deleteId: String? = null) {
        if (state.value.loading) return
        val id = state.value.selectedId ?: return
        val credential = credentials[id] ?: return
        if (deleteId != null && state.value.snapshot?.devices?.none { it.id == deleteId } != false) return
        mutableState.update { it.copy(loading = true, error = null, removed = false) }
        viewModelScope.launch {
            try {
                // Re-read before each operation so removed or changed subscriptions cannot use a stale credential.
                val current = withContext(Dispatchers.IO) { MmkvManager.decodeSubscription(id) }
                if (current?.enabled != true || deviceCredential(current.url) != credential) throw DeviceApiException("invalid_subscription")
                val snapshot = FilvlessDevices.load(credential, deleteId)
                mutableState.update { it.copy(snapshot = snapshot, removed = deleteId != null) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update { it.copy(error = (error as? DeviceApiException)?.code ?: "network") }
            } finally {
                mutableState.update { it.copy(loading = false) }
            }
        }
    }
}
