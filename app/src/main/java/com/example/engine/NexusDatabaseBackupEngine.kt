package com.example.engine

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.AppDatabase
import com.example.data.IdentityEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

enum class BackupOperationType {
    NONE, EXPORT, RESTORE
}

enum class BackupStatus {
    IDLE,
    RUNNING,
    COMPLETED,
    ERROR,
    CANCELLED
}

data class BackupProgressState(
    val operation: BackupOperationType = BackupOperationType.NONE,
    val status: BackupStatus = BackupStatus.IDLE,
    val targetFileName: String = "",
    val totalRecords: Long = 0L,
    val processedRecords: Long = 0L,
    val processedBytes: Long = 0L,
    val recordsPerSecond: Long = 0L,
    val mbPerSecond: Float = 0f,
    val progressFraction: Float = 0f,
    val activeElapsedMs: Long = 0L,
    val moduleBreakdown: Map<String, Long> = emptyMap(),
    val statusLog: String = "STANDBY // BACKUP & RESTORE MATRIX ONLINE",
    val errorMessage: String? = null
)

class NexusDatabaseBackupEngine(
    private val context: Context,
    private val database: AppDatabase,
    private val externalScope: CoroutineScope
) {
    companion object {
        private const val MAGIC_HEADER_STRING = "NEXUSBK2"
        private const val BACKUP_VERSION = 2
        private const val BATCH_CHUNK_SIZE = 5_000
        private const val BUFFER_STREAM_SIZE = 524_288 // 512 KB high-throughput buffer

        fun generateDefaultBackupFileName(): String {
            val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            return "nexus_hud_backup_$dateStr.nexusbak"
        }
    }

    private val _state = MutableStateFlow(BackupProgressState())
    val state: StateFlow<BackupProgressState> = _state.asStateFlow()

    private var activeJob: Job? = null

    fun cancelOperation() {
        activeJob?.cancel()
        _state.update {
            it.copy(
                status = BackupStatus.CANCELLED,
                recordsPerSecond = 0L,
                mbPerSecond = 0f,
                statusLog = "OPERATOR ABORTED BACKUP PIPELINE"
            )
        }
    }

    fun dismissCompleted() {
        _state.update {
            BackupProgressState(
                operation = BackupOperationType.NONE,
                status = BackupStatus.IDLE,
                statusLog = "STANDBY // BACKUP & RESTORE MATRIX ONLINE"
            )
        }
    }

    /**
     * Exports all identity databases (BankMeli, BankMelat, BankSaderat, Citizen, Irancell, Telegram)
     * into a single compressed, high-speed unified archive file (.nexusbak).
     */
    fun exportAllToBackup(destinationUri: Uri, fileName: String = "nexus_hud_backup.nexusbak") {
        activeJob?.cancel()

        activeJob = externalScope.launch(Dispatchers.IO) {
            val startTimeMs = System.currentTimeMillis()
            var lastUiUpdateMs = startTimeMs
            var exportedRecords = 0L
            var bytesWrittenEstimated = 0L

            try {
                val dao = database.identityDao()
                val totalRecords = dao.getTotalRecordCount()

                _state.value = BackupProgressState(
                    operation = BackupOperationType.EXPORT,
                    status = BackupStatus.RUNNING,
                    targetFileName = fileName,
                    totalRecords = totalRecords,
                    processedRecords = 0L,
                    statusLog = "INITIALIZING EXPORT PIPELINE // $totalRecords RECORDS TO COMPRESS"
                )

                if (totalRecords == 0L) {
                    _state.value = BackupProgressState(
                        operation = BackupOperationType.EXPORT,
                        status = BackupStatus.COMPLETED,
                        targetFileName = fileName,
                        totalRecords = 0L,
                        processedRecords = 0L,
                        progressFraction = 1f,
                        statusLog = "DATABASE EMPTY // NO RECORDS TO BACKUP"
                    )
                    return@launch
                }

                val outputStream = context.contentResolver.openOutputStream(destinationUri, "w")
                    ?: throw IllegalStateException("Failed to open backup destination stream.")

                val countsBreakdown = HashMap<String, Long>()

                BufferedOutputStream(outputStream, BUFFER_STREAM_SIZE).use { rawBuffered ->
                    val dataOut = DataOutputStream(rawBuffered)
                    // Magic header (uncompressed pre-amble for fast format detection)
                    dataOut.write(MAGIC_HEADER_STRING.toByteArray(Charsets.US_ASCII))
                    dataOut.writeInt(BACKUP_VERSION)
                    dataOut.writeLong(System.currentTimeMillis())
                    dataOut.writeLong(totalRecords)
                    dataOut.flush()

                    // Gzip compressed payload
                    GZIPOutputStream(rawBuffered, BUFFER_STREAM_SIZE).use { gzipOut ->
                        val payloadOut = DataOutputStream(gzipOut)

                        var lastRowId = 0L
                        while (exportedRecords < totalRecords) {
                            ensureActive()

                            val chunk = dao.getRecordsPaged(lastRowId, BATCH_CHUNK_SIZE)
                            if (chunk.isEmpty()) break

                            for (r in chunk) {
                                payloadOut.writeUTF(r.moduleId)
                                payloadOut.writeUTF(r.fullName)
                                payloadOut.writeUTF(r.nationalCode)
                                payloadOut.writeUTF(r.phone)
                                payloadOut.writeUTF(r.accountOrHandle)
                                payloadOut.writeUTF(r.cardNumber)
                                payloadOut.writeUTF(r.fatherName)
                                payloadOut.writeUTF(r.idNumber)
                                payloadOut.writeUTF(r.birthDate)
                                payloadOut.writeUTF(r.city)
                                payloadOut.writeUTF(r.province)
                                payloadOut.writeUTF(r.birthCity)
                                payloadOut.writeUTF(r.birthProvince)
                                payloadOut.writeUTF(r.postalCode)
                                payloadOut.writeUTF(r.address)
                                payloadOut.writeUTF(r.email)
                                payloadOut.writeUTF(r.username)
                                payloadOut.writeUTF(r.memberCode)
                                payloadOut.writeUTF(r.branchId)
                                payloadOut.writeUTF(r.defNumOld)
                                payloadOut.writeUTF(r.passwordHint)
                                payloadOut.writeUTF(r.passwValue)
                                payloadOut.writeUTF(r.rawDetails)
                                payloadOut.writeLong(r.gpuSignatureHash)
                                payloadOut.writeLong(r.importedAt)

                                countsBreakdown[r.moduleId] = (countsBreakdown[r.moduleId] ?: 0L) + 1L
                                lastRowId = r.id
                            }

                            exportedRecords += chunk.size
                            bytesWrittenEstimated += chunk.size * 180L

                            val now = System.currentTimeMillis()
                            if (now - lastUiUpdateMs >= 150L || exportedRecords >= totalRecords) {
                                val elapsedSec = ((now - startTimeMs).toDouble() / 1000.0).coerceAtLeast(0.001)
                                val rps = (exportedRecords / elapsedSec).toLong()
                                val mbps = ((bytesWrittenEstimated / (1024.0 * 1024.0)) / elapsedSec).toFloat()
                                val frac = if (totalRecords > 0) (exportedRecords.toFloat() / totalRecords) else 0f

                                _state.value = _state.value.copy(
                                    processedRecords = exportedRecords,
                                    processedBytes = bytesWrittenEstimated,
                                    recordsPerSecond = rps,
                                    mbPerSecond = mbps,
                                    progressFraction = frac.coerceIn(0f, 1f),
                                    activeElapsedMs = now - startTimeMs,
                                    moduleBreakdown = HashMap(countsBreakdown),
                                    statusLog = "STREAMING COMPRESSED ARCHIVE: $exportedRecords / $totalRecords @ $rps REC/SEC"
                                )
                                lastUiUpdateMs = now
                            }
                        }
                        payloadOut.flush()
                    }
                }

                val totalElapsedMs = (System.currentTimeMillis() - startTimeMs).coerceAtLeast(1L)
                val finalSec = totalElapsedMs / 1000.0
                val finalRps = (exportedRecords / finalSec).toLong()

                _state.value = BackupProgressState(
                    operation = BackupOperationType.EXPORT,
                    status = BackupStatus.COMPLETED,
                    targetFileName = fileName,
                    totalRecords = totalRecords,
                    processedRecords = exportedRecords,
                    processedBytes = bytesWrittenEstimated,
                    recordsPerSecond = finalRps,
                    mbPerSecond = ((bytesWrittenEstimated / (1024.0 * 1024.0)) / finalSec).toFloat(),
                    progressFraction = 1f,
                    activeElapsedMs = totalElapsedMs,
                    moduleBreakdown = countsBreakdown,
                    statusLog = "BACKUP COMPLETED // $exportedRecords IDENTITIES EXPORTED AT $finalRps REC/SEC"
                )

            } catch (ce: CancellationException) {
                _state.value = _state.value.copy(
                    status = BackupStatus.CANCELLED,
                    statusLog = "EXPORT CANCELLED BY USER"
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    status = BackupStatus.ERROR,
                    errorMessage = t.localizedMessage ?: t.javaClass.simpleName,
                    statusLog = "EXPORT FAILED // ${t.localizedMessage ?: "I/O Fault"}"
                )
            }
        }
    }

    /**
     * Fast imports all databases from a single backup file (.nexusbak) into SQLite Room
     * with high-speed WAL batching and immediate FTS4 optimization.
     *
     * @param cleanRestore if true, clears existing records first for a fresh 1:1 state.
     *                     if false, merges with existing database records.
     */
    fun fastImportFromBackup(sourceUri: Uri, cleanRestore: Boolean = false) {
        activeJob?.cancel()

        activeJob = externalScope.launch(Dispatchers.IO) {
            val startTimeMs = System.currentTimeMillis()
            var lastUiUpdateMs = startTimeMs
            var restoredRecords = 0L

            try {
                val (resolvedName, fileSizeBytes) = resolveUriMeta(sourceUri)

                _state.value = BackupProgressState(
                    operation = BackupOperationType.RESTORE,
                    status = BackupStatus.RUNNING,
                    targetFileName = resolvedName,
                    totalRecords = 0L,
                    processedRecords = 0L,
                    processedBytes = fileSizeBytes,
                    statusLog = "MOUNTING ARCHIVE [$resolvedName] // DETECTING ENCODING"
                )

                val inputStream = context.contentResolver.openInputStream(sourceUri)
                    ?: throw IllegalStateException("Cannot open selected backup file.")

                val dao = database.identityDao()

                if (cleanRestore) {
                    _state.value = _state.value.copy(statusLog = "CLEANING LOCAL PARTITIONS FOR FRESH RESTORE…")
                    dao.clearAllRecords()
                }

                val countsBreakdown = HashMap<String, Long>()

                BufferedInputStream(inputStream, BUFFER_STREAM_SIZE).use { rawBuffered ->
                    val dataIn = DataInputStream(rawBuffered)

                    val magicBytes = ByteArray(8)
                    dataIn.readFully(magicBytes)
                    val magicStr = String(magicBytes, Charsets.US_ASCII)

                    if (magicStr != MAGIC_HEADER_STRING) {
                        throw IllegalArgumentException("Unsupported or corrupted backup file format (Header mismatch: $magicStr).")
                    }

                    val version = dataIn.readInt()
                    val createdAt = dataIn.readLong()
                    val totalRecordsInArchive = dataIn.readLong()

                    _state.value = _state.value.copy(
                        totalRecords = totalRecordsInArchive,
                        statusLog = "INGESTING $totalRecordsInArchive IDENTITIES FROM ARCHIVE (V$version)…"
                    )

                    // Read GZIP payload
                    GZIPInputStream(rawBuffered, BUFFER_STREAM_SIZE).use { gzipIn ->
                        val payloadIn = DataInputStream(gzipIn)
                        val batchList = ArrayList<IdentityEntity>(BATCH_CHUNK_SIZE)

                        for (i in 0 until totalRecordsInArchive) {
                            ensureActive()

                            val modId = payloadIn.readUTF()
                            val fullName = payloadIn.readUTF()
                            val nationalCode = payloadIn.readUTF()
                            val phone = payloadIn.readUTF()
                            val accountOrHandle = payloadIn.readUTF()
                            val cardNumber = payloadIn.readUTF()
                            val fatherName = payloadIn.readUTF()
                            val idNumber = payloadIn.readUTF()
                            val birthDate = payloadIn.readUTF()
                            val city = payloadIn.readUTF()
                            val province = payloadIn.readUTF()
                            val birthCity = payloadIn.readUTF()
                            val birthProvince = payloadIn.readUTF()
                            val postalCode = payloadIn.readUTF()
                            val address = payloadIn.readUTF()
                            val email = payloadIn.readUTF()
                            val username = payloadIn.readUTF()
                            val memberCode = payloadIn.readUTF()
                            val branchId = payloadIn.readUTF()
                            val defNumOld = payloadIn.readUTF()
                            val passwordHint = payloadIn.readUTF()
                            val passwValue = payloadIn.readUTF()
                            val rawDetails = payloadIn.readUTF()
                            val gpuSig = payloadIn.readLong()
                            val importedAt = payloadIn.readLong()

                            val entity = IdentityEntity(
                                moduleId = modId,
                                fullName = fullName,
                                nationalCode = nationalCode,
                                phone = phone,
                                accountOrHandle = accountOrHandle,
                                cardNumber = cardNumber,
                                fatherName = fatherName,
                                idNumber = idNumber,
                                birthDate = birthDate,
                                city = city,
                                province = province,
                                birthCity = birthCity,
                                birthProvince = birthProvince,
                                postalCode = postalCode,
                                address = address,
                                email = email,
                                username = username,
                                memberCode = memberCode,
                                branchId = branchId,
                                defNumOld = defNumOld,
                                passwordHint = passwordHint,
                                passwValue = passwValue,
                                rawDetails = rawDetails,
                                gpuSignatureHash = gpuSig,
                                importedAt = importedAt
                            )
                            batchList.add(entity)
                            countsBreakdown[modId] = (countsBreakdown[modId] ?: 0L) + 1L

                            if (batchList.size >= BATCH_CHUNK_SIZE) {
                                dao.insertBatch(batchList)
                                restoredRecords += batchList.size
                                batchList.clear()

                                val now = System.currentTimeMillis()
                                if (now - lastUiUpdateMs >= 150L) {
                                    val elapsedSec = ((now - startTimeMs).toDouble() / 1000.0).coerceAtLeast(0.001)
                                    val rps = (restoredRecords / elapsedSec).toLong()
                                    val frac = if (totalRecordsInArchive > 0) (restoredRecords.toFloat() / totalRecordsInArchive) else 0f

                                    _state.value = _state.value.copy(
                                        processedRecords = restoredRecords,
                                        recordsPerSecond = rps,
                                        progressFraction = frac.coerceIn(0f, 1f),
                                        activeElapsedMs = now - startTimeMs,
                                        moduleBreakdown = HashMap(countsBreakdown),
                                        statusLog = "FAST RESTORING: $restoredRecords / $totalRecordsInArchive @ $rps REC/SEC"
                                    )
                                    lastUiUpdateMs = now
                                }
                            }
                        }

                        if (batchList.isNotEmpty()) {
                            dao.insertBatch(batchList)
                            restoredRecords += batchList.size
                            batchList.clear()
                        }
                    }
                }

                _state.value = _state.value.copy(statusLog = "OPTIMIZING FTS4 FULL-TEXT INVERTED INDEX…")
                try {
                    dao.optimizeFtsIndex()
                } catch (_: Exception) {
                    // Safe ignore
                }

                val totalDurationMs = (System.currentTimeMillis() - startTimeMs).coerceAtLeast(1L)
                val finalSecs = totalDurationMs / 1000.0
                val finalRps = (restoredRecords / finalSecs).toLong()

                _state.value = BackupProgressState(
                    operation = BackupOperationType.RESTORE,
                    status = BackupStatus.COMPLETED,
                    targetFileName = resolvedName,
                    totalRecords = restoredRecords,
                    processedRecords = restoredRecords,
                    recordsPerSecond = finalRps,
                    progressFraction = 1f,
                    activeElapsedMs = totalDurationMs,
                    moduleBreakdown = countsBreakdown,
                    statusLog = "FAST RESTORE COMPLETE // $restoredRecords IDENTITIES RESTORED AT $finalRps REC/SEC"
                )

            } catch (ce: CancellationException) {
                _state.value = _state.value.copy(
                    status = BackupStatus.CANCELLED,
                    statusLog = "RESTORE OPERATION CANCELLED"
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    status = BackupStatus.ERROR,
                    errorMessage = t.localizedMessage ?: t.javaClass.simpleName,
                    statusLog = "RESTORE FAILED // ${t.localizedMessage ?: "Archive Stream Error"}"
                )
            }
        }
    }

    private fun resolveUriMeta(uri: Uri): Pair<String, Long> {
        var name = "nexus_hud_backup.nexusbak"
        var size = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
                    if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
                }
            }
        } catch (_: Exception) {}
        return name to size
    }
}
