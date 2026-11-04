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
    fun `verify 6 HUD modules have clean unnumbered names with SabtAhval replacing Irancell`() {
        val expected = listOf(
            "BankMeli",
            "BankMelat",
            "BankSaderat",
            "Citizen",
            "SabtAhval",
            "Telegram"
        )
        assertEquals(expected, HudModule.entries.map { it.displayLabel })
        assertTrue(HudModule.SABT_AHVAL.usesMdfLdfEngine)
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

    @Test
    fun `verify record detail card fields and formatted copy share text`() {
        val record = com.example.data.IdentityEntity(
            id = 77L,
            moduleId = "bank_melat",
            fullName = "علی رضایی",
            nationalCode = "0019283746",
            phone = "09121112233",
            accountOrHandle = "4400112233",
            cardNumber = "6104337812345678",
            fatherName = "داریوش",
            birthDate = "1368/04/15",
            city = "تهران",
            province = "تهران",
            address = "خیابان ولیعصر، پلاک ۴۲",
            gpuSignatureHash = 0xABCDEFL,
            importedAt = 1000L
        )

        val fields = com.example.ui.components.buildRecordDetailFields(record, HudModule.BANK_MELAT)
        val fatherField = fields.first { it.label == "FATHER NAME" }
        assertTrue(fatherField.rightAlignValue)
        assertEquals("داریوش", fatherField.value)

        val formatted = com.example.ui.components.formatRecordInformationText(record, HudModule.BANK_MELAT, fields)
        assertTrue(formatted.contains("[BankMelat]"))
        assertTrue(formatted.contains("NAME: علی رضایی"))
        assertTrue(formatted.contains("NATIONAL CODE: 0019283746"))
        assertTrue(formatted.contains("MOBILE: 09121112233"))
        assertTrue(formatted.contains("CARD NUMBER: 6104337812345678"))
        assertTrue(formatted.contains("ADDRESS: خیابان ولیعصر، پلاک ۴۲"))
        // Placeholder "-" values should never pollute copied/shared text
        assertTrue(!formatted.contains(": -"))
    }

    @Test
    fun `verify SabtAhval binary MDF 8KB pages and LDF transaction log parser with deduplication`() {
        val sampleMdfRows = listOf(
            com.example.engine.MdfLdfDatabaseParser.BinaryPageSynthesizer.SyntheticSabtRow(
                nationalCode10 = "0019283746",
                shenasnameNo = 45821,
                firstName = "علی",
                lastName = "رضایی",
                fatherName = "داریوش",
                birthDate = "1368/04/15",
                city = "تهران",
                province = "تهران",
                serial = "الف/12-654321",
                postalCode = "1415567890",
                mobile = "09121112233"
            ),
            com.example.engine.MdfLdfDatabaseParser.BinaryPageSynthesizer.SyntheticSabtRow(
                nationalCode10 = "0078451296",
                shenasnameNo = 12094,
                firstName = "سارا",
                lastName = "شیرازی",
                fatherName = "بهرام",
                birthDate = "1374/09/21",
                city = "شیراز",
                province = "فارس",
                serial = "ب/34-987654",
                postalCode = "7134567890",
                mobile = "09354445566"
            )
        )

        // 1. Test UTF-16LE NVARCHAR binary 8,192-byte MDF page extraction
        val mdfUtf16Bytes = com.example.engine.MdfLdfDatabaseParser.BinaryPageSynthesizer
            .buildMdfPages(sampleMdfRows, useWindows1256Varchar = false)
        assertEquals(8_192, mdfUtf16Bytes.size)
        assertEquals(
            com.example.engine.MdfLdfDatabaseParser.SqlFileKind.MDF_DATA_FILE,
            com.example.engine.MdfLdfDatabaseParser.detectFileKind("SabtAhval.mdf", mdfUtf16Bytes, mdfUtf16Bytes.size)
        )

        val parsedMdf = com.example.engine.MdfLdfDatabaseParser.parseMdfDataPage(mdfUtf16Bytes, 0, 0)
        assertEquals(2, parsedMdf.size)
        assertEquals("0019283746", parsedMdf[0].nationalCode)
        assertEquals("45821", parsedMdf[0].idNumber)
        assertEquals("علی رضایی", parsedMdf[0].fullName)
        assertEquals("داریوش", parsedMdf[0].fatherName)
        assertEquals("1368/04/15", parsedMdf[0].birthDate)
        assertEquals("تهران", parsedMdf[0].city)
        assertEquals("الف/12-654321", parsedMdf[0].accountOrHandle)
        assertEquals("1415567890", parsedMdf[0].postalCode)
        assertEquals("09121112233", parsedMdf[0].phone)

        // 2. Test Windows-1256 Persian VARCHAR binary 8,192-byte MDF page extraction
        val mdfWin1256Bytes = com.example.engine.MdfLdfDatabaseParser.BinaryPageSynthesizer
            .buildMdfPages(sampleMdfRows, useWindows1256Varchar = true)
        val parsedWin1256 = com.example.engine.MdfLdfDatabaseParser.parseMdfDataPage(mdfWin1256Bytes, 0, 0)
        assertEquals(2, parsedWin1256.size)
        assertEquals("سارا شیرازی", parsedWin1256[1].fullName)
        assertEquals("بهرام", parsedWin1256[1].fatherName)
        assertEquals("0078451296", parsedWin1256[1].nationalCode)

        // 3. Test binary .ldf Transaction Log LOP_INSERT_ROWS extraction + MDF/LDF deduplication
        val newLogOnlyRow = com.example.engine.MdfLdfDatabaseParser.BinaryPageSynthesizer.SyntheticSabtRow(
            nationalCode10 = "0099112233",
            shenasnameNo = 88776,
            firstName = "کیان",
            lastName = "راد",
            fatherName = "فرهاد",
            birthDate = "1379/02/10",
            city = "اصفهان",
            province = "اصفهان",
            serial = "ج/56-112233",
            postalCode = "8146512345",
            mobile = "09132223344"
        )
        val ldfBytes = com.example.engine.MdfLdfDatabaseParser.BinaryPageSynthesizer
            .buildLdfLogStream(listOf(sampleMdfRows[0], newLogOnlyRow))
        assertEquals(
            com.example.engine.MdfLdfDatabaseParser.SqlFileKind.LDF_LOG_FILE,
            com.example.engine.MdfLdfDatabaseParser.detectFileKind("SabtAhval_log.ldf", ldfBytes, ldfBytes.size)
        )

        val (parsedLdf, _) = com.example.engine.MdfLdfDatabaseParser.scanLdfBufferChunk(
            buf = ldfBytes,
            validLen = ldfBytes.size,
            isFinalChunk = true,
            baseRowIndex = 0
        )
        assertEquals(2, parsedLdf.size)
        assertEquals("کیان راد", parsedLdf[1].fullName)
        assertEquals("0099112233", parsedLdf[1].nationalCode)

        val dedup = com.example.engine.MdfLdfDatabaseParser.CompactRecordDeduplicator()
        val combinedUnique = ArrayList<com.example.engine.ParsedCsvIdentityRow>()
        for (r in parsedMdf + parsedLdf) {
            if (dedup.markIfNew(r)) combinedUnique.add(r)
        }
        // 2 rows in MDF + 2 rows in LDF (1 overlapping duplicate + 1 new log-only insert) => 3 unique records
        assertEquals(3, combinedUnique.size)
    }
}


