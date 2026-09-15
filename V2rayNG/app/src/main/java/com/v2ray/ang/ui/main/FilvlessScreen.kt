package com.v2ray.ang.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R

private val Ink = Color(0xFF0B080F)
private val Violet = Color(0xFFB59AD8)
private val Muted = Color(0xFFB8B0C5)
private val Card = Color(0xFF1A1521)

/** Filvless presentation; all connection and subscription operations stay in MainAction. */
@Composable
fun FilvlessScreen(
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onNavigate: (MainDestination) -> Unit,
) {
    val state by mainViewModel.uiState.collectAsStateWithLifecycle()
    val loading by mainViewModel.isLoading.collectAsStateWithLifecycle()
    val serverFlow = remember(state.selectedGroupId) { mainViewModel.serversForGroup(state.selectedGroupId) }
    val servers by serverFlow.collectAsStateWithLifecycle()
    val providerDenied = servers.any { isUnsupportedDeviceNotice(it.profile.remarks) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var importing by rememberSaveable { mutableStateOf(false) }
    var subscriptionText by rememberSaveable { mutableStateOf("") }
    BackHandler(settings) { settings = false }

    Box(Modifier.fillMaxSize().background(Ink)) {
        Box(Modifier.fillMaxWidth().height(530.dp).background(
            Brush.radialGradient(listOf(Color(0xFF42176B), Color(0xFF241035), Ink),
                center = Offset(500f, 250f), radius = 1150f)))
        LazyColumn(
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { settings = !settings }, modifier = Modifier.size(50.dp).background(Card, CircleShape)) {
                        Icon(painterResource(if (settings) R.drawable.ic_arrow_back_24dp else R.drawable.ic_settings_24dp),
                            stringResource(if (settings) R.string.fv_back else R.string.title_settings), tint = Color.White)
                    }
                    Text(stringResource(if (settings) R.string.title_settings else R.string.fv_brand),
                        Modifier.weight(1f).padding(horizontal = 22.dp), color = Color.White,
                        fontSize = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
                    if (!settings) TextButton(onClick = { importing = true }, enabled = !loading) {
                        Text(stringResource(R.string.fv_add), color = Violet)
                    }
                }
            }
            if (settings) {
                item { FilvlessSetting(R.string.fv_subscription, R.string.fv_subscription_hint) { onNavigate(MainDestination.Subscriptions) } }
                item { FilvlessSetting(R.string.fv_tunnel, R.string.fv_tunnel_hint) { onNavigate(MainDestination.Routing) } }
                item { FilvlessSetting(R.string.fv_apps, R.string.fv_apps_hint) { onNavigate(MainDestination.PerAppProxy) } }
                item { FilvlessSetting(R.string.fv_advanced, R.string.fv_advanced_hint) { onNavigate(MainDestination.Settings) } }
                item { FilvlessSetting(R.string.fv_diagnostics, R.string.fv_diagnostics_hint) { onNavigate(MainDestination.Logcat) } }
                item {
                    Column(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(26.dp)).padding(22.dp)) {
                        Text(stringResource(R.string.fv_about), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        Text(stringResource(R.string.fv_credits), color = Muted, lineHeight = 23.sp)
                        TextButton(onClick = { onNavigate(MainDestination.About) }) { Text(stringResource(R.string.fv_licenses), color = Violet) }
                    }
                }
            } else {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 30.dp, bottom = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.fv_private), color = Muted, fontSize = 13.sp, letterSpacing = 3.sp)
                        Spacer(Modifier.height(20.dp))
                        Text(stringResource(R.string.fv_timer, state.elapsedSeconds / 3600,
                            state.elapsedSeconds / 60 % 60, state.elapsedSeconds % 60),
                            color = Color.White, fontSize = 36.sp, letterSpacing = 5.sp)
                        Spacer(Modifier.height(18.dp))
                        Text(stringResource(if (state.isRunning) R.string.fv_connected else R.string.fv_disconnected),
                            Modifier.background(Color(0x663D2451), CircleShape).padding(horizontal = 22.dp, vertical = 10.dp),
                            color = Color.White, fontSize = 17.sp)
                        Spacer(Modifier.height(30.dp))
                        val powerLabel = stringResource(if (state.isRunning) R.string.fv_disconnect else R.string.fv_connect)
                        Box(Modifier.size(144.dp).background(Brush.verticalGradient(listOf(Ink, Color(0xFF381953))), CircleShape)
                            .border(2.dp, if (state.isRunning) Violet else Color(0xFFE8DEEF), CircleShape)
                            .clickable(enabled = state.isRunning || (!loading && !providerDenied && state.selectedGuid != null), role = Role.Button) { onAction(MainAction.ToggleService) }
                            .semantics { contentDescription = powerLabel }, contentAlignment = Alignment.Center) {
                            Canvas(Modifier.size(68.dp)) {
                                val stroke = 4.dp.toPx()
                                drawArc(Color.White, -48f, 276f, false, Offset(stroke, stroke),
                                    Size(size.width - stroke * 2, size.height - stroke * 2), style = Stroke(stroke, cap = StrokeCap.Round))
                                drawLine(Color.White, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height * .43f), stroke, StrokeCap.Round)
                            }
                        }
                        Spacer(Modifier.height(22.dp))
                        val selected = servers.firstOrNull { it.guid == state.selectedGuid }
                        Text(selected?.profile?.remarks ?: stringResource(R.string.fv_select_server), color = Muted, fontSize = 15.sp)
                        if (state.isTesting) Text(mainViewModel.formatStatus(state.status), color = Violet, fontSize = 13.sp)
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.fv_servers), Modifier.weight(1f), color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { onAction(MainAction.UpdateSubscriptions) }, enabled = !loading) { Text(stringResource(R.string.fv_refresh), color = Violet) }
                    }
                }
                if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Violet, trackColor = Card) }
                if (state.groups.size > 1) item {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.groups.forEach { group ->
                            FilterChip(selected = state.selectedGroupId == group.id, onClick = { onAction(MainAction.SelectGroup(group.id)) },
                                label = { Text(group.remarks) })
                        }
                    }
                }
                if ((servers.isEmpty() || providerDenied) && !loading) item {
                    Column(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(26.dp)).padding(24.dp)) {
                        Text(stringResource(if (providerDenied) R.string.fv_denied_title else R.string.fv_empty_title), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(if (providerDenied) R.string.fv_denied_hint else R.string.fv_empty_hint), color = Muted, lineHeight = 24.sp)
                        Spacer(Modifier.height(18.dp))
                        Button(onClick = { importing = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF522680))) {
                            Text(stringResource(R.string.fv_import))
                        }
                    }
                }
                items(if (providerDenied) emptyList() else servers, key = { it.guid }) { server ->
                    Row(Modifier.fillMaxWidth().background(if (state.selectedGuid == server.guid) Color(0xFF2A193A) else Color.Transparent, RoundedCornerShape(20.dp))
                        .selectable(selected = state.selectedGuid == server.guid, role = Role.RadioButton, onClick = { onAction(MainAction.SelectServer(server.guid)) })
                        .padding(horizontal = 14.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).background(Color(0xFF30203F), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                            Canvas(Modifier.size(20.dp)) { drawCircle(Violet, style = Stroke(2.dp.toPx())); drawLine(Violet, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx()) }
                        }
                        Column(Modifier.weight(1f).padding(start = 16.dp)) {
                            Text(server.profile.remarks, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                            Text(stringResource(R.string.fv_protocol, server.profile.configType.name, server.profile.network.orEmpty().uppercase()), color = Muted, fontSize = 13.sp)
                        }
                        if (state.selectedGuid == server.guid) Text(stringResource(R.string.fv_selected), color = Violet, fontSize = 12.sp)
                    }
                }
                if (servers.isNotEmpty() && !providerDenied) item {
                    TextButton(onClick = { onAction(MainAction.TestRealAllServers) }, enabled = !state.isTesting && !loading) {
                        Text(stringResource(R.string.fv_test), color = Violet)
                    }
                }
            }
        }
    }
    if (importing) AlertDialog(
        onDismissRequest = { importing = false; subscriptionText = "" },
        containerColor = Card,
        title = { Text(stringResource(R.string.fv_import), color = Color.White) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).imePadding()) {
                Text(stringResource(R.string.fv_import_hint), color = Muted)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = subscriptionText, onValueChange = { subscriptionText = it },
                    label = { Text(stringResource(R.string.fv_link)) }, maxLines = 5)
            }
        },
        confirmButton = { TextButton(enabled = subscriptionText.isNotBlank() && !loading, onClick = {
            onAction(MainAction.ImportBatchConfig(subscriptionText.trim())); subscriptionText = ""; importing = false
        }) { Text(stringResource(R.string.fv_import), color = Violet) } },
        dismissButton = { TextButton(onClick = { importing = false; subscriptionText = "" }) { Text(stringResource(R.string.fv_cancel), color = Muted) } },
    )
}

@Composable
private fun FilvlessSetting(title: Int, hint: Int, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(26.dp)).clickable(role = Role.Button, onClick = onClick).padding(22.dp)) {
        Text(stringResource(title), color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Text(stringResource(hint), color = Muted, lineHeight = 23.sp)
    }
}
