package com.v2ray.ang.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R

@Composable
internal fun FilvlessRoutingDialog(state: MainUiState, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var vpn by rememberSaveable { mutableStateOf(state.routingVpn) }
    var direct by rememberSaveable { mutableStateOf(state.routingDirect) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = FilvlessCard,
        title = { Text(stringResource(R.string.fv_routing)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.fv_routing_hint), color = FilvlessMuted)
                OutlinedTextField(vpn, { vpn = it }, label = { Text(stringResource(R.string.fv_routing_vpn)) },
                    minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(direct, { direct = it }, label = { Text(stringResource(R.string.fv_routing_direct)) },
                    minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth())
                if (state.routingError) Text(stringResource(R.string.fv_routing_error), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(vpn, direct) }) { Text(stringResource(R.string.fv_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.fv_cancel)) } },
    )
}
