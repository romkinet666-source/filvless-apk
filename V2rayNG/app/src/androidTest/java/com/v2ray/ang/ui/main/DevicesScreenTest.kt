package com.v2ray.ang.ui.main

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.v2ray.ang.R
import com.v2ray.ang.handler.DeviceSnapshot
import com.v2ray.ang.handler.FilvlessDevice
import com.v2ray.ang.ui.compose.AppTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class DevicesScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(context.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun deviceRemovalRequiresConfirmationAndDisplaysCurrentBadge() {
        val current = FilvlessDevice("a".repeat(64), "Samsung Galaxy S24", "Android", "15", "2026-09-15T18:00:00Z", true)
        val other = FilvlessDevice("b".repeat(64), "iPhone 16 Pro", "iOS", "18", "2026-09-14T10:15:00Z", false)
        var removed: String? = null
        compose.setContent { AppTheme { DevicesContent(DevicesState(
            subscriptions = listOf(DeviceSubscription("sample", "Filvless")), selectedId = "sample", loading = false,
            snapshot = DeviceSnapshot(listOf(current, other), 3)), {}, {}, {}, { removed = it }) } }
        compose.onNodeWithText(text(R.string.fv_devices_current)).assertIsDisplayed()
        compose.onNodeWithText("Samsung Galaxy S24").assertIsDisplayed()
        screenshot("devices-list-test.png")
        compose.onAllNodesWithContentDescription(text(R.string.fv_devices_remove))[0].performClick()
        compose.onNodeWithText(text(R.string.fv_devices_confirm_title)).assertIsDisplayed()
        assertNull(removed)
        compose.onNodeWithText(text(R.string.fv_cancel)).performClick()
        assertNull(removed)
        compose.onAllNodesWithContentDescription(text(R.string.fv_devices_remove))[0].performClick()
        compose.onAllNodesWithText(text(R.string.fv_devices_remove)).onLast().performClick()
        compose.runOnIdle { assertEquals(current.id, removed) }
    }

    @Test fun permissionsErrorOffersRetryWithoutFakeDevices() {
        var retried = false
        compose.setContent { AppTheme { DevicesContent(DevicesState(
            subscriptions = listOf(DeviceSubscription("sample", "Filvless")), selectedId = "sample",
            loading = false, error = "panel_permissions"), {}, { retried = true }, {}, {}) } }
        compose.onNodeWithText(text(R.string.fv_devices_permissions)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.fv_devices_remove)).assertDoesNotExist()
        screenshot("devices-error-test.png")
        compose.onNodeWithText(text(R.string.fv_devices_retry)).performClick()
        compose.runOnIdle { assertTrue(retried) }
    }

    @Test fun deviceAliasIsLocalAndCanBeReset() {
        val device = FilvlessDevice("c".repeat(64), "QA phone", "Android", "15", "", true)
        val key = "filvless_device_alias_qa-alias_${device.id}"
        val old = com.v2ray.ang.handler.MmkvManager.decodeSettingsString(key)
        try {
            compose.setContent { AppTheme { DevicesContent(DevicesState(
                subscriptions = listOf(DeviceSubscription("qa-alias", "QA")), selectedId = "qa-alias", loading = false,
                snapshot = DeviceSnapshot(listOf(device), 5)), {}, {}, {}, {}) } }
            compose.onNodeWithContentDescription(text(R.string.fv_device_rename)).performClick()
            compose.onNode(hasSetTextAction()).performTextReplacement("My phone")
            compose.onNodeWithText(text(R.string.fv_save)).performClick()
            compose.onNodeWithText("My phone").assertIsDisplayed()
            assertEquals("My phone", com.v2ray.ang.handler.MmkvManager.decodeSettingsString(key))
            compose.onNodeWithContentDescription(text(R.string.fv_device_rename)).performClick()
            compose.onNode(hasSetTextAction()).performTextReplacement("")
            compose.onNodeWithText(text(R.string.fv_save)).performClick()
            compose.onNodeWithText("QA phone").assertIsDisplayed()
        } finally { com.v2ray.ang.handler.MmkvManager.encodeSettings(key, old) }
    }
}
