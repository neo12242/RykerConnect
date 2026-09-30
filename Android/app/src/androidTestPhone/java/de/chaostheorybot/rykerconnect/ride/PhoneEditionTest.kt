package de.chaostheorybot.rykerconnect.ride

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.chaostheorybot.rykerconnect.BuildConfig
import de.chaostheorybot.rykerconnect.MainActivity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class PhoneEditionTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test fun phoneManifestCannotRegisterSensitiveCompanionFeatures() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES)
        assertTrue(BuildConfig.PHONE_EDITION)
        assertTrue(context.packageName.endsWith(".phone"))
        val denied = setOf(Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
        assertTrue(info.requestedPermissions.orEmpty().none { it in denied || it.contains("COMPANION") })
        assertTrue(info.services.orEmpty().none { it.permission in setOf(
            "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE", "android.permission.BIND_ACCESSIBILITY_SERVICE",
            "android.permission.BIND_COMPANION_DEVICE_SERVICE") })
        assertTrue(info.services.orEmpty().any { it.name.endsWith("TripRecordingService") })
        assertTrue(info.services.orEmpty().any { it.name.endsWith("DadRidesJob") })
        assertTrue(info.requestedPermissions.orEmpty().contains(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
    }

    @Test fun phoneStartsAndSavesAManualRideWithoutAnEsp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.ACCESS_COARSE_LOCATION)
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.ACCESS_FINE_LOCATION)
        if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        ui.onNodeWithText("RykerConnect Phone").assertIsDisplayed()
        ui.onNodeWithText("Connect", useUnmergedTree = true).assertDoesNotExist()
        ui.waitUntilAtLeastOneExists(hasText("Start Ride"),15_000)
        ui.onNodeWithText("Start Ride").performClick()
        ui.waitUntil(15_000) { TripStore.summary.value.recording }
        assertFalse(TripStore.summary.value.automatic)
        val id = TripStore.summary.value.id
        ui.waitUntilAtLeastOneExists(hasText("Pause Ride"),10_000)
        ui.onNodeWithText("Pause Ride").performClick()
        ui.waitUntil(10_000){TripStore.summary.value.paused}
        ui.waitUntilAtLeastOneExists(hasText("Resume Ride"),10_000)
        ui.onNodeWithText("Resume Ride").performClick()
        ui.waitUntil(10_000){!TripStore.summary.value.paused}
        assertEquals(id,TripStore.summary.value.id)
        ui.onNodeWithText("End Ride").performClick()
        ui.onAllNodesWithText("End Ride").onLast().performClick()
        ui.waitUntil(10_000) { !TripStore.summary.value.recording && TripStore.history.value.any { it.id == id } }
        ui.waitUntilAtLeastOneExists(hasText("Start Ride"),10_000)
        assertFalse(AutoRide.enabled.value)
    }

    @Test fun phoneRetainsPublishingAndBackupToolsWithoutListenerSetup() {
        ui.onNodeWithText("Settings", useUnmergedTree = true).performClick()
        ui.onNodeWithText("Backup & restore").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Device tools").assertDoesNotExist()
        ui.onNodeWithText("Add-ons").performScrollTo().performClick()
        ui.onNodeWithText("Enable publishing").assertExists()
        ui.onNodeWithText("Notification access settings").assertDoesNotExist()
    }
}
