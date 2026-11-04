package com.example.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * High-speed streaming binary parser for Microsoft SQL Server Primary Data Files (.mdf / .ndf)
 * and Transaction Log Files (.ldf) tailored for SabtAhval civil registry databases.
 *
 * Supports:
 * 1. Binary 8,192-byte SQL Server MDF Data Pages (`m_headerVersion = 1`, `m_type = 1`),
 *    slot offset arrays, `sys.syscolpars` catalog column discovery, and `FixedVar` records
 *    (`CHAR`/`NCHAR`/`INT`/`BIGINT`/`SMALLINT` + `NVARCHAR` UTF-16LE & `VARCHAR` Windows-1256/UTF-8).
 * 2. Binary SQL Server LDF Transaction Log files (`LOP_FORMAT_PAGE` 8KB pages and
 *    `LOP_INSERT_ROWS` / `LOP_MODIFY_ROW` `FixedVar` row images across 512-byte log blocks).
 * 3. SQL `INSERT INTO ... VALUES (...)` text dumps exported from SQL Server Management Studio.
 */
object MdfLdfDatabaseParser {

    const val MDF_PAGE_SIZE = 8_192
    private const val PAGE_HEADER_SIZE = 96
    private const val SYS_SYSCOLPARS_OBJ_ID = 41

    /**
     * Complete Windows-1256 (Arabic/Persian SQL Server `Arabic_CI_AS` / `Persian_100_CI_AI`)
     * byte-to-Unicode lookup table so Persian letters (`پ`, `چ`, `ژ`, `گ`, `ک`, `ی`) decode
     * accurately on all Android runtimes and minimal JVMs without optional `charsets.jar`.
     */
    private val CP1256_TABLE: CharArray = charArrayOf(
        // 0x80..0x8F
        '\u20AC', '\u067E', '\u201A', '\u0192', '\u201E', '\u2026', '\u2020', '\u2021',
        '\u02C6', '\u2030', '\u0679', '\u2039', '\u0152', '\u0686', '\u0698', '\u0688',
        // 0x90..0x9F
        '\u06AF', '\u2018', '\u2019', '\u201C', '\u201D', '\u2022', '\u2013', '\u2014',
        '\u06A9', '\u2122', '\u0691', '\u203A', '\u0153', '\u200C', '\u200D', '\u06BA',
        // 0xA0..0xAF
        '\u00A0', '\u060C', '\u00A2', '\u00A3', '\u00A4', '\u00A5', '\u00A6', '\u00A7',
        '\u00A8', '\u00A9', '\u06BE', '\u00AB', '\u00AC', '\u00AD', '\u00AE', '\u00AF',
        // 0xB0..0xBF
        '\u00B0', '\u00B1', '\u00B2', '\u00B3', '\u00B4', '\u00B5', '\u00B6', '\u00B7',
        '\u00B8', '\u00B9', '\u061B', '\u00BB', '\u00BC', '\u00BD', '\u00BE', '\u061F',
        // 0xC0..0xCF
        '\u06C1', '\u0621', '\u0622', '\u0623', '\u0624', '\u0625', '\u0626', '\u0627',
        '\u0628', '\u0629', '\u062A', '\u062B', '\u062C', '\u062D', '\u062E', '\u062F',
        // 0xD0..0xDF
        '\u0630', '\u0631', '\u0632', '\u0633', '\u0634', '\u0635', '\u0636', '\u00D7',
        '\u0637', '\u0638', '\u0639', '\u063A', '\u0640', '\u0641', '\u0642', '\u0643',
        // 0xE0..0xEF
        '\u00E0', '\u0644', '\u00E2', '\u0645', '\u0646', '\u0647', '\u0648', '\u00E7',
        '\u00E8', '\u00E9', '\u00EA', '\u00EB', '\u0649', '\u064A', '\u00EE', '\u00EF',
        // 0xF0..0xFF
        '\u064B', '\u064C', '\u064D', '\u064E', '\u064F', '\u0650', '\u0651', '\u00F7',
        '\u0652', '\u00F9', '\u00FA', '\u00FB', '\u00FC', '\u200E', '\u200F', '\u06D2'
    )

    private fun decodeWindows1256Persian(buf: ByteArray, offset: Int, len: Int): String {
        val chars = CharArray(len)
        for (i in 0 until len) {
            val b = buf[offset + i].toInt() and 0xFF
            val ch = if (b < 0x80) b.toChar() else CP1256_TABLE[b - 0x80]
            // Normalize Arabic Yeh/Kaf in CP1256 (0xED = \u064A, 0xDF = \u0643) to standard Persian Yeh/Kaf
            chars[i] = when (ch) {
                '\u064A', '\u0649' -> '\u06CC'
                '\u0643' -> '\u06A9'
                else -> ch
            }
        }
        return String(chars)
    }

    internal fun encodeWindows1256Persian(text: String): ByteArray {
        val out = ByteArray(text.length)
        for (i in text.indices) {
            val ch = when (val c = text[i]) {
                '\u06CC' -> '\u064A' // Persian Yeh -> CP1256 0xED
                else -> c
            }
            if (ch.code < 0x80) {
                out[i] = ch.code.toByte()
            } else {
                val idx = CP1256_TABLE.indexOf(ch)
                out[i] = if (idx >= 0) (0x80 + idx).toByte() else '?'.code.toByte()
            }
        }
        return out
    }

    enum class SqlFileKind {
        MDF_DATA_FILE,
        LDF_LOG_FILE,
        SQL_OR_TEXT_DUMP
    }

    /**
     * Memory-bounded open-addressing 64-bit hash set (524,288 slots = 4 MB) used to deduplicate
     * records when importing `.mdf` data pages alongside `.ldf` transaction log inserts/updates.
     */
    class CompactRecordDeduplicator(powerOfTwoBits: Int = 19) {
        private val capacity = 1 shl powerOfTwoBits
        private val mask = capacity - 1
        private val table = LongArray(capacity)
        private var size = 0

        fun markIfNew(row: ParsedCsvIdentityRow): Boolean {
            var h = -0x340d631b7bdddcdbL
            h = fnvMix(h, row.nationalCode)
            h = fnvMix(h, row.fullName)
            h = fnvMix(h, row.idNumber)
            h = fnvMix(h, row.birthDate)
            if (h == 0L) h = 1L

            if (size >= (capacity * 3) shr 2) {
                table.fill(0L)
                size = 0
            }

            var idx = (h.toInt() xor (h ushr 32).toInt()) and mask
            while (true) {
                val existing = table[idx]
                if (existing == 0L) {
                    table[idx] = h
                    size++
                    return true
                }
                if (existing == h) {
                    return false
                }
                idx = (idx + 1) and mask
            }
        }

        private fun fnvMix(seed: Long, text: String): Long {
            var h = seed
            for (i in text.indices) {
                h = (h xor text[i].code.toLong()) * 0x100000001b3L
            }
            return (h xor 0xFFL) * 0x100000001b3L
        }
    }

