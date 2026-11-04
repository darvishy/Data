package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.IdentityEntity
import com.example.data.IdentityRepository
import com.example.data.SearchStage
import com.example.model.HudModule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

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
        } finally {
            db.close()
        }
    }
}
