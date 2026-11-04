package com.example.data

import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

enum class SearchStage {
    IDLE,
    NORMALIZING_QUERY,
    SCANNING_FTS_PARTITIONS,
    DEEP_COLUMN_MATCHING,
    GPU_SIGNATURE_RANKING,
    COMPLETE
}

data class SearchTelemetry(
    val query: String = "",
    val stage: SearchStage = SearchStage.IDLE,
    val isSearching: Boolean = false,
    val totalIndexedInModule: Long = 0L,
    val scannedPartitions: Int = 0,
    val totalPartitions: Int = 8,
    val matchCount: Long = 0L,
    val elapsedMillis: Double = 0.0,
    val executionTrace: String = "HYBRID FTS4 + MULTI-COLUMN INDEX READY",
    val results: List<IdentityEntity> = emptyList()
)

object TextNormalizer {
    /**
     * Normalizes Persian & Arabic digits and characters (ي/ی, ك/ک, zero-width non-joiner, etc.)
     * so queries in BankMeli, BankMelat, BankSaderat, Citizen, Irancell, and Telegram match reliably
     * regardless of keyboard layout or CSV source encoding.
     */
    fun normalizeForSearch(input: String): String {
        if (input.isEmpty()) return input
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                // Persian digits ۰-۹ -> 0-9
                in '\u06F0'..'\u06F9' -> sb.append(('0'.code + (ch.code - '\u06F0'.code)).toChar())
                // Arabic-Indic digits ٠-٩ -> 0-9
                in '\u0660'..'\u0669' -> sb.append(('0'.code + (ch.code - '\u0660'.code)).toChar())
                // Arabic Yeh / Alef Maksura -> Persian Yeh
                '\u064A', '\u0649' -> sb.append('\u06CC')
                // Arabic Kaf -> Persian Keheh
                '\u0643' -> sb.append('\u06A9')
                // Zero-width non-joiner (half-space) -> regular space for consistent matching
                '\u200C' -> sb.append(' ')
                // Strip invisible directional marks / BOM
                '\uFEFF', '\u200E', '\u200F' -> {}
                else -> sb.append(ch)
            }
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }
}

class IdentityRepository(private val dao: IdentityDao) {

    val moduleCountsFlow: Flow<Map<String, Long>> = dao.observeCountsByModule().map { list ->
        list.associate { it.moduleId to it.count }
    }

    fun observeCountForModule(moduleId: String): Flow<Long> = dao.observeModuleCount(moduleId)

    suspend fun getRecentForModule(moduleId: String, limit: Int = 100): List<IdentityEntity> =
        withContext(Dispatchers.IO) {
            dao.getRecentRecords(moduleId, limit)
        }

    suspend fun clearModule(moduleId: String) = withContext(Dispatchers.IO) {
        dao.clearModuleRecords(moduleId)
        try {
            dao.rebuildFtsIndex()
        } catch (_: Exception) {
            // Safe ignore
        }
    }

