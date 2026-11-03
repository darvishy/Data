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
    fun `verify app strings developer message and HUD module labels`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Nexus HUD", context.getString(R.string.app_name))
        assertEquals(
            "This application was developed by Nullemperor.",
            context.getString(R.string.developer_credit)
        )
        assertEquals("http://t.me/Nullemperor", context.getString(R.string.telegram_url))

        val expectedLabels = listOf(
            "1-BankMeli",
            "2-BankMelat",
            "3BankSaderat",
            "4-BankTejarat",
            "5-BankSepah",
            "6-Citizen",
            "7-Irancell",
            "8-Telegram"
        )
        assertEquals(expectedLabels, HudModule.entries.map { context.getString(it.labelRes) })
    }

    @Test
    fun `verify FTS4 high speed search and batch insert`() = runBlocking {
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
                    fullName = "Aria Parsa",
                    nationalCode = "0019283746",
                    phone = "09121112233",
                    accountOrHandle = "MELI-998877",
                    city = "Tehran",
                    rawDetails = "VIP-CORE",
                    gpuSignatureHash = 123456L,
                    importedAt = 1000L
                ),
                IdentityEntity(
                    moduleId = "bank_meli",
                    fullName = "Sara Shirazi",
                    nationalCode = "0099887766",
                    phone = "09354445566",
                    accountOrHandle = "MELI-112233",
                    city = "Shiraz",
                    rawDetails = "STD-CORE",
                    gpuSignatureHash = 654321L,
                    importedAt = 1000L
                )
            )
            dao.insertBatch(records)

            val stages = mutableListOf<SearchStage>()
            var finalHits = 0L
            repo.executeVisibleHighSpeedSearch("bank_meli", "Aria") { telemetry ->
                stages.add(telemetry.stage)
                finalHits = telemetry.matchCount
            }

            assertTrue(stages.contains(SearchStage.COMPLETE))
            assertEquals(1L, finalHits)
        } finally {
            db.close()
        }
    }
}
