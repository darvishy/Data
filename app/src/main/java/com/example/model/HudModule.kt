package com.example.model

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.R
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
        code = "FIN-MELI",
        labelRes = R.string.node_bank_meli,
        displayLabel = "BankMeli",
        sectorTag = "FIN-CORE // MELI",
        primaryColor = HudCyan,
        secondaryColor = HudElectricBlue,
        icon = Icons.Filled.AccountBalance
    ),
    BANK_MELAT(
        id = "bank_melat",
        code = "FIN-MELAT",
        labelRes = R.string.node_bank_melat,
        displayLabel = "BankMelat",
        sectorTag = "FIN-NET // MELAT",
        primaryColor = HudMagenta,
        secondaryColor = HudCoral,
        icon = Icons.Filled.CreditCard
    ),
    BANK_SADERAT(
        id = "bank_saderat",
        code = "FIN-BSI",
        labelRes = R.string.node_bank_saderat,
        displayLabel = "BankSaderat",
        sectorTag = "FIN-GRID // SADERAT",
        primaryColor = HudElectricBlue,
        secondaryColor = HudCyan,
        icon = Icons.Filled.CurrencyExchange
    ),
    CITIZEN(
        id = "citizen",
        code = "CIV-REG",
        labelRes = R.string.node_citizen,
        displayLabel = "Citizen",
        sectorTag = "CIV-REG // CITIZEN",
        primaryColor = HudLime,
        secondaryColor = HudTeal,
        icon = Icons.Filled.Badge
    ),
    IRANCELL(
        id = "irancell",
        code = "TEL-MTN",
        labelRes = R.string.node_irancell,
        displayLabel = "Irancell",
        sectorTag = "TEL-CELL // IRANCELL",
        primaryColor = HudViolet,
        secondaryColor = HudMagenta,
        icon = Icons.Filled.CellTower
    ),
    TELEGRAM(
        id = "telegram",
        code = "MSG-TG",
        labelRes = R.string.node_telegram,
        displayLabel = "Telegram",
        sectorTag = "MSG-RELAY // TELEGRAM",
        primaryColor = HudTeal,
        secondaryColor = HudCyan,
        icon = Icons.AutoMirrored.Filled.Send
    );

    val expectedCsvSchema: String
        get() = when (this) {
            BANK_MELI ->
                "NATIONAL_CODE, CARD_NO, FULL_NAME, BIRTH_DATE, MOBILE"
            BANK_MELAT ->
                "NATIONAL_CODE, ACCOUNT_NUMBER, FULL_NAME, FATHER_NAME, ID_NUMBER, BIRTH_DATE, CITY_NAME, PROVINCE_NAME, BIRTH_CITY, BIRTH_PROVINCE, ADDRESS, CARD_NO, MOBILE"
            BANK_SADERAT ->
                "AccountNumber, DefineationNumber, FullName, CardNumber, Password_Hint, user_email_address, Passw, user_phone_number, Member, Username, DefineNum_Old, ID_Branch"
            CITIZEN ->
                "FULLNAME, BIRTHDAY, National ID, Postal Code, Phone, City, Address"
            IRANCELL ->
                "FULL_NAME, NATIONAL_CODE, MOBILE, ACCOUNT_HANDLE, CITY, ADDRESS"
            TELEGRAM ->
                "account_id;phone;username;first_name;last_name  [FILTER: phone starts with 98]"
        }

    companion object {
        fun fromId(id: String): HudModule = entries.firstOrNull { it.id == id } ?: BANK_MELI
    }
}