    fun detectFileKind(fileName: String, headerSample: ByteArray, sampleLen: Int): SqlFileKind {
        val lowerName = fileName.lowercase()
        if (sampleLen >= 96) {
            // Check if page 0 or page 1 has a valid SQL Server 8KB page header (`m_headerVersion == 1`)
            val p0Valid = isPlausibleMdfPageHeader(headerSample, 0, sampleLen)
            val p1Valid = sampleLen >= MDF_PAGE_SIZE + 96 &&
                isPlausibleMdfPageHeader(headerSample, MDF_PAGE_SIZE, sampleLen)
            if (p0Valid || p1Valid) {
                return if (lowerName.endsWith(".ldf")) SqlFileKind.LDF_LOG_FILE else SqlFileKind.MDF_DATA_FILE
            }
        }

        if (lowerName.endsWith(".ldf")) return SqlFileKind.LDF_LOG_FILE
        if (lowerName.endsWith(".mdf") || lowerName.endsWith(".ndf")) {
            return if (looksLikeReadableTextSample(headerSample, sampleLen)) {
                SqlFileKind.SQL_OR_TEXT_DUMP
            } else {
                SqlFileKind.MDF_DATA_FILE
            }
        }

        return if (looksLikeReadableTextSample(headerSample, sampleLen)) {
            SqlFileKind.SQL_OR_TEXT_DUMP
        } else {
            SqlFileKind.LDF_LOG_FILE
        }
    }

    private fun isPlausibleMdfPageHeader(buf: ByteArray, offset: Int, limit: Int): Boolean {
        if (offset + PAGE_HEADER_SIZE > limit) return false
        val headerVer = buf[offset].toInt() and 0xFF
        val pageType = buf[offset + 1].toInt() and 0xFF
        val level = buf[offset + 3].toInt() and 0xFF
        val slotCnt = readUInt16LE(buf, offset + 22)
        val freeCnt = readUInt16LE(buf, offset + 28)
        val freeData = readUInt16LE(buf, offset + 30)
        return headerVer == 1 &&
            pageType in 1..20 &&
            level <= 16 &&
            slotCnt <= 1024 &&
            freeCnt <= MDF_PAGE_SIZE &&
            freeData in 0..MDF_PAGE_SIZE
    }

    private fun looksLikeReadableTextSample(buf: ByteArray, len: Int): Boolean {
        if (len <= 0) return false
        val checkLen = minOf(len, 1024)
        var zeroBytes = 0
        var controlBytes = 0
        for (i in 0 until checkLen) {
            val b = buf[i].toInt() and 0xFF
            if (b == 0) zeroBytes++
            else if (b < 9 || (b in 14..31)) controlBytes++
        }
        return zeroBytes == 0 && controlBytes * 20 <= checkLen
    }

    /**
     * Scans an 8KB-aligned MDF buffer for `sys.syscolpars` (Object ID 41) catalog metadata,
     * mapping `tableObjectId -> ordered list of column names`.
     */
    fun extractCatalogColumnMap(
        buffer: ByteArray,
        bytesRead: Int,
        catalogOut: MutableMap<Int, MutableList<Pair<Int, String>>>
    ) {
        var pageOffset = 0
        while (pageOffset + MDF_PAGE_SIZE <= bytesRead) {
            val ver = buffer[pageOffset].toInt() and 0xFF
            val type = buffer[pageOffset + 1].toInt() and 0xFF
            val objId = readInt32LE(buffer, pageOffset + 24)
            if (ver == 1 && type == 1 && objId == SYS_SYSCOLPARS_OBJ_ID) {
                parseSysColParsPage(buffer, pageOffset, catalogOut)
            }
            pageOffset += MDF_PAGE_SIZE
        }
    }

    private fun parseSysColParsPage(
        buf: ByteArray,
        pageOffset: Int,
        catalogOut: MutableMap<Int, MutableList<Pair<Int, String>>>
    ) {
        val slotCnt = readUInt16LE(buf, pageOffset + 22)
        if (slotCnt !in 1..1024) return
        val slotArrayBottom = MDF_PAGE_SIZE - slotCnt * 2
        if (slotArrayBottom < PAGE_HEADER_SIZE) return

        for (s in 0 until slotCnt) {
            val relStart = readUInt16LE(buf, pageOffset + MDF_PAGE_SIZE - 2 * (s + 1))
            if (relStart !in PAGE_HEADER_SIZE..(slotArrayBottom - 16)) continue
            val absStart = pageOffset + relStart
            val statusA = buf[absStart].toInt() and 0xFF
            if ((statusA and 0x20) == 0) continue
            val fixedLen = readUInt16LE(buf, absStart + 2)
            if (fixedLen < 14 || relStart + fixedLen + 6 > slotArrayBottom) continue

            // sys.syscolpars fixed layout begins with: id (int32 @ +4), number (int16 @ +8), colid (int32 @ +10)
            val tableObjId = readInt32LE(buf, absStart + 4)
            val colId = readInt32LE(buf, absStart + 10)
            if (tableObjId <= 99 || colId !in 1..128) continue

            val rawVarCols = extractFixedVarRawColumns(buf, absStart, pageOffset + slotArrayBottom, pminlen = 4)
                ?: continue
            val colName = rawVarCols.varStrings.firstOrNull { it.isNotBlank() } ?: continue
            val list = catalogOut.getOrPut(tableObjId) { ArrayList() }
            if (list.none { it.first == colId }) {
                list.add(colId to colName)
                list.sortBy { it.first }
            }
        }
    }

    /**
     * Parses a single 8,192-byte SQL Server MDF Data Page (`m_type == 1`) into `ParsedCsvIdentityRow` records.
     */
    fun parseMdfDataPage(
        buf: ByteArray,
        pageOffset: Int,
        baseRowIndex: Int,
        catalogColumns: Map<Int, List<String>> = emptyMap()
    ): List<ParsedCsvIdentityRow> {
        if (pageOffset + MDF_PAGE_SIZE > buf.size) return emptyList()
        val headerVer = buf[pageOffset].toInt() and 0xFF
        val pageType = buf[pageOffset + 1].toInt() and 0xFF
        val level = buf[pageOffset + 3].toInt() and 0xFF
        if (headerVer != 1 || pageType != 1 || level != 0) return emptyList()

        val pminlen = readUInt16LE(buf, pageOffset + 14)
        val slotCnt = readUInt16LE(buf, pageOffset + 22)
        val mObjId = readInt32LE(buf, pageOffset + 24)
        val freeData = readUInt16LE(buf, pageOffset + 30)

        // Skip internal SQL Server system catalog base tables (object IDs 1..99)
        if (mObjId in 1..99) return emptyList()
        if (pminlen !in 4..8000 || slotCnt !in 1..1024) return emptyList()

        val slotArrayBottom = MDF_PAGE_SIZE - slotCnt * 2
        if (slotArrayBottom < PAGE_HEADER_SIZE) return emptyList()

        val validOffsets = IntArray(slotCnt)
        var validCount = 0
        for (s in 0 until slotCnt) {
            val slotRel = readUInt16LE(buf, pageOffset + MDF_PAGE_SIZE - 2 * (s + 1))
            if (slotRel in PAGE_HEADER_SIZE until slotArrayBottom) {
                validOffsets[validCount++] = slotRel
            }
        }
        if (validCount == 0) return emptyList()

        // Sort copy to compute exact upper-bound per record on the 8KB page
        val sortedOffsets = validOffsets.copyOf(validCount).also { it.sort() }
        val dataTop = if (freeData in PAGE_HEADER_SIZE..slotArrayBottom) freeData else slotArrayBottom
        val knownColNames = catalogColumns[mObjId]

        val out = ArrayList<ParsedCsvIdentityRow>(validCount)
        for (idx in 0 until validCount) {
            val relStart = validOffsets[idx]
            val posInSorted = sortedOffsets.binarySearch(relStart)
            val nextRel = if (posInSorted >= 0 && posInSorted + 1 < sortedOffsets.size) {
                sortedOffsets[posInSorted + 1]
            } else {
                maxOf(dataTop, relStart + pminlen)
            }.coerceAtMost(slotArrayBottom)

            val rawRecord = extractFixedVarRawColumns(
                buf = buf,
                absStart = pageOffset + relStart,
                absLimit = pageOffset + nextRel,
                pminlen = pminlen
            ) ?: continue

            val mapped = mapDecodedColumnsToSabtAhvalRow(
                raw = rawRecord,
                columnNames = knownColNames,
                rowIndex = baseRowIndex + out.size
            )
            if (mapped != null) {
                out.add(mapped)
            }
        }
        return out
    }

