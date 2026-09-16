package com.v2ray.ang.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.v2ray.ang.handler.AppUpdatePolicy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import java.text.DateFormat
import java.util.Date

internal val FilvlessCard = Color(0xFF1A171F)
internal val FilvlessAccent = Color(0xFFC1A4E1)
internal val FilvlessMuted = Color(0xFFB9B2C4)

@Composable
internal fun FilvlessSettingsContent(
    state: MainUiState,
    hasProfiles: Boolean,
    serverCount: Int,
    onLanguage: () -> Unit,
    onSubscription: () -> Unit,
    onAbout: () -> Unit,
    onForget: () -> Unit,
    onDevices: () -> Unit,
    onHistory: () -> Unit,
    onAction: (MainAction) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val tileLabel = stringResource(R.string.app_name)
    val backgroundNeedsAttention = remember {
        val manager = context.getSystemService(android.app.ActivityManager::class.java)
        val power = context.getSystemService(android.os.PowerManager::class.java)
        (android.os.Build.VERSION.SDK_INT >= 28 && manager.isBackgroundRestricted) ||
            !power.isIgnoringBatteryOptimizations(context.packageName) || power.isPowerSaveMode
    }
    var tileHelp by remember { mutableStateOf(false) }
    if (tileHelp) AlertDialog(onDismissRequest = { tileHelp = false },
        title = { Text(stringResource(R.string.fv_tile)) }, text = { Text(stringResource(R.string.fv_tile_hint)) },
        confirmButton = { TextButton(onClick = { tileHelp = false }) { Text(stringResource(R.string.fv_close)) } })
    var downloadPolicyDialog by remember { mutableStateOf(false) }
    val policyLabels = mapOf(AppUpdatePolicy.WIFI_ONLY to R.string.fv_update_wifi,
        AppUpdatePolicy.ANY_NETWORK to R.string.fv_update_any, AppUpdatePolicy.MANUAL to R.string.fv_update_manual)
    if (downloadPolicyDialog) AlertDialog(onDismissRequest = { downloadPolicyDialog = false },
        title = { Text(stringResource(R.string.fv_update_download_policy)) },
        text = { Column {
            Text(stringResource(R.string.fv_update_policy_hint), fontSize = 13.sp)
            AppUpdatePolicy.entries.forEach { policy ->
                Row(Modifier.fillMaxWidth().clickable(role = Role.RadioButton) {
                    onAction(MainAction.SetUpdateDownloadPolicy(policy)); downloadPolicyDialog = false
                }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = state.preferences.updateDownloadPolicy == policy, onClick = null)
                    Text(stringResource(policyLabels.getValue(policy)), Modifier.padding(start = 8.dp))
                }
            }
        } }, confirmButton = { TextButton(onClick = { downloadPolicyDialog = false }) { Text(stringResource(R.string.fv_close)) } })
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val expired = state.subscriptionExpiresAt?.let { it * 1000 < System.currentTimeMillis() } == true
        val subscriptionLabel = when {
            state.subscriptionExpiresAt != null -> stringResource(
                if (expired) R.string.fv_expired_date else R.string.fv_active_date,
                DateFormat.getDateInstance(DateFormat.LONG).format(Date(state.subscriptionExpiresAt * 1000)),
            )
            hasProfiles -> stringResource(R.string.fv_server_count, serverCount)
            else -> stringResource(R.string.fv_subscription_empty)
        }
        val banner = if (state.preferences.visualEffects) Modifier.background(
            Brush.horizontalGradient(listOf(Color(0xFF361750), FilvlessCard, Color(0xFF2C143F))), RoundedCornerShape(18.dp),
        ) else Modifier.background(FilvlessCard, RoundedCornerShape(18.dp))
        Column(Modifier.fillMaxWidth().then(banner).clickable(role = Role.Button, onClick = onSubscription).padding(16.dp)) {
            Text(stringResource(R.string.fv_subscription), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(subscriptionLabel, color = FilvlessMuted, fontSize = 13.sp, lineHeight = 18.sp)
            if (state.subscriptionUpdatedAt > 0) Text(stringResource(R.string.fv_last_updated,
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(state.subscriptionUpdatedAt))),
                Modifier.padding(top = 4.dp), color = FilvlessMuted, fontSize = 11.sp)
            if (state.subscriptionUpdateFailed) Text(stringResource(R.string.fv_sub_kept),
                Modifier.padding(top = 4.dp), color = Color(0xFFE5BA82), fontSize = 12.sp)
            Text(stringResource(R.string.fv_buy_subscription), Modifier.padding(top = 5.dp), color = FilvlessAccent, fontSize = 12.sp)
        }
        Column(Modifier.fillMaxWidth().background(FilvlessCard, RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 2.dp)) {
            SettingsLink(R.drawable.fv_phone, R.string.fv_devices, onClick = onDevices)
            SettingsLink(R.drawable.ic_logcat_24dp, R.string.fv_history, onClick = onHistory)
        }
        SettingsGroup(R.string.fv_appearance) {
            val language = when (state.preferences.language) {
                "ru" -> stringResource(R.string.fv_language_ru)
                "en" -> stringResource(R.string.fv_language_en)
                else -> stringResource(R.string.fv_language_system)
            }
            SettingsLink(R.drawable.ic_translate_24dp, R.string.title_language, language, onLanguage)
        }
        SettingsGroup(R.string.fv_functions) {
            SettingsLink(R.drawable.fv_power, R.string.fv_tile, onClick = {
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    try {
                        context.getSystemService(android.app.StatusBarManager::class.java).requestAddTileService(
                            android.content.ComponentName(context, com.v2ray.ang.service.QSTileService::class.java),
                            tileLabel, android.graphics.drawable.Icon.createWithResource(context, R.drawable.ic_stat_name),
                            context.mainExecutor) { result ->
                                if (result != android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED &&
                                    result != android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) tileHelp = true
                            }
                    } catch (_: Exception) { tileHelp = true }
                } else tileHelp = true
            })
            SettingsLink(R.drawable.fv_phone, R.string.fv_background, onClick = {
                context.startActivity(android.content.Intent(context, BackgroundWorkActivity::class.java))
            })
            if (backgroundNeedsAttention) Text(stringResource(R.string.fv_bg_attention),
                Modifier.padding(start = 42.dp, end = 8.dp, bottom = 6.dp), color = Color(0xFFE5BA82), fontSize = 12.sp, lineHeight = 16.sp)
            SettingsToggle(R.drawable.fv_phone, R.string.fv_expiry_reminder, R.string.fv_expiry_reminder_hint,
                state.preferences.expiryReminder, state.preferencesLoaded) { onAction(MainAction.SetPreference(FilvlessPreference.EXPIRY_REMINDER, it)) }
            SettingsToggle(R.drawable.fv_ping, R.string.fv_auto_update, R.string.fv_auto_update_hint,
                state.preferences.autoUpdateSubscriptions, state.preferencesLoaded) { onAction(MainAction.SetPreference(FilvlessPreference.AUTO_UPDATE_SUBSCRIPTIONS, it)) }

            SettingsToggle(R.drawable.fv_power, R.string.fv_auto_connect, R.string.fv_auto_connect_hint,
                state.preferences.autoConnect, state.preferencesLoaded) { onAction(MainAction.SetPreference(FilvlessPreference.AUTO_CONNECT, it)) }
            SettingsToggle(R.drawable.fv_phone, R.string.fv_haptics, R.string.fv_haptics_hint,
                state.preferences.haptics, state.preferencesLoaded) { onAction(MainAction.SetPreference(FilvlessPreference.HAPTICS, it)) }
            SettingsToggle(R.drawable.fv_palette, R.string.fv_effects, R.string.fv_effects_hint,
                state.preferences.visualEffects, state.preferencesLoaded) { onAction(MainAction.SetPreference(FilvlessPreference.VISUAL_EFFECTS, it)) }
        }
        SettingsGroup(R.string.fv_check_update) {
            SettingsLink(R.drawable.fv_ping, R.string.fv_update_download_policy, onClick = {
                if (state.preferencesLoaded) downloadPolicyDialog = true
            })
            Text(stringResource(policyLabels.getValue(state.preferences.updateDownloadPolicy)),
                Modifier.padding(start = 42.dp, bottom = 6.dp), color = FilvlessMuted, fontSize = 12.sp)
        }
        SettingsGroup(R.string.fv_information) {
            val context = androidx.compose.ui.platform.LocalContext.current
            SettingsLink(R.drawable.fv_ping, R.string.fv_connection_diagnostics, onClick = {
                context.startActivity(android.content.Intent(context, DiagnosticsActivity::class.java))
            })
            SettingsLink(R.drawable.fv_ping, R.string.fv_check_update,
                if (state.checkingAppUpdate) "…" else null, onClick = { onAction(MainAction.CheckAppUpdate) })
            SettingsLink(R.drawable.ic_about_24dp, R.string.fv_about, onClick = onAbout)
            SettingsLink(R.drawable.ic_feedback_24dp, R.string.fv_review, onClick = { onAction(MainAction.OpenReview) })
            SettingsLink(R.drawable.fv_headphones, R.string.fv_support, onClick = { onAction(MainAction.OpenSupport) })
        }
        if (hasProfiles) {
            TextButton(onClick = onForget, modifier = Modifier.fillMaxWidth().background(Color(0xFF29181F), RoundedCornerShape(18.dp))) {
                Text(stringResource(R.string.fv_remove_subscription), Modifier.padding(4.dp), color = Color(0xFFE5A2AB), fontSize = 15.sp)
            }
        }
        Text(stringResource(R.string.fv_version, BuildConfig.VERSION_NAME), Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp),
            color = FilvlessMuted, fontSize = 12.sp)
    }
}

