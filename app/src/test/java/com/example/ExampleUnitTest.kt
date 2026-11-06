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
    fun `verify 8 HUD modules have clean unnumbered names without BankSepah or BankTejarat`() {
        val expected = listOf(
            "BankMeli",
            "BankMelat",
            "BankSaderat",
            "Citizen",
            "Irancell",
            "Telegram",
            "Basij",
            "Entezami"
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

        // Verify non-989 Iranian numbers (e.g. landline 9821...) are strictly rejected
        val nonMobileRow = CsvSchemaParser.parseIdentityCsvRow(
            "telegram",
            "784513001;982188776655;tehran_landline;Reza;Tehrani",
            headers,
            3
        )
        assertTrue(!CsvSchemaParser.passesModuleImportFilter("telegram", nonMobileRow))

        // Verify zero-allocation raw line 989 pre-filter (including Persian digits, quotes, '+', and empty-column guard)
        assertTrue(CsvSchemaParser.rawTelegramLinePasses989Filter("784512963;989123456789;user;Aria;Parsa", 1, ';'))
        assertTrue(CsvSchemaParser.rawTelegramLinePasses989Filter("784512963;\"+989123456789\";user;Aria;Parsa", 1, ';'))
        assertTrue(CsvSchemaParser.rawTelegramLinePasses989Filter("784512963;\"(989)123456789\";user;Aria;Parsa", 1, ';'))
        assertTrue(CsvSchemaParser.rawTelegramLinePasses989Filter("784512963;۹۸۹۱۲۳۴۵۶۷۸۹;user;Aria;Parsa", 1, ';'))
        assertTrue(!CsvSchemaParser.rawTelegramLinePasses989Filter("784512999;14155552671;foreign;John;Doe", 1, ';'))
        assertTrue(!CsvSchemaParser.rawTelegramLinePasses989Filter("784513001;982188776655;landline;Reza;Tehrani", 1, ';'))
        assertTrue(!CsvSchemaParser.rawTelegramLinePasses989Filter("784513000;;989_username_not_phone;Sara;Rad", 1, ';'))
    }

    @Test
    fun `verify Basij 5 column CSV format with and without header`() {
        val headerLine = "name,dob,national_id,address,postal_code"
        assertTrue(CsvSchemaParser.looksLikeCsvHeader(headerLine))
        val headers = CsvSchemaParser.splitCsvLineFast(headerLine)

        val rowLine = "Reza Ahmadi,1365/02/10,0012345678,\"Tehran Enghelab Sq No 12\",1415512345"

        val parsedHeader = CsvSchemaParser.parseIdentityCsvRow("basij", rowLine, headers, 0)
        val parsedPositional = CsvSchemaParser.parseIdentityCsvRow("basij", rowLine, null, 0)

        for (parsed in listOf(parsedHeader, parsedPositional)) {
            assertEquals("Reza Ahmadi", parsed.fullName)
            assertEquals("1365/02/10", parsed.birthDate)
            assertEquals("0012345678", parsed.nationalCode)
            assertEquals("Tehran Enghelab Sq No 12", parsed.address)
            assertEquals("1415512345", parsed.postalCode)
            assertEquals("-", parsed.phone)
        }
    }

    @Test
    fun `verify Entezami 6 column CSV format with and without header`() {
        val headerLine = "full_name,dob,national_id,address,postal_code,date_of_address"
        assertTrue(CsvSchemaParser.looksLikeCsvHeader(headerLine))
        val headers = CsvSchemaParser.splitCsvLineFast(headerLine)

        val rowLine = "Mahdi Hosseini,1370/08/20,0098765432,\"Isfahan Chaharbagh Ave\",8146598765,1402/05/11"

        val parsedHeader = CsvSchemaParser.parseIdentityCsvRow("entezami", rowLine, headers, 0)
        val parsedPositional = CsvSchemaParser.parseIdentityCsvRow("entezami", rowLine, null, 0)

        for (parsed in listOf(parsedHeader, parsedPositional)) {
            assertEquals("Mahdi Hosseini", parsed.fullName)
            assertEquals("1370/08/20", parsed.birthDate)
            assertEquals("0098765432", parsed.nationalCode)
            assertEquals("Isfahan Chaharbagh Ave", parsed.address)
            assertEquals("8146598765", parsed.postalCode)
            assertEquals("1402/05/11", parsed.defNumOld)
            assertTrue(parsed.rawDetails.contains("1402/05/11"))
            assertEquals("-", parsed.phone)
        }
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
    fun `verify universal search token normalization and multi-database module mapping`() {
        // Test Persian phone normalization to digits and variants
        val persianPhone = "۰۹۱۲۱۱۱۲۲۳۳"
        val normalized = TextNormalizer.normalizeForSearch(persianPhone)
        assertEquals("09121112233", normalized)

        // Verify all 6 modules resolve correctly from module ID for universal search cards
        val expectedModules = listOf(
            "bank_meli" to HudModule.BANK_MELI,
            "bank_melat" to HudModule.BANK_MELAT,
            "bank_saderat" to HudModule.BANK_SADERAT,
            "citizen" to HudModule.CITIZEN,
            "irancell" to HudModule.IRANCELL,
            "telegram" to HudModule.TELEGRAM
        )
        for ((id, expected) in expectedModules) {
            assertEquals(expected, HudModule.fromId(id))
        }

        // Test fallback for unknown ID
        assertEquals(HudModule.BANK_MELI, HudModule.fromId("unknown_xyz"))
    }

    @Test
    fun `verify backup file name generation and nexusbak extension`() {
        val fileName = com.example.engine.NexusDatabaseBackupEngine.generateDefaultBackupFileName()
        assertTrue(fileName.startsWith("nexus_hud_backup_"))
        assertTrue(fileName.endsWith(".nexusbak"))
    }

    @Test
    fun `verify database backup binary archive stream round trip serialization`() {
        val sampleRecords = listOf(
            com.example.data.IdentityEntity(
                id = 1L,
                moduleId = "bank_meli",
                fullName = "Aria Parsa",
                nationalCode = "0019283746",
                phone = "09121112233",
                accountOrHandle = "6037997112345678",
                cardNumber = "6037997112345678",
                fatherName = "Dariush",
                idNumber = "45821",
                birthDate = "1368/04/15",
                city = "Tehran",
                province = "Tehran",
                gpuSignatureHash = 123456L,
                importedAt = 9999L
            ),
            com.example.data.IdentityEntity(
                id = 2L,
                moduleId = "telegram",
                fullName = "Kian Rad",
                nationalCode = "784512963",
                phone = "989123456789",
                accountOrHandle = "784512963",
                username = "@kian_rad",
                gpuSignatureHash = 654321L,
                importedAt = 10000L
            )
        )

        val byteOut = java.io.ByteArrayOutputStream()
        java.io.DataOutputStream(byteOut).use { dataOut ->
            dataOut.write("NEXUSBK2".toByteArray(Charsets.US_ASCII))
            dataOut.writeInt(2)
            dataOut.writeLong(System.currentTimeMillis())
            dataOut.writeLong(sampleRecords.size.toLong())
            dataOut.flush()

            java.util.zip.GZIPOutputStream(byteOut).use { gzOut ->
                val pOut = java.io.DataOutputStream(gzOut)
                for (r in sampleRecords) {
                    pOut.writeUTF(r.moduleId)
                    pOut.writeUTF(r.fullName)
                    pOut.writeUTF(r.nationalCode)
                    pOut.writeUTF(r.phone)
                    pOut.writeUTF(r.accountOrHandle)
                    pOut.writeUTF(r.cardNumber)
                    pOut.writeUTF(r.fatherName)
                    pOut.writeUTF(r.idNumber)
                    pOut.writeUTF(r.birthDate)
                    pOut.writeUTF(r.city)
                    pOut.writeUTF(r.province)
                    pOut.writeUTF(r.birthCity)
                    pOut.writeUTF(r.birthProvince)
                    pOut.writeUTF(r.postalCode)
                    pOut.writeUTF(r.address)
                    pOut.writeUTF(r.email)
                    pOut.writeUTF(r.username)
                    pOut.writeUTF(r.memberCode)
                    pOut.writeUTF(r.branchId)
                    pOut.writeUTF(r.defNumOld)
                    pOut.writeUTF(r.passwordHint)
                    pOut.writeUTF(r.passwValue)
                    pOut.writeUTF(r.rawDetails)
                    pOut.writeLong(r.gpuSignatureHash)
                    pOut.writeLong(r.importedAt)
                }
                pOut.flush()
            }
        }

        val rawArchiveBytes = byteOut.toByteArray()
        assertTrue(rawArchiveBytes.isNotEmpty())

        // Read back and verify
        val byteIn = java.io.ByteArrayInputStream(rawArchiveBytes)
        val dataIn = java.io.DataInputStream(byteIn)
        val magic = ByteArray(8)
        dataIn.readFully(magic)
        assertEquals("NEXUSBK2", String(magic, Charsets.US_ASCII))
        val version = dataIn.readInt()
        assertEquals(2, version)
        val timestamp = dataIn.readLong()
        assertTrue(timestamp > 0L)
        val count = dataIn.readLong()
        assertEquals(sampleRecords.size.toLong(), count)

        val restored = ArrayList<com.example.data.IdentityEntity>()
        java.util.zip.GZIPInputStream(byteIn).use { gzIn ->
            val pIn = java.io.DataInputStream(gzIn)
            for (i in 0 until count) {
                restored.add(
                    com.example.data.IdentityEntity(
                        moduleId = pIn.readUTF(),
                        fullName = pIn.readUTF(),
                        nationalCode = pIn.readUTF(),
                        phone = pIn.readUTF(),
                        accountOrHandle = pIn.readUTF(),
                        cardNumber = pIn.readUTF(),
                        fatherName = pIn.readUTF(),
                        idNumber = pIn.readUTF(),
                        birthDate = pIn.readUTF(),
                        city = pIn.readUTF(),
                        province = pIn.readUTF(),
                        birthCity = pIn.readUTF(),
                        birthProvince = pIn.readUTF(),
                        postalCode = pIn.readUTF(),
                        address = pIn.readUTF(),
                        email = pIn.readUTF(),
                        username = pIn.readUTF(),
                        memberCode = pIn.readUTF(),
                        branchId = pIn.readUTF(),
                        defNumOld = pIn.readUTF(),
                        passwordHint = pIn.readUTF(),
                        passwValue = pIn.readUTF(),
                        rawDetails = pIn.readUTF(),
                        gpuSignatureHash = pIn.readLong(),
                        importedAt = pIn.readLong()
                    )
                )
            }
        }

        assertEquals(2, restored.size)
        assertEquals("bank_meli", restored[0].moduleId)
        assertEquals("Aria Parsa", restored[0].fullName)
        assertEquals("0019283746", restored[0].nationalCode)
        assertEquals("telegram", restored[1].moduleId)
        assertEquals("Kian Rad", restored[1].fullName)
        assertEquals("@kian_rad", restored[1].username)
    }
}


