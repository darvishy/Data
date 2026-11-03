package com.example.model

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.R
import com.example.ui.theme.HudAmber
import com.example.ui.theme.HudCoral
import com.example.ui.theme.HudCyan
import com.example.ui.theme.HudElectricBlue
import com.example.ui.theme.HudLime
import com.example.ui.theme.HudMagenta
import com.example.ui.theme.HudTeal
import com.example.ui.theme.HudViolet

enum class HudModule(
    val id: String,
    val code: String,
    @StringRes val labelRes: Int,
    val displayLabel: String,
    val sectorTag: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val icon: ImageVector
) {
    BANK_MELI(
        id = "bank_meli",
        code = "NODE-01",
        labelRes = R.string.node_1,
        displayLabel = "1-BankMeli",
        sectorTag = "FIN-CORE // MELI",
        primaryColor = HudCyan,
        secondaryColor = HudElectricBlue,
        icon = Icons.Filled.AccountBalance
    ),
    BANK_MELAT(
        id = "bank_melat",
        code = "NODE-02",
        labelRes = R.string.node_2,
        displayLabel = "2-BankMelat",
        sectorTag = "FIN-NET // MELAT",
        primaryColor = HudMagenta,
        secondaryColor = HudCoral,
        icon = Icons.Filled.CreditCard
    ),
    BANK_SADERAT(
        id = "bank_saderat",
        code = "NODE-03",
        labelRes = R.string.node_3,
        displayLabel = "3BankSaderat",
        sectorTag = "FIN-GRID // SADERAT",
        primaryColor = HudElectricBlue,
        secondaryColor = HudCyan,
        icon = Icons.Filled.CurrencyExchange
    ),
    BANK_TEJARAT(
        id = "bank_tejarat",
        code = "NODE-04",
        labelRes = R.string.node_4,
        displayLabel = "4-BankTejarat",
        sectorTag = "FIN-TRADE // TEJARAT",
        primaryColor = HudAmber,
        secondaryColor = HudCoral,
        icon = Icons.Filled.Savings
    ),
    BANK_SEPAH(
        id = "bank_sepah",
        code = "NODE-05",
        labelRes = R.string.node_5,
        displayLabel = "5-BankSepah",
        sectorTag = "FIN-VAULT // SEPAH",
        primaryColor = HudCoral,
        secondaryColor = HudAmber,
        icon = Icons.Filled.Shield
    ),
    CITIZEN(
        id = "citizen",
        code = "NODE-06",
        labelRes = R.string.node_6,
        displayLabel = "6-Citizen",
        sectorTag = "CIV-REG // CITIZEN",
        primaryColor = HudLime,
        secondaryColor = HudTeal,
        icon = Icons.Filled.Badge
    ),
    IRANCELL(
        id = "irancell",
        code = "NODE-07",
        labelRes = R.string.node_7,
        displayLabel = "7-Irancell",
        sectorTag = "TEL-CELL // IRANCELL",
        primaryColor = HudViolet,
        secondaryColor = HudMagenta,
        icon = Icons.Filled.CellTower
    ),
    TELEGRAM(
        id = "telegram",
        code = "NODE-08",
        labelRes = R.string.node_8,
        displayLabel = "8-Telegram",
        sectorTag = "MSG-RELAY // TELEGRAM",
        primaryColor = HudTeal,
        secondaryColor = HudCyan,
        icon = Icons.AutoMirrored.Filled.Send
    );

    val expectedCsvSchema: String
        get() = when (this) {
            BANK_MELAT ->
                "NATIONAL_CODE, ACCOUNT_NUMBER, FULL_NAME, FATHER_NAME, ID_NUMBER, BIRTH_DATE, CITY_NAME, PROVINCE_NAME, BIRTH_CITY, BIRTH_PROVINCE, ADDRESS, CARD_NO, MOBILE"
            CITIZEN ->
                "FULLNAME, BIRTHDAY, National ID, Postal Code, Phone, City, Address"
            BANK_MELI, BANK_SADERAT, BANK_TEJARAT, BANK_SEPAH ->
                "FULL_NAME, NATIONAL_CODE, MOBILE, ACCOUNT_NUMBER, CITY_NAME, CARD_NO / DETAILS"
            IRANCELL ->
                "FULL_NAME, NATIONAL_CODE, MOBILE, ACCOUNT_HANDLE, CITY, ADDRESS"
            TELEGRAM ->
                "FULL_NAME, USER_ID / NATIONAL_CODE, PHONE, @USERNAME, CITY, METADATA"
        }

    companion object {
        fun fromId(id: String): HudModule = entries.firstOrNull { it.id == id } ?: BANK_MELI
    }
}