@Composable
private fun SettingsGroup(title: Int, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(FilvlessCard, RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(stringResource(title), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun SettingsGlyph(icon: Int) {
    Box(Modifier.size(32.dp).background(Color(0xFF28212F), CircleShape), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), contentDescription = null, tint = FilvlessAccent, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun SettingsLink(icon: Int, title: Int, trailing: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp)
        .semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        SettingsGlyph(icon)
        Text(stringResource(title), Modifier.weight(1f).padding(start = 10.dp), color = Color.White, fontSize = 15.sp)
        if (trailing != null) {
            Text(trailing, Modifier.background(Color(0xFF29232F), CircleShape).padding(horizontal = 9.dp, vertical = 5.dp),
                color = FilvlessMuted, fontSize = 13.sp)
        }
        Spacer(Modifier.width(10.dp))
        Icon(painterResource(R.drawable.fv_chevron), null, tint = FilvlessMuted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SettingsToggle(icon: Int, title: Int, hint: Int, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
        .padding(vertical = 8.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        SettingsGlyph(icon)
        Column(Modifier.weight(1f).padding(start = 10.dp, end = 8.dp)) {
            Text(stringResource(title), color = Color.White, fontSize = 15.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(2.dp))
            Text(stringResource(hint), color = FilvlessMuted, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF6A3D91),
                checkedBorderColor = Color.Transparent, uncheckedThumbColor = Color.White, uncheckedTrackColor = Color(0xFF49434E),
                uncheckedBorderColor = Color.Transparent))
    }
}