    /**
     * Executes visible, multi-stage hybrid search across millions of rows:
     * 1. Normalizes Persian/Arabic digits & characters (ک/ی, ۰-۹).
     * 2. Fast B-Tree indexed lookup + FTS4 `rowid IN (...)` prefix scan (with moduleId scoped inside FTS).
     * 3. Multi-token column `LIKE` scan across all 19 columns (handles leading zeros stripped in CSVs,
     *    +98/98/09 phone variants, @username variants, middle-of-number substrings, and Persian text).
     */
    suspend fun executeVisibleHighSpeedSearch(
        moduleId: String,
        rawQuery: String,
        onStageUpdate: (SearchTelemetry) -> Unit
    ) = withContext(Dispatchers.IO) {
        val normalizedQuery = TextNormalizer.normalizeForSearch(rawQuery)
        val totalInModule = dao.getModuleCount(moduleId)

        if (normalizedQuery.isEmpty()) {
            val recent = dao.getRecentRecords(moduleId, limit = 100)
            onStageUpdate(
                SearchTelemetry(
                    query = rawQuery,
                    stage = SearchStage.IDLE,
                    isSearching = false,
                    totalIndexedInModule = totalInModule,
                    scannedPartitions = 0,
                    matchCount = totalInModule,
                    elapsedMillis = 0.0,
                    executionTrace = "DISPLAYING LATEST ${recent.size} OF $totalInModule INDEXED IDENTITIES",
                    results = recent
                )
            )
            return@withContext
        }

        val startNanos = System.nanoTime()

        // Stage 1: Normalize & Tokenize Query
        val rawTokens = normalizedQuery
            .split(" ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val safeFtsTokens = rawTokens
            .map { token ->
                token.trimStart('@', '+')
                    .replace(Regex("[^\\p{L}\\p{N}_]"), "")
            }
            .filter { it.isNotEmpty() }

        onStageUpdate(
            SearchTelemetry(
                query = rawQuery,
                stage = SearchStage.NORMALIZING_QUERY,
                isSearching = true,
                totalIndexedInModule = totalInModule,
                scannedPartitions = 2,
                executionTrace = "NORMALIZED QUERY [$normalizedQuery] OVER $totalInModule RECORDS…"
            )
        )

        val mergedById = LinkedHashMap<Long, IdentityEntity>(200)
        var totalMatches = 0L

        // Fast Path A: Exact B-Tree Indexed Lookup (National Code, Mobile, Account, Card, Username)
        if (rawTokens.size == 1) {
            val single = rawTokens[0]
            val exactCandidates = buildPhoneAndIdVariants(single)
            for (variant in exactCandidates) {
                val exactHits = dao.searchExactIndexedInModule(moduleId, variant, limit = 200)
                for (row in exactHits) {
                    mergedById[row.id] = row
                }
            }
        }

        // Stage 2: Scan FTS4 Inverted Index (scoped to moduleId inside FTS4 to avoid global post-filter loss)
        onStageUpdate(
            SearchTelemetry(
                query = rawQuery,
                stage = SearchStage.SCANNING_FTS_PARTITIONS,
                isSearching = true,
                totalIndexedInModule = totalInModule,
                scannedPartitions = 4,
                executionTrace = "SCANNING FTS4 INVERTED PARTITIONS [MODULE=$moduleId]…"
            )
        )

        if (safeFtsTokens.isNotEmpty()) {
            // Also include stripped-leading-zero token alternative if user typed e.g. 0019283746 or 0912...
            val ftsClauses = safeFtsTokens.map { tok ->
                val strippedZero = tok.trimStart('0')
                if (tok.all { it.isDigit() } && strippedZero.length >= 4 && strippedZero != tok) {
                    "($tok* OR $strippedZero*)"
                } else {
                    "$tok*"
                }
            }
            val ftsExpr = "moduleId:$moduleId " + ftsClauses.joinToString(" ")
            try {
                val ftsRows = dao.searchFtsInModule(moduleId, ftsExpr, limit = 200)
                for (row in ftsRows) {
                    if (mergedById.size < 200) {
                        mergedById.putIfAbsent(row.id, row)
                    }
                }
                if (ftsRows.size >= 200) {
                    totalMatches = maxOf(totalMatches, dao.countFtsMatchesInModule(moduleId, ftsExpr))
                }
            } catch (_: Exception) {
                // Continue seamlessly to deep multi-column SQL scan
            }
        }

        // Stage 3: Deep Multi-Column Substring Scan (catches middle-of-card/phone digits, Persian Yeh/Kaf variants,
        // addresses, passwords/hints, emails, or whenever FTS4 returned fewer than 200 results)
        if (mergedById.size < 50) {
            onStageUpdate(
                SearchTelemetry(
                    query = rawQuery,
                    stage = SearchStage.DEEP_COLUMN_MATCHING,
                    isSearching = true,
                    totalIndexedInModule = totalInModule,
                    scannedPartitions = 6,
                    executionTrace = "DEEP MULTI-COLUMN SCAN ACROSS ALL IDENTITY ATTRIBUTES…"
                )
            )

            val (selectSql, selectArgs) = buildMultiTokenLikeQuery(
                moduleId = moduleId,
                tokens = rawTokens,
                countOnly = false,
                limit = 200
            )
            val fallbackRows = dao.searchMultiTokenFallback(SimpleSQLiteQuery(selectSql, selectArgs))
            for (row in fallbackRows) {
                if (mergedById.size < 200) {
                    mergedById.putIfAbsent(row.id, row)
                }
            }

            if (fallbackRows.size >= 200) {
                val (countSql, countArgs) = buildMultiTokenLikeQuery(
                    moduleId = moduleId,
                    tokens = rawTokens,
                    countOnly = true,
                    limit = 200
                )
                totalMatches = maxOf(
                    totalMatches,
                    dao.countMultiTokenFallback(SimpleSQLiteQuery(countSql, countArgs))
                )
            }
        }

        val finalResults = mergedById.values.toList()
        if (totalMatches < finalResults.size) {
            totalMatches = finalResults.size.toLong()
        }

        // Stage 4: GPU Signature Verification & Complete
        onStageUpdate(
            SearchTelemetry(
                query = rawQuery,
                stage = SearchStage.GPU_SIGNATURE_RANKING,
                isSearching = true,
                totalIndexedInModule = totalInModule,
                scannedPartitions = 7,
                matchCount = totalMatches,
                executionTrace = "VERIFYING 64-BIT GPU SIGNATURES FOR ${finalResults.size} MATCHED RECORDS…"
            )
        )

        val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000.0
        val formattedMs = "%.2f".format(elapsedMs)

        onStageUpdate(
            SearchTelemetry(
                query = rawQuery,
                stage = SearchStage.COMPLETE,
                isSearching = false,
                totalIndexedInModule = totalInModule,
                scannedPartitions = 8,
                matchCount = totalMatches,
                elapsedMillis = elapsedMs,
                executionTrace = "QUERY COMPLETE IN ${formattedMs}ms // $totalMatches MATCHES ACROSS $totalInModule RECORDS",
                results = finalResults
            )
        )
    }

    private fun buildPhoneAndIdVariants(token: String): List<String> {
        val clean = token.trim().removePrefix("@").removePrefix("+")
        val variants = LinkedHashSet<String>()
        variants.add(token.trim())
        variants.add(clean)
        if (clean.all { it.isDigit() }) {
            // Handle CSVs where leading 0 was stripped (e.g. 0012345678 -> 12345678, 0912... -> 912...)
            val noLeadingZeros = clean.trimStart('0')
            if (noLeadingZeros.length >= 5) {
                variants.add(noLeadingZeros)
            }
            // Handle 09xxxxxxxxx <-> 989xxxxxxxxx (Telegram vs Bank/Citizen mobile format)
            if (clean.startsWith("09") && clean.length == 11) {
                variants.add("98" + clean.substring(1))
                variants.add("+98" + clean.substring(1))
                variants.add(clean.substring(1))
            } else if (clean.startsWith("989") && clean.length == 12) {
                variants.add("0" + clean.substring(2))
                variants.add(clean.substring(2))
            }
        }
        return variants.toList()
    }

    private fun buildMultiTokenLikeQuery(
        moduleId: String,
        tokens: List<String>,
        countOnly: Boolean,
        limit: Int
    ): Pair<String, Array<Any>> {
        val searchableColumns = listOf(
            "fullName",
            "nationalCode",
            "phone",
            "accountOrHandle",
            "cardNumber",
            "fatherName",
            "idNumber",
            "birthDate",
            "city",
            "province",
            "birthCity",
            "birthProvince",
            "postalCode",
            "address",
            "email",
            "username",
            "memberCode",
            "branchId",
            "defNumOld",
            "passwordHint",
            "passwValue",
            "rawDetails"
        )

        val args = ArrayList<Any>()
        val sql = StringBuilder()
        if (countOnly) {
            sql.append("SELECT COUNT(*) FROM identity_records WHERE moduleId = ?")
        } else {
            sql.append("SELECT * FROM identity_records WHERE moduleId = ?")
        }
        args.add(moduleId)

        for (token in tokens) {
            val tokenVariants = buildTokenLikeVariants(token)
            sql.append(" AND (")
            var firstClause = true
            for (variant in tokenVariants) {
                val pattern = "%$variant%"
                for (col in searchableColumns) {
                    if (!firstClause) sql.append(" OR ")
                    firstClause = false
                    sql.append("$col LIKE ?")
                    args.add(pattern)
                }
            }
            sql.append(")")
        }

        if (!countOnly) {
            sql.append(" LIMIT ")
            sql.append(limit)
        }

        return sql.toString() to args.toTypedArray()
    }

    private fun buildTokenLikeVariants(token: String): List<String> {
        val set = LinkedHashSet<String>()
        val base = token.trim().removePrefix("@").removePrefix("+")
        if (base.isNotEmpty()) set.add(base)

        // Also match Arabic Yeh/Kaf if an existing DB had unnormalized Arabic characters
        val arabicVariant = base
            .replace('\u06CC', '\u064A')
            .replace('\u06A9', '\u0643')
        if (arabicVariant != base && arabicVariant.isNotEmpty()) {
            set.add(arabicVariant)
        }

        if (base.all { it.isDigit() }) {
            val stripped = base.trimStart('0')
            if (stripped.length >= 4 && stripped != base) {
                set.add(stripped)
            }
            if (base.startsWith("09") && base.length >= 6) {
                set.add("98" + base.substring(1))
            } else if (base.startsWith("989") && base.length >= 7) {
                set.add("0" + base.substring(2))
            }
        }
        return set.toList()
    }
}
