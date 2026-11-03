package com.example.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "identity_records",
    indices = [
        Index(value = ["moduleId"]),
        Index(value = ["moduleId", "nationalCode"]),
        Index(value = ["moduleId", "phone"]),
        Index(value = ["moduleId", "accountOrHandle"]),
        Index(value = ["moduleId", "cardNumber"])
    ]
)
data class IdentityEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "rowid")
    val id: Long = 0L,
    val moduleId: String,
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
    val rawDetails: String = "",
    val gpuSignatureHash: Long,
    val importedAt: Long
)

@Fts4(contentEntity = IdentityEntity::class)
@Entity(tableName = "identity_records_fts")
data class IdentityFtsEntity(
    val moduleId: String,
    val fullName: String,
    val nationalCode: String,
    val phone: String,
    val accountOrHandle: String,
    val cardNumber: String,
    val fatherName: String,
    val idNumber: String,
    val birthDate: String,
    val city: String,
    val province: String,
    val birthCity: String,
    val birthProvince: String,
    val postalCode: String,
    val address: String,
    val rawDetails: String
)

data class ModuleCountTuple(
    val moduleId: String,
    val count: Long
)
