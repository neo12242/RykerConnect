package de.chaostheorybot.rykerconnect.ride

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import de.chaostheorybot.rykerconnect.ui.theme.RykerConnectTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class TripDeletionUiTest {
    @get:Rule val ui=createComposeRule()
    @Test fun cancelKeepsTripAndConfirmedDeleteReturnsToJournal() {
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        DadRides.enable(false);SharedLibrary.enable(false)
        val id=UUID.randomUUID().toString()
        File(c.noBackupFilesDir,"rides/$id.jsonl").writeText("{\"start\":1790251200000,\"version\":2}\n{\"end\":1790251260000}\n")
        val title="UI false start ${id.take(8)}"
        TripStore.saveMetadata(id,title,"Synthetic UI test")
        ui.setContent { RykerConnectTheme { TripJournal(embedded=true,close={}) } }
        ui.onNodeWithText("Search name, notes or date").performTextInput(title)
        ui.onNodeWithText("View map and trip data").performClick()
        ui.onNodeWithText("Delete trip").performClick()
        ui.onNodeWithText("Delete this trip?").assertIsDisplayed()
        ui.onNodeWithText("Keep trip").assertIsDisplayed()
        ui.onNode(isDialog()).captureToImage().asAndroidBitmap().let { b ->
            File(c.cacheDir,"trip-delete-confirmation.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)}
        }
        ui.onNodeWithText("Keep trip").performClick()
        assertTrue(TripStore.contains(id))
        ui.onNodeWithText("Delete trip").performClick()
        ui.onNode(hasText("Delete trip") and hasAnyAncestor(isDialog())).performClick()
        try { ui.waitUntil(30000){!TripStore.contains(id) && ui.onAllNodesWithText("My Trips").fetchSemanticsNodes().isNotEmpty() && ui.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty()} }
        catch(e:Exception){throw AssertionError("Saved trip exists: ${TripStore.contains(id)}; ${ui.onAllNodes(isRoot()).printToString()}",e)}
        ui.onNodeWithText("My Trips").assertIsDisplayed()
        assertTrue(TripStore.history.value.none{it.id==id})
        SharedLibrary.enable(true)
    }
}
