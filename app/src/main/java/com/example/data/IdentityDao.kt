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
    suspend fun getRecentRecords(moduleId: String, limit: Int = 120): List<IdentityEntity>

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
        limit: Int = 200
    ): List<IdentityEntity>

    @Query(
        """
        SELECT COUNT(*) FROM identity_records
        WHERE moduleId = :moduleId
          AND rowid IN (
            SELECT rowid FROM identity_records_fts
            WHERE identity_records_fts MATCH :ftsQuery
          )
        """
    )
    suspend fun countFtsMatchesInModule(
        moduleId: String,
        ftsQuery: String
    ): Long

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
            OR username = '@' || :exactQuery
            OR idNumber = :exactQuery
            OR postalCode = :exactQuery
          )
        LIMIT :limit
        """
    )
    suspend fun searchExactIndexedInModule(
        moduleId: String,
        exactQuery: String,
        limit: Int = 200
    ): List<IdentityEntity>

    @RawQuery
    suspend fun searchMultiTokenFallback(query: SupportSQLiteQuery): List<IdentityEntity>

    @RawQuery
    suspend fun countMultiTokenFallback(query: SupportSQLiteQuery): Long

    @Query("DELETE FROM identity_records WHERE moduleId = :moduleId")
    suspend fun clearModuleRecords(moduleId: String)

    @Query("INSERT INTO identity_records_fts(identity_records_fts) VALUES('rebuild')")
    suspend fun rebuildFtsIndex()
}
