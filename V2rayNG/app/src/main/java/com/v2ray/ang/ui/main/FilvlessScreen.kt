package com.v2ray.ang.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.AppConfig

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
    val allServers by serverFlow.collectAsStateWithLifecycle()
    val deniedGroups = allServers.filter { isUnsupportedDeviceNotice(it.profile.remarks) }.map { it.profile.subscriptionId }.toSet()
    val servers = allServers.filter { it.profile.subscriptionId !in deniedGroups }
    val providerDenied = deniedGroups.isNotEmpty() && servers.isEmpty()
    val context = LocalContext.current
    var settings by rememberSaveable { mutableStateOf(false) }
    var importing by rememberSaveable { mutableStateOf(false) }
    var subscriptionText by rememberSaveable { mutableStateOf("") }
    var languagePicker by rememberSaveable { mutableStateOf(false) }
    var subscriptionDialog by rememberSaveable { mutableStateOf(false) }
    var aboutDialog by rememberSaveable { mutableStateOf(false) }
    var forgetDialog by rememberSaveable { mutableStateOf(false) }
    val view = LocalView.current
    val feedback: () -> Unit = { if (state.preferences.haptics) view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK) }
    val act: (MainAction) -> Unit = { action ->
        if (action == MainAction.ToggleService) connectionHaptic(context, state.preferences.haptics) else feedback()
        onAction(action)
    }
    val openImport: () -> Unit = {
        feedback()
        subscriptionText = runCatching { com.v2ray.ang.util.Utils.getClipboard(context).orEmpty().trim() }.getOrDefault("")
        importing = true
    }
    val glow by animateColorAsState(
        when {
            state.isRunning -> Color(0xFF812DD1)
            state.connectionPending -> Color(0xFF5A218D)
            else -> Color(0xFF36154F)
        },
        animationSpec = if (state.preferences.visualEffects) tween(950) else snap(), label = "connectionGlow",
    )
    val powerGlow by animateColorAsState(
        if (state.isRunning) Color(0xFF8B39D0) else if (state.connectionPending) Color(0xFF61269A) else Color(0xFF381953),
        animationSpec = if (state.preferences.visualEffects) tween(800) else snap(), label = "powerGlow",
    )
    val connectionEmphasis by animateFloatAsState(
        if (state.preferences.visualEffects && (state.isRunning || state.connectionPending)) 1f else 0f,
        animationSpec = if (state.preferences.visualEffects) tween(800) else snap(), label = "connectionEmphasis",
    )
    BackHandler(settings) { settings = false }

    Box(Modifier.fillMaxSize().background(Ink)) {
        if (!settings && state.preferences.visualEffects) {
            Box(Modifier.fillMaxWidth().height(530.dp).drawWithCache {
                val brush = Brush.radialGradient(listOf(glow, Color(0xFF1C1028), Ink),
                    center = Offset(size.width * .5f, size.height * .22f), radius = size.height * .8f)
                onDrawBehind { drawRect(brush) }
            })
        }
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp).height(56.dp)) {
                IconButton(onClick = { feedback(); settings = !settings },
                    modifier = Modifier.align(Alignment.CenterStart).size(50.dp).background(Card, CircleShape)) {
                    Icon(painterResource(if (settings) R.drawable.ic_arrow_back_24dp else R.drawable.ic_settings_24dp),
                        stringResource(if (settings) R.string.fv_back else R.string.title_settings), tint = Color.White)
                }
                Text(stringResource(if (settings) R.string.title_settings else R.string.fv_brand),
                    Modifier.align(Alignment.Center).then(if (settings) Modifier.background(Card, CircleShape).padding(horizontal = 18.dp, vertical = 12.dp) else Modifier),
                    color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold,
                    letterSpacing = if (settings) 0.sp else 2.sp)
                if (!settings) TextButton(onClick = { openImport() }, enabled = !loading,
                    modifier = Modifier.align(Alignment.CenterEnd)) { Text(stringResource(R.string.fv_add), color = Violet) }
            }
            if (!settings) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.fv_timer, state.elapsedSeconds / 3600,
                            state.elapsedSeconds / 60 % 60, state.elapsedSeconds % 60),
                            color = Color.White, fontSize = 27.sp, letterSpacing = 3.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(when {
                            state.connectionPending -> R.string.fv_connecting
                            state.connectionFailed -> R.string.fv_connection_failed
                            state.isRunning -> R.string.fv_connected
                            else -> R.string.fv_disconnected
                        }),
                            Modifier.background(Color(0x663D2451), CircleShape).padding(horizontal = 18.dp, vertical = 6.dp),
                            color = Color.White, fontSize = 14.sp)
                        Spacer(Modifier.height(12.dp))
                        val powerLabel = stringResource(if (state.isRunning) R.string.fv_disconnect else R.string.fv_connect)
                        Box(Modifier.size(100.dp).graphicsLayer {
                            scaleX = 1f + .04f * connectionEmphasis
                            scaleY = 1f + .04f * connectionEmphasis
                        }.drawWithCache {
                            val radius = size.width * .85f
                            val halo = Brush.radialGradient(listOf(powerGlow.copy(alpha = .65f * connectionEmphasis), Color.Transparent),
                                center = Offset(size.width / 2, size.height / 2), radius = radius)
                            onDrawBehind { drawCircle(halo, radius = radius) }
                        }.background(Brush.verticalGradient(listOf(Ink, if (state.preferences.visualEffects) powerGlow else Ink)), CircleShape)
                            .border(2.dp, if (state.isRunning) Color(0xFFE5C7FF) else Color(0xFFE8DEEF), CircleShape)
                            .clickable(enabled = !state.connectionPending && (state.isRunning || (!loading && servers.any { it.guid == state.selectedGuid })), role = Role.Button) { act(MainAction.ToggleService) }
                            .semantics { contentDescription = powerLabel }, contentAlignment = Alignment.Center) {
                            if (state.connectionPending) CircularProgressIndicator(Modifier.size(44.dp), color = Violet, strokeWidth = 3.dp)
                            else Canvas(Modifier.size(48.dp)) {
                                val stroke = 4.dp.toPx()
                                drawArc(Color.White, -48f, 276f, false, Offset(stroke, stroke),
                                    Size(size.width - stroke * 2, size.height - stroke * 2), style = Stroke(stroke, cap = StrokeCap.Round))
                                drawLine(Color.White, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height * .43f), stroke, StrokeCap.Round)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        val selected = servers.firstOrNull { it.guid == state.selectedGuid }
                        Text(selected?.profile?.remarks ?: stringResource(R.string.fv_select_server), color = Muted, fontSize = 13.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        if (state.isTesting) Text(mainViewModel.formatStatus(state.status), color = Violet, fontSize = 13.sp)
                    }
                }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(if (settings) 14.dp else 6.dp),
            ) {
            if (settings) {
                item {
                    FilvlessSettingsContent(state, servers.isNotEmpty() || state.groups.any { it.id.isNotBlank() && it.id != AppConfig.DEFAULT_SUBSCRIPTION_ID }, servers.size,
                        onLanguage = { feedback(); languagePicker = true },
                        onSubscription = { feedback(); subscriptionDialog = true },
                        onAbout = { feedback(); aboutDialog = true },
                        onForget = { feedback(); forgetDialog = true },
                        onAction = act)
                }
            } else {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.fv_servers), Modifier.weight(1f), color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = { act(MainAction.TestRealAllServers) },
                            enabled = servers.isNotEmpty() && !providerDenied && !state.isTesting && !loading) {
                            if (state.isTesting) CircularProgressIndicator(Modifier.size(20.dp).semantics {
                                contentDescription = context.getString(R.string.fv_test)
                            }, color = Violet, strokeWidth = 2.dp)
                            else Icon(painterResource(R.drawable.fv_ping), stringResource(R.string.fv_test),
                                tint = if (servers.isNotEmpty() && !loading) Violet else Muted.copy(alpha = .4f))
                        }
                        TextButton(onClick = { act(MainAction.UpdateSubscriptions) }, enabled = !loading) { Text(stringResource(R.string.fv_refresh), color = Violet) }
                    }
                }
                if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Violet, trackColor = Card) }
                if ((servers.isEmpty() || providerDenied) && !loading) item {
                    Column(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(26.dp)).padding(24.dp)) {
                        Text(stringResource(if (providerDenied) R.string.fv_denied_title else R.string.fv_empty_title), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(if (providerDenied) R.string.fv_denied_hint else R.string.fv_empty_hint), color = Muted, lineHeight = 24.sp)
                        Spacer(Modifier.height(18.dp))
                        Button(onClick = { openImport() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF522680), contentColor = Color.White)) {
                            Text(stringResource(R.string.fv_import))
                        }
                    }
                }
                items(if (providerDenied) emptyList() else servers, key = { it.guid }) { server ->
                    Row(Modifier.fillMaxWidth().background(if (state.selectedGuid == server.guid) Color(0xFF2A193A) else Color.Transparent, RoundedCornerShape(20.dp))
                        .selectable(selected = state.selectedGuid == server.guid, role = Role.RadioButton, onClick = { act(MainAction.SelectServer(server.guid)) })
                        .padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(34.dp).background(Color(0xFF30203F), RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
                            Text(serverFlag(server.profile.remarks), Modifier.clearAndSetSemantics {}, fontSize = 21.sp)
                        }
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(server.profile.remarks, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                                maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text(if (server.profile.configType == com.v2ray.ang.enums.EConfigType.CUSTOM) "VPN" else
                                listOf(server.profile.configType.name, server.profile.network.orEmpty().uppercase()).filter { it.isNotBlank() }.joinToString(" | "), color = Muted, fontSize = 12.sp)
                        }
                        if (state.selectedGuid == server.guid) Text(stringResource(R.string.fv_selected), color = Violet, fontSize = 12.sp)
                        if (server.testDelayMillis > 0) Text(stringResource(R.string.fv_latency, server.testDelayMillis),
                            Modifier.padding(start = 8.dp), color = Violet, fontSize = 12.sp)
                    }
                }
            }
        }
    }
    }
    if (languagePicker) AlertDialog(
        onDismissRequest = { languagePicker = false }, containerColor = Card,
        title = { Text(stringResource(R.string.title_language)) },
        text = {
            Column {
                listOf("auto" to R.string.fv_language_system, "ru" to R.string.fv_language_ru, "en" to R.string.fv_language_en).forEach { (code, title) ->
                    Row(Modifier.fillMaxWidth().selectable(state.preferences.language == code, role = Role.RadioButton) {
                        languagePicker = false; act(MainAction.SetLanguage(code))
                    }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = state.preferences.language == code, onClick = null)
                        Text(stringResource(title), Modifier.padding(start = 12.dp))
                    }
                }
            }
        }, confirmButton = {},
        dismissButton = { TextButton(onClick = { languagePicker = false }) { Text(stringResource(R.string.fv_cancel)) } },
    )
    if (aboutDialog) AlertDialog(
        onDismissRequest = { aboutDialog = false }, containerColor = Card,
        title = { Text(stringResource(R.string.fv_about)) },
        text = { Column { Text(stringResource(R.string.fv_about_description)); Spacer(Modifier.height(16.dp)); Text(stringResource(R.string.fv_version, BuildConfig.VERSION_NAME), color = Muted) } },
        confirmButton = { TextButton(onClick = { aboutDialog = false; onNavigate(MainDestination.About) }) { Text(stringResource(R.string.fv_licenses)) } },
        dismissButton = { TextButton(onClick = { aboutDialog = false }) { Text(stringResource(R.string.fv_close)) } },
    )
    if (subscriptionDialog) AlertDialog(
        onDismissRequest = { subscriptionDialog = false }, containerColor = Card,
        title = { Text(stringResource(R.string.fv_subscription)) },
        text = { Text(stringResource(if (providerDenied) R.string.fv_denied_hint else R.string.fv_manage_subscription_hint)) },
        confirmButton = { TextButton(onClick = { subscriptionDialog = false; openImport() }) { Text(stringResource(R.string.fv_import)) } },
        dismissButton = { TextButton(enabled = !loading, onClick = { subscriptionDialog = false; act(MainAction.UpdateSubscriptions) }) { Text(stringResource(R.string.fv_refresh)) } },
    )
    if (forgetDialog) AlertDialog(
        onDismissRequest = { forgetDialog = false }, containerColor = Card,
        title = { Text(stringResource(R.string.fv_remove_subscription)) },
        text = { Text(stringResource(if (state.isRunning || state.connectionPending) R.string.fv_disconnect_before_remove else R.string.fv_remove_subscription_confirm)) },
        confirmButton = { TextButton(enabled = !state.isRunning && !state.connectionPending && !loading, onClick = {
            forgetDialog = false; settings = false; act(MainAction.ForgetSubscriptions)
        }) { Text(stringResource(R.string.fv_remove), color = Color(0xFFE5A2AB)) } },
        dismissButton = { TextButton(onClick = { forgetDialog = false }) { Text(stringResource(R.string.fv_cancel)) } },
    )
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
            act(MainAction.ImportBatchConfig(subscriptionText.trim())); subscriptionText = ""; importing = false
        }) { Text(stringResource(R.string.fv_import), color = Violet) } },
        dismissButton = { TextButton(onClick = { importing = false; subscriptionText = "" }) { Text(stringResource(R.string.fv_cancel), color = Muted) } },
    )
}
