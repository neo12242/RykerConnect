package de.chaostheorybot.rykerconnect.ride

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import de.chaostheorybot.rykerconnect.BuildConfig
import de.chaostheorybot.rykerconnect.data.RykerConnectStore
import de.chaostheorybot.rykerconnect.ui.screens.homescreen.cards.ConnectionPanel
import de.chaostheorybot.rykerconnect.ui.theme.RykerConnectTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Runs against both distributed editions, without deleting or seeding saved rides. */
class DemoFreeEditionTest {
    @get:Rule val ui = createComposeRule()

    @Test fun journalAndSettingsOfferRealRidesWithoutDemoDestinations() {
        assertFalse(BuildConfig.DEMO_FEATURES)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ui.setContent { RykerConnectTheme { RykerAppShell(RykerConnectStore(context), {}, {}) } }
        ui.onNode(hasText("My Trips") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).performClick()
        ui.onNodeWithText("Search name, notes or date").assertExists()
        ui.onNodeWithText("Demo rides").assertDoesNotExist()
        ui.onNodeWithText("Downtown coffee loop").assertDoesNotExist()
        ui.onNode(hasText("Settings") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).performClick()
        ui.onNodeWithText("Guided demo").assertDoesNotExist()
        ui.onNodeWithText("Backup & restore").performScrollTo().assertIsDisplayed()
        if (!BuildConfig.PHONE_EDITION) {
            ui.onNode(hasText("Connect") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).performClick()
            ui.onNodeWithText("Guided demo").assertDoesNotExist()
        }
    }

    @Test fun directDemoEntryCannotStartSyntheticPlayback() {
        ui.setContent { RykerConnectTheme { GuidedDemo() } }
        ui.onNodeWithText("Demo features are unavailable in this build.").assertExists()
        ui.onNodeWithText("Start demo").assertDoesNotExist()
        ui.onNodeWithText("GUIDED DEMO · Synthetic ride").assertDoesNotExist()
    }

    @Test fun sampleNavigationCannotReplaceLiveDirections() {
        val previous = RideState.navigation.value
        val live = NavigationFrame(instruction = "Turn right onto Main Street", updated = System.currentTimeMillis(), active = true)
        try {
            RideState.live(live)
            RideState.sampleRoute()
            assertEquals(live, RideState.navigation.value)
        } finally {
            RideState.useLive()
            RideState.live(previous)
        }
    }

    @Test fun connectionPanelDoesNotOfferLocalSimulatorPreview() {
        ui.setContent { RykerConnectTheme { ConnectionPanel(false, {}, {}) } }
        ui.onNodeWithText("Select Device").assertExists()
        ui.onNodeWithText("OLED preview").assertDoesNotExist()
    }
}
