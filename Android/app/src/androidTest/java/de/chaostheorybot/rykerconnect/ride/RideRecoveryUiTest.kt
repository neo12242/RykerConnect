package de.chaostheorybot.rykerconnect.ride

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
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
import java.util.UUID

class RideRecoveryUiTest {
    @get:Rule val ui=createComposeRule()
    @Test fun restoreConfirmationAndWebsiteCopyDetails() {
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        val id=UUID.randomUUID().toString();val title="UI recovery ${id.take(8)}"
        DadRides.configure("http://127.0.0.1:8890",File(c.cacheDir,"shared-test-key").readText().trim());DadRides.enable(true);SharedLibrary.enable(true)
        val m=JSONObject("""{"version":1,"id":"$id","title":"$title","story":"Synthetic UI recovery","date":"2026-09-23","tags":[],"cover":"","photos":[],"route":[],"stats":{},"privacy":{"trimMeters":500,"statsIncluded":false}}""")
        val bytes=m.toString().toByteArray();val revision=PublicRide.hash(bytes)
        DadRides.request("rides/$id/revisions/$revision","PUT",bytes);DadRides.request("rides/$id/finish/$revision","POST")
        val wrongSite=RideRecovery.preview(id).put("origin","https://different.invalid")
        assertThrows(IllegalStateException::class.java){SharedLibrary.restoreWebsite(wrongSite)}
        assertFalse(TripStore.contains(id))
        ui.setContent{RykerConnectTheme{Surface(Modifier.fillMaxSize()){var journal by remember{mutableStateOf(false)}
            if(journal)TripJournal(embedded=true,close={}) else Column(Modifier.verticalScroll(rememberScrollState())){
                RideRecoverySettings();Button(onClick={journal=true}){Text("My Trips")}
            }
        }}}
        fun choose(){
            ui.onNode(hasText("Restore from DadRides") and hasClickAction()).performClick()
            ui.waitUntil(30000){ui.onAllNodesWithText("$title\n2026-09-23").fetchSemanticsNodes().isNotEmpty()}
            ui.onNodeWithText("$title\n2026-09-23").performScrollTo().performClick()
            ui.waitUntil(30000){ui.onAllNodesWithText("Restore this website copy?").fetchSemanticsNodes().isNotEmpty()}
        }
        choose();assertFalse(TripStore.contains(id))
        ui.onNode(isDialog()).captureToImage().asAndroidBitmap().let{b->File(c.cacheDir,"recovery-confirmation.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)}}
        ui.onNodeWithText("Back",useUnmergedTree=true).performClick()
        ui.onNodeWithText("Close").performClick();assertFalse(TripStore.contains(id))
        choose();ui.onNodeWithText("Restore website copy").performClick()
        ui.waitUntil(30000){TripStore.contains(id)&&ui.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty()}
        ui.onNodeWithText("My Trips").performScrollTo().performClick()
        ui.onNodeWithText("Search name, notes or date").performTextInput(title)
        ui.onNodeWithText("View map and trip data").performClick()
        ui.waitUntil(30000){ui.onAllNodesWithText("Restored website copy").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText("Restored website copy").assertIsDisplayed()
        ui.onNodeWithText("View original recording").assertDoesNotExist()
        ui.onNodeWithText("Statistics were not included in this website copy.").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Export original GPX").performScrollTo().assertIsNotEnabled()
        ui.onNodeWithText("Restored website copy").performScrollTo().assertIsDisplayed()
        ui.onAllNodes(isRoot()).filter(!isDialog()).onFirst().captureToImage().asAndroidBitmap().let{b->File(c.cacheDir,"recovery-details.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)}}
        assertEquals(1,TripStore.history.value.count{it.id==id})
    }
}
