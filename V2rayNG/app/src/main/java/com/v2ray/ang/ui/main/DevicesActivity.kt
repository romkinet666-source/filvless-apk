package com.v2ray.ang.ui.main

import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.handler.FilvlessDevice
import com.v2ray.ang.ui.base.BaseComponentActivity
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class DevicesActivity : BaseComponentActivity() {
    private val model: DevicesViewModel by viewModels()
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }
    @Composable override fun ScreenContent() {
        val state by model.state.collectAsStateWithLifecycle()
        DevicesContent(state, onBack = { finish() }, onRefresh = { model.refresh() },
            onSelect = model::select, onDelete = { model.refresh(it) })
    }
}

@Composable
internal fun DevicesContent(state: DevicesState, onBack: () -> Unit, onRefresh: () -> Unit,
    onSelect: (String) -> Unit, onDelete: (String) -> Unit) {
    var picker by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<FilvlessDevice?>(null) }
    Column(Modifier.fillMaxSize().background(Color(0xFF0B080F)).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.background(FilvlessCard, CircleShape)) {
                Icon(painterResource(R.drawable.ic_arrow_back_24dp), stringResource(R.string.fv_back), tint = Color.White)
            }
            Text(stringResource(R.string.fv_devices), Modifier.weight(1f).padding(start = 12.dp),
                color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = onRefresh, enabled = !state.loading && state.selectedId != null) {
                Text(stringResource(R.string.fv_refresh), color = FilvlessAccent)
            }
        }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = FilvlessAccent)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Text(stringResource(R.string.fv_devices_hint), color = FilvlessMuted, fontSize = 14.sp, lineHeight = 21.sp)
            }
            if (state.subscriptions.size > 1) item {
                Box {
                    OutlinedButton(onClick = { picker = true }, enabled = !state.loading) {
                        Text(state.subscriptions.firstOrNull { it.id == state.selectedId }?.title.orEmpty(), color = FilvlessAccent)
                    }
                    DropdownMenu(expanded = picker, onDismissRequest = { picker = false }) {
                        state.subscriptions.forEach { sub -> DropdownMenuItem(text = { Text(sub.title) },
                            onClick = { picker = false; onSelect(sub.id) }) }
                    }
                }
            }
            if (!state.loading && state.subscriptions.isEmpty()) item {
                DeviceMessage(stringResource(R.string.fv_devices_no_subscription))
            }
            state.error?.let { error -> item {
                val message = when (error) {
                    "panel_permissions" -> R.string.fv_devices_permissions
                    "invalid_subscription" -> R.string.fv_devices_invalid
                    "rate_limited" -> R.string.fv_devices_rate
                    "device_not_found" -> R.string.fv_devices_missing
                    else -> R.string.fv_devices_network
                }
                DeviceMessage(stringResource(message))
                TextButton(onClick = onRefresh, enabled = !state.loading) { Text(stringResource(R.string.fv_devices_retry), color = FilvlessAccent) }
            } }
            if (state.removed) item { Text(stringResource(R.string.fv_devices_removed), color = FilvlessAccent) }
            state.snapshot?.let { snapshot ->
                item {
                    Text(if (snapshot.limit != null) stringResource(R.string.fv_devices_count_limit, snapshot.devices.size, snapshot.limit)
                        else stringResource(R.string.fv_devices_count, snapshot.devices.size),
                        color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                }
                if (snapshot.devices.isEmpty()) item { DeviceMessage(stringResource(R.string.fv_devices_empty)) }
                items(snapshot.devices, key = { it.id }) { device ->
                    Column(Modifier.fillMaxWidth().background(FilvlessCard, RoundedCornerShape(24.dp)).padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.fv_phone), null, tint = FilvlessAccent, modifier = Modifier.size(28.dp))
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(device.model.ifBlank { stringResource(R.string.fv_devices_unknown) }, color = Color.White,
                                    fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                                Text(listOf(device.platform, device.osVersion).filter { it.isNotBlank() }.joinToString(" "),
                                    color = FilvlessMuted, fontSize = 13.sp)
                            }
                        }
                        if (device.isCurrent) Text(stringResource(R.string.fv_devices_current), Modifier.padding(top = 10.dp),
                            color = FilvlessAccent, fontSize = 13.sp)
                        deviceDate(device.updatedAt)?.let { date ->
                            Text(stringResource(R.string.fv_devices_last_request, date), Modifier.padding(top = 8.dp), color = FilvlessMuted, fontSize = 12.sp)
                        }
                        TextButton(onClick = { deleting = device }, enabled = !state.loading && state.error == null,
                            modifier = Modifier.align(Alignment.End)) {
                            Text(stringResource(R.string.fv_devices_remove), color = Color(0xFFE5A2AB))
                        }
                    }
                }
            }
        }
    }
    deleting?.let { device -> AlertDialog(onDismissRequest = { deleting = null }, containerColor = FilvlessCard,
        title = { Text(stringResource(R.string.fv_devices_confirm_title)) },
        text = { Text(stringResource(R.string.fv_devices_confirm, device.model.ifBlank { stringResource(R.string.fv_devices_unknown) })) },
        confirmButton = { TextButton(onClick = { deleting = null; onDelete(device.id) }) { Text(stringResource(R.string.fv_devices_remove)) } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.fv_cancel)) } }) }
}

@Composable private fun DeviceMessage(text: String) {
    Text(text, Modifier.fillMaxWidth().background(FilvlessCard, RoundedCornerShape(20.dp)).padding(18.dp),
        color = FilvlessMuted, fontSize = 15.sp, lineHeight = 23.sp)
}

private fun deviceDate(value: String): String? = runCatching {
    val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    val date = parser.parse(value.take(19)) ?: return null
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(date)
}.getOrNull()
