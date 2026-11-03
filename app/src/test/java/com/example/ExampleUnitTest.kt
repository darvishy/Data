package com.example

import com.example.engine.CsvSchemaParser
import com.example.engine.GpuComputeAccelerator
import com.example.model.HudModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `verify 8 HUD modules match exact required labels`() {
        val expected = listOf(
            "1-BankMeli",
            "2-BankMelat",
            "3BankSaderat",
            "4-BankTejarat",
            "5-BankSepah",
            "6-Citizen",
            "7-Irancell",
            "8-Telegram"
        )
        assertEquals(expected, HudModule.entries.map { it.displayLabel })
    }

    @Test
    fun `verify BankMelat 13 column CSV format with and without header`() {
        val headerLine =
            "NATIONAL_CODE,ACCOUNT_NUMBER,FULL_NAME,FATHER_NAME,ID_NUMBER,BIRTH_DATE,CITY_NAME,PROVINCE_NAME,BIRTH_CITY,BIRTH_PROVINCE,ADDRESS,CARD_NO,MOBILE"
        assertTrue(CsvSchemaParser.looksLikeCsvHeader(headerLine))
        val headers = CsvSchemaParser.splitCsvLineFast(headerLine)

        val rowLine =
            "0019283746,4400112233,Aria Parsa,Dariush,45821,1368/04/15,Tehran,Tehran,Shiraz,Fars,\"Valiasr Ave, No 42\",6104337812345678,09121112233"

        val parsedHeader = CsvSchemaParser.parseIdentityCsvRow("bank_melat", rowLine, headers, 0)
        val parsedPositional = CsvSchemaParser.parseIdentityCsvRow("bank_melat", rowLine, null, 0)

        for (parsed in listOf(parsedHeader, parsedPositional)) {
            assertEquals("0019283746", parsed.nationalCode)
            assertEquals("4400112233", parsed.accountOrHandle)
            assertEquals("Aria Parsa", parsed.fullName)
            assertEquals("Dariush", parsed.fatherName)
            assertEquals("45821", parsed.idNumber)
            assertEquals("1368/04/15", parsed.birthDate)
            assertEquals("Tehran", parsed.city)
            assertEquals("Tehran", parsed.province)
            assertEquals("Shiraz", parsed.birthCity)
            assertEquals("Fars", parsed.birthProvince)
            assertEquals("Valiasr Ave, No 42", parsed.address)
            assertEquals("6104337812345678", parsed.cardNumber)
            assertEquals("09121112233", parsed.phone)
        }
    }

    @Test
    fun `verify Citizen 7 column CSV format with and without header`() {
        val headerLine = "FULLNAME,BIRTHDAY,National ID,Postal Code,Phone,City,Address"
        assertTrue(CsvSchemaParser.looksLikeCsvHeader(headerLine))
        val headers = CsvSchemaParser.splitCsvLineFast(headerLine)

        val rowLine =
            "Sara Shirazi,1372/09/21,0099887766,1415567890,09354445566,Shiraz,\"Zand Blvd, Block 9\""

        val parsedHeader = CsvSchemaParser.parseIdentityCsvRow("citizen", rowLine, headers, 0)
        val parsedPositional = CsvSchemaParser.parseIdentityCsvRow("citizen", rowLine, null, 0)

        for (parsed in listOf(parsedHeader, parsedPositional)) {
            assertEquals("Sara Shirazi", parsed.fullName)
            assertEquals("1372/09/21", parsed.birthDate)
            assertEquals("0099887766", parsed.nationalCode)
            assertEquals("1415567890", parsed.postalCode)
            assertEquals("09354445566", parsed.phone)
            assertEquals("Shiraz", parsed.city)
            assertEquals("Zand Blvd, Block 9", parsed.address)
        }
    }

    @Test
    fun `verify GPU signature accelerator deterministic batch hashing`() {
        val accelerator = GpuComputeAccelerator()
        val sampleBatch = listOf(
            "0019283746,4400112233,Aria Parsa,Dariush,45821,1368/04/15,Tehran,Tehran,Shiraz,Fars,Valiasr,6104337812345678,09121112233",
            "Sara Shirazi,1372/09/21,0099887766,1415567890,09354445566,Shiraz,Zand Blvd"
        )
        val result = accelerator.computeBatchSignatures(sampleBatch, 0.25f)
        assertEquals(2, result.signatures.size)
        assertTrue(result.signatures[0] > 0L)
        assertTrue(result.signatures[1] > 0L)
        accelerator.release()
    }
}
