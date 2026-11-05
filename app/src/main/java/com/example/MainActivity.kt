package com.example

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.security.BiometricAuthHelper
import com.example.ui.HudAuthScreenState
import com.example.ui.NexusHudViewModel
import com.example.ui.components.MulticolorHudBackdrop
import com.example.ui.screens.BiometricUnlockScreen
import com.example.ui.screens.FirstLaunchSetupScreen
import com.example.ui.screens.HudGridHomeScreen
import com.example.ui.screens.ModuleDetailScreen
import com.example.ui.theme.HudBackgroundDeep
import com.example.ui.theme.HudCyan
import com.example.ui.theme.MyApplicationTheme

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(HudBackgroundDeep.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(HudBackgroundDeep.toArgb())
        )
        setContent {
            MyApplicationTheme {
                NexusHudApp(activity = this)
            }
        }
    }
}

@Composable
fun NexusHudApp(
    activity: FragmentActivity,
    viewModel: NexusHudViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val moduleCounts by viewModel.moduleCounts.collectAsStateWithLifecycle()
    val searchTelemetry by viewModel.searchTelemetry.collectAsStateWithLifecycle()
    val universalSearchTelemetry by viewModel.universalSearchTelemetry.collectAsStateWithLifecycle()
    val universalModuleFilter by viewModel.universalModuleFilter.collectAsStateWithLifecycle()
    val backupState by viewModel.backupState.collectAsStateWithLifecycle()

    Crossfade(
        targetState = uiState.authState,
        label = "hudAuthTransition"
    ) { authState ->
        when (authState) {
            is HudAuthScreenState.Loading -> {
                MulticolorHudBackdrop {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = HudCyan)
                    }
                }
            }

            is HudAuthScreenState.FirstLaunchSetup -> {
                FirstLaunchSetupScreen(
                    errorMessage = uiState.authError,
                    onCompleteSetup = { pwd, confirmPwd, enableBio ->
                        viewModel.completeInitialSetup(pwd, confirmPwd, enableBio)
                    },
                    onClearError = viewModel::clearAuthError
                )
            }

            is HudAuthScreenState.Locked -> {
                BiometricUnlockScreen(
                    biometricEnabled = authState.biometricEnabled,
                    errorMessage = uiState.authError,
                    onRequestBiometricPrompt = {
                        if (BiometricAuthHelper.canAuthenticateWithBiometrics(activity)) {
                            BiometricAuthHelper.showBiometricPrompt(
                                activity = activity,
                                title = activity.getString(R.string.biometric_prompt_title),
                                subtitle = activity.getString(R.string.biometric_prompt_subtitle),
                                negativeButtonText = activity.getString(R.string.biometric_prompt_negative),
                                onSuccess = viewModel::onBiometricUnlockSuccess,
                                onError = viewModel::onBiometricUnlockError
                            )
                        } else {
                            viewModel.onBiometricUnlockError(
                                activity.getString(R.string.biometric_unavailable_hint)
                            )
                        }
                    },
                    onUnlockWithPassword = viewModel::unlockWithPassword,
                    onClearError = viewModel::clearAuthError
                )
            }

            is HudAuthScreenState.Unlocked -> {
                val activeModule = uiState.selectedModule
                if (activeModule == null) {
                    HudGridHomeScreen(
                        moduleCounts = moduleCounts,
                        searchTelemetry = universalSearchTelemetry,
                        selectedFilterModuleId = universalModuleFilter,
                        backupState = backupState,
                        suggestedBackupFileName = viewModel.getSuggestedBackupFileName(),
                        onExportBackup = viewModel::exportAllToBackup,
                        onFastRestoreBackup = viewModel::fastImportFromBackup,
                        onCancelBackupOperation = viewModel::cancelBackupOperation,
                        onDismissBackupCompleted = viewModel::dismissBackupCompleted,
                        onSearchQueryChange = viewModel::updateUniversalSearchQuery,
                        onFilterModuleChange = viewModel::setUniversalModuleFilter,
                        onClearSearch = viewModel::clearUniversalSearch,
                        onSelectModule = viewModel::openModule,
                        onLockHud = viewModel::lockHud
                    )
                } else {
                    ModuleDetailScreen(
                        module = activeModule,
                        moduleRecordCount = moduleCounts[activeModule.id] ?: 0L,
                        importState = importState,
                        searchTelemetry = searchTelemetry,
                        onBack = viewModel::closeModuleToGrid,
                        onImportCsvUri = viewModel::startCsvImportFromUri,
                        onRunBenchmarkStream = viewModel::startSyntheticBenchmarkStream,
                        onPauseImport = viewModel::pauseCsvImport,
                        onResumeImport = viewModel::resumeCsvImport,
                        onCancelImport = viewModel::cancelCsvImport,
                        onClearModule = viewModel::purgeCurrentModuleRecords,
                        onSearchQueryChange = viewModel::updateSearchQuery
                    )
                }
            }
        }
    }
}
