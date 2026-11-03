package com.example.engine

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.AppDatabase
import com.example.data.IdentityEntity
import java.io.BufferedReader
import java.io.InputStreamReader
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
    val rawDetails: String = ""
)

object CsvSchemaParser {

    fun looksLikeCsvHeader(firstLine: String): Boolean {
        val clean = firstLine.removePrefix("\uFEFF").lowercase()
        return clean.contains("national_code") ||
            clean.contains("national id") ||
            clean.contains("fullname") ||
            clean.contains("full_name") ||
            clean.contains("account_number") ||
            clean.contains("postal code") ||
            clean.contains("father_name") ||
            clean.contains("card_no") ||
            clean.contains("birthday") ||
            clean.contains("birth_date") ||
            (clean.contains("name") && (clean.contains("phone") || clean.contains("mobile")))
    }

    fun splitCsvLineFast(line: String): List<String> {
        val cleanLine = line.removePrefix("\uFEFF")
        val delimiter = when {
            cleanLine.count { it == ',' } >= cleanLine.count { it == ';' } && cleanLine.contains(',') -> ','
            cleanLine.contains(';') -> ';'
            cleanLine.contains('\t') -> '\t'
            cleanLine.contains('|') -> '|'
            else -> ','
        }
        val result = ArrayList<String>(14)
        val sb = StringBuilder(48)
        var inQuotes = false
        var i = 0
        while (i < cleanLine.length) {
            val ch = cleanLine[i]
            when {
                ch == '"' -> {
                    if (inQuotes && i + 1 < cleanLine.length && cleanLine[i + 1] == '"') {
                        sb.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ch == delimiter && !inQuotes -> {
                    result.add(sb.toString().trim())
                    sb.setLength(0)
                }
                else -> sb.append(ch)
            }
            i++
        }
        result.add(sb.toString().trim())
        return result
    }

    /**
     * Parses a CSV row according to module-specific schemas and header detection:
     * - BankMelat (`bank_melat`):
     *   (NATIONAL_CODE,ACCOUNT_NUMBER,FULL_NAME,FATHER_NAME,ID_NUMBER,BIRTH_DATE,CITY_NAME,PROVINCE_NAME,BIRTH_CITY,BIRTH_PROVINCE,ADDRESS,CARD_NO,MOBILE)
     * - Citizen (`citizen`):
     *   (FULLNAME,BIRTHDAY,National ID,Postal Code,Phone,City,Address)
     * - Dynamic header mapping if custom header order is supplied, or module positional fallback.
     */
    fun parseIdentityCsvRow(
        moduleId: String,
        rawLine: String,
        headers: List<String>?,
        index: Int
    ): ParsedCsvIdentityRow {
        val cols = splitCsvLineFast(rawLine)
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

        // If header columns are present, check whether they match BankMelat or Citizen or arbitrary header order
        if (headers != null && headers.size >= 2) {
            return parseWithHeaders(moduleId, cols, headers, index, rawLine)
        }

        // Headerless positional schema mapping
        return when (moduleId) {
            "bank_melat" -> parseBankMelatPositional(cols, index)
            "citizen" -> parseCitizenPositional(cols, index)
            else -> {
                // Auto-detect 13-col BankMelat or 7-col Citizen if imported into another node
                if (cols.size >= 13) {
                    parseBankMelatPositional(cols, index)
                } else {
                    parseGenericPositional(cols, index, rawLine)
                }
            }
        }
    }

    private fun parseBankMelatPositional(cols: List<String>, index: Int): ParsedCsvIdentityRow {
        // (0:NATIONAL_CODE, 1:ACCOUNT_NUMBER, 2:FULL_NAME, 3:FATHER_NAME, 4:ID_NUMBER,
        //  5:BIRTH_DATE, 6:CITY_NAME, 7:PROVINCE_NAME, 8:BIRTH_CITY, 9:BIRTH_PROVINCE,
        //  10:ADDRESS, 11:CARD_NO, 12:MOBILE)
        val nationalCode = cols.colOrDash(0)
        val accountNumber = cols.colOrDash(1)
        val fullName = cols.getOrNull(2)?.takeIf { it.isNotBlank() } ?: "Record-${index + 1}"
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

        val extra = if (cols.size > 13) {
            cols.subList(13, cols.size).joinToString(" | ")
        } else {
            ""
        }

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

    private fun parseCitizenPositional(cols: List<String>, index: Int): ParsedCsvIdentityRow {
        // (0:FULLNAME, 1:BIRTHDAY, 2:National ID, 3:Postal Code, 4:Phone, 5:City, 6:Address)
        val fullName = cols.getOrNull(0)?.takeIf { it.isNotBlank() } ?: "Citizen-${index + 1}"
        val birthday = cols.colOrDash(1)
        val nationalId = cols.colOrDash(2)
        val postalCode = cols.colOrDash(3)
        val phone = cols.colOrDash(4)
        val city = cols.colOrDash(5)
        val address = cols.colOrDash(6)

        val extra = if (cols.size > 7) {
            cols.subList(7, cols.size).joinToString(" | ")
        } else {
            ""
        }

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

    private fun parseWithHeaders(
        moduleId: String,
        cols: List<String>,
        headers: List<String>,
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
        val unmapped = ArrayList<String>(4)

        for (i in cols.indices) {
            val rawHeader = headers.getOrNull(i)?.trim().orEmpty()
            val norm = rawHeader.lowercase().replace(Regex("[\\s_-]+"), "_")
            val value = cols[i]
            if (value.isBlank()) continue

            when (norm) {
                "national_code", "national_id", "nationalid", "meli_code", "ssn", "codemeli" ->
                    if (nationalCode.isEmpty()) nationalCode = value
                "account_number", "account_no", "account", "iban", "sheba", "username", "handle" ->
                    if (accountNumber.isEmpty()) accountNumber = value
                "full_name", "fullname", "name", "customer_name" ->
                    if (fullName.isEmpty()) fullName = value
                "father_name", "fathername", "father" ->
                    if (fatherName.isEmpty()) fatherName = value
                "id_number", "id_no", "shenasnameh", "certificate_no" ->
                    if (idNumber.isEmpty()) idNumber = value
                "birth_date", "birthday", "birthdate", "dob" ->
                    if (birthDate.isEmpty()) birthDate = value
                "city_name", "city" ->
                    if (city.isEmpty()) city = value
                "province_name", "province", "state" ->
                    if (province.isEmpty()) province = value
                "birth_city", "birthcity" ->
                    if (birthCity.isEmpty()) birthCity = value
                "birth_province", "birthprovince" ->
                    if (birthProvince.isEmpty()) birthProvince = value
                "postal_code", "postalcode", "zip", "zip_code", "postcode" ->
                    if (postalCode.isEmpty()) postalCode = value
                "address", "home_address", "street_address" ->
                    if (address.isEmpty()) address = value
                "card_no", "card_number", "card", "pan" ->
                    if (cardNumber.isEmpty()) cardNumber = value
                "mobile", "phone", "phone_number", "mobile_no", "tel", "cell" ->
                    if (phone.isEmpty()) phone = value
                else -> {
                    if (rawHeader.isNotEmpty()) {
                        unmapped.add("$rawHeader: $value")
                    } else {
                        unmapped.add(value)
                    }
                }
            }
        }

        // Fallback if headers had unexpected names
        if (fullName.isEmpty() && nationalCode.isEmpty()) {
            return when (moduleId) {
                "bank_melat" -> parseBankMelatPositional(cols, index)
                "citizen" -> parseCitizenPositional(cols, index)
                else -> parseGenericPositional(cols, index, rawLine)
            }
        }

        return ParsedCsvIdentityRow(
            originalIndex = index,
            fullName = fullName.ifEmpty { "Record-${index + 1}" },
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
            rawDetails = unmapped.joinToString(" | ")
        )
    }

    private fun parseGenericPositional(
        cols: List<String>,
        index: Int,
        rawLine: String
    ): ParsedCsvIdentityRow {
        val fullName = cols.getOrNull(0)?.takeIf { it.isNotBlank() } ?: "Record-${index + 1}"
        val nationalCode = cols.colOrDash(1)
        val phone = cols.colOrDash(2)
        val account = cols.colOrDash(3)
        val city = cols.colOrDash(4)
        val extra = if (cols.size > 5) {
            cols.subList(5, cols.size).joinToString(" | ")
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

    private fun List<String>.colOrDash(idx: Int): String =
        getOrNull(idx)?.takeIf { it.isNotBlank() } ?: "-"
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
            try {
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

                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Unable to open selected CSV stream.")

                val startWallMs = System.currentTimeMillis()
                var activeDurationAccumulatorMs = 0L
                var lastResumeTimestampMs = startWallMs

                val processedRows = AtomicLong(0L)
                val processedBytes = AtomicLong(0L)
                var batchCounter = 0
                var headerColumns: List<String>? = null
                var checkedFirstNonEmptyLine = false
                val chunkBuffer = ArrayList<String>(3500)

                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8), 262_144).use { reader ->
                    var line: String? = reader.readLine()
                    while (line != null) {
                        ensureActive()
                        if (pauseGate.value) {
                            activeDurationAccumulatorMs += (System.currentTimeMillis() - lastResumeTimestampMs)
                            pauseGate.first { !it }
                            lastResumeTimestampMs = System.currentTimeMillis()
                        }

                        val trimmed = line.trim()
                        val lineBytes = line.length.toLong() + 1L
                        processedBytes.addAndGet(lineBytes)

                        if (trimmed.isNotEmpty()) {
                            if (!checkedFirstNonEmptyLine) {
                                checkedFirstNonEmptyLine = true
                                if (CsvSchemaParser.looksLikeCsvHeader(trimmed)) {
                                    headerColumns = CsvSchemaParser.splitCsvLineFast(trimmed)
                                } else {
                                    chunkBuffer.add(trimmed)
                                }
                            } else {
                                chunkBuffer.add(trimmed)
                            }
                        }

                        if (chunkBuffer.size >= 3500) {
                            batchCounter++
                            val snapshotChunk = ArrayList(chunkBuffer)
                            chunkBuffer.clear()
                            processChunkParallel(
                                moduleId = moduleId,
                                lines = snapshotChunk,
                                headerColumns = headerColumns,
                                batchIndex = batchCounter,
                                gpuAccelerator = gpuAccelerator,
                                processedRows = processedRows,
                                processedBytes = processedBytes.get(),
                                totalBytes = fileSizeBytes,
                                activeElapsedMs = activeDurationAccumulatorMs +
                                    (System.currentTimeMillis() - lastResumeTimestampMs)
                            )
                        }
                        line = reader.readLine()
                    }

                    if (chunkBuffer.isNotEmpty()) {
                        batchCounter++
                        processChunkParallel(
                            moduleId = moduleId,
                            lines = chunkBuffer,
                            headerColumns = headerColumns,
                            batchIndex = batchCounter,
                            gpuAccelerator = gpuAccelerator,
                            processedRows = processedRows,
                            processedBytes = processedBytes.get(),
                            totalBytes = fileSizeBytes,
                            activeElapsedMs = activeDurationAccumulatorMs +
                                (System.currentTimeMillis() - lastResumeTimestampMs)
                        )
                        chunkBuffer.clear()
                    }
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
                        progressFraction = 1f,
                        recordsPerSecond = avgRps,
                        cpuLoadPercent = 0,
                        gpuLoadPercent = 0,
                        statusLog = "IMPORT COMPLETE // $finalCount RECORDS INDEXED AT $avgRps REC/SEC"
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        status = ImportStatus.ERROR,
                        cpuLoadPercent = 0,
                        gpuLoadPercent = 0,
                        errorMessage = e.message ?: "CSV ingestion error",
                        statusLog = "ERROR // ${e.message ?: "CSV stream failure"}"
                    )
                }
            } finally {
                withContext(gpuSingleThreadDispatcher) {
                    gpuAccelerator.release()
                }
            }
        }
    }

    /**
     * Generates and streams a module-accurate synthetic CSV dataset:
     * - BankMelat uses exact 13-column CSV format:
     *   NATIONAL_CODE,ACCOUNT_NUMBER,FULL_NAME,FATHER_NAME,ID_NUMBER,BIRTH_DATE,CITY_NAME,PROVINCE_NAME,BIRTH_CITY,BIRTH_PROVINCE,ADDRESS,CARD_NO,MOBILE
     * - Citizen uses exact 7-column CSV format:
     *   FULLNAME,BIRTHDAY,National ID,Postal Code,Phone,City,Address
     */
    fun runHighSpeedCsvStreamBenchmark(moduleId: String, targetRecords: Int = 100_000) {
        activeImportJob?.cancel()
        pauseGate.value = false

        activeImportJob = externalScope.launch(Dispatchers.IO) {
            val gpuAccelerator = GpuComputeAccelerator()
            try {
                withContext(gpuSingleThreadDispatcher) {
                    gpuAccelerator.initializeOnCurrentThread()
                }

                val avgBytesPerRow = when (moduleId) {
                    "bank_melat" -> 148L
                    "citizen" -> 108L
                    else -> 84L
                }
                val totalBytes = targetRecords.toLong() * avgBytesPerRow

                val schemaHeader = when (moduleId) {
                    "bank_melat" -> listOf(
                        "NATIONAL_CODE", "ACCOUNT_NUMBER", "FULL_NAME", "FATHER_NAME",
                        "ID_NUMBER", "BIRTH_DATE", "CITY_NAME", "PROVINCE_NAME",
                        "BIRTH_CITY", "BIRTH_PROVINCE", "ADDRESS", "CARD_NO", "MOBILE"
                    )
                    "citizen" -> listOf(
                        "FULLNAME", "BIRTHDAY", "National ID", "Postal Code",
                        "Phone", "City", "Address"
                    )
                    else -> null
                }

                _state.value = ImportProgressState(
                    status = ImportStatus.RUNNING,
                    activeModuleId = moduleId,
                    sourceFileName = "${moduleId}_identity_stream_${targetRecords}.csv",
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
                val processedRows = AtomicLong(0L)
                var batchIndex = 0
                val batchSize = 4_000
                var generated = 0

                while (generated < targetRecords) {
                    ensureActive()
                    if (pauseGate.value) {
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
                                for (local in startIdx until endIdx) {
                                    val globalIdx = baseOffset + local + 1
                                    val fn = firstNames[globalIdx % firstNames.size]
                                    val ln = lastNames[(globalIdx / 7) % lastNames.size]
                                    val natId = "%010d".format((1029384756L + globalIdx * 7919L) % 9_999_999_999L)
                                    val mobile = "09%09d".format((120000000L + globalIdx * 104729L) % 999_999_999L)
                                    val cityIdx = (globalIdx / 13) % cities.size
                                    val birthIdx = (globalIdx / 17) % cities.size
                                    val year = 1345 + (globalIdx % 42)
                                    val month = "%02d".format((globalIdx % 12) + 1)
                                    val day = "%02d".format((globalIdx % 28) + 1)
                                    val birthDate = "$year/$month/$day"
                                    val address = "District ${(globalIdx % 22) + 1} Blvd ${globalIdx % 90} Unit ${(globalIdx % 16) + 1}"

                                    when (moduleId) {
                                        "bank_melat" -> {
                                            val accNo = "%010d".format(4400000000L + globalIdx)
                                            val father = fatherNames[globalIdx % fatherNames.size]
                                            val idNo = "${(globalIdx % 89999) + 1000}"
                                            val cardNo = "61043378%08d".format(globalIdx % 100_000_000)
                                            list.add(
                                                "$natId,$accNo,$fn $ln,$father,$idNo,$birthDate,${cities[cityIdx]},${provinces[cityIdx]},${cities[birthIdx]},${provinces[birthIdx]},\"$address\",$cardNo,$mobile"
                                            )
                                        }
                                        "citizen" -> {
                                            val postal = "%010d".format((1415500000L + globalIdx * 313L) % 9_999_999_999L)
                                            list.add(
                                                "$fn $ln,$birthDate,$natId,$postal,$mobile,${cities[cityIdx]},\"$address\""
                                            )
                                        }
                                        else -> {
                                            val acc = "${moduleId.uppercase().take(4)}-${1000000 + globalIdx}"
                                            list.add("$fn $ln,$natId,$mobile,$acc,${cities[cityIdx]},SECTOR-${globalIdx % 64}")
                                        }
                                    }
                                }
                                list
                            }
                        }.awaitAll().flatten()
                    }

                    generated += currentBatchCount
                    batchIndex++
                    val bytesNow = generated.toLong() * avgBytesPerRow

                    processChunkParallel(
                        moduleId = moduleId,
                        lines = csvLines,
                        headerColumns = schemaHeader,
                        batchIndex = batchIndex,
                        gpuAccelerator = gpuAccelerator,
                        processedRows = processedRows,
                        processedBytes = bytesNow,
                        totalBytes = totalBytes,
                        explicitTotalRecords = targetRecords.toLong(),
                        activeElapsedMs = activeDurationAccumulatorMs +
                            (System.currentTimeMillis() - lastResumeTimestampMs)
                    )
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
                        progressFraction = 1f,
                        recordsPerSecond = avgRps,
                        cpuLoadPercent = 0,
                        gpuLoadPercent = 0,
                        statusLog = "BENCHMARK COMPLETE // $finalCount RECORDS INDEXED AT $avgRps REC/SEC"
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        status = ImportStatus.ERROR,
                        errorMessage = e.message,
                        statusLog = "BENCHMARK ERROR // ${e.message}"
                    )
                }
            } finally {
                withContext(gpuSingleThreadDispatcher) {
                    gpuAccelerator.release()
                }
            }
        }
    }

    private suspend fun processChunkParallel(
        moduleId: String,
        lines: List<String>,
        headerColumns: List<String>?,
        batchIndex: Int,
        gpuAccelerator: GpuComputeAccelerator,
        processedRows: AtomicLong,
        processedBytes: Long,
        totalBytes: Long,
        explicitTotalRecords: Long = 0L,
        activeElapsedMs: Long
    ) = coroutineScope {
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
                    parsed.add(
                        CsvSchemaParser.parseIdentityCsvRow(
                            moduleId = moduleId,
                            rawLine = lines[i],
                            headers = headerColumns,
                            index = i
                        )
                    )
                }
                parsed
            }
        }

        val gpuResult = gpuDeferred.await()
        val parsedRows = parsedSlicesDeferred.awaitAll().flatten()

        val entities = ArrayList<IdentityEntity>(parsedRows.size)
        for (i in parsedRows.indices) {
            val row = parsedRows[i]
            val sig = if (row.originalIndex in gpuResult.signatures.indices) {
                gpuResult.signatures[row.originalIndex]
            } else {
                0L
            }
            entities.add(
                IdentityEntity(
                    moduleId = moduleId,
                    fullName = row.fullName,
                    nationalCode = row.nationalCode,
                    phone = row.phone,
                    accountOrHandle = row.accountOrHandle,
                    cardNumber = row.cardNumber,
                    fatherName = row.fatherName,
                    idNumber = row.idNumber,
                    birthDate = row.birthDate,
                    city = row.city,
                    province = row.province,
                    birthCity = row.birthCity,
                    birthProvince = row.birthProvince,
                    postalCode = row.postalCode,
                    address = row.address,
                    rawDetails = row.rawDetails,
                    gpuSignatureHash = sig,
                    importedAt = nowTimestamp
                )
            )
        }

        database.identityDao().insertBatch(entities)

        val currentTotal = processedRows.addAndGet(entities.size.toLong())
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
                gpuRenderer = gpuResult.rendererName,
                gpuHardwareActive = gpuResult.hardwareActive,
                gpuLoadPercent = simulatedDynamicGpuLoad,
                activeBatchIndex = batchIndex,
                statusLog = "BATCH #$batchIndex COMMITTED // CPU ${cpuThreadCount}T @ ${simulatedDynamicCpuLoad}% | GPU SHADER ${gpuResult.computeMicros}µs"
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
        } catch (_: Exception) {
            // Fallback to default metadata
        }
        return displayName to size
    }
}
