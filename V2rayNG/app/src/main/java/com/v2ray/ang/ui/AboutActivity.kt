package com.v2ray.ang.ui

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.main.AppUpdateActivity
import com.v2ray.ang.util.Utils

class AboutActivity : BaseComponentActivity() {
    @Composable
    override fun ScreenContent() = FilvlessAboutScreen(onBack = ::finish)
}

@Composable
private fun FilvlessAboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val card = Color(0xFF1A1521)
    val violet = Color(0xFFB59AD8)
    Column(
        Modifier.fillMaxSize().background(Color(0xFF0B080F))
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back_24dp), stringResource(R.string.fv_back), tint = Color.White)
            }
            Text(stringResource(R.string.fv_about), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(20.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.filvless_brand), contentDescription = null,
                modifier = Modifier.size(150.dp), contentScale = ContentScale.Crop)
            Text("FILVLESS", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.fv_about_tagline), color = Color(0xFFB8B0C5), fontSize = 15.sp)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.fv_about_version, BuildConfig.VERSION_NAME), color = violet, fontSize = 14.sp)
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = { context.startActivity(Intent(context, AppUpdateActivity::class.java)) },
            modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(17.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF632D91), contentColor = Color.White)) {
            Text(stringResource(R.string.fv_check_update))
        }
        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth().background(card, RoundedCornerShape(24.dp)).padding(vertical = 4.dp)) {
            AboutRow(stringResource(R.string.fv_support)) { Utils.openUri(context, "https://t.me/Godfather099") }
            AboutRow(stringResource(R.string.fv_purchase_bot)) {
                MmkvManager.encodeSettings("filvless_purchase_return", System.currentTimeMillis().toString())
                Utils.openUri(context, "https://t.me/filvless_bot")
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AboutRow(title: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 17.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, fontSize = 16.sp)
        Icon(painterResource(R.drawable.fv_chevron), contentDescription = null, tint = Color(0xFFB59AD8))
    }
}
