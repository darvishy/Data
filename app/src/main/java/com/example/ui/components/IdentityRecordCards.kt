package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.IdentityEntity
import com.example.model.HudModule
import com.example.ui.theme.HudAmber
import com.example.ui.theme.HudBackgroundDeep
import com.example.ui.theme.HudBackgroundElevated
import com.example.ui.theme.HudCyan
import com.example.ui.theme.HudLime
import com.example.ui.theme.HudMagenta
import com.example.ui.theme.HudSurfaceCard
import com.example.ui.theme.HudTextPrimary
import com.example.ui.theme.HudTextSecondary

data class RecordDetailField(
    val label: String,
    val value: String,
    val color: Color,
    val rightAlignValue: Boolean = false
)

@Composable
fun IdentityRecordHudRow(
    record: IdentityEntity,
    module: HudModule,
    accentColor: Color,
    onClick: (IdentityEntity) -> Unit
) {
    val shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp)
    Surface(
        onClick = { onClick(record) },
        shape = shape,
        color = HudSurfaceCard.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.42f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("identity_record_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Header row: Left compact HUD tag, Right-aligned Full Name + Person Icon
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.OpenInFull,
                            contentDescription = stringResource(R.string.record_card_tap_hint),
                            tint = HudCyan,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "GPU#${record.gpuSignatureHash.toString(16).uppercase().takeLast(6)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = HudMagenta
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = record.fullName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Right,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("identity_record_name")
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = "Identity Record",
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            when {
                module == HudModule.BANK_MELI -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RecordFieldChip(label = "NAT_CODE", value = record.nationalCode, color = HudCyan, modifier = Modifier.weight(1f))
                        RecordFieldChip(label = "MOBILE", value = record.phone, color = HudLime, modifier = Modifier.weight(1f))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RecordFieldChip(label = "CARD_NO", value = record.cardNumber, color = HudMagenta, modifier = Modifier.weight(1.2f))
                        RecordFieldChip(label = "BIRTH_DATE", value = record.birthDate, color = HudAmber, modifier = Modifier.weight(0.8f))
                    }
                }

                module == HudModule.BANK_SADERAT || record.branchId != "-" || record.memberCode != "-" -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RecordFieldChip(label = "ACC_NO", value = record.accountOrHandle, color = HudCyan, modifier = Modifier.weight(1f))
                        RecordFieldChip(label = "DEF_NO", value = record.idNumber, color = HudAmber, modifier = Modifier.weight(1f))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RecordFieldChip(label = "CARD_NO", value = record.cardNumber, color = HudMagenta, modifier = Modifier.weight(1.1f))
                        RecordFieldChip(label = "PHONE", value = record.phone, color = HudLime, modifier = Modifier.weight(0.9f))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RecordFieldChip(label = "USER", value = record.username, color = accentColor, modifier = Modifier.weight(1f))
                        RecordFieldChip(label = "EMAIL", value = record.email, color = HudCyan, modifier = Modifier.weight(1f))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RecordFieldChip(label = "MEMBER", value = record.memberCode, color = HudLime, modifier = Modifier.weight(1f))
                        RecordFieldChip(label = "DEF_OLD", value = record.defNumOld, color = HudAmber, modifier = Modifier.weight(1f))
                        RecordFieldChip(label = "BRANCH", value = record.branchId, color = HudMagenta, modifier = Modifier.weight(0.9f))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RecordFieldChip(label = "PASSW", value = record.passwValue, color = HudAmber, modifier = Modifier.weight(1f))
                        RecordFieldChip(label = "HINT", value = record.passwordHint, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    }
                }

                module == HudModule.SABT_AHVAL -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RecordFieldChip(label = "NAT_CODE", value = record.nationalCode, color = HudCyan, modifier = Modifier.weight(1f))
                        RecordFieldChip(label = "SHENASNAME", value = record.idNumber, color = HudAmber, modifier = Modifier.weight(1f))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RecordFieldChip(label = "FATHER", value = record.fatherName, color = accentColor, modifier = Modifier.weight(1f))
                        RecordFieldChip(label = "BIRTH_DATE", value = record.birthDate, color = HudLime, modifier = Modifier.weight(1f))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val issueLoc = if (record.province != "-") "${record.city} / ${record.province}" else record.city
                        val serialOrTel = if (record.accountOrHandle != "-") record.accountOrHandle else record.phone
                        RecordFieldChip(label = "ISSUE_LOC", value = issueLoc, color = HudCyan, modifier = Modifier.weight(1f))
                        RecordFieldChip(
                            label = if (record.accountOrHandle != "-") "SERIAL" else "MOBILE",
                            value = serialOrTel,
                            color = HudMagenta,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (record.address != "-") {
                        RecordFieldChip(
                            label = "ADDRESS",
                            value = record.address,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                module == HudModule.BANK_MELAT || record.fatherName != "-" -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RecordFieldChip(label = "NAT_CODE", value = record.nationalCode, color = HudCyan, modifier = Modifier.weight(1f))
                        RecordFieldChip(label = "MOBILE", value = record.phone, color = HudLime, modifier = Modifier.weight(1f))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RecordFieldChip(label = "ACC_NO", value = record.accountOrHandle, color = HudAmber, modifier = Modifier.weight(1f))
                        RecordFieldChip(label = "CARD_NO", value = record.cardNumber, color = HudMagenta, modifier = Modifier.weight(1f))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RecordFieldChip(label = "FATHER", value = record.fatherName, color = accentColor, modifier = Modifier.weight(1f))
                        RecordFieldChip(label = "ID_NO", value = record.idNumber, color = HudCyan, modifier = Modifier.weight(0.9f))
                        RecordFieldChip(label = "DOB", value = record.birthDate, color = HudLime, modifier = Modifier.weight(1f))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val locText = if (record.province != "-") "${record.city} / ${record.province}" else record.city
                        val birthLocText = if (record.birthProvince != "-") "${record.birthCity} / ${record.birthProvince}" else record.birthCity
                        RecordFieldChip(label = "CITY/PROV", value = locText, color = HudCyan, modifier = Modifier.weight(1f))
                        RecordFieldChip(label = "BIRTH_LOC", value = birthLocText, color = HudAmber, modifier = Modifier.weight(1f))
                    }
                    if (record.address != "-") {
                        RecordFieldChip(
                            label = "ADDRESS",
                            value = record.address,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                module == HudModule.CITIZEN || record.postalCode != "-" -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RecordFieldChip(label = "NAT_ID", value = record.nationalCode, color = HudCyan, modifier = Modifier.weight(1f))
                        RecordFieldChip(label = "PHONE", value = record.phone, color = HudLime, modifier = Modifier.weight(1f))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RecordFieldChip(label = "BIRTHDAY", value = record.birthDate, color = HudAmber, modifier = Modifier.weight(1f))
                        RecordFieldChip(label = "POSTAL", value = record.postalCode, color = HudMagenta, modifier = Modifier.weight(1f))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RecordFieldChip(label = "CITY", value = record.city, color = accentColor, modifier = Modifier.weight(1f))
                    }
                    if (record.address != "-") {
                        RecordFieldChip(
                            label = "ADDRESS",
                            value = record.address,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                module == HudModule.TELEGRAM -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RecordFieldChip(label = "ACCOUNT_ID", value = record.accountOrHandle, color = HudCyan, modifier = Modifier.weight(1f))
                        RecordFieldChip(label = "PHONE", value = record.phone, color = HudLime, modifier = Modifier.weight(1f))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RecordFieldChip(label = "USERNAME", value = record.username, color = accentColor, modifier = Modifier.weight(1f))
                    }
                }

                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RecordFieldChip(label = "ID", value = record.nationalCode, color = HudCyan, modifier = Modifier.weight(1f))
                        RecordFieldChip(label = "TEL", value = record.phone, color = HudLime, modifier = Modifier.weight(1f))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RecordFieldChip(label = "ACC", value = record.accountOrHandle, color = HudAmber, modifier = Modifier.weight(1f))
                        RecordFieldChip(label = "LOC", value = record.city, color = accentColor, modifier = Modifier.weight(1f))
                    }
                }
            }

            if (record.rawDetails.isNotBlank()) {
                Text(
                    text = record.rawDetails,
                    style = MaterialTheme.typography.labelSmall,
                    color = HudTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RecordFieldChip(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.labelSmall,
            color = HudTextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun IdentityRecordDetailCardDialog(
    record: IdentityEntity,
    module: HudModule,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val shareChooserTitle = stringResource(R.string.share_chooser_title)
    val copiedAllMessage = stringResource(R.string.msg_info_copied_clipboard)
    val copiedFieldPrefix = stringResource(R.string.msg_field_copied_prefix)

    val detailFields = remember(record, module) {
        buildRecordDetailFields(record = record, module = module)
    }
    val formattedRecordText = remember(record, module, detailFields) {
        formatRecordInformationText(record = record, module = module, fields = detailFields)
    }

    var copyFeedbackMessage by remember(record.id) { mutableStateOf<String?>(null) }
    var fullCardCopied by remember(record.id) { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val cardShape = CutCornerShape(topStart = 18.dp, bottomEnd = 18.dp)
        Surface(
            shape = cardShape,
            color = HudBackgroundElevated,
            border = BorderStroke(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    colors = listOf(module.primaryColor, HudCyan, module.secondaryColor, HudAmber)
                )
            ),
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 540.dp)
                .testTag("identity_detail_dialog_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                module.primaryColor.copy(alpha = 0.12f),
                                HudBackgroundElevated
                            )
                        )
                    )
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MulticolorSpectrumBar()

                // Dialog Card Top Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = module.icon,
                            contentDescription = stringResource(module.labelRes),
                            tint = module.primaryColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = stringResource(module.labelRes),
                                style = MaterialTheme.typography.titleMedium,
                                color = module.primaryColor
                            )
                            Text(
                                text = stringResource(R.string.detail_card_header_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = HudTextSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("detail_card_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.btn_close_card),
                            tint = HudTextSecondary
                        )
                    }
                }

                // Right-Aligned Name Banner
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp))
                            .background(HudBackgroundDeep.copy(alpha = 0.78f))
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SelectionContainer(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = record.fullName,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Right,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("detail_card_full_name")
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = null,
                                tint = module.primaryColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // Scrollable Information Fields
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    detailFields.forEachIndexed { index, field ->
                        DetailFieldRow(
                            field = field,
                            onCopyField = {
                                copyPlainTextToClipboard(
                                    context = context,
                                    label = field.label,
                                    text = field.value
                                )
                                fullCardCopied = false
                                copyFeedbackMessage = "$copiedFieldPrefix ${field.label}"
                            }
                        )
                        if (index < detailFields.lastIndex) {
                            HorizontalDivider(color = module.primaryColor.copy(alpha = 0.14f))
                        }
                    }
                }

                // Copy Confirmation Banner
                AnimatedVisibility(visible = copyFeedbackMessage != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp))
                            .background(HudLime.copy(alpha = 0.16f))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("copy_confirmation_banner"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = HudLime,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = copyFeedbackMessage.orEmpty(),
                            style = MaterialTheme.typography.labelMedium,
                            color = HudLime
                        )
                    }
                }

                // Primary Copy & Share Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            copyPlainTextToClipboard(
                                context = context,
                                label = "${module.displayLabel} - ${record.fullName}",
                                text = formattedRecordText
                            )
                            fullCardCopied = true
                            copyFeedbackMessage = copiedAllMessage
                        },
                        shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (fullCardCopied) HudLime else module.primaryColor,
                            contentColor = HudBackgroundDeep
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("copy_record_info_button")
                    ) {
                        Icon(
                            imageVector = if (fullCardCopied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                            contentDescription = stringResource(R.string.btn_copy_information),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (fullCardCopied) {
                                stringResource(R.string.btn_copied_information)
                            } else {
                                stringResource(R.string.btn_copy_information)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            shareRecordInformation(
                                context = context,
                                subject = "${module.displayLabel} — ${record.fullName}",
                                chooserTitle = shareChooserTitle,
                                text = formattedRecordText
                            )
                        },
                        shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
                        border = BorderStroke(1.2.dp, HudCyan),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("share_record_info_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = stringResource(R.string.btn_share_information),
                            tint = HudCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.btn_share_information),
                            style = MaterialTheme.typography.labelLarge,
                            color = HudCyan,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailFieldRow(
    field: RecordDetailField,
    onCopyField: () -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = field.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = HudTextSecondary
                )
                SelectionContainer {
                    Text(
                        text = field.value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = field.color,
                        textAlign = if (field.rightAlignValue) TextAlign.Right else TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            IconButton(
                onClick = onCopyField,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "${stringResource(R.string.btn_copy_field)}: ${field.label}",
                    tint = field.color.copy(alpha = 0.85f),
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

fun buildRecordDetailFields(
    record: IdentityEntity,
    module: HudModule
): List<RecordDetailField> {
    val fields = mutableListOf<RecordDetailField>()
    val addedLabels = HashSet<String>()

    fun addIfPresent(
        label: String,
        value: String,
        color: Color,
        rightAlignValue: Boolean = false
    ) {
        val trimmed = value.trim()
        if (trimmed.isNotEmpty() && trimmed != "-" && addedLabels.add(label)) {
            fields.add(
                RecordDetailField(
                    label = label,
                    value = trimmed,
                    color = color,
                    rightAlignValue = rightAlignValue
                )
            )
        }
    }

    when (module) {
        HudModule.BANK_MELI -> {
            addIfPresent("NATIONAL CODE", record.nationalCode, HudCyan)
            addIfPresent("MOBILE", record.phone, HudLime)
            addIfPresent("CARD NUMBER", record.cardNumber, HudMagenta)
            addIfPresent("BIRTH DATE", record.birthDate, HudAmber)
        }
        HudModule.BANK_MELAT -> {
            addIfPresent("NATIONAL CODE", record.nationalCode, HudCyan)
            addIfPresent("MOBILE", record.phone, HudLime)
            addIfPresent("ACCOUNT NUMBER", record.accountOrHandle, HudAmber)
            addIfPresent("CARD NUMBER", record.cardNumber, HudMagenta)
            addIfPresent("FATHER NAME", record.fatherName, module.primaryColor, rightAlignValue = true)
            addIfPresent("ID NUMBER", record.idNumber, HudCyan)
            addIfPresent("BIRTH DATE", record.birthDate, HudLime)
            val locText = combineLocation(record.city, record.province)
            val birthLocText = combineLocation(record.birthCity, record.birthProvince)
            addIfPresent("CITY / PROVINCE", locText, HudCyan)
            addIfPresent("BIRTH LOCATION", birthLocText, HudAmber)
            addIfPresent("ADDRESS", record.address, HudTextPrimary, rightAlignValue = true)
        }
        HudModule.BANK_SADERAT -> {
            addIfPresent("ACCOUNT NUMBER", record.accountOrHandle, HudCyan)
            addIfPresent("DEFINITION NUMBER", record.idNumber, HudAmber)
            addIfPresent("CARD NUMBER", record.cardNumber, HudMagenta)
            addIfPresent("PHONE", record.phone, HudLime)
            addIfPresent("USERNAME", record.username, module.primaryColor)
            addIfPresent("EMAIL", record.email, HudCyan)
            addIfPresent("MEMBER CODE", record.memberCode, HudLime)
            addIfPresent("OLD DEFINITION NO", record.defNumOld, HudAmber)
            addIfPresent("BRANCH ID", record.branchId, HudMagenta)
            addIfPresent("PASSWORD", record.passwValue, HudAmber)
            addIfPresent("PASSWORD HINT", record.passwordHint, HudTextPrimary)
        }
        HudModule.CITIZEN -> {
            addIfPresent("NATIONAL ID", record.nationalCode, HudCyan)
            addIfPresent("PHONE", record.phone, HudLime)
            addIfPresent("BIRTHDAY", record.birthDate, HudAmber)
            addIfPresent("POSTAL CODE", record.postalCode, HudMagenta)
            addIfPresent("CITY", record.city, module.primaryColor)
            addIfPresent("ADDRESS", record.address, HudTextPrimary, rightAlignValue = true)
        }
        HudModule.TELEGRAM -> {
            addIfPresent("ACCOUNT ID", record.accountOrHandle, HudCyan)
            addIfPresent("PHONE", record.phone, HudLime)
            addIfPresent("USERNAME", record.username, module.primaryColor)
        }
        HudModule.SABT_AHVAL -> {
            addIfPresent("NATIONAL CODE", record.nationalCode, HudCyan)
            addIfPresent("SHENASNAME NO", record.idNumber, HudAmber)
            addIfPresent("FATHER NAME", record.fatherName, module.primaryColor, rightAlignValue = true)
            addIfPresent("BIRTH DATE", record.birthDate, HudLime)
            addIfPresent("SERIAL / SERIES", record.accountOrHandle, HudMagenta)
            val issueLoc = combineLocation(record.city, record.province)
            val birthLoc = combineLocation(record.birthCity, record.birthProvince)
            addIfPresent("ISSUE PLACE / CITY", issueLoc, HudCyan)
            addIfPresent("BIRTH LOCATION", birthLoc, HudAmber)
            addIfPresent("POSTAL CODE", record.postalCode, HudMagenta)
            addIfPresent("MOBILE / PHONE", record.phone, HudLime)
            addIfPresent("ADDRESS", record.address, HudTextPrimary, rightAlignValue = true)
        }
    }

    // Include any additional populated attributes from flexible CSV imports
    addIfPresent("NATIONAL CODE", record.nationalCode, HudCyan)
    addIfPresent("PHONE", record.phone, HudLime)
    addIfPresent("ACCOUNT / ID", record.accountOrHandle, HudAmber)
    addIfPresent("CARD NUMBER", record.cardNumber, HudMagenta)
    addIfPresent("FATHER NAME", record.fatherName, module.primaryColor, rightAlignValue = true)
    addIfPresent("ID NUMBER", record.idNumber, HudCyan)
    addIfPresent("BIRTH DATE", record.birthDate, HudAmber)
    addIfPresent("USERNAME", record.username, module.primaryColor)
    addIfPresent("EMAIL", record.email, HudCyan)
    addIfPresent("POSTAL CODE", record.postalCode, HudMagenta)
    addIfPresent("CITY", record.city, HudCyan)
    addIfPresent("PROVINCE", record.province, HudCyan)
    addIfPresent("ADDRESS", record.address, HudTextPrimary, rightAlignValue = true)
    addIfPresent("MEMBER CODE", record.memberCode, HudLime)
    addIfPresent("BRANCH ID", record.branchId, HudMagenta)
    addIfPresent("OLD DEFINITION NO", record.defNumOld, HudAmber)
    addIfPresent("PASSWORD", record.passwValue, HudAmber)
    addIfPresent("PASSWORD HINT", record.passwordHint, HudTextPrimary)
    addIfPresent("DETAILS", record.rawDetails, HudTextSecondary)
    addIfPresent(
        "GPU SIGNATURE",
        "GPU#${record.gpuSignatureHash.toString(16).uppercase().takeLast(6)}",
        HudMagenta
    )

    return fields
}

private fun combineLocation(city: String, province: String): String {
    val cleanCity = city.trim().takeIf { it.isNotEmpty() && it != "-" }
    val cleanProv = province.trim().takeIf { it.isNotEmpty() && it != "-" }
    return when {
        cleanCity != null && cleanProv != null -> "$cleanCity / $cleanProv"
        cleanCity != null -> cleanCity
        cleanProv != null -> cleanProv
        else -> "-"
    }
}

fun formatRecordInformationText(
    record: IdentityEntity,
    module: HudModule,
    fields: List<RecordDetailField> = buildRecordDetailFields(record, module)
): String {
    val sb = StringBuilder()
    sb.appendLine("[${module.displayLabel}]")
    sb.appendLine("NAME: ${record.fullName}")
    for (field in fields) {
        if (field.label != "GPU SIGNATURE") {
            sb.appendLine("${field.label}: ${field.value}")
        }
    }
    return sb.toString().trimEnd()
}

fun copyPlainTextToClipboard(
    context: Context,
    label: String,
    text: String
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
}

fun shareRecordInformation(
    context: Context,
    subject: String,
    chooserTitle: String,
    text: String
) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooserIntent = Intent.createChooser(sendIntent, chooserTitle).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(chooserIntent)
    } catch (_: Exception) {
        // Safe fallback on headless environments without an activity resolver
    }
}