    /**
     * Scans a binary SQL Server `.ldf` Transaction Log buffer for both:
     * 1. Embedded 8,192-byte `LOP_FORMAT_PAGE` formatted Data Pages, and
     * 2. `LOP_INSERT_ROWS` / `LOP_MODIFY_ROW` `FixedVar` row images inside 512-byte log blocks.
     *
     * Returns `(parsedRows, safeNextStartOffset)` so overlapping chunk reads never split a log record.
     */
    fun scanLdfBufferChunk(
        buf: ByteArray,
        validLen: Int,
        isFinalChunk: Boolean,
        baseRowIndex: Int
    ): Pair<List<ParsedCsvIdentityRow>, Int> {
        if (validLen < 24) return emptyList<ParsedCsvIdentityRow>() to validLen
        val safeScanEnd = if (isFinalChunk) validLen else (validLen - MDF_PAGE_SIZE).coerceAtLeast(0)
        val results = ArrayList<ParsedCsvIdentityRow>(256)

        var i = 0
        while (i < safeScanEnd) {
            // Check 1: Full 8KB formatted data page (`LOP_FORMAT_PAGE` image aligned at i)
            if (i + MDF_PAGE_SIZE <= validLen &&
                buf[i] == 1.toByte() &&
                buf[i + 1] == 1.toByte() &&
                buf[i + 3] == 0.toByte()
            ) {
                val pageRows = parseMdfDataPage(buf, i, baseRowIndex + results.size)
                if (pageRows.isNotEmpty()) {
                    results.addAll(pageRows)
                    i += MDF_PAGE_SIZE
                    continue
                }
            }

            // Check 2: Fast 2-instruction pre-filter for SQL Server FixedVar row inside LOP_INSERT_ROWS / LOP_MODIFY_ROW
            if (i + 16 <= validLen && buf[i + 1] == 0.toByte()) {
                val statusA = buf[i].toInt() and 0xFF
                if (statusA == 0x30 || statusA == 0x10 || statusA == 0x36 || statusA == 0x16 || statusA == 0x70) {
                    val maxEnd = minOf(validLen, i + 2048)
                    val decodedPair = tryExtractStrictLogFixedVar(buf, i, maxEnd)
                    if (decodedPair != null) {
                        val (rawRec, consumedBytes) = decodedPair
                        val mapped = mapDecodedColumnsToSabtAhvalRow(
                            raw = rawRec,
                            columnNames = null,
                            rowIndex = baseRowIndex + results.size
                        )
                        if (mapped != null && isHighConfidenceLdfIdentityRow(mapped)) {
                            results.add(mapped)
                            i += consumedBytes.coerceAtLeast(8)
                            continue
                        }
                    }
                }
            }
            i++
        }

        val consumedUntil = if (isFinalChunk) validLen else safeScanEnd
        return results to consumedUntil
    }

    /**
     * Parses SQL `INSERT INTO ... VALUES (...), (...)` dump lines or delimited text exports.
     */
    fun parseSqlDumpOrDelimitedLine(line: String, rowIndex: Int): List<ParsedCsvIdentityRow> {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("--") || trimmed.startsWith("/*")) return emptyList()

        val upper = trimmed.uppercase()
        if (upper.startsWith("CREATE ") || upper.startsWith("ALTER ") ||
            upper.startsWith("SET ") || upper.startsWith("USE ") || upper == "GO"
        ) {
            return emptyList()
        }

        val valuesIdx = upper.indexOf("VALUES")
        if (upper.startsWith("INSERT") && valuesIdx >= 0) {
            val colHeaderPart = trimmed.substring(0, valuesIdx)
            val openCol = colHeaderPart.indexOf('(')
            val closeCol = colHeaderPart.lastIndexOf(')')
            val sqlHeaders = if (openCol >= 0 && closeCol > openCol) {
                colHeaderPart.substring(openCol + 1, closeCol)
                    .split(',')
                    .map { it.trim().trim('[', ']', '"', '`', '\'') }
            } else {
                null
            }

            val tuples = extractSqlValueTuples(trimmed.substring(valuesIdx + 6))
            if (tuples.isEmpty()) return emptyList()
            val out = ArrayList<ParsedCsvIdentityRow>(tuples.size)
            for (tupleCols in tuples) {
                val raw = DecodedFixedVarColumns(
                    fixedStrings = emptyList(),
                    fixedNumbers = emptyList(),
                    varStrings = tupleCols
                )
                mapDecodedColumnsToSabtAhvalRow(raw, sqlHeaders, rowIndex + out.size)?.let { out.add(it) }
            }
            return out
        }

