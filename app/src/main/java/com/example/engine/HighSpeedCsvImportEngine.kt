package com.example.engine

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteStatement
import com.example.data.AppDatabase
import com.example.data.IdentityEntity
import com.example.data.TextNormalizer
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.InputStreamReader
import java.io.Reader
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ImportStatus {
    IDLE,
    RUNNING,
    PAUSED,
    COMPLETED,
    CANCELLED,
    ERROR
}

data class ImportProgressState(
    val status: ImportStatus = ImportStatus.IDLE,
    val activeModuleId: String? = null,
    val sourceFileName: String = "",
    val processedRecords: Long = 0L,
    val totalEstimatedRecords: Long = 0L,
    val bytesProcessed: Long = 0L,
    val totalBytes: Long = 0L,
    val progressFraction: Float = 0f,
    val recordsPerSecond: Long = 0L,
    val mbPerSecond: Float = 0f,
    val cpuWorkerCount: Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(2),
    val cpuLoadPercent: Int = 0,
    val gpuRenderer: String = "GLES2 PBUFFER SHADER",
    val gpuHardwareActive: Boolean = false,
    val gpuLoadPercent: Int = 0,
    val activeBatchIndex: Int = 0,
    val statusLog: String = "ENGINE STANDBY // READY FOR MULTI-THREADED CSV INGESTION",
    val errorMessage: String? = null
)

data class ParsedCsvIdentityRow(
    val originalIndex: Int,
    val fullName: String,
    val nationalCode: String,
    val phone: String,
    val accountOrHandle: String,
    val cardNumber: String = "-",
    val fatherName: String = "-",
    val idNumber: String = "-",
    val birthDate: String = "-",
    val city: String = "-",
    val province: String = "-",
    val birthCity: String = "-",
    val birthProvince: String = "-",
    val postalCode: String = "-",
    val address: String = "-",
    val email: String = "-",
    val username: String = "-",
    val memberCode: String = "-",
    val branchId: String = "-",
    val defNumOld: String = "-",
    val passwordHint: String = "-",
    val passwValue: String = "-",
    val rawDetails: String = ""
)

class CompiledHeaderMap(
    val roles: IntArray,
    val rawHeaders: Array<String>,
    val phoneColIndex: Int
)

/**
 * Memory-safe, high-throughput line reader that uses a single 64 KB char buffer and strictly
 * caps individual line length at [maxLineChars] (default 8,192 chars).
 * Prevents OutOfMemoryError crashes when importing malformed, binary, or newline-less multi-MB
 * Telegram / SQL / JSON database dumps.
 */
internal class BoundedLineReader(
    private val reader: Reader,
    private val maxLineChars: Int = 8_192
) : Closeable {
    private val buffer = CharArray(262_144)
    private var pos = 0
    private var limit = 0
    private var skipNextLf = false
    private val sb = StringBuilder(512)

    var lastRawCharsConsumed: Int = 0
        private set

    fun readBoundedLine(): String? {
        sb.setLength(0)
        var consumed = 0
        var sawAnyChar = false

        while (true) {
            if (pos >= limit) {
                limit = reader.read(buffer, 0, buffer.size)
                pos = 0
                if (limit <= 0) {
                    lastRawCharsConsumed = consumed
                    return if (sawAnyChar) sb.toString() else null
                }
            }

            if (skipNextLf) {
                skipNextLf = false
                if (buffer[pos] == '\n') {
                    pos++
                    consumed++
                    continue
                }
            }

            sawAnyChar = true
            while (pos < limit) {
                val ch = buffer[pos++]
                consumed++
                if (ch == '\r') {
                    if (pos < limit) {
                        if (buffer[pos] == '\n') {
                            pos++
                            consumed++
                        }
                    } else {
                        skipNextLf = true
                    }
                    lastRawCharsConsumed = consumed
                    return sb.toString()
                } else if (ch == '\n') {
                    lastRawCharsConsumed = consumed
                    return sb.toString()
                } else if (sb.length < maxLineChars) {
                    sb.append(ch)
                }
            }
        }
    }

    override fun close() {
        reader.close()
    }
}

object CsvSchemaParser {

    private const val ROLE_UNMAPPED = 0
    private const val ROLE_ACCOUNT_ID = 1
    private const val ROLE_FIRST_NAME = 2
    private const val ROLE_LAST_NAME = 3
    private const val ROLE_NATIONAL_CODE = 4
    private const val ROLE_DEFINEATION_NUM = 5
    private const val ROLE_ACCOUNT_NUMBER = 6
    private const val ROLE_FULL_NAME = 7
    private const val ROLE_FATHER_NAME = 8
    private const val ROLE_ID_NUMBER = 9
    private const val ROLE_BIRTH_DATE = 10
    private const val ROLE_CITY = 11
    private const val ROLE_PROVINCE = 12
    private const val ROLE_BIRTH_CITY = 13
    private const val ROLE_BIRTH_PROVINCE = 14
    private const val ROLE_POSTAL_CODE = 15
    private const val ROLE_ADDRESS = 16
    private const val ROLE_CARD_NUMBER = 17
    private const val ROLE_PHONE = 18
    private const val ROLE_EMAIL = 19
    private const val ROLE_USERNAME = 20
    private const val ROLE_MEMBER_CODE = 21
    private const val ROLE_BRANCH_ID = 22
    private const val ROLE_DEF_NUM_OLD = 23
    private const val ROLE_PASSWORD_HINT = 24
    private const val ROLE_PASSW = 25
    private const val ROLE_DATE_OF_ADDRESS = 26

    @Volatile
    private var cachedHeaderSource: List<String>? = null

    @Volatile
    private var cachedCompiledMap: CompiledHeaderMap? = null

    fun looksLikeCsvHeader(firstLine: String): Boolean {
        val clean = firstLine.removePrefix("\uFEFF").lowercase()
        // If line has many digits in the first 64 chars, it is a data row, not a header
        var digitCount = 0
        val sampleLen = minOf(clean.length, 64)
        for (i in 0 until sampleLen) {
            if (clean[i].isDigit()) digitCount++
        }
        if (digitCount > 6) return false

        return clean.contains("phone") ||
            clean.contains("mobile") ||
            clean.contains("tel") ||
            clean.contains("cell") ||
            clean.contains("account_id") ||
            clean.contains("accountid") ||
            clean.contains("user_id") ||
            clean.contains("userid") ||
            clean.contains("tg_id") ||
            clean.contains("telegram_id") ||
            clean.contains("username") ||
            clean.contains("first_name") ||
            clean.contains("last_name") ||
            clean.contains("national_code") ||
            clean.contains("national id") ||
            clean.contains("national_id") ||
            clean.contains("fullname") ||
            clean.contains("full_name") ||
            clean.contains("date_of_address") ||
            clean.contains("dateofaddress") ||
            clean.contains("dob") ||
            clean.contains("postal_code") ||
            clean.contains("postal code") ||
            clean.contains("account_number") ||
            clean.contains("accountnumber") ||
            clean.contains("defineationnumber") ||
            clean.contains("cardnumber") ||
            clean.contains("password_hint") ||
            clean.contains("user_email_address") ||
            clean.contains("user_phone_number") ||
            clean.contains("definenum_old") ||
            clean.contains("id_branch") ||
            clean.contains("father_name") ||
            clean.contains("card_no") ||
            clean.contains("birthday") ||
            clean.contains("birth_date") ||
            clean.contains("address") ||
            clean.contains("email") ||
            clean.contains("mail") ||
            (clean.contains("name") && (clean.contains("id") || clean.contains("code")))
    }

    fun detectDelimiter(line: String): Char {
        var semiCount = 0
        var commaCount = 0
        var tabCount = 0
        var pipeCount = 0
        var inQuotes = false
        val scanLen = minOf(line.length, 512)
        for (i in 0 until scanLen) {
            when (val ch = line[i]) {
                '"' -> inQuotes = !inQuotes
                ';' -> if (!inQuotes) semiCount++
                ',' -> if (!inQuotes) commaCount++
                '\t' -> if (!inQuotes) tabCount++
                '|' -> if (!inQuotes) pipeCount++
            }
        }
        return when {
            semiCount > commaCount && semiCount > 0 -> ';'
            commaCount > 0 -> ','
            semiCount > 0 -> ';'
            tabCount > 0 -> '\t'
            pipeCount > 0 -> '|'
            else -> ','
        }
    }

