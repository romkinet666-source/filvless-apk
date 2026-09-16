package com.v2ray.ang.ui.main

import android.os.Bundle
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.base.BaseComponentActivity

internal const val FILVLESS_ONBOARDING_COMPLETE = "filvless_onboarding_complete"

internal fun shouldShowOnboarding(completed: Boolean, isPackageUpdate: Boolean, hasProfiles: Boolean): Boolean =
    !completed && !isPackageUpdate && !hasProfiles

class OnboardingActivity : BaseComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    private fun complete() {
        MmkvManager.encodeSettings(FILVLESS_ONBOARDING_COMPLETE, true)
        finish()
    }

    @Composable
    override fun ScreenContent() {
        var page by remember { mutableIntStateOf(0) }
        val titles = listOf(
            R.string.fv_onboarding_welcome,
            R.string.fv_onboarding_subscription,
            R.string.fv_onboarding_connect,
        )
        val hints = listOf(
            R.string.fv_onboarding_welcome_hint,
            R.string.fv_onboarding_subscription_hint,
            R.string.fv_onboarding_connect_hint,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF0B080F))
                .padding(horizontal = 28.dp, vertical = 24.dp),
        ) {
            TextButton(onClick = ::complete, modifier = Modifier.align(Alignment.TopEnd)) {
                Text(stringResource(R.string.fv_skip), color = Color(0xFFB59AD8))
            }
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(R.drawable.filvless_brand),
                    contentDescription = null,
                    modifier = Modifier.size(190.dp),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.height(32.dp))
                Text(
                    stringResource(titles[page]),
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(hints[page]),
                    color = Color(0xFFB8B0C5),
                    fontSize = 17.sp,
                    lineHeight = 25.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(36.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(titles.size) { index ->
                        Box(
                            Modifier
                                .size(if (index == page) 24.dp else 8.dp, 8.dp)
                                .background(
                                    if (index == page) Color(0xFF9B5DCC) else Color(0xFF3C3247),
                                    CircleShape,
                                ),
                        )
                    }
                }
            }
            Button(
                onClick = { if (page == titles.lastIndex) complete() else page += 1 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF632D91), contentColor = Color.White),
            ) {
                Text(
                    stringResource(if (page == titles.lastIndex) R.string.fv_start else R.string.fv_next),
                    fontSize = 17.sp,
                )
            }
        }
    }
}
