package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Dao
interface IdentityDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(records: List<IdentityEntity>)

    @Query("SELECT moduleId, COUNT(*) as count FROM identity_records GROUP BY moduleId")
    fun observeCountsByModule(): Flow<List<ModuleCountTuple>>

    @Query("SELECT COUNT(*) FROM identity_records WHERE moduleId = :moduleId")
    fun observeModuleCount(moduleId: String): Flow<Long>

    @Query("SELECT COUNT(*) FROM identity_records WHERE moduleId = :moduleId")
    suspend fun getModuleCount(moduleId: String): Long

    @Query(
        """
        SELECT * FROM identity_records 
        WHERE moduleId = :moduleId 
        ORDER BY rowid DESC 
        LIMIT :limit
        """
    )
    suspend fun getRecentRecords(moduleId: String, limit: Int = 80): List<IdentityEntity>

    @Query(
        """
        SELECT * FROM identity_records
        WHERE moduleId = :moduleId
          AND rowid IN (
            SELECT rowid FROM identity_records_fts
            WHERE identity_records_fts MATCH :ftsQuery
          )
        LIMIT :limit
        """
    )
    suspend fun searchFtsInModule(
        moduleId: String,
        ftsQuery: String,
        limit: Int = 150
    ): List<IdentityEntity>

    @Query(
        """
        SELECT * FROM identity_records
        WHERE moduleId = :moduleId
          AND (
            nationalCode = :exactQuery
            OR phone = :exactQuery
            OR accountOrHandle = :exactQuery
            OR cardNumber = :exactQuery
            OR username = :exactQuery
            OR username = :atExactQuery
          )
        LIMIT :limit
        """
    )
    suspend fun searchExactIndexedInModule(
        moduleId: String,
        exactQuery: String,
        atExactQuery: String = "@$exactQuery",
        limit: Int = 150
    ): List<IdentityEntity>

    @Query(
        """
        SELECT * FROM identity_records
        WHERE moduleId = :moduleId
          AND (
            (nationalCode >= :prefix AND nationalCode < :prefixUpper)
            OR (phone >= :prefix AND phone < :prefixUpper)
            OR (accountOrHandle >= :prefix AND accountOrHandle < :prefixUpper)
            OR (cardNumber >= :prefix AND cardNumber < :prefixUpper)
            OR (username >= :prefix AND username < :prefixUpper)
            OR (username >= :atPrefix AND username < :atPrefixUpper)
          )
        LIMIT :limit
        """
    )
    suspend fun searchPrefixIndexedInModule(
        moduleId: String,
        prefix: String,
        prefixUpper: String = prefix + "\uFFFF",
        atPrefix: String = "@$prefix",
        atPrefixUpper: String = "@$prefix\uFFFF",
        limit: Int = 150
    ): List<IdentityEntity>

    @RawQuery
    suspend fun searchMultiTokenFallback(query: SupportSQLiteQuery): List<IdentityEntity>

    @Query("DELETE FROM identity_records WHERE moduleId = :moduleId")
    suspend fun clearModuleRecords(moduleId: String)

    @Query("INSERT INTO identity_records_fts(identity_records_fts) VALUES('rebuild')")
    suspend fun rebuildFtsIndex()

    @Query("INSERT INTO identity_records_fts(identity_records_fts) VALUES('optimize')")
    suspend fun optimizeFtsIndex()
}