        val single = CsvSchemaParser.parseIdentityCsvRow("sabt_ahval", trimmed, null, rowIndex)
        return listOf(single)
    }

    private fun extractSqlValueTuples(valuesTail: String): List<List<String>> {
        val tuples = ArrayList<List<String>>()
        var inTuple = false
        var inSingleQuote = false
        var currentTuple = ArrayList<String>(10)
        val sb = StringBuilder(64)
        var i = 0
        val len = valuesTail.length

        while (i < len) {
            val c = valuesTail[i]
            when {
                inSingleQuote -> {
                    if (c == '\'' && i + 1 < len && valuesTail[i + 1] == '\'') {
                        sb.append('\'')
                        i++
                    } else if (c == '\'') {
                        inSingleQuote = false
                    } else {
                        sb.append(c)
                    }
                }
                !inTuple -> {
                    if (c == '(') {
                        inTuple = true
                        currentTuple = ArrayList(10)
                        sb.setLength(0)
                    }
                }
                else -> {
                    when (c) {
                        '\'' -> {
                            // Strip SQL Server Unicode literal prefix N'...'
                            if (sb.length == 1 && (sb[0] == 'N' || sb[0] == 'n')) {
                                sb.setLength(0)
                            }
                            inSingleQuote = true
                        }
                        ',' -> {
                            currentTuple.add(cleanSqlToken(sb.toString()))
                            sb.setLength(0)
                        }
                        ')' -> {
                            currentTuple.add(cleanSqlToken(sb.toString()))
                            sb.setLength(0)
                            if (currentTuple.isNotEmpty()) {
                                tuples.add(currentTuple)
                            }
                            inTuple = false
                        }
                        else -> sb.append(c)
                    }
                }
            }
            i++
        }
        return tuples
    }

    private fun cleanSqlToken(raw: String): String {
        val t = raw.trim()
        return if (t.equals("NULL", ignoreCase = true)) "" else t
    }

    internal class DecodedFixedVarColumns(
        val fixedStrings: List<String>,
        val fixedNumbers: List<Long>,
        val varStrings: List<String>
    )

    private fun extractFixedVarRawColumns(
        buf: ByteArray,
        absStart: Int,
        absLimit: Int,
        pminlen: Int
    ): DecodedFixedVarColumns? {
        if (absStart + 6 > absLimit || absLimit > buf.size) return null
        val statusA = buf[absStart].toInt() and 0xFF
        val recType = (statusA shr 1) and 0x07
        // Only PRIMARY_RECORD (0) and FORWARDED_RECORD (3) hold live table data
        if (recType != 0 && recType != 3) return null

        val fixedLen = readUInt16LE(buf, absStart + 2)
        if (fixedLen < 4 || fixedLen < pminlen || absStart + fixedLen + 2 > absLimit) return null

        val hasNullBitmap = (statusA and 0x10) != 0
        val hasVarCols = (statusA and 0x20) != 0

        var cursor = absStart + fixedLen
        if (hasNullBitmap) {
            if (cursor + 2 > absLimit) return null
            val numCols = readUInt16LE(buf, cursor)
            if (numCols !in 1..256) return null
            val bitmapBytes = (numCols + 7) shr 3
            cursor += 2 + bitmapBytes
            if (cursor > absLimit) return null
        }

        val varStrings = ArrayList<String>(10)
        if (hasVarCols && cursor + 2 <= absLimit) {
            val numVarCols = readUInt16LE(buf, cursor)
            if (numVarCols !in 0..128) return null
            val offsetTableEnd = cursor + 2 + numVarCols * 2
            if (offsetTableEnd > absLimit) return null

            var prevEndRel = offsetTableEnd - absStart
            for (v in 0 until numVarCols) {
                val rawEnd = readUInt16LE(buf, cursor + 2 + v * 2)
                val isComplexLob = (rawEnd and 0x8000) != 0
                val endRel = rawEnd and 0x7FFF
                if (endRel < prevEndRel || absStart + endRel > absLimit) return null

                if (!isComplexLob && endRel > prevEndRel) {
                    val str = decodeSqlColumnBytes(buf, absStart + prevEndRel, endRel - prevEndRel)
                    varStrings.add(str)
                } else {
                    varStrings.add("")
                }
                prevEndRel = endRel
            }
        }

        val (fixedStrings, fixedNumbers) = decodeFixedPortion(buf, absStart + 4, fixedLen - 4)
        if (varStrings.isEmpty() && fixedStrings.isEmpty() && fixedNumbers.isEmpty()) return null

        return DecodedFixedVarColumns(
            fixedStrings = fixedStrings,
            fixedNumbers = fixedNumbers,
            varStrings = varStrings
        )
    }

    private fun tryExtractStrictLogFixedVar(
        buf: ByteArray,
        absStart: Int,
        absMaxEnd: Int
    ): Pair<DecodedFixedVarColumns, Int>? {
        val statusA = buf[absStart].toInt() and 0xFF
        val hasNullBitmap = (statusA and 0x10) != 0
        val hasVarCols = (statusA and 0x20) != 0
        if (!hasNullBitmap) return null

        val fixedLen = readUInt16LE(buf, absStart + 2)
        if (fixedLen !in 4..256 || absStart + fixedLen + 4 > absMaxEnd) return null

        val numCols = readUInt16LE(buf, absStart + fixedLen)
        if (numCols !in 2..64) return null
        val bitmapBytes = (numCols + 7) shr 3
        val varHeaderPos = absStart + fixedLen + 2 + bitmapBytes
        if (varHeaderPos > absMaxEnd) return null

        val varStrings = ArrayList<String>(10)
        var recordEndRel = varHeaderPos - absStart

        if (hasVarCols) {
            if (varHeaderPos + 2 > absMaxEnd) return null
            val numVarCols = readUInt16LE(buf, varHeaderPos)
            if (numVarCols !in 1..minOf(numCols, 32)) return null
            val offsetTableEnd = varHeaderPos + 2 + numVarCols * 2
            if (offsetTableEnd > absMaxEnd) return null

            var prevEndRel = offsetTableEnd - absStart
            for (v in 0 until numVarCols) {
                val rawEnd = readUInt16LE(buf, varHeaderPos + 2 + v * 2)
                if ((rawEnd and 0x8000) != 0) return null
                val endRel = rawEnd and 0x7FFF
                if (endRel < prevEndRel || endRel > 1800 || absStart + endRel > absMaxEnd) return null
                if (endRel > prevEndRel) {
                    varStrings.add(decodeSqlColumnBytes(buf, absStart + prevEndRel, endRel - prevEndRel))
                } else {
                    varStrings.add("")
                }
                prevEndRel = endRel
            }
            if (prevEndRel <= (offsetTableEnd - absStart) + 2) return null
            recordEndRel = prevEndRel
        } else {
            return null
        }

        val (fixedStrings, fixedNumbers) = decodeFixedPortion(buf, absStart + 4, fixedLen - 4)
        return DecodedFixedVarColumns(fixedStrings, fixedNumbers, varStrings) to recordEndRel
    }

    /**
     * Extracts fixed-length `CHAR(n)` / `NCHAR(n)` strings (such as `CHAR(10)` NationalCode or BirthDate)
     * as well as `INT` / `SMALLINT` / `BIGINT` values (such as ShenasnameNo, BirthYear/Month/Day, or numeric IDs).
     */
    private fun decodeFixedPortion(
        buf: ByteArray,
        start: Int,
        len: Int
    ): Pair<List<String>, List<Long>> {
        if (len <= 0 || start + len > buf.size) return emptyList<String>() to emptyList()
        val fixedStrings = ArrayList<String>(4)
        val fixedNumbers = ArrayList<Long>(6)

        val consumedMask = BooleanArray(len)

        // 1. Detect ASCII CHAR(8..16) tokens (e.g. 10-digit NationalCode "0019283746" or date "1368/04/15")
        var pos = 0
        while (pos < len) {
            if (isPrintableFixedAsciiTokenChar(buf[start + pos])) {
                var runEnd = pos + 1
                while (runEnd < len && isPrintableFixedAsciiTokenChar(buf[start + runEnd])) {
                    runEnd++
                }
                val runLen = runEnd - pos
                if (runLen in 8..20) {
                    val token = String(buf, start + pos, runLen, Charsets.US_ASCII).trim()
                    if (token.count { it.isDigit() } >= 6) {
                        fixedStrings.add(token)
                        for (k in pos until runEnd) consumedMask[k] = true
                    }
                }
                pos = runEnd
            } else {
                pos++
            }
        }

        // 2. Detect UTF-16LE NCHAR(8..12) digit/date runs if unconsumed
        pos = 0
        while (pos + 16 <= len) {
            if (!consumedMask[pos] && isUtf16AsciiDigitOrSlash(buf, start + pos)) {
                var chars = 0
                while (pos + (chars + 1) * 2 <= len &&
                    !consumedMask[pos + chars * 2] &&
                    isUtf16AsciiDigitOrSlash(buf, start + pos + chars * 2)
                ) {
                    chars++
                }
                if (chars in 8..16) {
                    val token = String(buf, start + pos, chars * 2, Charsets.UTF_16LE).trim()
                    if (token.count { it.isDigit() } >= 6) {
                        fixedStrings.add(token)
                        for (k in pos until pos + chars * 2) consumedMask[k] = true
                        pos += chars * 2
                        continue
                    }
                }
            }
            pos += 2
        }

        // 3. Extract unconsumed 4-byte INTs / 8-byte BIGINTs / Solar Hijri Year+Month+Day tuples
        var offset = 0
        while (offset < len) {
            if (consumedMask[offset]) {
                offset++
                continue
            }
            var freeRun = 0
            while (offset + freeRun < len && !consumedMask[offset + freeRun]) {
                freeRun++
            }

            // Check for (BirthYear SMALLINT 1280..1406, BirthMonth TINYINT/SMALLINT 1..12, BirthDay TINYINT/SMALLINT 1..31)
            if (freeRun >= 4) {
                val y16 = readUInt16LE(buf, start + offset)
                val m8 = buf[start + offset + 2].toInt() and 0xFF
                val d8 = buf[start + offset + 3].toInt() and 0xFF
                if (y16 in 1280..1406 && m8 in 1..12 && d8 in 1..31) {
                    fixedStrings.add(formatDateParts(y16, m8, d8))
                    offset += 4
                    continue
                }
            }
            if (freeRun >= 6) {
                val y16 = readUInt16LE(buf, start + offset)
                val m16 = readUInt16LE(buf, start + offset + 2)
                val d16 = readUInt16LE(buf, start + offset + 4)
                if (y16 in 1280..1406 && m16 in 1..12 && d16 in 1..31) {
                    fixedStrings.add(formatDateParts(y16, m16, d16))
                    offset += 6
                    continue
                }
            }

            if (freeRun >= 8) {
                val v64 = readInt64LE(buf, start + offset)
                if (v64 in 10_000_000L..99_999_999_999L) {
                    fixedNumbers.add(v64)
                    offset += 8
                    continue
                }
            }
            if (freeRun >= 4) {
                val v32 = readInt32LE(buf, start + offset).toLong()
                if (v32 in 1L..2_147_483_647L) {
                    fixedNumbers.add(v32)
                }
                offset += 4
            } else {
                offset += freeRun
            }
        }

        return fixedStrings to fixedNumbers
    }

    private fun isPrintableFixedAsciiTokenChar(b: Byte): Boolean {
        val c = b.toInt() and 0xFF
        return (c in 0x30..0x39) || c == 0x2F || c == 0x2D || c == 0x20
    }

    private fun isUtf16AsciiDigitOrSlash(buf: ByteArray, pos: Int): Boolean {
        val lo = buf[pos].toInt() and 0xFF
        val hi = buf[pos + 1].toInt() and 0xFF
        return hi == 0 && ((lo in 0x30..0x39) || lo == 0x2F || lo == 0x2D || lo == 0x20)
    }

    private fun formatDateParts(year: Int, month: Int, day: Int): String {
        val mm = if (month < 10) "0$month" else month.toString()
        val dd = if (day < 10) "0$day" else day.toString()
        return "$year/$mm/$dd"
    }

    /**
     * Automatically distinguishes SQL Server `NVARCHAR` (UTF-16LE Persian/Latin) from
     * `VARCHAR` (UTF-8 or Persian/Arabic Windows-1256 collation).
     */
    internal fun decodeSqlColumnBytes(buf: ByteArray, offset: Int, len: Int): String {
        if (len <= 0 || offset + len > buf.size) return ""

        if (len >= 2 && len % 2 == 0 && looksLikeUtf16LeColumn(buf, offset, len)) {
            return cleanDecodedText(String(buf, offset, len, Charsets.UTF_16LE))
        }

        if (isValidUtf8Slice(buf, offset, len)) {
            return cleanDecodedText(String(buf, offset, len, Charsets.UTF_8))
        }

        return cleanDecodedText(decodeWindows1256Persian(buf, offset, len))
    }

    private fun looksLikeUtf16LeColumn(buf: ByteArray, offset: Int, len: Int): Boolean {
        val pairs = len shr 1
        var validUtf16Pairs = 0
        var hasZeroOrPersianHighByte = false
        for (p in 0 until pairs) {
            val lo = buf[offset + p * 2].toInt() and 0xFF
            val hi = buf[offset + p * 2 + 1].toInt() and 0xFF
            when {
                // ASCII / Latin-1 in UTF-16LE (high byte 0x00, printable low byte)
                hi == 0x00 && lo in 0x20..0x7E -> {
                    validUtf16Pairs++
                    hasZeroOrPersianHighByte = true
                }
                // Persian & Arabic Unicode block U+0600..U+06FF
                hi == 0x06 && lo in 0x0C..0xFF -> {
                    validUtf16Pairs++
                    hasZeroOrPersianHighByte = true
                }
                // ZWNJ / General Punctuation U+200C..U+200F (Persian half-space)
                hi == 0x20 && lo in 0x0C..0x0F -> {
                    validUtf16Pairs++
                    hasZeroOrPersianHighByte = true
                }
                // Arabic Presentation Forms if present
                hi in 0xFB..0xFE -> {
                    validUtf16Pairs++
                }
            }
        }
        return hasZeroOrPersianHighByte && validUtf16Pairs * 10 >= pairs * 8
    }

    private fun isValidUtf8Slice(buf: ByteArray, offset: Int, len: Int): Boolean {
        var i = offset
        val end = offset + len
        while (i < end) {
            val b = buf[i].toInt() and 0xFF
            when {
                b <= 0x7F -> i++
                b in 0xC2..0xDF -> {
                    if (i + 1 >= end || (buf[i + 1].toInt() and 0xC0) != 0x80) return false
                    i += 2
                }
                b in 0xE0..0xEF -> {
                    if (i + 2 >= end) return false
                    val b1 = buf[i + 1].toInt() and 0xFF
                    val b2 = buf[i + 2].toInt() and 0xFF
                    if ((b1 and 0xC0) != 0x80 || (b2 and 0xC0) != 0x80) return false
                    i += 3
                }
                else -> return false
            }
        }
        return true
    }

    private fun cleanDecodedText(raw: String): String {
        if (raw.isEmpty()) return ""
        val sb = StringBuilder(raw.length)
        for (i in raw.indices) {
            val ch = raw[i]
            if (ch == '\u0000') continue
            if (ch < ' ' && ch != '\t') continue
            sb.append(ch)
        }
        return sb.toString().trim()
    }

    /**
     * Maps decoded `FixedVar` columns (assisted by `sys.syscolpars` column names if available, or
     * SabtAhval civil registry domain heuristics otherwise) to `ParsedCsvIdentityRow`.
     */
    private fun mapDecodedColumnsToSabtAhvalRow(
        raw: DecodedFixedVarColumns,
        columnNames: List<String>?,
        rowIndex: Int
    ): ParsedCsvIdentityRow? {
        var firstName = ""
        var lastName = ""
        var fullName = ""
        var fatherName = ""
        var nationalCode = ""
        var idNumber = ""
        var birthDate = ""
        var city = ""
        var province = ""
        var birthCity = ""
        var postalCode = ""
        var phone = ""
        var serialOrHandle = ""
        var address = ""
        val extraParts = ArrayList<String>(4)

        // 1. If exact SQL catalog/INSERT column names match varStrings count, map by column name first
        if (columnNames != null && columnNames.size == raw.varStrings.size && raw.fixedStrings.isEmpty()) {
            for (i in columnNames.indices) {
                val v = raw.varStrings[i].trim()
                if (v.isEmpty()) continue
                val key = columnNames[i].lowercase()
                    .replace("_", "")
                    .replace(" ", "")
                when {
                    key in setOf("nationalcode", "codemeli", "mellicode", "nationalid", "nid", "ssn", "کدملی") ->
                        if (nationalCode.isEmpty()) nationalCode = normalizeDigitsOnly(v)
                    key in setOf("firstname", "fname", "name", "nam", "نام") ->
                        if (firstName.isEmpty()) firstName = v
                    key in setOf("lastname", "lname", "family", "famili", "surname", "نامخانوادگی") ->
                        if (lastName.isEmpty()) lastName = v
                    key in setOf("fullname", "نامونامخانوادگی") ->
                        if (fullName.isEmpty()) fullName = v
                    key in setOf("fathername", "father", "nampedar", "pedar", "نامپدر") ->
                        if (fatherName.isEmpty()) fatherName = v
                    key in setOf("shenasnameno", "shenasname", "shsh", "idnumber", "idno", "certificateno", "شمارهشناسنامه") ->
                        if (idNumber.isEmpty()) idNumber = v
                    key in setOf("birthdate", "birthday", "dob", "tarikhtavalod", "bdate", "تاریختولد") ->
                        if (birthDate.isEmpty()) birthDate = normalizeBirthDateToken(v)
                    key in setOf("city", "cityname", "issueplace", "mahalsodoor", "sodoor", "shahr", "محلصدور", "شهر") ->
                        if (city.isEmpty()) city = v
                    key in setOf("province", "provincename", "ostan", "استان") ->
                        if (province.isEmpty()) province = v
                    key in setOf("birthcity", "mahaltavalod", "محلتولد") ->
                        if (birthCity.isEmpty()) birthCity = v
                    key in setOf("postalcode", "postcode", "zip", "codeposti", "کدپستی") ->
                        if (postalCode.isEmpty()) postalCode = v
                    key in setOf("mobile", "phone", "tel", "cell", "موبایل", "تلفن") ->
                        if (phone.isEmpty()) phone = v
                    key in setOf("serial", "seri", "shenasnameserial", "series", "سریال") ->
                        if (serialOrHandle.isEmpty()) serialOrHandle = v
                    key in setOf("address", "homeaddress", "adres", "آدرس", "نشانی") ->
                        if (address.isEmpty()) address = v
                    else -> extraParts.add("${columnNames[i]}: $v")
                }
            }
        } else {
            // 2. Schema-agnostic binary classification for SabtAhval MDF pages & LDF log records
            val allStringTokens = ArrayList<String>(raw.fixedStrings.size + raw.varStrings.size)
            allStringTokens.addAll(raw.fixedStrings)
            allStringTokens.addAll(raw.varStrings)

            val candidateNames = ArrayList<String>(6)

            for (rawToken in allStringTokens) {
                val token = rawToken.trim()
                if (token.isEmpty()) continue
                val asciiNorm = normalizePersianDigits(token)

                when {
                    // Date like 1368/04/15 or 1989-07-06 or compact 8-digit Solar Hijri 13680415
                    looksLikeDateString(asciiNorm) -> {
                        if (birthDate.isEmpty()) {
                            birthDate = normalizeBirthDateToken(asciiNorm)
                        } else {
                            extraParts.add(token)
                        }
                    }
                    // Iranian mobile / phone (09xxxxxxxxx or +989xxxxxxxxx or 989xxxxxxxxx)
                    looksLikeIranPhone(asciiNorm) -> {
                        if (phone.isEmpty()) {
                            phone = asciiNorm
                        } else {
                            extraParts.add(token)
                        }
                    }
                    // Pure 10-digit code -> NationalCode first, then PostalCode
                    isPureDigits(asciiNorm) && asciiNorm.length == 10 -> {
                        when {
                            nationalCode.isEmpty() -> nationalCode = asciiNorm
                            postalCode.isEmpty() -> postalCode = asciiNorm
                            else -> extraParts.add(token)
                        }
                    }
                    // Pure 1..9 digit number -> Shenasnameh ID Number (or NationalCode if 8-9 digits & none found yet)
                    isPureDigits(asciiNorm) && asciiNorm.length in 1..9 -> {
                        if (idNumber.isEmpty()) {
                            idNumber = asciiNorm
                        } else if (nationalCode.isEmpty() && asciiNorm.length in 8..9) {
                            nationalCode = asciiNorm.padStart(10, '0')
                        } else {
                            extraParts.add(token)
                        }
                    }
                    // SabtAhval Shenasnameh Serial (e.g. "الف/12 458921" or "A12-458921" or "SER-...")
                    looksLikeShenasnameSerial(asciiNorm) -> {
                        if (serialOrHandle.isEmpty()) {
                            serialOrHandle = token
                        } else {
                            extraParts.add(token)
                        }
                    }
                    // Long text or address keywords -> Address
                    looksLikeAddressText(token) -> {
                        if (address.isEmpty()) {
                            address = token
                        } else {
                            extraParts.add(token)
                        }
                    }
                    // Human name / city text
                    containsHumanLetters(token) -> {
                        candidateNames.add(token)
                    }
                    else -> {
                        if (token.length <= 64) extraParts.add(token)
                    }
                }
            }

            // Inspect fixed binary integers (BIGINT / INT) for NationalCode, ShenasnameNo, or BirthYear
            var birthYearCandidate = 0
            for (num in raw.fixedNumbers) {
                val numStr = num.toString()
                when {
                    birthDate.isEmpty() && num in 13000101L..14061231L && looksLikeDateString(numStr) -> {
                        birthDate = normalizeBirthDateToken(numStr)
                    }
                    nationalCode.isEmpty() && num in 10_000_000L..9_999_999_999L -> {
                        nationalCode = numStr.padStart(10, '0')
                    }
                    birthDate.isEmpty() && birthYearCandidate == 0 && num in 1280L..1406L -> {
                        birthYearCandidate = num.toInt()
                    }
                    idNumber.isEmpty() && num in 1L..99_999_999L -> {
                        // Note: first 4-byte INT may be a row PK if another ShenasnameNo exists; keep if needed
                        idNumber = numStr
                    }
                }
            }
            if (birthDate.isEmpty() && birthYearCandidate != 0) {
                birthDate = birthYearCandidate.toString()
            }

            // Assign SabtAhval civil registry name sequence:
            // [0]=FirstName, [1]=LastName, [2]=FatherName, [3]=IssuePlace/City, [4]=Province
            when (candidateNames.size) {
                0 -> Unit
                1 -> fullName = candidateNames[0]
                2 -> {
                    // If first token already has space (FullName), second is FatherName
                    if (candidateNames[0].contains(' ') && candidateNames[0].length >= 6) {
                        fullName = candidateNames[0]
                        fatherName = candidateNames[1]
                    } else {
                        firstName = candidateNames[0]
                        lastName = candidateNames[1]
                    }
                }
                else -> {
                    if (candidateNames[0].contains(' ') && candidateNames[0].length >= 7 && candidateNames.size == 3) {
                        fullName = candidateNames[0]
                        fatherName = candidateNames[1]
                        if (city.isEmpty()) city = candidateNames[2]
                    } else {
                        firstName = candidateNames[0]
                        lastName = candidateNames[1]
                        if (fatherName.isEmpty()) fatherName = candidateNames[2]
                        if (candidateNames.size >= 4 && city.isEmpty()) city = candidateNames[3]
                        if (candidateNames.size >= 5 && province.isEmpty()) province = candidateNames[4]
                    }
                }
            }
        }

        if (fullName.isEmpty()) {
            fullName = when {
                firstName.isNotEmpty() && lastName.isNotEmpty() -> "$firstName $lastName"
                firstName.isNotEmpty() -> firstName
                lastName.isNotEmpty() -> lastName
                else -> ""
            }
        }

        // Reject empty / non-identity binary metadata rows
        val hasIdentitySignal = fullName.isNotEmpty() || nationalCode.isNotEmpty() || idNumber.isNotEmpty()
        if (!hasIdentitySignal) return null
        if (fullName.isEmpty() && nationalCode.isEmpty()) return null

        return ParsedCsvIdentityRow(
            originalIndex = rowIndex,
            fullName = fullName.ifEmpty { "SabtAhval-${rowIndex + 1}" },
            nationalCode = nationalCode.ifEmpty { "-" },
            phone = phone.ifEmpty { "-" },
            accountOrHandle = serialOrHandle.ifEmpty { "-" },
            fatherName = fatherName.ifEmpty { "-" },
            idNumber = idNumber.ifEmpty { "-" },
            birthDate = birthDate.ifEmpty { "-" },
            city = city.ifEmpty { "-" },
            province = province.ifEmpty { "-" },
            birthCity = birthCity.ifEmpty { "-" },
            postalCode = postalCode.ifEmpty { "-" },
            address = address.ifEmpty { "-" },
            rawDetails = extraParts.joinToString(" | ")
        )
    }

    private fun isHighConfidenceLdfIdentityRow(row: ParsedCsvIdentityRow): Boolean {
        val hasRealName = containsHumanLetters(row.fullName) && row.fullName.length in 2..80
        val hasIdentifier = row.nationalCode != "-" || row.idNumber != "-" || row.birthDate != "-" || row.phone != "-"
        return hasRealName && hasIdentifier
    }

    private fun normalizePersianDigits(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            when (ch) {
                in '\u06F0'..'\u06F9' -> sb.append(('0'.code + (ch.code - '\u06F0'.code)).toChar())
                in '\u0660'..'\u0669' -> sb.append(('0'.code + (ch.code - '\u0660'.code)).toChar())
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun normalizeDigitsOnly(s: String): String = normalizePersianDigits(s).trim()

    private fun isPureDigits(s: String): Boolean {
        if (s.isEmpty()) return false
        for (ch in s) {
            if (ch !in '0'..'9') return false
        }
        return true
    }

    private fun looksLikeDateString(s: String): Boolean {
        if (s.length == 10 && (s[4] == '/' || s[4] == '-') && (s[7] == '/' || s[7] == '-')) {
            val y = s.substring(0, 4).toIntOrNull() ?: return false
            val m = s.substring(5, 7).toIntOrNull() ?: return false
            val d = s.substring(8, 10).toIntOrNull() ?: return false
            return (y in 1270..1410 || y in 1900..2030) && m in 1..12 && d in 1..31
        }
        if (s.length in 8..9 && s.count { it == '/' || it == '-' } == 2) {
            val parts = s.split('/', '-')
            if (parts.size == 3) {
                val y = parts[0].toIntOrNull() ?: return false
                val m = parts[1].toIntOrNull() ?: return false
                val d = parts[2].toIntOrNull() ?: return false
                return (y in 1270..1410 || y in 1900..2030) && m in 1..12 && d in 1..31
            }
        }
        if (s.length == 8 && isPureDigits(s)) {
            val y = s.substring(0, 4).toIntOrNull() ?: return false
            val m = s.substring(4, 6).toIntOrNull() ?: return false
            val d = s.substring(6, 8).toIntOrNull() ?: return false
            return (y in 1280..1406 || y in 1920..2026) && m in 1..12 && d in 1..31
        }
        return false
    }

    private fun normalizeBirthDateToken(s: String): String {
        val norm = normalizePersianDigits(s).trim()
        if (norm.length == 8 && isPureDigits(norm)) {
            return "${norm.substring(0, 4)}/${norm.substring(4, 6)}/${norm.substring(6, 8)}"
        }
        return norm.replace('-', '/')
    }

    private fun looksLikeIranPhone(s: String): Boolean {
        val clean = s.removePrefix("+")
        if (!isPureDigits(clean)) return false
        return (clean.length == 11 && clean.startsWith("09")) ||
            (clean.length == 12 && clean.startsWith("989"))
    }

    private fun looksLikeShenasnameSerial(s: String): Boolean {
        if (s.length !in 5..20) return false
        val digits = s.count { it.isDigit() }
        val hasSep = s.contains('/') || s.contains('-')
        return digits in 4..9 && hasSep && !looksLikeDateString(s)
    }

    private fun looksLikeAddressText(s: String): Boolean {
        if (s.length > 32) return true
        val lower = s.lowercase()
        return lower.contains("خیابان") ||
            lower.contains("بلوار") ||
            lower.contains("کوچه") ||
            lower.contains("پلاک") ||
            lower.contains("واحد") ||
            lower.contains("میدان") ||
            lower.contains("blvd") ||
            lower.contains("street") ||
            lower.contains("district")
    }

    private fun containsHumanLetters(s: String): Boolean {
        var letters = 0
        for (ch in s) {
            if (ch in '\u0600'..'\u06FF' || ch in 'A'..'Z' || ch in 'a'..'z') {
                letters++
            }
        }
        return letters >= 2
    }

    /**
     * Synthesizes spec-compliant binary 8,192-byte SQL Server `.mdf` Data Pages and 512-byte-aligned
     * `.ldf` Transaction Log blocks containing `LOP_INSERT_ROWS` `FixedVar` SabtAhval records.
     * Used for end-to-end binary parser benchmarking and unit verification.
     */
    object BinaryPageSynthesizer {

        data class SyntheticSabtRow(
            val nationalCode10: String,
            val shenasnameNo: Int,
            val firstName: String,
            val lastName: String,
            val fatherName: String,
            val birthDate: String,
            val city: String,
            val province: String,
            val serial: String,
            val postalCode: String,
            val mobile: String
        )

        fun encodeFixedVarRecord(row: SyntheticSabtRow, useWindows1256Varchar: Boolean = false): ByteArray {
            val varCols = listOf(
                row.firstName,
                row.lastName,
                row.fatherName,
                row.birthDate,
                row.city,
                row.province,
                row.serial,
                row.postalCode,
                row.mobile
            )
            val encodedCols = varCols.map { col ->
                if (useWindows1256Varchar) {
                    encodeWindows1256Persian(col)
                } else {
                    col.toByteArray(Charsets.UTF_16LE)
                }
            }

            // Fixed portion: 4B header + 10B CHAR(10) NationalCode + 4B INT ShenasnameNo = 18 bytes
            val fixedLen = 18
            val numCols = 2 + encodedCols.size // 11 total columns
            val nullBitmapBytes = (numCols + 7) shr 3 // 2 bytes
            val numVarCols = encodedCols.size
            val varDataStart = fixedLen + 2 + nullBitmapBytes + 2 + numVarCols * 2
            val totalVarBytes = encodedCols.sumOf { it.size }
            val totalLen = varDataStart + totalVarBytes

            val rec = ByteArray(totalLen)
            val bb = ByteBuffer.wrap(rec).order(ByteOrder.LITTLE_ENDIAN)

            rec[0] = 0x30.toByte() // statusA: Primary Record + HasNullBitmap (0x10) + HasVarCols (0x20)
            rec[1] = 0x00.toByte() // statusB
            bb.putShort(2, fixedLen.toShort())

            val natBytes = row.nationalCode10.padStart(10, '0').take(10).toByteArray(Charsets.US_ASCII)
            System.arraycopy(natBytes, 0, rec, 4, 10)
            bb.putInt(14, row.shenasnameNo)

            // Null bitmap (0 = non-null for all columns)
            bb.putShort(fixedLen, numCols.toShort())
            val varHeaderOffset = fixedLen + 2 + nullBitmapBytes
            bb.putShort(varHeaderOffset, numVarCols.toShort())

            var cursor = varDataStart
            for (v in encodedCols.indices) {
                val colBytes = encodedCols[v]
                System.arraycopy(colBytes, 0, rec, cursor, colBytes.size)
                cursor += colBytes.size
                bb.putShort(varHeaderOffset + 2 + v * 2, cursor.toShort())
            }
            return rec
        }

        fun buildMdfPages(rows: List<SyntheticSabtRow>, useWindows1256Varchar: Boolean = false): ByteArray {
            val pages = ArrayList<ByteArray>()
            var currentPage = ByteArray(MDF_PAGE_SIZE)
            var currentSlots = ArrayList<Int>(64)
            var writeCursor = PAGE_HEADER_SIZE

            fun sealCurrentPage(pageId: Int) {
                if (currentSlots.isEmpty()) return
                val bb = ByteBuffer.wrap(currentPage).order(ByteOrder.LITTLE_ENDIAN)
                currentPage[0] = 1 // m_headerVersion = 1
                currentPage[1] = 1 // m_type = DATA_PAGE (1)
                currentPage[2] = 0
                currentPage[3] = 0 // m_level = 0 (leaf)
                bb.putShort(14, 18.toShort()) // pminlen = 18
                bb.putShort(22, currentSlots.size.toShort()) // m_slotCnt
                bb.putInt(24, 245_575_913) // User table m_objId (> 99)
                val slotArrayBottom = MDF_PAGE_SIZE - currentSlots.size * 2
                bb.putShort(28, (slotArrayBottom - writeCursor).coerceAtLeast(0).toShort()) // m_freeCnt
                bb.putShort(30, writeCursor.toShort()) // m_freeData
                bb.putInt(32, pageId) // m_pageId
                bb.putShort(36, 1.toShort()) // m_fileId = 1

                for (s in currentSlots.indices) {
                    bb.putShort(MDF_PAGE_SIZE - 2 * (s + 1), currentSlots[s].toShort())
                }
                pages.add(currentPage)
                currentPage = ByteArray(MDF_PAGE_SIZE)
                currentSlots = ArrayList(64)
                writeCursor = PAGE_HEADER_SIZE
            }

            for (row in rows) {
                val recBytes = encodeFixedVarRecord(row, useWindows1256Varchar)
                val neededSlotBytes = (currentSlots.size + 1) * 2
                if (writeCursor + recBytes.size + neededSlotBytes > MDF_PAGE_SIZE) {
                    sealCurrentPage(pages.size + 1)
                }
                currentSlots.add(writeCursor)
                System.arraycopy(recBytes, 0, currentPage, writeCursor, recBytes.size)
                writeCursor += recBytes.size
            }
            sealCurrentPage(pages.size + 1)

            val out = ByteArray(pages.size * MDF_PAGE_SIZE)
            for (p in pages.indices) {
                System.arraycopy(pages[p], 0, out, p * MDF_PAGE_SIZE, MDF_PAGE_SIZE)
            }
            return out
        }

        fun buildLdfLogStream(rows: List<SyntheticSabtRow>): ByteArray {
            // 8KB LDF file header + 512-byte-aligned log blocks containing LOP_INSERT_ROWS (op = 2) records
            val rawOut = java.io.ByteArrayOutputStream(MDF_PAGE_SIZE + rows.size * 240)
            val ldfHeader = ByteArray(MDF_PAGE_SIZE)
            ldfHeader[0] = 1
            ldfHeader[1] = 15 // FILE_HEADER_PAGE
            rawOut.write(ldfHeader)

            for ((idx, row) in rows.withIndex()) {
                val fixedVarPayload = encodeFixedVarRecord(row, useWindows1256Varchar = false)
                // 28-byte LOP_INSERT_ROWS log record prefix before FixedVar payload
                val logPrefix = ByteArray(28)
                val bb = ByteBuffer.wrap(logPrefix).order(ByteOrder.LITTLE_ENDIAN)
                bb.putShort(0, (28 + fixedVarPayload.size).toShort())
                logPrefix[4] = 2 // LOP_INSERT_ROWS
                logPrefix[5] = 4 // LCX_HEAP
                bb.putInt(8, idx + 1)
                rawOut.write(logPrefix)
                rawOut.write(fixedVarPayload)
            }

            val rem = rawOut.size() % 512
            if (rem != 0) {
                rawOut.write(ByteArray(512 - rem))
            }
            return rawOut.toByteArray()
        }
    }

    private fun readUInt16LE(buf: ByteArray, offset: Int): Int =
        (buf[offset].toInt() and 0xFF) or ((buf[offset + 1].toInt() and 0xFF) shl 8)

    private fun readInt32LE(buf: ByteArray, offset: Int): Int =
        (buf[offset].toInt() and 0xFF) or
            ((buf[offset + 1].toInt() and 0xFF) shl 8) or
            ((buf[offset + 2].toInt() and 0xFF) shl 16) or
            ((buf[offset + 3].toInt() and 0xFF) shl 24)

    private fun readInt64LE(buf: ByteArray, offset: Int): Long =
        (readInt32LE(buf, offset).toLong() and 0xFFFF_FFFFL) or
            (readInt32LE(buf, offset + 4).toLong() shl 32)
}
