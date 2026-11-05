package com.example

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.IdentityEntity
import com.example.data.IdentityRepository
import com.example.data.SearchStage
import com.example.model.HudModule
import com.example.ui.components.IdentityRecordDetailCardDialog
import com.example.ui.components.IdentityRecordHudRow
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `verify app strings developer message and 6 unnumbered HUD module labels`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Nexus HUD", context.getString(R.string.app_name))
        assertEquals(
            "This application was developed by Nullemperor.",
            context.getString(R.string.developer_credit)
        )
        assertEquals("http://t.me/Nullemperor", context.getString(R.string.telegram_url))

        val expectedLabels = listOf(
            "BankMeli",
            "BankMelat",
            "BankSaderat",
            "Citizen",
            "Irancell",
            "Telegram"
        )
        assertEquals(expectedLabels, HudModule.entries.map { context.getString(it.labelRes) })
    }

    @Test
    fun `verify hybrid search across BankMeli BankMelat BankSaderat Citizen and Telegram`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.identityDao()
            val repo = IdentityRepository(dao)

            val records = listOf(
                IdentityEntity(
                    moduleId = "bank_meli",
                    fullName = "علی رضایی",
                    nationalCode = "0019283746",
                    phone = "09121112233",
                    accountOrHandle = "-",
                    cardNumber = "6037997112345678",
                    gpuSignatureHash = 123456L,
                    importedAt = 1000L
                ),
                IdentityEntity(
                    moduleId = "telegram",
                    fullName = "Aria Parsa",
                    nationalCode = "784512963",
                    phone = "989123456789",
                    accountOrHandle = "784512963",
                    username = "@nullemperor_dev",
                    gpuSignatureHash = 654321L,
                    importedAt = 1000L
                )
            )
            dao.insertBatch(records)

            var meliHits = 0L
            repo.executeVisibleHighSpeedSearch("bank_meli", "۰۹۱۲۱۱۱۲۲۳۳") { telemetry ->
                if (telemetry.stage == SearchStage.COMPLETE) {
                    meliHits = telemetry.matchCount
                }
            }
            assertEquals(1L, meliHits)

            var tgHits = 0L
            repo.executeVisibleHighSpeedSearch("telegram", "09123456789") { telemetry ->
                if (telemetry.stage == SearchStage.COMPLETE) {
                    tgHits = telemetry.matchCount
                }
            }
            assertEquals(1L, tgHits)

            // Test Universal Multi-Database Search across all nodes
            var universalHits = 0L
            var returnedModules = emptyList<String>()
            repo.executeUniversalSearch("علی") { telemetry ->
                if (telemetry.stage == SearchStage.COMPLETE) {
                    universalHits = telemetry.matchCount
                    returnedModules = telemetry.results.map { it.moduleId }
                }
            }
            assertEquals(1L, universalHits)
            assertTrue(returnedModules.contains("bank_meli"))

            // Test Universal Multi-Database Search for phone prefix across all nodes
            var phoneHits = 0L
            repo.executeUniversalSearch("0912") { telemetry ->
                if (telemetry.stage == SearchStage.COMPLETE) {
                    phoneHits = telemetry.matchCount
                }
            }
            assertTrue(phoneHits >= 1L)

            // Test Universal Filter targeted to Telegram node
            var filteredHits = 0L
            repo.executeUniversalSearch(rawQuery = "Aria", targetModuleId = "telegram") { telemetry ->
                if (telemetry.stage == SearchStage.COMPLETE) {
                    filteredHits = telemetry.matchCount
                }
            }
            assertEquals(1L, filteredHits)
        } finally {
            db.close()
        }
    }

    @Test
    fun `verify right aligned result card name and click opens detail card with copy and share`() {
        val sampleRecord = IdentityEntity(
            id = 42L,
            moduleId = "bank_meli",
            fullName = "علی رضایی",
            nationalCode = "0019283746",
            phone = "09121112233",
            accountOrHandle = "-",
            cardNumber = "6037997112345678",
            birthDate = "1370/05/12",
            gpuSignatureHash = 0x1A2B3CL,
            importedAt = 1000L
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                var selected by remember { mutableStateOf<IdentityEntity?>(null) }
                IdentityRecordHudRow(
                    record = sampleRecord,
                    module = HudModule.BANK_MELI,
                    accentColor = HudModule.BANK_MELI.primaryColor,
                    onClick = { selected = it }
                )
                selected?.let { active ->
                    IdentityRecordDetailCardDialog(
                        record = active,
                        module = HudModule.BANK_MELI,
                        onDismiss = { selected = null }
                    )
                }
            }
        }

        composeTestRule.waitForIdle()

        // Verify result card and right-aligned name placement
        val cardBounds = composeTestRule.onNodeWithTag("identity_record_card")
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        val nameBounds = composeTestRule.onNodeWithTag("identity_record_name", useUnmergedTree = true)
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        assertTrue(
            "Expected name container to extend toward the right side of the card",
            nameBounds.right > (cardBounds.left + cardBounds.right) / 2f
        )

        // Click result card -> opens detail card dialog
        composeTestRule.onNodeWithTag("identity_record_card").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("identity_detail_dialog_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("detail_card_full_name", useUnmergedTree = true).assertIsDisplayed()

        // Test Copy Information button
        composeTestRule.onNodeWithTag("copy_record_info_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("copy_confirmation_banner").assertIsDisplayed()
        val app = ApplicationProvider.getApplicationContext<Application>()
        val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val copiedText = clipboard.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
        assertTrue(copiedText.contains("علی رضایی"))
        assertTrue(copiedText.contains("0019283746"))
        assertTrue(copiedText.contains("6037997112345678"))

        // Test Share Information button
        composeTestRule.onNodeWithTag("share_record_info_button").performClick()
        composeTestRule.waitForIdle()

        val startedChooser = shadowOf(app).nextStartedActivity
        assertNotNull("Expected share chooser intent to be launched", startedChooser)
        assertEquals(Intent.ACTION_CHOOSER, startedChooser.action)
        @Suppress("DEPRECATION")
        val targetSendIntent = startedChooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertNotNull(targetSendIntent)
        assertEquals(Intent.ACTION_SEND, targetSendIntent?.action)
        val sharedBody = targetSendIntent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        assertTrue(sharedBody.contains("علی رضایی"))
        assertTrue(sharedBody.contains("09121112233"))
    }
}