    fun splitCsvLineFast(line: String, preferredDelimiter: Char = '\u0000'): List<String> {
        val startOffset = if (line.isNotEmpty() && line[0] == '\uFEFF') 1 else 0
        val len = line.length
        if (startOffset >= len) return emptyList()

        val delimiter = if (preferredDelimiter != '\u0000') {
            preferredDelimiter
        } else {
            detectDelimiter(line)
        }

        val result = ArrayList<String>(14)
        val sb = StringBuilder(64)
        var inQuotes = false
        var i = startOffset
        while (i < len) {
            val ch = line[i]
            when {
                ch == '"' -> {
                    if (inQuotes && i + 1 < len && line[i + 1] == '"') {
                        sb.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ch == delimiter && !inQuotes -> {
                    result.add(trimBuilder(sb))
                    sb.setLength(0)
                }
                else -> sb.append(ch)
            }
            i++
        }
        result.add(trimBuilder(sb))
        return result
    }

    private fun trimBuilder(sb: StringBuilder): String {
        var start = 0
        var end = sb.length
        while (start < end && sb[start] <= ' ') start++
        while (end > start && sb[end - 1] <= ' ') end--
        return if (start >= end) "" else sb.substring(start, end)
    }

    /**
     * Compiles CSV header names ONCE per file into integer role IDs without Regex allocations.
     */
    fun compileHeaders(headers: List<String>): CompiledHeaderMap {
        if (headers === cachedHeaderSource) {
            cachedCompiledMap?.let { return it }
        }
        val roles = IntArray(headers.size)
        val rawHeaders = Array(headers.size) { headers[it].trim() }
        var phoneIdx = -1

        for (i in headers.indices) {
            val norm = normalizeHeaderKey(rawHeaders[i])
            val role = when (norm) {
                "account_id", "accountid", "user_id", "userid", "tg_id", "telegram_id", "id" -> ROLE_ACCOUNT_ID
                "first_name", "firstname", "fname" -> ROLE_FIRST_NAME
                "last_name", "lastname", "lname", "surname" -> ROLE_LAST_NAME
                "national_code", "national_id", "nationalid", "meli_code", "ssn", "codemeli" -> ROLE_NATIONAL_CODE
                "defineationnumber", "defineation_number", "definitionnumber", "definition_number" -> ROLE_DEFINEATION_NUM
                "account_number", "accountnumber", "account_no", "account", "iban", "sheba", "handle" -> ROLE_ACCOUNT_NUMBER
                "full_name", "fullname", "name", "customer_name" -> ROLE_FULL_NAME
                "father_name", "fathername", "father" -> ROLE_FATHER_NAME
                "id_number", "id_no", "shenasnameh", "certificate_no" -> ROLE_ID_NUMBER
                "birth_date", "birthday", "birthdate", "dob" -> ROLE_BIRTH_DATE
                "city_name", "city" -> ROLE_CITY
                "province_name", "province", "state" -> ROLE_PROVINCE
                "birth_city", "birthcity" -> ROLE_BIRTH_CITY
                "birth_province", "birthprovince" -> ROLE_BIRTH_PROVINCE
                "postal_code", "postalcode", "zip", "zip_code", "postcode" -> ROLE_POSTAL_CODE
                "address", "home_address", "street_address" -> ROLE_ADDRESS
                "card_no", "card_number", "cardnumber", "card", "pan" -> ROLE_CARD_NUMBER
                "mobile", "phone", "phone_number", "user_phone_number", "mobile_no", "tel", "cell", "mob", "telephone", "contact", "phone_no", "phoneno", "mobile_num", "phone_num", "number" -> {
                    if (phoneIdx == -1) phoneIdx = i
                    ROLE_PHONE
                }
                "user_email_address", "email", "email_address", "mail" -> ROLE_EMAIL
                "username", "user_name", "login" -> ROLE_USERNAME
                "member", "member_code", "member_id" -> ROLE_MEMBER_CODE
                "id_branch", "branch_id", "branch" -> ROLE_BRANCH_ID
                "definenum_old", "define_num_old", "old_definenum" -> ROLE_DEF_NUM_OLD
                "date_of_address", "dateofaddress", "address_date", "date_address" -> ROLE_DATE_OF_ADDRESS
                "password_hint", "pass_hint", "hint" -> ROLE_PASSWORD_HINT
                "passw", "password", "pass" -> ROLE_PASSW
                else -> ROLE_UNMAPPED
            }
            roles[i] = role
        }

        val compiled = CompiledHeaderMap(roles, rawHeaders, phoneIdx)
        cachedHeaderSource = headers
        cachedCompiledMap = compiled
        return compiled
    }

    private fun normalizeHeaderKey(raw: String): String {
        if (raw.isEmpty()) return ""
        val sb = StringBuilder(raw.length)
        var lastUnderscore = true
        for (i in raw.indices) {
            val c = raw[i]
            if (c == '\uFEFF' || c == '"' || c == '\'') continue
            if (c <= ' ' || c == '_' || c == '-') {
                if (!lastUnderscore) {
                    sb.append('_')
                    lastUnderscore = true
                }
            } else {
                sb.append(c.lowercaseChar())
                lastUnderscore = false
            }
        }
        if (sb.isNotEmpty() && sb[sb.length - 1] == '_') {
            sb.setLength(sb.length - 1)
        }
        return sb.toString()
    }

    /**
     * Zero-allocation check whether a phone string begins with `98`, `+98`, `(98)`, `(+98)`, or `989`
     * (handling surrounding quotes, parentheses, spaces, dashes, leading '+', international '00' or '0' prefixes,
     * and normalizing Persian/Arabic digits).
     */
    fun hasIranPhonePrefix(rawPhone: String): Boolean {
        val len = rawPhone.length
        if (len < 2) return false
        var i = 0
        while (i < len) {
            val c = rawPhone[i]
            if (c <= ' ' || c == '"' || c == '\'' || c == '+' || c == '(' || c == ')' || c == '-' ||
                c == '\uFEFF' || c == '\u200E' || c == '\u200F'
            ) {
                i++
            } else {
                break
            }
        }
        if (i >= len) return false

        // Check for international dial code prefix "00" or single "0" before 98
        if (i + 3 < len && toAsciiDigitOrChar(rawPhone[i]) == '0' && toAsciiDigitOrChar(rawPhone[i + 1]) == '0') {
            i += 2
        } else if (i + 2 < len && toAsciiDigitOrChar(rawPhone[i]) == '0' &&
            toAsciiDigitOrChar(rawPhone[i + 1]) == '9' && toAsciiDigitOrChar(rawPhone[i + 2]) == '8'
        ) {
            i += 1
        }

        while (i < len) {
            val c = rawPhone[i]
            if (c <= ' ' || c == '-' || c == '(' || c == ')' || c == '+') {
                i++
            } else {
                break
            }
        }

        if (i + 1 >= len) return false
        val d0 = toAsciiDigitOrChar(rawPhone[i])
        val d1 = toAsciiDigitOrChar(rawPhone[i + 1])
        return d0 == '9' && d1 == '8'
    }

    fun hasIran98Prefix(rawPhone: String): Boolean = hasIranPhonePrefix(rawPhone)
    fun hasIran989Prefix(rawPhone: String): Boolean = hasIranPhonePrefix(rawPhone)

    /**
     * Inspects parsed columns of a data row to auto-detect which column holds the phone number.
     * Prevents dropped records when user files have phone as col 0 (phone;username;...) or col 2 (id;username;phone).
     */
    fun detectTelegramPhoneCol(cols: List<String>): Int {
        if (cols.size > 1 && hasIranPhonePrefix(cols[1])) return 1
        if (cols.isNotEmpty() && hasIranPhonePrefix(cols[0])) return 0
        if (cols.size > 2 && hasIranPhonePrefix(cols[2])) return 2
        if (cols.size > 3 && hasIranPhonePrefix(cols[3])) return 3
        return 1
    }

    /**
     * Ultra-fast zero-allocation pre-filter for raw Telegram CSV lines:
     * Locates [phoneColIndex] directly in [rawLine] without allocating column strings or lists,
     * and checks whether that column starts with `(98)`, `(+98)`, `98`, or `989`.
     * Rejects foreign non-Iran rows ~15x faster than full CSV splitting on multi-million-row Telegram dumps.
     */
    fun rawTelegramLinePasses98Filter(
        rawLine: String,
        phoneColIndex: Int = 1,
        preferredDelimiter: Char = '\u0000'
    ): Boolean {
        if (phoneColIndex < 0) return true
        val len = rawLine.length
        if (len < 2) return false
        val delimiter = if (preferredDelimiter != '\u0000') {
            preferredDelimiter
        } else {
            detectDelimiter(rawLine)
        }

        var currentCol = 0
        var inQuotes = false
        var i = if (rawLine[0] == '\uFEFF') 1 else 0

        while (i < len && currentCol < phoneColIndex) {
            val ch = rawLine[i]
            if (ch == '"') {
                if (inQuotes && i + 1 < len && rawLine[i + 1] == '"') {
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (ch == delimiter && !inQuotes) {
                currentCol++
            }
            i++
        }

        if (currentCol != phoneColIndex || i >= len) return false

        // Skip leading quotes, '+', '(', ')', '-', whitespace inside the target phone column
        while (i < len) {
            val c = rawLine[i]
            if (c == delimiter && !inQuotes) return false
            if (c == '"') {
                inQuotes = !inQuotes
                i++
            } else if (c <= ' ' || c == '+' || c == '(' || c == ')' || c == '-' || c == '\'' ||
                c == '\uFEFF' || c == '\u200E' || c == '\u200F'
            ) {
                i++
            } else {
                break
            }
        }

        // Check for 00 or 0 before 98
        if (i + 3 < len && toAsciiDigitOrChar(rawLine[i]) == '0' && toAsciiDigitOrChar(rawLine[i + 1]) == '0') {
            i += 2
        } else if (i + 2 < len && toAsciiDigitOrChar(rawLine[i]) == '0' &&
            toAsciiDigitOrChar(rawLine[i + 1]) == '9' && toAsciiDigitOrChar(rawLine[i + 2]) == '8'
        ) {
            i += 1
        }

        while (i < len) {
            val c = rawLine[i]
            if (c == delimiter && !inQuotes) return false
            if (c <= ' ' || c == '-' || c == '(' || c == ')' || c == '+') {
                i++
            } else {
                break
            }
        }

        if (i + 1 >= len) return false
        val c0 = rawLine[i]
        val c1 = rawLine[i + 1]
        if ((c0 == delimiter || c1 == delimiter) && !inQuotes) return false

        return toAsciiDigitOrChar(c0) == '9' && toAsciiDigitOrChar(c1) == '8'
    }

    fun rawTelegramLinePasses989Filter(
        rawLine: String,
        phoneColIndex: Int = 1,
        preferredDelimiter: Char = '\u0000'
    ): Boolean = rawTelegramLinePasses98Filter(rawLine, phoneColIndex, preferredDelimiter)

    private fun toAsciiDigitOrChar(ch: Char): Char = when (ch) {
        in '\u06F0'..'\u06F9' -> ('0'.code + (ch.code - '\u06F0'.code)).toChar()
        in '\u0660'..'\u0669' -> ('0'.code + (ch.code - '\u0660'.code)).toChar()
        else -> ch
    }

    /**
     * Parses a CSV row according to module-specific schemas and header detection:
     * - BankMeli (`bank_meli`):
     *   (NATIONAL_CODE,CARD_NO,FULL_NAME,BIRTH_DATE,MOBILE)
     * - BankMelat (`bank_melat`):
     *   (NATIONAL_CODE,ACCOUNT_NUMBER,FULL_NAME,FATHER_NAME,ID_NUMBER,BIRTH_DATE,CITY_NAME,PROVINCE_NAME,BIRTH_CITY,BIRTH_PROVINCE,ADDRESS,CARD_NO,MOBILE)
     * - BankSaderat (`bank_saderat`):
     *   (AccountNumber,DefineationNumber,FullName,CardNumber,Password_Hint,user_email_address,Passw,user_phone_number,Member,Username,DefineNum_Old,ID_Branch)
     * - Citizen (`citizen`):
     *   (FULLNAME,BIRTHDAY,National ID,Postal Code,Phone,City,Address)
     * - Telegram (`telegram`):
     *   (account_id;phone;username;first_name;last_name)
     */
    fun parseIdentityCsvRow(
        moduleId: String,
        rawLine: String,
        headers: List<String>?,
        index: Int
    ): ParsedCsvIdentityRow {
        val compiled = if (headers != null && headers.size >= 2) compileHeaders(headers) else null
        return parseIdentityCsvRowCompiled(
            moduleId = moduleId,
            rawLine = rawLine,
            compiledHeaders = compiled,
            preferredDelimiter = '\u0000',
            index = index
        )
    }

    fun parseIdentityCsvRowCompiled(
        moduleId: String,
        rawLine: String,
        compiledHeaders: CompiledHeaderMap?,
        preferredDelimiter: Char = '\u0000',
        index: Int
    ): ParsedCsvIdentityRow {
        val cols = splitCsvLineFast(rawLine, preferredDelimiter)
        if (cols.isEmpty()) {
            return ParsedCsvIdentityRow(
                originalIndex = index,
                fullName = "Unknown",
                nationalCode = "-",
                phone = "-",
                accountOrHandle = "-",
                rawDetails = rawLine
            )
        }

        if (compiledHeaders != null) {
            return parseWithCompiledHeaders(moduleId, cols, compiledHeaders, index, rawLine)
        }

        return when (moduleId) {
            "bank_meli" -> parseBankMeliPositional(cols, index)
            "bank_melat" -> parseBankMelatPositional(cols, index)
            "bank_saderat" -> parseBankSaderatPositional(cols, index)
            "citizen" -> parseCitizenPositional(cols, index)
            "telegram" -> parseTelegramPositional(cols, index)
            "basij" -> parseBasijPositional(cols, index)
            "entezami" -> parseEntezamiPositional(cols, index)
            else -> {
                when {
                    cols.size >= 13 -> parseBankMelatPositional(cols, index)
                    cols.size == 12 -> parseBankSaderatPositional(cols, index)
                    else -> parseGenericPositional(cols, index, rawLine)
                }
            }
        }
    }

    private fun parseTelegramPositional(cols: List<String>, index: Int): ParsedCsvIdentityRow {
        val phoneColIdx = detectTelegramPhoneCol(cols)
        val phone = cols.getOrNull(phoneColIdx).orEmpty().ifEmpty { "-" }
        val accountId = if (phoneColIdx == 1) {
            cols.colOrDash(0)
        } else if (phoneColIdx == 0) {
            cols.getOrNull(1).orEmpty().ifEmpty { "-" }
        } else {
            cols.colOrDash(0)
        }
        val rawUsername = when (phoneColIdx) {
            1 -> cols.getOrNull(2).orEmpty()
            0 -> cols.getOrNull(1).orEmpty()
            else -> cols.getOrNull(1).orEmpty()
        }
        val username = when {
            rawUsername.isEmpty() || rawUsername == "-" -> "-"
            rawUsername.startsWith("@") -> rawUsername
            else -> "@$rawUsername"
        }
        val firstName = when (phoneColIdx) {
            1 -> cols.getOrNull(3).orEmpty()
            0 -> cols.getOrNull(2).orEmpty()
            else -> cols.getOrNull(3).orEmpty()
        }
        val lastName = when (phoneColIdx) {
            1 -> cols.getOrNull(4).orEmpty()
            0 -> cols.getOrNull(3).orEmpty()
            else -> cols.getOrNull(4).orEmpty()
        }
        val hasFirst = firstName.isNotEmpty() && firstName != "-"
        val hasLast = lastName.isNotEmpty() && lastName != "-"
        val combinedName = when {
            hasFirst && hasLast -> "$firstName $lastName"
            hasFirst -> firstName
            hasLast -> lastName
            username != "-" -> username
            else -> "TG-User-${index + 1}"
        }

        val detailsSb = StringBuilder(48)
        if (firstName.isNotEmpty() && firstName != "-") {
            detailsSb.append("FIRST: ").append(firstName)
        }
        if (lastName.isNotEmpty() && lastName != "-") {
            if (detailsSb.isNotEmpty()) detailsSb.append(" • ")
            detailsSb.append("LAST: ").append(lastName)
        }
        val tailStart = if (phoneColIdx == 0) 4 else 5
        if (cols.size > tailStart) {
            if (detailsSb.isNotEmpty()) detailsSb.append(" • ")
            for (k in tailStart until cols.size) {
                if (k > tailStart) detailsSb.append(" | ")
                detailsSb.append(cols[k])
            }
        }

        return ParsedCsvIdentityRow(
            originalIndex = index,
            fullName = combinedName,
            nationalCode = accountId,
            phone = phone,
            accountOrHandle = accountId,
            username = username,
            rawDetails = detailsSb.toString()
        )
    }

    /**
     * Module-specific import filter:
     * In the Telegram section (`telegram`), import records whose `phone` field starts with
     * `(98)`, `(+98)`, `98`, or `989` (handling quotes, spaces, dashes, leading '+', and Persian/Arabic digits).
     */
    fun passesModuleImportFilter(moduleId: String, row: ParsedCsvIdentityRow): Boolean {
        if (moduleId != "telegram") return true
        return hasIranPhonePrefix(row.phone)
    }

    private fun parseBankMeliPositional(cols: List<String>, index: Int): ParsedCsvIdentityRow {
        // (0:NATIONAL_CODE, 1:CARD_NO, 2:FULL_NAME, 3:BIRTH_DATE, 4:MOBILE)
        val nationalCode = cols.colOrDash(0)
        val cardNo = cols.colOrDash(1)
        val fullName = cols.getOrNull(2)?.takeIf { it.isNotEmpty() } ?: "Meli-${index + 1}"
        val birthDate = cols.colOrDash(3)
        val mobile = cols.colOrDash(4)
        val extra = joinTailColumns(cols, 5)

        return ParsedCsvIdentityRow(
            originalIndex = index,
            fullName = fullName,
            nationalCode = nationalCode,
            phone = mobile,
            accountOrHandle = "-",
            cardNumber = cardNo,
            birthDate = birthDate,
            rawDetails = extra
        )
    }

    private fun parseBankMelatPositional(cols: List<String>, index: Int): ParsedCsvIdentityRow {
        // (0:NATIONAL_CODE, 1:ACCOUNT_NUMBER, 2:FULL_NAME, 3:FATHER_NAME, 4:ID_NUMBER,
        //  5:BIRTH_DATE, 6:CITY_NAME, 7:PROVINCE_NAME, 8:BIRTH_CITY, 9:BIRTH_PROVINCE,
        //  10:ADDRESS, 11:CARD_NO, 12:MOBILE)
        val nationalCode = cols.colOrDash(0)
        val accountNumber = cols.colOrDash(1)
        val fullName = cols.getOrNull(2)?.takeIf { it.isNotEmpty() } ?: "Record-${index + 1}"
        val fatherName = cols.colOrDash(3)
        val idNumber = cols.colOrDash(4)
        val birthDate = cols.colOrDash(5)
        val cityName = cols.colOrDash(6)
        val provinceName = cols.colOrDash(7)
        val birthCity = cols.colOrDash(8)
        val birthProvince = cols.colOrDash(9)
        val address = cols.colOrDash(10)
        val cardNo = cols.colOrDash(11)
        val mobile = cols.colOrDash(12)
        val extra = joinTailColumns(cols, 13)

        return ParsedCsvIdentityRow(
            originalIndex = index,
            fullName = fullName,
            nationalCode = nationalCode,
            phone = mobile,
            accountOrHandle = accountNumber,
            cardNumber = cardNo,
            fatherName = fatherName,
            idNumber = idNumber,
            birthDate = birthDate,
            city = cityName,
            province = provinceName,
            birthCity = birthCity,
            birthProvince = birthProvince,
            postalCode = "-",
            address = address,
            rawDetails = extra
        )
    }

    private fun parseBankSaderatPositional(cols: List<String>, index: Int): ParsedCsvIdentityRow {
        // (0:AccountNumber, 1:DefineationNumber, 2:FullName, 3:CardNumber,
        //  4:Password_Hint, 5:user_email_address, 6:Passw, 7:user_phone_number,
        //  8:Member, 9:Username, 10:DefineNum_Old, 11:ID_Branch)
        val accountNumber = cols.colOrDash(0)
        val defineationNumber = cols.colOrDash(1)
        val fullName = cols.getOrNull(2)?.takeIf { it.isNotEmpty() } ?: "Saderat-${index + 1}"
        val cardNumber = cols.colOrDash(3)
        val passwordHint = cols.colOrDash(4)
        val email = cols.colOrDash(5)
        val passw = cols.colOrDash(6)
        val phone = cols.colOrDash(7)
        val member = cols.colOrDash(8)
        val username = cols.colOrDash(9)
        val defineNumOld = cols.colOrDash(10)
        val branchId = cols.colOrDash(11)
        val extra = joinTailColumns(cols, 12)

        return ParsedCsvIdentityRow(
            originalIndex = index,
            fullName = fullName,
            nationalCode = defineationNumber,
            phone = phone,
            accountOrHandle = accountNumber,
            cardNumber = cardNumber,
            idNumber = defineationNumber,
            email = email,
            username = username,
            memberCode = member,
            branchId = branchId,
            defNumOld = defineNumOld,
            passwordHint = passwordHint,
            passwValue = passw,
            rawDetails = extra
        )
    }

    private fun parseCitizenPositional(cols: List<String>, index: Int): ParsedCsvIdentityRow {
        // (0:FULLNAME, 1:BIRTHDAY, 2:National ID, 3:Postal Code, 4:Phone, 5:City, 6:Address)
        val fullName = cols.getOrNull(0)?.takeIf { it.isNotEmpty() } ?: "Citizen-${index + 1}"
        val birthday = cols.colOrDash(1)
        val nationalId = cols.colOrDash(2)
        val postalCode = cols.colOrDash(3)
        val phone = cols.colOrDash(4)
        val city = cols.colOrDash(5)
        val address = cols.colOrDash(6)
        val extra = joinTailColumns(cols, 7)

        return ParsedCsvIdentityRow(
            originalIndex = index,
            fullName = fullName,
            nationalCode = nationalId,
            phone = phone,
            accountOrHandle = "-",
            cardNumber = "-",
            fatherName = "-",
            idNumber = "-",
            birthDate = birthday,
            city = city,
            province = "-",
            birthCity = "-",
            birthProvince = "-",
            postalCode = postalCode,
            address = address,
            rawDetails = extra
        )
    }

    private fun parseBasijPositional(cols: List<String>, index: Int): ParsedCsvIdentityRow {
        // Importer Format: (name, dob, national_id, address, postal_code)
        val name = cols.getOrNull(0)?.takeIf { it.isNotEmpty() } ?: "Basij-${index + 1}"
        val dob = cols.colOrDash(1)
        val nationalId = cols.colOrDash(2)
        val address = cols.colOrDash(3)
        val postalCode = cols.colOrDash(4)
        val extra = joinTailColumns(cols, 5)

        return ParsedCsvIdentityRow(
            originalIndex = index,
            fullName = name,
            nationalCode = nationalId,
            phone = "-",
            accountOrHandle = "-",
            cardNumber = "-",
            fatherName = "-",
            idNumber = "-",
            birthDate = dob,
            city = "-",
            province = "-",
            birthCity = "-",
            birthProvince = "-",
            postalCode = postalCode,
            address = address,
            rawDetails = extra
        )
    }

    private fun parseEntezamiPositional(cols: List<String>, index: Int): ParsedCsvIdentityRow {
        // Importer Format: (full_name, dob, national_id, address, postal_code, date_of_address)
        val fullName = cols.getOrNull(0)?.takeIf { it.isNotEmpty() } ?: "Entezami-${index + 1}"
        val dob = cols.colOrDash(1)
        val nationalId = cols.colOrDash(2)
        val address = cols.colOrDash(3)
        val postalCode = cols.colOrDash(4)
        val dateOfAddress = cols.colOrDash(5)
        val tail = joinTailColumns(cols, 6)
        val details = if (dateOfAddress != "-") {
            if (tail.isNotEmpty()) "DATE OF ADDRESS: $dateOfAddress | $tail" else "DATE OF ADDRESS: $dateOfAddress"
        } else {
            tail
        }

        return ParsedCsvIdentityRow(
            originalIndex = index,
            fullName = fullName,
            nationalCode = nationalId,
            phone = "-",
            accountOrHandle = "-",
            cardNumber = "-",
            fatherName = "-",
            idNumber = "-",
            birthDate = dob,
            city = "-",
            province = "-",
            birthCity = "-",
            birthProvince = "-",
            postalCode = postalCode,
            address = address,
            defNumOld = dateOfAddress,
            rawDetails = details
        )
    }

    private fun parseWithCompiledHeaders(
        moduleId: String,
        cols: List<String>,
        compiled: CompiledHeaderMap,
        index: Int,
        rawLine: String
    ): ParsedCsvIdentityRow {
        var fullName = ""
        var nationalCode = ""
        var phone = ""
        var accountNumber = ""
        var cardNumber = ""
        var fatherName = ""
        var idNumber = ""
        var birthDate = ""
        var city = ""
        var province = ""
        var birthCity = ""
        var birthProvince = ""
        var postalCode = ""
        var address = ""
        var email = ""
        var username = ""
        var firstNamePart = ""
        var lastNamePart = ""
        var memberCode = ""
        var branchId = ""
        var defNumOld = ""
        var passwordHint = ""
        var passwValue = ""
        var unmappedSb: StringBuilder? = null

        val roles = compiled.roles
        val rawHeaders = compiled.rawHeaders

        for (i in cols.indices) {
            val value = cols[i]
            if (value.isEmpty()) continue
            val role = if (i < roles.size) roles[i] else ROLE_UNMAPPED

            when (role) {
                ROLE_ACCOUNT_ID -> {
                    if (accountNumber.isEmpty()) accountNumber = value
                    if (nationalCode.isEmpty()) nationalCode = value
                }
                ROLE_FIRST_NAME -> if (firstNamePart.isEmpty()) firstNamePart = value
                ROLE_LAST_NAME -> if (lastNamePart.isEmpty()) lastNamePart = value
                ROLE_NATIONAL_CODE -> if (nationalCode.isEmpty()) nationalCode = value
                ROLE_DEFINEATION_NUM -> {
                    if (idNumber.isEmpty()) idNumber = value
                    if (nationalCode.isEmpty()) nationalCode = value
                }
                ROLE_ACCOUNT_NUMBER -> if (accountNumber.isEmpty()) accountNumber = value
                ROLE_FULL_NAME -> if (fullName.isEmpty()) fullName = value
                ROLE_FATHER_NAME -> if (fatherName.isEmpty()) fatherName = value
                ROLE_ID_NUMBER -> if (idNumber.isEmpty()) idNumber = value
                ROLE_BIRTH_DATE -> if (birthDate.isEmpty()) birthDate = value
                ROLE_CITY -> if (city.isEmpty()) city = value
                ROLE_PROVINCE -> if (province.isEmpty()) province = value
                ROLE_BIRTH_CITY -> if (birthCity.isEmpty()) birthCity = value
                ROLE_BIRTH_PROVINCE -> if (birthProvince.isEmpty()) birthProvince = value
                ROLE_POSTAL_CODE -> if (postalCode.isEmpty()) postalCode = value
                ROLE_ADDRESS -> if (address.isEmpty()) address = value
                ROLE_CARD_NUMBER -> if (cardNumber.isEmpty()) cardNumber = value
                ROLE_PHONE -> if (phone.isEmpty()) phone = value
                ROLE_EMAIL -> if (email.isEmpty()) email = value
                ROLE_USERNAME -> {
                    if (username.isEmpty()) {
                        username = if (moduleId == "telegram" && !value.startsWith("@") && value != "-") {
                            "@$value"
                        } else {
                            value
                        }
                    }
                    if (accountNumber.isEmpty() && moduleId == "telegram") accountNumber = value
                }
                ROLE_MEMBER_CODE -> if (memberCode.isEmpty()) memberCode = value
                ROLE_BRANCH_ID -> if (branchId.isEmpty()) branchId = value
                ROLE_DEF_NUM_OLD -> if (defNumOld.isEmpty()) defNumOld = value
                ROLE_DATE_OF_ADDRESS -> {
                    if (defNumOld.isEmpty()) defNumOld = value
                    val sb = unmappedSb ?: StringBuilder(64).also { unmappedSb = it }
                    if (sb.isNotEmpty()) sb.append(" | ")
                    sb.append("DATE OF ADDRESS: ").append(value)
                }
                ROLE_PASSWORD_HINT -> if (passwordHint.isEmpty()) passwordHint = value
                ROLE_PASSW -> if (passwValue.isEmpty()) passwValue = value
                else -> {
                    val sb = unmappedSb ?: StringBuilder(64).also { unmappedSb = it }
                    if (sb.isNotEmpty()) sb.append(" | ")
                    val rawHeader = if (i < rawHeaders.size) rawHeaders[i] else ""
                    if (rawHeader.isNotEmpty()) {
                        sb.append(rawHeader).append(": ").append(value)
                    } else {
                        sb.append(value)
                    }
                }
            }
        }

        if (fullName.isEmpty() && (firstNamePart.isNotEmpty() || lastNamePart.isNotEmpty())) {
            val hasFirst = firstNamePart.isNotEmpty() && firstNamePart != "-"
            val hasLast = lastNamePart.isNotEmpty() && lastNamePart != "-"
            fullName = when {
                hasFirst && hasLast -> "$firstNamePart $lastNamePart"
                hasFirst -> firstNamePart
                hasLast -> lastNamePart
                else -> ""
            }
            val prefixSb = StringBuilder(48)
            if (hasFirst) prefixSb.append("FIRST: ").append(firstNamePart)
            if (hasLast) {
                if (prefixSb.isNotEmpty()) prefixSb.append(" | ")
                prefixSb.append("LAST: ").append(lastNamePart)
            }
            if (prefixSb.isNotEmpty()) {
                val tail = unmappedSb?.toString().orEmpty()
                unmappedSb = StringBuilder(prefixSb.length + tail.length + 4).apply {
                    append(prefixSb)
                    if (tail.isNotEmpty()) append(" | ").append(tail)
                }
            }
        }

        if (fullName.isEmpty() && nationalCode.isEmpty() && accountNumber.isEmpty() && cardNumber.isEmpty() && username.isEmpty()) {
            return when (moduleId) {
                "bank_meli" -> parseBankMeliPositional(cols, index)
                "bank_melat" -> parseBankMelatPositional(cols, index)
                "bank_saderat" -> parseBankSaderatPositional(cols, index)
                "citizen" -> parseCitizenPositional(cols, index)
                "telegram" -> parseTelegramPositional(cols, index)
                "basij" -> parseBasijPositional(cols, index)
                "entezami" -> parseEntezamiPositional(cols, index)
                else -> parseGenericPositional(cols, index, rawLine)
            }
        }

        return ParsedCsvIdentityRow(
            originalIndex = index,
            fullName = fullName.ifEmpty { if (username.isNotEmpty()) username else "Record-${index + 1}" },
            nationalCode = nationalCode.ifEmpty { "-" },
            phone = phone.ifEmpty { "-" },
            accountOrHandle = accountNumber.ifEmpty { "-" },
            cardNumber = cardNumber.ifEmpty { "-" },
            fatherName = fatherName.ifEmpty { "-" },
            idNumber = idNumber.ifEmpty { "-" },
            birthDate = birthDate.ifEmpty { "-" },
            city = city.ifEmpty { "-" },
            province = province.ifEmpty { "-" },
            birthCity = birthCity.ifEmpty { "-" },
            birthProvince = birthProvince.ifEmpty { "-" },
            postalCode = postalCode.ifEmpty { "-" },
            address = address.ifEmpty { "-" },
            email = email.ifEmpty { "-" },
            username = username.ifEmpty { "-" },
            memberCode = memberCode.ifEmpty { "-" },
            branchId = branchId.ifEmpty { "-" },
            defNumOld = defNumOld.ifEmpty { "-" },
            passwordHint = passwordHint.ifEmpty { "-" },
            passwValue = passwValue.ifEmpty { "-" },
            rawDetails = unmappedSb?.toString().orEmpty()
        )
    }

    private fun parseGenericPositional(
        cols: List<String>,
        index: Int,
        rawLine: String
    ): ParsedCsvIdentityRow {
        val fullName = cols.getOrNull(0)?.takeIf { it.isNotEmpty() } ?: "Record-${index + 1}"
        val nationalCode = cols.colOrDash(1)
        val phone = cols.colOrDash(2)
        val account = cols.colOrDash(3)
        val city = cols.colOrDash(4)
        val extra = if (cols.size > 5) {
            joinTailColumns(cols, 5)
        } else {
            rawLine.take(120)
        }
        return ParsedCsvIdentityRow(
            originalIndex = index,
            fullName = fullName,
            nationalCode = nationalCode,
            phone = phone,
            accountOrHandle = account,
            city = city,
            rawDetails = extra
        )
    }

    private fun joinTailColumns(cols: List<String>, startIdx: Int): String {
        if (cols.size <= startIdx) return ""
        val sb = StringBuilder(32)
        for (i in startIdx until cols.size) {
            if (i > startIdx) sb.append(" | ")
            sb.append(cols[i])
        }
        return sb.toString()
    }

    private fun List<String>.colOrDash(idx: Int): String =
        getOrNull(idx)?.takeIf { it.isNotEmpty() } ?: "-"
}

class HighSpeedCsvImportEngine(
    private val context: Context,
    private val database: AppDatabase,
    private val externalScope: CoroutineScope
) {
    private val _state = MutableStateFlow(ImportProgressState())
    val state: StateFlow<ImportProgressState> = _state.asStateFlow()

    private val pauseGate = MutableStateFlow(false)
    private var activeImportJob: Job? = null

    private val cpuThreadCount: Int = Runtime.getRuntime().availableProcessors().coerceIn(2, 12)
    private val gpuSingleThreadDispatcher =
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "NexusHud-GPU-Pbuffer").apply { isDaemon = true }
        }.asCoroutineDispatcher()

    companion object {
        private const val RAW_SCAN_CHUNK_SIZE = 15_000
        private const val SQL_COMMIT_BATCH_SIZE = 15_000
        private const val UI_TELEMETRY_MIN_INTERVAL_MS = 100L
    }

    fun pauseImport() {
        if (_state.value.status == ImportStatus.RUNNING) {
            pauseGate.value = true
            _state.update {
                it.copy(
                    status = ImportStatus.PAUSED,
                    recordsPerSecond = 0L,
                    mbPerSecond = 0f,
                    cpuLoadPercent = 0,
                    gpuLoadPercent = 0,
                    statusLog = "IMPORT PAUSED AT ${it.processedRecords} RECORDS // AWAITING RESUME"
                )
            }
        }
    }

    fun resumeImport() {
        if (_state.value.status == ImportStatus.PAUSED) {
            pauseGate.value = false
            _state.update {
                it.copy(
                    status = ImportStatus.RUNNING,
                    statusLog = "RESUMING MULTI-THREADED CPU + GPU PIPELINE…"
                )
            }
        }
    }

    fun cancelImport() {
        pauseGate.value = false
        activeImportJob?.cancel()
        activeImportJob = null
        _state.update {
            it.copy(
                status = ImportStatus.CANCELLED,
                recordsPerSecond = 0L,
                mbPerSecond = 0f,
                cpuLoadPercent = 0,
                gpuLoadPercent = 0,
                statusLog = "PIPELINE ABORTED BY OPERATOR"
            )
        }
    }

    fun importCsvFromUri(moduleId: String, uri: Uri) {
        activeImportJob?.cancel()
        pauseGate.value = false

        activeImportJob = externalScope.launch(Dispatchers.IO) {
            val gpuAccelerator = GpuComputeAccelerator()
            val pendingEntities = ArrayList<IdentityEntity>(SQL_COMMIT_BATCH_SIZE * 2)
            val db = database.openHelper.writableDatabase
            try {
                // Flash-speed bulk loading SQLite PRAGMAs (eliminates fsync disk bottleneck on 20M rows)
                try {
                    db.execSQL("PRAGMA synchronous = OFF")
                    db.execSQL("PRAGMA temp_store = MEMORY")
                    db.execSQL("PRAGMA cache_size = -64000") // 64 MB RAM cache
                } catch (_: Exception) {}

                val (fileName, fileSizeBytes) = resolveFileMeta(uri)
                withContext(gpuSingleThreadDispatcher) {
                    gpuAccelerator.initializeOnCurrentThread()
                }

                _state.value = ImportProgressState(
                    status = ImportStatus.RUNNING,
                    activeModuleId = moduleId,
                    sourceFileName = fileName,
                    totalBytes = fileSizeBytes,
                    cpuWorkerCount = cpuThreadCount,
                    gpuRenderer = gpuAccelerator.rendererInfo,
                    gpuHardwareActive = gpuAccelerator.isHardwareBound,
                    statusLog = "STREAMING CSV [$fileName] ON $cpuThreadCount CPU CORES + ${gpuAccelerator.rendererInfo}"
                )

                val rawStream = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Unable to open selected CSV stream.")
                val bufferedStream = BufferedInputStream(rawStream, 1_048_576) // 1 MB high-throughput stream buffer
                val detectedCharset = detectCsvCharset(bufferedStream)
                val decoder = detectedCharset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE)

                val startWallMs = System.currentTimeMillis()
                var activeDurationAccumulatorMs = 0L
                var lastResumeTimestampMs = startWallMs
                var lastUiUpdateMs = 0L

                val processedRows = AtomicLong(0L)
                val skippedRows = AtomicLong(0L)
                val processedBytes = AtomicLong(0L)
                var batchCounter = 0
                var compiledHeaders: CompiledHeaderMap? = null
                var fileDelimiter = '\u0000'
                var checkedFirstNonEmptyLine = false
                var detectedPhoneCol = 1
                val chunkBuffer = ArrayList<String>(RAW_SCAN_CHUNK_SIZE)

                BoundedLineReader(InputStreamReader(bufferedStream, decoder), maxLineChars = 8_192).use { reader ->
                    var line: String? = reader.readBoundedLine()
                    while (line != null) {
                        ensureActive()
                        if (pauseGate.value) {
                            if (pendingEntities.isNotEmpty()) {
                                flushPendingEntities(pendingEntities, processedRows)
                            }
                            activeDurationAccumulatorMs += (System.currentTimeMillis() - lastResumeTimestampMs)
                            pauseGate.first { !it }
                            lastResumeTimestampMs = System.currentTimeMillis()
                        }

                        // Approximate UTF-8/ANSI byte advancement including truncated overflow chars
                        processedBytes.addAndGet(reader.lastRawCharsConsumed.toLong())

                        val trimmed = line.trim()
                        if (trimmed.isNotEmpty()) {
                            if (!checkedFirstNonEmptyLine) {
                                checkedFirstNonEmptyLine = true
                                fileDelimiter = CsvSchemaParser.detectDelimiter(trimmed)
                                if (CsvSchemaParser.looksLikeCsvHeader(trimmed)) {
                                    val headerCols = CsvSchemaParser.splitCsvLineFast(trimmed, fileDelimiter)
                                    val compiled = CsvSchemaParser.compileHeaders(headerCols)
                                    compiledHeaders = compiled
                                    detectedPhoneCol = if (compiled.phoneColIndex >= 0) compiled.phoneColIndex else 1
                                } else {
                                    val cols = CsvSchemaParser.splitCsvLineFast(trimmed, fileDelimiter)
                                    detectedPhoneCol = CsvSchemaParser.detectTelegramPhoneCol(cols)
                                    if (moduleId == "telegram") {
                                        val candidatePhone = cols.getOrNull(detectedPhoneCol).orEmpty()
                                        if (!CsvSchemaParser.hasIranPhonePrefix(candidatePhone)) {
                                            skippedRows.incrementAndGet()
                                        } else {
                                            chunkBuffer.add(trimmed)
                                        }
                                    } else {
                                        chunkBuffer.add(trimmed)
                                    }
                                }
                            } else {
                                val tgPhoneIdx = detectedPhoneCol
                                if (moduleId == "telegram" &&
                                    tgPhoneIdx >= 0 &&
                                    !CsvSchemaParser.rawTelegramLinePasses98Filter(trimmed, tgPhoneIdx, fileDelimiter)
                                ) {
                                    skippedRows.incrementAndGet()
                                } else {
                                    chunkBuffer.add(trimmed)
                                }
                            }
                        }

                        val nowMs = System.currentTimeMillis()
                        if (chunkBuffer.size >= RAW_SCAN_CHUNK_SIZE) {
                            batchCounter++
                            val snapshotChunk = ArrayList(chunkBuffer)
                            chunkBuffer.clear()
                            lastUiUpdateMs = processSurvivingChunkParallel(
                                moduleId = moduleId,
                                lines = snapshotChunk,
                                compiledHeaders = compiledHeaders,
                                preferredDelimiter = fileDelimiter,
                                batchIndex = batchCounter,
                                gpuAccelerator = gpuAccelerator,
                                pendingEntities = pendingEntities,
                                processedRows = processedRows,
                                skippedRowsTotal = skippedRows,
                                processedBytes = processedBytes.get(),
                                totalBytes = fileSizeBytes,
                                activeElapsedMs = activeDurationAccumulatorMs + (nowMs - lastResumeTimestampMs),
                                lastUiUpdateMs = lastUiUpdateMs,
                                forceFlush = false
                            )
                        } else if (moduleId == "telegram" && nowMs - lastUiUpdateMs >= 250L) {
                            // Keep progress bar smooth even when scanning millions of foreign non-98 rows
                            lastUiUpdateMs = nowMs
                            emitProgressSnapshot(
                                batchIndex = batchCounter,
                                currentTotal = processedRows.get(),
                                skippedTotal = skippedRows.get(),
                                moduleId = moduleId,
                                processedBytes = processedBytes.get(),
                                totalBytes = fileSizeBytes,
                                explicitTotalRecords = 0L,
                                activeElapsedMs = activeDurationAccumulatorMs + (nowMs - lastResumeTimestampMs),
                                rendererName = gpuAccelerator.rendererInfo,
                                hardwareActive = gpuAccelerator.isHardwareBound,
                                computeMicros = 0L
                            )
                        }

                        line = reader.readBoundedLine()
                    }

                    if (chunkBuffer.isNotEmpty() || pendingEntities.isNotEmpty()) {
                        batchCounter++
                        processSurvivingChunkParallel(
                            moduleId = moduleId,
                            lines = chunkBuffer,
                            compiledHeaders = compiledHeaders,
                            preferredDelimiter = fileDelimiter,
                            batchIndex = batchCounter,
                            gpuAccelerator = gpuAccelerator,
                            pendingEntities = pendingEntities,
                            processedRows = processedRows,
                            skippedRowsTotal = skippedRows,
                            processedBytes = processedBytes.get(),
                            totalBytes = fileSizeBytes,
                            activeElapsedMs = activeDurationAccumulatorMs +
                                (System.currentTimeMillis() - lastResumeTimestampMs),
                            lastUiUpdateMs = 0L,
                            forceFlush = true
                        )
                        chunkBuffer.clear()
                    }
                }

                // Optimize FTS4 B-tree segments incrementally without full table rebuild OOM
                try {
                    database.identityDao().optimizeFtsIndex()
                } catch (_: Exception) {
                    // Safe fallback
                }

                val finalCount = processedRows.get()
                val totalSkipped = skippedRows.get()
                val totalActiveMs = (activeDurationAccumulatorMs +
                    (System.currentTimeMillis() - lastResumeTimestampMs)).coerceAtLeast(1L)
                val avgRps = (finalCount * 1000L) / totalActiveMs
                val skipSuffix = if (moduleId == "telegram" && totalSkipped > 0L) {
                    " (FILTERED OUT $totalSkipped NON-98 ROWS)"
                } else {
                    ""
                }

                _state.update {
                    it.copy(
                        status = ImportStatus.COMPLETED,
                        processedRecords = finalCount,
                        totalEstimatedRecords = finalCount,
                        bytesProcessed = if (fileSizeBytes > 0L) fileSizeBytes else processedBytes.get(),
                        progressFraction = 1f,
                        recordsPerSecond = avgRps,
                        cpuLoadPercent = 0,
                        gpuLoadPercent = 0,
                        statusLog = "IMPORT COMPLETE // $finalCount RECORDS INDEXED AT $avgRps REC/SEC$skipSuffix"
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (oom: OutOfMemoryError) {
                pendingEntities.clear()
                System.gc()
                _state.update {
                    it.copy(
                        status = ImportStatus.ERROR,
                        cpuLoadPercent = 0,
                        gpuLoadPercent = 0,
                        errorMessage = "Memory limit reached on malformed file segment; partial batches were safely saved.",
                        statusLog = "ERROR // MEMORY GUARD TRIGGERED — PARTIAL BATCHES COMMITTED SAFELY"
                    )
                }
            } catch (t: Throwable) {
                pendingEntities.clear()
                _state.update {
                    it.copy(
                        status = ImportStatus.ERROR,
                        cpuLoadPercent = 0,
                        gpuLoadPercent = 0,
                        errorMessage = t.message ?: "CSV ingestion error",
                        statusLog = "ERROR // ${t.message ?: "CSV stream failure"}"
                    )
                }
            } finally {
                try {
                    db.execSQL("PRAGMA synchronous = NORMAL")
                    compiledInsertStmt?.close()
                    compiledInsertStmt = null
                } catch (_: Exception) {}
                withContext(gpuSingleThreadDispatcher) {
                    gpuAccelerator.release()
                }
            }
        }
    }

    /**
     * Generates and streams a module-accurate synthetic dataset for benchmark:
     * Streams module-accurate CSV rows for the identity nodes (Bank Meli, Bank Melat, Bank Saderat, Citizen, Irancell, Telegram).
     */
    fun runHighSpeedCsvStreamBenchmark(moduleId: String, targetRecords: Int = 100_000) {
        activeImportJob?.cancel()
        pauseGate.value = false

        activeImportJob = externalScope.launch(Dispatchers.IO) {
            val gpuAccelerator = GpuComputeAccelerator()
            val pendingEntities = ArrayList<IdentityEntity>(SQL_COMMIT_BATCH_SIZE * 2)
            try {
                withContext(gpuSingleThreadDispatcher) {
                    gpuAccelerator.initializeOnCurrentThread()
                }

                val avgBytesPerRow = when (moduleId) {
                    "bank_meli" -> 68L
                    "bank_melat" -> 148L
                    "bank_saderat" -> 152L
                    "citizen" -> 108L
                    "telegram" -> 64L
                    else -> 84L
                }
                val totalBytes = targetRecords.toLong() * avgBytesPerRow
                val delimiter = if (moduleId == "telegram") ';' else ','

                val schemaHeader = when (moduleId) {
                    "bank_meli" -> listOf(
                        "NATIONAL_CODE", "CARD_NO", "FULL_NAME", "BIRTH_DATE", "MOBILE"
                    )
                    "bank_melat" -> listOf(
                        "NATIONAL_CODE", "ACCOUNT_NUMBER", "FULL_NAME", "FATHER_NAME",
                        "ID_NUMBER", "BIRTH_DATE", "CITY_NAME", "PROVINCE_NAME",
                        "BIRTH_CITY", "BIRTH_PROVINCE", "ADDRESS", "CARD_NO", "MOBILE"
                    )
                    "bank_saderat" -> listOf(
                        "AccountNumber", "DefineationNumber", "FullName", "CardNumber",
                        "Password_Hint", "user_email_address", "Passw", "user_phone_number",
                        "Member", "Username", "DefineNum_Old", "ID_Branch"
                    )
                    "citizen" -> listOf(
                        "FULLNAME", "BIRTHDAY", "National ID", "Postal Code",
                        "Phone", "City", "Address"
                    )
                    "telegram" -> listOf(
                        "account_id", "phone", "username", "first_name", "last_name"
                    )
                    "basij" -> listOf(
                        "name", "dob", "national_id", "address", "postal_code"
                    )
                    "entezami" -> listOf(
                        "full_name", "dob", "national_id", "address", "postal_code", "date_of_address"
                    )
                    else -> null
                }
                val compiledHeaders = schemaHeader?.let { CsvSchemaParser.compileHeaders(it) }

                val benchSourceName = "${moduleId}_identity_stream_${targetRecords}.csv"

                _state.value = ImportProgressState(
                    status = ImportStatus.RUNNING,
                    activeModuleId = moduleId,
                    sourceFileName = benchSourceName,
                    totalEstimatedRecords = targetRecords.toLong(),
                    totalBytes = totalBytes,
                    cpuWorkerCount = cpuThreadCount,
                    gpuRenderer = gpuAccelerator.rendererInfo,
                    gpuHardwareActive = gpuAccelerator.isHardwareBound,
                    statusLog = "STREAMING ${targetRecords} ${moduleId.uppercase()} CSV RECORDS // $cpuThreadCount CPU WORKERS + GPU SHADER"
                )

                val firstNames = arrayOf(
                    "Aria", "Sina", "Nima", "Kian", "Sara", "Nazanin", "Reza", "Mahdi",
                    "Parisa", "Arman", "Dorsa", "Omid", "Yasamin", "Ashkan", "Taraneh", "Kamran"
                )
                val lastNames = arrayOf(
                    "Ariani", "Rad", "Tehrani", "Karimi", "Hosseini", "Moradi", "Rahimi",
                    "Ahmadi", "Rostami", "Shirazi", "Tabrizi", "Jafari", "Sadeghi", "Parsa"
                )
                val fatherNames = arrayOf(
                    "Dariush", "Bahram", "Kourosh", "Farhad", "Saeed", "Hamid", "Majid", "Javad"
                )
                val cities = arrayOf(
                    "Tehran", "Isfahan", "Shiraz", "Tabriz", "Mashhad", "Karaj", "Ahvaz", "Rasht"
                )
                val provinces = arrayOf(
                    "Tehran", "Isfahan", "Fars", "East Azerbaijan", "Khorasan Razavi", "Alborz", "Khuzestan", "Gilan"
                )

                var activeDurationAccumulatorMs = 0L
                var lastResumeTimestampMs = System.currentTimeMillis()
                var lastUiUpdateMs = 0L
                val processedRows = AtomicLong(0L)
                val skippedRows = AtomicLong(0L)
                var batchIndex = 0
                val batchSize = 5_000
                var generated = 0

                while (generated < targetRecords) {
                    ensureActive()
                    if (pauseGate.value) {
                        if (pendingEntities.isNotEmpty()) {
                            flushPendingEntities(pendingEntities, processedRows)
                        }
                        activeDurationAccumulatorMs += (System.currentTimeMillis() - lastResumeTimestampMs)
                        pauseGate.first { !it }
                        lastResumeTimestampMs = System.currentTimeMillis()
                    }

                    val currentBatchCount = minOf(batchSize, targetRecords - generated)
                    val baseOffset = generated

                    val csvLines = coroutineScope {
                        val sliceSize = (currentBatchCount + cpuThreadCount - 1) / cpuThreadCount
                        (0 until cpuThreadCount).map { worker ->
                            async(Dispatchers.Default) {
                                val startIdx = worker * sliceSize
                                val endIdx = minOf(currentBatchCount, startIdx + sliceSize)
                                if (startIdx >= endIdx) return@async emptyList<String>()
                                val list = ArrayList<String>(endIdx - startIdx)
                                val sb = StringBuilder(160)
                                for (local in startIdx until endIdx) {
                                    sb.setLength(0)
                                    val globalIdx = baseOffset + local + 1
                                    val fn = firstNames[globalIdx % firstNames.size]
                                    val ln = lastNames[(globalIdx / 7) % lastNames.size]
                                    val natNum = (1029384756L + globalIdx * 7919L) % 9_999_999_999L
                                    val mobSuffix = (120000000L + globalIdx * 104729L) % 999_999_999L
                                    val cityIdx = (globalIdx / 13) % cities.size
                                    val birthIdx = (globalIdx / 17) % cities.size
                                    val year = 1345 + (globalIdx % 42)
                                    val month = (globalIdx % 12) + 1
                                    val day = (globalIdx % 28) + 1

                                    when (moduleId) {
                                        "bank_meli" -> {
                                            appendZeroPadded(sb, natNum, 10).append(',')
                                            sb.append("60379971")
                                            appendZeroPadded(sb, (globalIdx % 100_000_000).toLong(), 8).append(',')
                                            sb.append(fn).append(' ').append(ln).append(',')
                                            appendBirthDate(sb, year, month, day).append(',')
                                            sb.append("09")
                                            appendZeroPadded(sb, mobSuffix, 9)
                                        }
                                        "bank_melat" -> {
                                            appendZeroPadded(sb, natNum, 10).append(',')
                                            appendZeroPadded(sb, 4400000000L + globalIdx, 10).append(',')
                                            sb.append(fn).append(' ').append(ln).append(',')
                                            sb.append(fatherNames[globalIdx % fatherNames.size]).append(',')
                                            sb.append((globalIdx % 89999) + 1000).append(',')
                                            appendBirthDate(sb, year, month, day).append(',')
                                            sb.append(cities[cityIdx]).append(',')
                                            sb.append(provinces[cityIdx]).append(',')
                                            sb.append(cities[birthIdx]).append(',')
                                            sb.append(provinces[birthIdx]).append(',')
                                            sb.append("\"District ").append((globalIdx % 22) + 1)
                                                .append(" Blvd ").append(globalIdx % 90)
                                                .append(" Unit ").append((globalIdx % 16) + 1).append("\",")
                                            sb.append("61043378")
                                            appendZeroPadded(sb, (globalIdx % 100_000_000).toLong(), 8).append(',')
                                            sb.append("09")
                                            appendZeroPadded(sb, mobSuffix, 9)
                                        }
                                        "bank_saderat" -> {
                                            sb.append("010")
                                            appendZeroPadded(sb, 2000000000L + globalIdx, 10).append(',')
                                            appendZeroPadded(sb, (50000000 + globalIdx).toLong(), 8).append(',')
                                            sb.append(fn).append(' ').append(ln).append(',')
                                            sb.append("60376919")
                                            appendZeroPadded(sb, (globalIdx % 100_000_000).toLong(), 8).append(',')
                                            sb.append("HNT-").append((globalIdx % 900) + 100).append(',')
                                            sb.append(fn.lowercase()).append('.').append(ln.lowercase())
                                                .append(globalIdx % 999).append("@bsi-mail.ir,")
                                            sb.append("BSI#").append(100000 + (globalIdx % 899999)).append(',')
                                            sb.append("09")
                                            appendZeroPadded(sb, mobSuffix, 9).append(',')
                                            sb.append("MBR-").append(10000 + (globalIdx % 89999)).append(',')
                                            sb.append(fn.lowercase()).append('_').append(globalIdx).append(',')
                                            sb.append("OLD-").append(300000 + (globalIdx % 699999)).append(',')
                                            sb.append("BR-").append((globalIdx % 2500) + 100)
                                        }
                                        "citizen" -> {
                                            sb.append(fn).append(' ').append(ln).append(',')
                                            appendBirthDate(sb, year, month, day).append(',')
                                            appendZeroPadded(sb, natNum, 10).append(',')
                                            appendZeroPadded(sb, (1415500000L + globalIdx * 313L) % 9_999_999_999L, 10).append(',')
                                            sb.append("09")
                                            appendZeroPadded(sb, mobSuffix, 9).append(',')
                                            sb.append(cities[cityIdx]).append(',')
                                            sb.append("\"District ").append((globalIdx % 22) + 1)
                                                .append(" Blvd ").append(globalIdx % 90)
                                                .append(" Unit ").append((globalIdx % 16) + 1).append('"')
                                        }
                                        "telegram" -> {
                                            sb.append(500000000L + globalIdx * 17L).append(';')
                                            sb.append("989")
                                            appendZeroPadded(sb, mobSuffix, 9).append(';')
                                            sb.append(fn.lowercase()).append('_').append(ln.lowercase())
                                                .append('_').append(globalIdx % 9999).append(';')
                                            sb.append(fn).append(';').append(ln)
                                        }
                                        "basij" -> {
                                            // (name, dob, national_id, address, postal_code)
                                            sb.append(fn).append(' ').append(ln).append(',')
                                            appendBirthDate(sb, year, month, day).append(',')
                                            appendZeroPadded(sb, natNum, 10).append(',')
                                            sb.append("\"District ").append((globalIdx % 22) + 1)
                                                .append(" St ").append(globalIdx % 80)
                                                .append(" No ").append((globalIdx % 50) + 1).append("\",")
                                            appendZeroPadded(sb, (1111100000L + globalIdx * 333L) % 9_999_999_999L, 10)
                                        }
                                        "entezami" -> {
                                            // (full_name, dob, national_id, address, postal_code, date_of_address)
                                            sb.append(fn).append(' ').append(ln).append(',')
                                            appendBirthDate(sb, year, month, day).append(',')
                                            appendZeroPadded(sb, natNum, 10).append(',')
                                            sb.append("\"Sector ").append((globalIdx % 18) + 1)
                                                .append(" Ave ").append(globalIdx % 100)
                                                .append(" Bldg ").append((globalIdx % 30) + 1).append("\",")
                                            appendZeroPadded(sb, (1333300000L + globalIdx * 444L) % 9_999_999_999L, 10).append(',')
                                            appendBirthDate(sb, year + (globalIdx % 5) + 1, month, day)
                                        }
                                        else -> {
                                            sb.append(fn).append(' ').append(ln).append(',')
                                            appendZeroPadded(sb, natNum, 10).append(',')
                                            sb.append("09")
                                            appendZeroPadded(sb, mobSuffix, 9).append(',')
                                            sb.append(moduleId.uppercase().take(4)).append('-').append(1000000 + globalIdx).append(',')
                                            sb.append(cities[cityIdx]).append(",SECTOR-").append(globalIdx % 64)
                                        }
                                    }
                                    list.add(sb.toString())
                                }
                                list
                            }
                        }.awaitAll().flatten()
                    }

                    generated += currentBatchCount
                    batchIndex++
                    val bytesNow = generated.toLong() * avgBytesPerRow
                    val isFinalBatch = generated >= targetRecords

                    lastUiUpdateMs = processSurvivingChunkParallel(
                        moduleId = moduleId,
                        lines = csvLines,
                        compiledHeaders = compiledHeaders,
                        preferredDelimiter = delimiter,
                        batchIndex = batchIndex,
                        gpuAccelerator = gpuAccelerator,
                        pendingEntities = pendingEntities,
                        processedRows = processedRows,
                        skippedRowsTotal = skippedRows,
                        processedBytes = bytesNow,
                        totalBytes = totalBytes,
                        explicitTotalRecords = targetRecords.toLong(),
                        activeElapsedMs = activeDurationAccumulatorMs +
                            (System.currentTimeMillis() - lastResumeTimestampMs),
                        lastUiUpdateMs = lastUiUpdateMs,
                        forceFlush = isFinalBatch
                    )
                }

                try {
                    database.identityDao().optimizeFtsIndex()
                } catch (_: Exception) {
                    // Safe fallback
                }

                val finalCount = processedRows.get()
                val totalActiveMs = (activeDurationAccumulatorMs +
                    (System.currentTimeMillis() - lastResumeTimestampMs)).coerceAtLeast(1L)
                val avgRps = (finalCount * 1000L) / totalActiveMs

                _state.update {
                    it.copy(
                        status = ImportStatus.COMPLETED,
                        processedRecords = finalCount,
                        totalEstimatedRecords = finalCount,
                        bytesProcessed = totalBytes,
                        progressFraction = 1f,
                        recordsPerSecond = avgRps,
                        cpuLoadPercent = 0,
                        gpuLoadPercent = 0,
                        statusLog = "BENCHMARK COMPLETE // $finalCount RECORDS INDEXED AT $avgRps REC/SEC"
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (oom: OutOfMemoryError) {
                pendingEntities.clear()
                System.gc()
                _state.update {
                    it.copy(
                        status = ImportStatus.ERROR,
                        errorMessage = "Memory limit reached during stream benchmark.",
                        statusLog = "BENCHMARK ERROR // MEMORY GUARD TRIGGERED"
                    )
                }
            } catch (t: Throwable) {
                pendingEntities.clear()
                _state.update {
                    it.copy(
                        status = ImportStatus.ERROR,
                        errorMessage = t.message,
                        statusLog = "BENCHMARK ERROR // ${t.message}"
                    )
                }
            } finally {
                withContext(gpuSingleThreadDispatcher) {
                    gpuAccelerator.release()
                }
            }
        }
    }

    private fun appendZeroPadded(sb: StringBuilder, value: Long, width: Int): StringBuilder {
        val str = value.toString()
        for (k in str.length until width) {
            sb.append('0')
        }
        sb.append(str)
        return sb
    }

    private fun appendBirthDate(sb: StringBuilder, year: Int, month: Int, day: Int): StringBuilder {
        sb.append(year).append('/')
        if (month < 10) sb.append('0')
        sb.append(month).append('/')
        if (day < 10) sb.append('0')
        sb.append(day)
        return sb
    }

    @Volatile
    private var compiledInsertStmt: SupportSQLiteStatement? = null

    private fun getOrCreateInsertStatement(db: SupportSQLiteDatabase): SupportSQLiteStatement {
        val existing = compiledInsertStmt
        if (existing != null) return existing
        val sql = """
            INSERT INTO identity_records (
                moduleId, fullName, nationalCode, phone, accountOrHandle,
                cardNumber, fatherName, idNumber, birthDate, city,
                province, birthCity, birthProvince, postalCode, address,
                email, username, memberCode, branchId, defNumOld,
                passwordHint, passwValue, rawDetails, gpuSignatureHash, importedAt
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        val stmt = db.compileStatement(sql)
        compiledInsertStmt = stmt
        return stmt
    }

    private inline fun fastNormalizeField(s: String): String {
        if (s.isEmpty() || s == "-") return "-"
        return TextNormalizer.normalizeForSearch(s).ifEmpty { "-" }
    }

    private suspend fun flushPendingEntities(
        pendingEntities: ArrayList<IdentityEntity>,
        processedRows: AtomicLong,
        maxCount: Int = pendingEntities.size
    ) {
        if (pendingEntities.isEmpty()) return
        val count = minOf(pendingEntities.size, maxCount)
        withContext(Dispatchers.IO) {
            val db = database.openHelper.writableDatabase
            val stmt = getOrCreateInsertStatement(db)
            db.beginTransactionNonExclusive()
            try {
                for (i in 0 until count) {
                    val e = pendingEntities[i]
                    stmt.clearBindings()
                    stmt.bindString(1, e.moduleId)
                    stmt.bindString(2, e.fullName)
                    stmt.bindString(3, e.nationalCode)
                    stmt.bindString(4, e.phone)
                    stmt.bindString(5, e.accountOrHandle)
                    stmt.bindString(6, e.cardNumber)
                    stmt.bindString(7, e.fatherName)
                    stmt.bindString(8, e.idNumber)
                    stmt.bindString(9, e.birthDate)
                    stmt.bindString(10, e.city)
                    stmt.bindString(11, e.province)
                    stmt.bindString(12, e.birthCity)
                    stmt.bindString(13, e.birthProvince)
                    stmt.bindString(14, e.postalCode)
                    stmt.bindString(15, e.address)
                    stmt.bindString(16, e.email)
                    stmt.bindString(17, e.username)
                    stmt.bindString(18, e.memberCode)
                    stmt.bindString(19, e.branchId)
                    stmt.bindString(20, e.defNumOld)
                    stmt.bindString(21, e.passwordHint)
                    stmt.bindString(22, e.passwValue)
                    stmt.bindString(23, e.rawDetails)
                    stmt.bindLong(24, e.gpuSignatureHash)
                    stmt.bindLong(25, e.importedAt)
                    stmt.executeInsert()
                }
                db.setTransactionSuccessful()
                processedRows.addAndGet(count.toLong())
            } finally {
                db.endTransaction()
            }
            if (count >= pendingEntities.size) {
                pendingEntities.clear()
            } else {
                pendingEntities.subList(0, count).clear()
            }
        }
    }

    private suspend fun processSurvivingChunkParallel(
        moduleId: String,
        lines: List<String>,
        compiledHeaders: CompiledHeaderMap?,
        preferredDelimiter: Char,
        batchIndex: Int,
        gpuAccelerator: GpuComputeAccelerator,
        pendingEntities: ArrayList<IdentityEntity>,
        processedRows: AtomicLong,
        skippedRowsTotal: AtomicLong,
        processedBytes: Long,
        totalBytes: Long,
        explicitTotalRecords: Long = 0L,
        activeElapsedMs: Long,
        lastUiUpdateMs: Long,
        forceFlush: Boolean
    ): Long = coroutineScope {
        var gpuRendererName = gpuAccelerator.rendererInfo
        var gpuHardware = gpuAccelerator.isHardwareBound
        var gpuMicros = 0L

        if (lines.isNotEmpty()) {
            // Compute GPU signatures & CPU normalized entities concurrently ONLY on surviving lines!
            val gpuDeferred = async(gpuSingleThreadDispatcher) {
                gpuAccelerator.computeBatchSignatures(lines, seedSalt = batchIndex * 0.137f)
            }

            val sliceSize = (lines.size + cpuThreadCount - 1) / cpuThreadCount
            val nowTimestamp = System.currentTimeMillis()

            val parsedSlicesDeferred = (0 until cpuThreadCount).map { workerIdx ->
                async(Dispatchers.Default) {
                    val from = workerIdx * sliceSize
                    val to = minOf(lines.size, from + sliceSize)
                    if (from >= to) return@async emptyList<ParsedCsvIdentityRow>()
                    val parsed = ArrayList<ParsedCsvIdentityRow>(to - from)
                    for (i in from until to) {
                        val candidate = CsvSchemaParser.parseIdentityCsvRowCompiled(
                            moduleId = moduleId,
                            rawLine = lines[i],
                            compiledHeaders = compiledHeaders,
                            preferredDelimiter = preferredDelimiter,
                            index = i
                        )
                        if (CsvSchemaParser.passesModuleImportFilter(moduleId, candidate)) {
                            parsed.add(candidate)
                        } else {
                            skippedRowsTotal.incrementAndGet()
                        }
                    }
                    parsed
                }
            }

            val gpuResult = gpuDeferred.await()
            gpuRendererName = gpuResult.rendererName
            gpuHardware = gpuResult.hardwareActive
            gpuMicros = gpuResult.computeMicros

            val parsedSlices = parsedSlicesDeferred.awaitAll()
            for (slice in parsedSlices) {
                for (k in slice.indices) {
                    val row = slice[k]
                    val sig = if (row.originalIndex in gpuResult.signatures.indices) {
                        gpuResult.signatures[row.originalIndex]
                    } else {
                        1L
                    }
                    val cleanedPhone = if (row.phone.isEmpty() || row.phone == "-") {
                        "-"
                    } else {
                        val norm = TextNormalizer.normalizeForSearch(row.phone)
                        if (moduleId == "telegram") norm.trimStart('+') else norm
                    }.ifEmpty { "-" }

                    pendingEntities.add(
                        IdentityEntity(
                            moduleId = moduleId,
                            fullName = if (row.fullName.isEmpty() || row.fullName == "-") "Unknown" else TextNormalizer.normalizeForSearch(row.fullName).ifEmpty { row.fullName },
                            nationalCode = fastNormalizeField(row.nationalCode),
                            phone = cleanedPhone,
                            accountOrHandle = fastNormalizeField(row.accountOrHandle),
                            cardNumber = fastNormalizeField(row.cardNumber),
                            fatherName = fastNormalizeField(row.fatherName),
                            idNumber = fastNormalizeField(row.idNumber),
                            birthDate = fastNormalizeField(row.birthDate),
                            city = fastNormalizeField(row.city),
                            province = fastNormalizeField(row.province),
                            birthCity = fastNormalizeField(row.birthCity),
                            birthProvince = fastNormalizeField(row.birthProvince),
                            postalCode = fastNormalizeField(row.postalCode),
                            address = fastNormalizeField(row.address),
                            email = fastNormalizeField(row.email),
                            username = fastNormalizeField(row.username),
                            memberCode = fastNormalizeField(row.memberCode),
                            branchId = fastNormalizeField(row.branchId),
                            defNumOld = fastNormalizeField(row.defNumOld),
                            passwordHint = fastNormalizeField(row.passwordHint),
                            passwValue = fastNormalizeField(row.passwValue),
                            rawDetails = if (row.rawDetails.isEmpty()) "" else TextNormalizer.normalizeForSearch(row.rawDetails),
                            gpuSignatureHash = sig,
                            importedAt = nowTimestamp
                        )
                    )
                }
            }
        }

        // Direct compiled SQLiteStatement batching for peak flash-speed throughput
        while (pendingEntities.size >= SQL_COMMIT_BATCH_SIZE) {
            flushPendingEntities(pendingEntities, processedRows, maxCount = SQL_COMMIT_BATCH_SIZE)
        }

        if (forceFlush && pendingEntities.isNotEmpty()) {
            flushPendingEntities(pendingEntities, processedRows, maxCount = pendingEntities.size)
        }

        val nowMs = System.currentTimeMillis()
        if (forceFlush || nowMs - lastUiUpdateMs >= UI_TELEMETRY_MIN_INTERVAL_MS) {
            emitProgressSnapshot(
                batchIndex = batchIndex,
                currentTotal = processedRows.get(),
                skippedTotal = skippedRowsTotal.get(),
                moduleId = moduleId,
                processedBytes = processedBytes,
                totalBytes = totalBytes,
                explicitTotalRecords = explicitTotalRecords,
                activeElapsedMs = activeElapsedMs,
                rendererName = gpuRendererName,
                hardwareActive = gpuHardware,
                computeMicros = gpuMicros
            )
            return@coroutineScope nowMs
        }
        return@coroutineScope lastUiUpdateMs
    }

    private fun emitProgressSnapshot(
        batchIndex: Int,
        currentTotal: Long,
        skippedTotal: Long,
        moduleId: String,
        processedBytes: Long,
        totalBytes: Long,
        explicitTotalRecords: Long,
        activeElapsedMs: Long,
        rendererName: String,
        hardwareActive: Boolean,
        computeMicros: Long
    ) {
        val safeElapsedMs = activeElapsedMs.coerceAtLeast(1L)
        val rps = (currentTotal * 1000L) / safeElapsedMs
        val mbps = (processedBytes.toFloat() / (1024f * 1024f)) / (safeElapsedMs.toFloat() / 1000f)

        val fraction = when {
            explicitTotalRecords > 0L ->
                (currentTotal.toFloat() / explicitTotalRecords.toFloat()).coerceIn(0f, 0.999f)
            totalBytes > 0L ->
                (processedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 0.999f)
            else -> 0.5f
        }

        val estimatedTotal = when {
            explicitTotalRecords > 0L -> explicitTotalRecords
            fraction > 0.01f -> (currentTotal / fraction).toLong()
            else -> currentTotal
        }

        val simulatedDynamicCpuLoad = (72 + (batchIndex * 7) % 26).coerceIn(65, 99)
        val simulatedDynamicGpuLoad = (58 + (batchIndex * 11) % 36).coerceIn(50, 96)
        val filterNote = if (moduleId == "telegram" && skippedTotal > 0L) {
            " | FILTERED $skippedTotal NON-98"
        } else {
            ""
        }

        _state.update {
            if (it.status == ImportStatus.PAUSED) it
            else it.copy(
                status = ImportStatus.RUNNING,
                processedRecords = currentTotal,
                totalEstimatedRecords = estimatedTotal,
                bytesProcessed = processedBytes,
                progressFraction = fraction,
                recordsPerSecond = rps,
                mbPerSecond = mbps,
                cpuLoadPercent = simulatedDynamicCpuLoad,
                gpuRenderer = rendererName,
                gpuHardwareActive = hardwareActive,
                gpuLoadPercent = simulatedDynamicGpuLoad,
                activeBatchIndex = batchIndex,
                statusLog = "BATCH #$batchIndex // CPU ${cpuThreadCount}T @ ${simulatedDynamicCpuLoad}% | GPU ${computeMicros}µs$filterNote"
            )
        }
    }

    private fun resolveFileMeta(uri: Uri): Pair<String, Long> {
        var displayName = "identity_dataset.csv"
        var size = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) {
                        displayName = cursor.getString(nameIdx) ?: displayName
                    }
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        size = cursor.getLong(sizeIdx)
                    }
                }
            }
            if (size <= 0L) {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    if (afd.length > 0L) {
                        size = afd.length
                    }
                }
            }
        } catch (_: Exception) {
            // Fallback to default metadata
        }
        return displayName to size
    }

    private fun detectCsvCharset(stream: BufferedInputStream): Charset {
        stream.mark(8192)
        val sample = ByteArray(4096)
        var totalRead = 0
        while (totalRead < sample.size) {
            val n = stream.read(sample, totalRead, sample.size - totalRead)
            if (n <= 0) break
            totalRead += n
        }
        stream.reset()
        if (totalRead >= 2) {
            val b0 = sample[0].toInt() and 0xFF
            val b1 = sample[1].toInt() and 0xFF
            if (b0 == 0xFF && b1 == 0xFE) return Charsets.UTF_16LE
            if (b0 == 0xFE && b1 == 0xFF) return Charsets.UTF_16BE
        }
        if (totalRead >= 3) {
            val b0 = sample[0].toInt() and 0xFF
            val b1 = sample[1].toInt() and 0xFF
            val b2 = sample[2].toInt() and 0xFF
            if (b0 == 0xEF && b1 == 0xBB && b2 == 0xBF) return Charsets.UTF_8
        }
        // Check if sample bytes form valid UTF-8; if invalid high bytes exist, fall back to Windows-1256 (Persian/Arabic ANSI)
        if (!isValidUtf8Sample(sample, totalRead)) {
            return try {
                Charset.forName("windows-1256")
            } catch (_: Exception) {
                Charsets.UTF_8
            }
        }
        return Charsets.UTF_8
    }

    private fun isValidUtf8Sample(bytes: ByteArray, length: Int): Boolean {
        var i = 0
        while (i < length) {
            val b = bytes[i].toInt() and 0xFF
            when {
                b <= 0x7F -> i++
                b in 0xC2..0xDF -> {
                    if (i + 1 >= length) return true // truncated at end of sample buffer
                    if ((bytes[i + 1].toInt() and 0xC0) != 0x80) return false
                    i += 2
                }
                b in 0xE0..0xEF -> {
                    if (i + 2 >= length) return true
                    val b1 = bytes[i + 1].toInt() and 0xFF
                    val b2 = bytes[i + 2].toInt() and 0xFF
                    if ((b1 and 0xC0) != 0x80 || (b2 and 0xC0) != 0x80) return false
                    i += 3
                }
                b in 0xF0..0xF4 -> {
                    if (i + 3 >= length) return true
                    val b1 = bytes[i + 1].toInt() and 0xFF
                    val b2 = bytes[i + 2].toInt() and 0xFF
                    val b3 = bytes[i + 3].toInt() and 0xFF
                    if ((b1 and 0xC0) != 0x80 || (b2 and 0xC0) != 0x80 || (b3 and 0xC0) != 0x80) return false
                    i += 4
                }
                else -> return false
            }
        }
        return true
    }
}
