package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.security.BiometricAuthHelper
import com.example.ui.components.AnimatedBiometricReticle
import com.example.ui.components.DeveloperAttributionCard
import com.example.ui.components.HudChamferedPanel
import com.example.ui.components.MulticolorHudBackdrop
import com.example.ui.components.MulticolorSpectrumBar
import com.example.ui.theme.HudAmber
import com.example.ui.theme.HudBackgroundDeep
import com.example.ui.theme.HudCyan
import com.example.ui.theme.HudError
import com.example.ui.theme.HudLime
import com.example.ui.theme.HudMagenta
import com.example.ui.theme.HudTextSecondary

@Composable
fun FirstLaunchSetupScreen(
    errorMessage: String?,
    onCompleteSetup: (password: String, confirmPassword: String, enableBiometrics: Boolean) -> Unit,
    onClearError: () -> Unit
) {
    val context = LocalContext.current
    val sensorReady = remember { BiometricAuthHelper.canAuthenticateWithBiometrics(context) }
    val sensorStatusText = remember { BiometricAuthHelper.getBiometricStatusSummary(context) }

    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var enableBiometrics by rememberSaveable { mutableStateOf(true) }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    MulticolorHudBackdrop(accentPrimary = HudCyan, accentSecondary = HudMagenta) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier.widthIn(max = 520.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MulticolorSpectrumBar()

                Box(contentAlignment = Alignment.Center) {
                    AnimatedBiometricReticle(primaryColor = HudCyan, secondaryColor = HudLime)
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = "HUD Security Shield",
                        tint = HudCyan,
                        modifier = Modifier.size(48.dp)
                    )
                }

                Text(
                    text = stringResource(R.string.setup_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = HudCyan,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = stringResource(R.string.setup_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = HudTextSecondary,
                    textAlign = TextAlign.Center
                )

                HudChamferedPanel(
                    modifier = Modifier.fillMaxWidth(),
                    primaryAccent = HudCyan,
                    secondaryAccent = HudMagenta
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                if (errorMessage != null) onClearError()
                            },
                            label = { Text(stringResource(R.string.label_master_password)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Key,
                                    contentDescription = "Master Password Icon",
                                    tint = HudCyan
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = "Toggle password visibility",
                                        tint = HudTextSecondary
                                    )
                                }
                            },
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            colors = hudTextFieldColors(HudCyan),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("setup_password_input")
                        )

                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = {
                                confirmPassword = it
                                if (errorMessage != null) onClearError()
                            },
                            label = { Text(stringResource(R.string.label_confirm_password)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = "Confirm Password Icon",
                                    tint = HudMagenta
                                )
                            },
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            colors = hudTextFieldColors(HudMagenta),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("setup_confirm_password_input")
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.label_enable_biometrics),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = HudLime
                                )
                                Text(
                                    text = sensorStatusText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (sensorReady) HudCyan else HudAmber
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = enableBiometrics,
                                onCheckedChange = { enableBiometrics = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = HudBackgroundDeep,
                                    checkedTrackColor = HudLime
                                ),
                                modifier = Modifier.testTag("setup_biometric_switch")
                            )
                        }

                        if (!errorMessage.isNullOrEmpty()) {
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = HudError,
                                modifier = Modifier.testTag("setup_error_text")
                            )
                        }

                        Button(
                            onClick = {
                                onCompleteSetup(password, confirmPassword, enableBiometrics)
                            },
                            shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = HudCyan,
                                contentColor = HudBackgroundDeep
                            ),
                            contentPadding = PaddingValues(vertical = 14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("complete_setup_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LockOpen,
                                contentDescription = "Initialize Security"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.btn_initialize_system),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }

                DeveloperAttributionCard()
            }
        }
    }
}

@Composable
fun BiometricUnlockScreen(
    biometricEnabled: Boolean,
    errorMessage: String?,
    onRequestBiometricPrompt: () -> Unit,
    onUnlockWithPassword: (String) -> Unit,
    onClearError: () -> Unit
) {
    val context = LocalContext.current
    val sensorReady = remember { BiometricAuthHelper.canAuthenticateWithBiometrics(context) }

    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(biometricEnabled, sensorReady) {
        if (biometricEnabled && sensorReady) {
            onRequestBiometricPrompt()
        }
    }

    MulticolorHudBackdrop(accentPrimary = HudMagenta, accentSecondary = HudCyan) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier.widthIn(max = 520.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MulticolorSpectrumBar()

                Box(contentAlignment = Alignment.Center) {
                    AnimatedBiometricReticle(primaryColor = HudMagenta, secondaryColor = HudCyan)
                    IconButton(
                        onClick = onRequestBiometricPrompt,
                        modifier = Modifier
                            .size(72.dp)
                            .testTag("fingerprint_reticle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Fingerprint,
                            contentDescription = stringResource(R.string.btn_fingerprint_scan),
                            tint = HudCyan,
                            modifier = Modifier.size(54.dp)
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.unlock_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = HudMagenta,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = stringResource(R.string.unlock_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = HudTextSecondary,
                    textAlign = TextAlign.Center
                )

                HudChamferedPanel(
                    modifier = Modifier.fillMaxWidth(),
                    primaryAccent = HudMagenta,
                    secondaryAccent = HudCyan
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (biometricEnabled) {
                            Button(
                                onClick = onRequestBiometricPrompt,
                                shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = HudCyan,
                                    contentColor = HudBackgroundDeep
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("scan_fingerprint_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Fingerprint,
                                    contentDescription = stringResource(R.string.btn_fingerprint_scan)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.btn_fingerprint_scan),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }

                            if (!sensorReady) {
                                Text(
                                    text = stringResource(R.string.biometric_unavailable_hint),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = HudAmber
                                )
                            }
                        }

                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                if (errorMessage != null) onClearError()
                            },
                            label = { Text(stringResource(R.string.label_master_password)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Key,
                                    contentDescription = "Master Cipher",
                                    tint = HudMagenta
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = "Toggle cipher visibility",
                                        tint = HudTextSecondary
                                    )
                                }
                            },
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            colors = hudTextFieldColors(HudMagenta),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("unlock_password_input")
                        )

                        if (!errorMessage.isNullOrEmpty()) {
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = HudError,
                                modifier = Modifier.testTag("unlock_error_text")
                            )
                        }

                        OutlinedButton(
                            onClick = { onUnlockWithPassword(password) },
                            shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = HudLime),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("unlock_password_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LockOpen,
                                contentDescription = stringResource(R.string.btn_unlock_password)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.btn_unlock_password),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }

                DeveloperAttributionCard()
            }
        }
    }
}

@Composable
fun hudTextFieldColors(accent: androidx.compose.ui.graphics.Color) =
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor = accent,
        unfocusedBorderColor = accent.copy(alpha = 0.38f),
        focusedLabelColor = accent,
        cursorColor = accent
    )
