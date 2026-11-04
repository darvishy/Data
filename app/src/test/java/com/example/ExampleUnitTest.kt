package com.example

import com.example.data.TextNormalizer
import com.example.engine.CsvSchemaParser
import com.example.engine.GpuComputeAccelerator
import com.example.model.HudModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `verify 6 HUD modules have clean unnumbered names without BankSepah or BankTejarat`() {
        val expected = listOf(
            "BankMeli",
            "BankMelat",
            "BankSaderat",
            "Citizen",
            "Irancell",
            "Telegram"
        )
        assertEquals(expected, HudModule.entries.map { it.displayLabel })
    }

    @Test
    fun `verify Persian and Arabic digits and Yeh Kaf normalization for search`() {
        val raw = "۰۹۱۲۳۴۵۶۷۸۹ يحيى كريمي\u200Cنژاد"
        val normalized = TextNormalizer.normalizeForSearch(raw)
        assertEquals("09123456789 یحیی کریمی نژاد", normalized)
    }

    @Test
    fun `verify BankMeli 5 column CSV format with and without header`() {
        val headerLine = "NATIONAL_CODE,CARD_NO,FULL_NAME,BIRTH_DATE,MOBILE"
        assertTrue(CsvSchemaParser.looksLikeCsvHeader(headerLine))
        val headers = CsvSchemaParser.splitCsvLineFast(headerLine)

        val rowLine = "0019283746,6037997112345678,Aria Parsa,1368/04/15,09121112233"

        val parsedHeader = CsvSchemaParser.parseIdentityCsvRow("bank_meli", rowLine, headers, 0)
        val parsedPositional = CsvSchemaParser.parseIdentityCsvRow("bank_meli", rowLine, null, 0)

        for (parsed in listOf(parsedHeader, parsedPositional)) {
            assertEquals("0019283746", parsed.nationalCode)
            assertEquals("6037997112345678", parsed.cardNumber)
            assertEquals("Aria Parsa", parsed.fullName)
            assertEquals("1368/04/15", parsed.birthDate)
            assertEquals("09121112233", parsed.phone)
        }
    }

    @Test
    fun `verify BankSaderat 12 column CSV format with and without header`() {
        val headerLine =
            "AccountNumber,DefineationNumber,FullName,CardNumber,Password_Hint,user_email_address,Passw,user_phone_number,Member,Username,DefineNum_Old,ID_Branch"
        assertTrue(CsvSchemaParser.looksLikeCsvHeader(headerLine))
        val headers = CsvSchemaParser.splitCsvLineFast(headerLine)

        val rowLine =
            "0102000012345,50001234,Kian Rad,6037691912345678,FirstPet,kian.rad@bsi.ir,BSI#9988,09123334455,MBR-7788,kian_rad,OLD-4455,BR-210"

        val parsedHeader = CsvSchemaParser.parseIdentityCsvRow("bank_saderat", rowLine, headers, 0)
        val parsedPositional = CsvSchemaParser.parseIdentityCsvRow("bank_saderat", rowLine, null, 0)

        for (parsed in listOf(parsedHeader, parsedPositional)) {
            assertEquals("0102000012345", parsed.accountOrHandle)
            assertEquals("50001234", parsed.idNumber)
            assertEquals("Kian Rad", parsed.fullName)
            assertEquals("6037691912345678", parsed.cardNumber)
            assertEquals("FirstPet", parsed.passwordHint)
            assertEquals("kian.rad@bsi.ir", parsed.email)
            assertEquals("BSI#9988", parsed.passwValue)
            assertEquals("09123334455", parsed.phone)
            assertEquals("MBR-7788", parsed.memberCode)
            assertEquals("kian_rad", parsed.username)
            assertEquals("OLD-4455", parsed.defNumOld)
            assertEquals("BR-210", parsed.branchId)
        }
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
    fun `verify Telegram semicolon 5 column CSV format with and without header`() {
        val headerLine = "account_id;phone;username;first_name;last_name"
        assertTrue(CsvSchemaParser.looksLikeCsvHeader(headerLine))
        val headers = CsvSchemaParser.splitCsvLineFast(headerLine)

        val rowLine = "784512963;989123456789;nullemperor_dev;Aria;Parsa"

        val parsedHeader = CsvSchemaParser.parseIdentityCsvRow("telegram", rowLine, headers, 0)
        val parsedPositional = CsvSchemaParser.parseIdentityCsvRow("telegram", rowLine, null, 0)

        for (parsed in listOf(parsedHeader, parsedPositional)) {
            assertEquals("784512963", parsed.accountOrHandle)
            assertEquals("784512963", parsed.nationalCode)
            assertEquals("989123456789", parsed.phone)
            assertEquals("@nullemperor_dev", parsed.username)
            assertEquals("Aria Parsa", parsed.fullName)
            assertTrue(CsvSchemaParser.passesModuleImportFilter("telegram", parsed))
        }

        val foreignRow = CsvSchemaParser.parseIdentityCsvRow(
            "telegram",
            "784512999;14155552671;foreign_user;John;Doe",
            headers,
            1
        )
        assertTrue(!CsvSchemaParser.passesModuleImportFilter("telegram", foreignRow))

        val emptyPhoneRow = CsvSchemaParser.parseIdentityCsvRow(
            "telegram",
            "784513000;;no_phone_user;Sara;Rad",
            headers,
            2
        )
        assertTrue(!CsvSchemaParser.passesModuleImportFilter("telegram", emptyPhoneRow))

        // Verify zero-allocation raw line 98 pre-filter (including Persian digits, quotes, '+', and empty-column guard)
        assertTrue(CsvSchemaParser.rawTelegramLinePasses98Filter("784512963;989123456789;user;Aria;Parsa", 1, ';'))
        assertTrue(CsvSchemaParser.rawTelegramLinePasses98Filter("784512963;\"+989123456789\";user;Aria;Parsa", 1, ';'))
        assertTrue(CsvSchemaParser.rawTelegramLinePasses98Filter("784512963;۹۸۹۱۲۳۴۵۶۷۸۹;user;Aria;Parsa", 1, ';'))
        assertTrue(!CsvSchemaParser.rawTelegramLinePasses98Filter("784512999;14155552671;foreign;John;Doe", 1, ';'))
        assertTrue(!CsvSchemaParser.rawTelegramLinePasses98Filter("784513000;;98_username_not_phone;Sara;Rad", 1, ';'))
    }

    @Test
    fun `verify BoundedLineReader caps runaway corrupt lines without crashing`() {
        val hugeLine = "989123456789;" + "X".repeat(25_000) + "\n784512963;989120001122;valid_after_corrupt;Kian;Rad"
        com.example.engine.BoundedLineReader(java.io.StringReader(hugeLine), maxLineChars = 8_192).use { reader ->
            val first = reader.readBoundedLine()
            val second = reader.readBoundedLine()
            val third = reader.readBoundedLine()
            assertEquals(8_192, first?.length)
            assertEquals("784512963;989120001122;valid_after_corrupt;Kian;Rad", second)
            assertEquals(null, third)
        }
    }

    @Test
    fun `verify GPU signature accelerator deterministic batch hashing`() {
        val accelerator = GpuComputeAccelerator()
        val sampleBatch = listOf(
            "0019283746,6037997112345678,Aria Parsa,1368/04/15,09121112233",
            "0102000012345,50001234,Kian Rad,6037691912345678,FirstPet,kian.rad@bsi.ir,BSI#9988,09123334455,MBR-7788,kian_rad,OLD-4455,BR-210"
        )
        val result = accelerator.computeBatchSignatures(sampleBatch, 0.25f)
        assertEquals(2, result.signatures.size)
        assertTrue(result.signatures[0] > 0L)
        assertTrue(result.signatures[1] > 0L)
        accelerator.release()
    }
}
