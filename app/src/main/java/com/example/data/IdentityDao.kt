package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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
        SELECT identity_records.* FROM identity_records
        JOIN identity_records_fts ON identity_records.rowid = identity_records_fts.rowid
        WHERE identity_records.moduleId = :moduleId
          AND identity_records_fts MATCH :ftsQuery
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
        JOIN identity_records_fts ON identity_records.rowid = identity_records_fts.rowid
        WHERE identity_records.moduleId = :moduleId
          AND identity_records_fts MATCH :ftsQuery
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
            nationalCode LIKE '%' || :rawQuery || '%'
            OR phone LIKE '%' || :rawQuery || '%'
            OR fullName LIKE '%' || :rawQuery || '%'
            OR accountOrHandle LIKE '%' || :rawQuery || '%'
            OR cardNumber LIKE '%' || :rawQuery || '%'
            OR postalCode LIKE '%' || :rawQuery || '%'
            OR address LIKE '%' || :rawQuery || '%'
            OR fatherName LIKE '%' || :rawQuery || '%'
            OR idNumber LIKE '%' || :rawQuery || '%'
          )
        LIMIT :limit
        """
    )
    suspend fun searchSubstringFallback(
        moduleId: String,
        rawQuery: String,
        limit: Int = 200
    ): List<IdentityEntity>

    @Query("DELETE FROM identity_records WHERE moduleId = :moduleId")
    suspend fun clearModuleRecords(moduleId: String)

    @Query("INSERT INTO identity_records_fts(identity_records_fts) VALUES('rebuild')")
    suspend fun rebuildFtsIndex()
}
