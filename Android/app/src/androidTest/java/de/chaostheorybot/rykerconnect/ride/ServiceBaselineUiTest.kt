package de.chaostheorybot.rykerconnect.ride

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import de.chaostheorybot.rykerconnect.ui.theme.RykerConnectTheme
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class ServiceBaselineUiTest {
    @get:Rule val ui=createComposeRule()
    @Test fun previewApplyCorrectAndRemoveNeverCreateCompletedWork() {
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        val sync=SharedLibrary.enabled();val site=DadRides.enabled();val prefs=RideState.preferences.value
        SharedLibrary.enable(false);DadRides.enable(false)
        try {
            SoftwareStore.initFile(File(c.cacheDir,"baseline-ui-${System.nanoTime()}.json"))
            SoftwareStore.commit(ServiceCatalog.migrate(JSONObject()))
            RideState.save(prefs.copy(imperial=true))
            ui.setContent{RykerConnectTheme{Surface(Modifier.fillMaxSize()){Column(Modifier.verticalScroll(rememberScrollState())){ServiceSettings()}}}}
            ui.onNodeWithText("Set new-bike baseline").performScrollTo().performClick()
            ui.onNodeWithText("In-service date (YYYY-MM-DD)").performTextReplacement("2026-09-22")
            ui.onNodeWithText("Starting odometer (miles)").performTextReplacement("1")
            ui.onNodeWithText("Preview baseline").performScrollTo().performClick()
            ui.onNodeWithText("Review service starting points").assertIsDisplayed()
            assertTrue(SoftwareStore.records("serviceBaselines").isEmpty())
            ui.onNodeWithText("Cancel").performClick()
            assertTrue(SoftwareStore.records("serviceBaselines").isEmpty())
            ui.onNodeWithText("Preview baseline").performScrollTo().performClick()
            ui.onNode(isDialog()).captureToImage().asAndroidBitmap().let{b->File(c.cacheDir,"service-baseline-preview.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)}}
            ui.onNodeWithText("Apply baseline").performClick();ui.waitForIdle()
            assertEquals(11,SoftwareStore.records("serviceBaselines").size)
            assertTrue(SoftwareStore.records("maintenance").isEmpty())
            assertTrue(SoftwareStore.records("serviceBaselines").all{it.getString("date")=="2026-09-22"&&it.getDouble("odometerKm")==1.609344})
            ui.onNodeWithText("Starting odometer (miles)").performScrollTo().performTextReplacement("2")
            ui.onNodeWithText("Preview baseline").performScrollTo().performClick()
            ui.onNodeWithText("Apply baseline").performClick();ui.waitForIdle()
            assertTrue(SoftwareStore.records("serviceBaselines").all{it.getDouble("odometerKm")==3.218688})
            ui.onNodeWithText("Remove new-bike baselines").performScrollTo().performClick()
            ui.onNodeWithText("Remove baselines").performClick();ui.waitForIdle()
            assertTrue(SoftwareStore.records("serviceBaselines").none{it.getBoolean("enabled")})
            assertTrue(SoftwareStore.records("maintenance").isEmpty())
        } finally {
            SoftwareStore.initFile(File(c.noBackupFilesDir,"software.json"))
            RideState.save(prefs);DadRides.enable(site);SharedLibrary.enable(sync)
        }
    }
}
