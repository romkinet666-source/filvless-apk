package com.v2ray.ang.ui.main

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
import com.v2ray.ang.R
import com.v2ray.ang.handler.FilvlessEvent
import com.v2ray.ang.handler.FilvlessEventLog
import com.v2ray.ang.ui.base.BaseComponentActivity
import java.text.DateFormat
import java.util.Date

class EventHistoryActivity : BaseComponentActivity() {
    @Composable override fun ScreenContent() {
        var events by remember { mutableStateOf(FilvlessEventLog.read()) }
        Column(Modifier.fillMaxSize().background(Color(0xFF0B080F)).safeDrawingPadding().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().height(58.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = ::finish, modifier = Modifier.background(FilvlessCard, CircleShape)) {
                    Icon(painterResource(R.drawable.ic_arrow_back_24dp), stringResource(R.string.fv_back), tint = Color.White)
                }
                Text(stringResource(R.string.fv_history), Modifier.weight(1f).padding(start = 14.dp), color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                if (events.isNotEmpty()) TextButton(onClick = { FilvlessEventLog.clear(); events = emptyList() }) {
                    Text(stringResource(R.string.fv_history_clear), color = FilvlessAccent)
                }
            }
            if (events.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.fv_history_empty), color = FilvlessMuted, fontSize = 15.sp)
            } else LazyColumn(contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(events, key = { it.timestamp }) { event -> EventRow(event) }
            }
        }
    }
}

@Composable private fun EventRow(event: FilvlessEvent) {
    Row(Modifier.fillMaxWidth().background(FilvlessCard, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(event.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            if (event.detail.isNotBlank()) Text(event.detail, Modifier.padding(top = 1.dp), color = FilvlessMuted, fontSize = 11.sp, maxLines = 1)
        }
        Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(event.timestamp)), Modifier.padding(start = 8.dp), color = FilvlessAccent, fontSize = 10.sp, maxLines = 1)
    }
}
