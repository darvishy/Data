package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.IdentityRepository
import com.example.data.SearchTelemetry
import com.example.engine.HighSpeedCsvImportEngine
import com.example.engine.ImportProgressState
import com.example.engine.ImportStatus
import com.example.model.HudModule
import com.example.security.SecurityPreferencesRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface HudAuthScreenState {
    data object Loading : HudAuthScreenState
    data object FirstLaunchSetup : HudAuthScreenState
    data class Locked(val biometricEnabled: Boolean) : HudAuthScreenState
    data object Unlocked : HudAuthScreenState
}

data class HudUiState(
    val authState: HudAuthScreenState = HudAuthScreenState.Loading,
    val selectedModule: HudModule? = null,
    val authError: String? = null,
    val biometricEnabled: Boolean = false
)

class NexusHudViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val identityRepository = IdentityRepository(database.identityDao())
    private val securityRepository = SecurityPreferencesRepository(application)
    private val importEngine = HighSpeedCsvImportEngine(
        context = application,
        database = database,
        externalScope = viewModelScope
    )

    private val _uiState = MutableStateFlow(HudUiState())
    val uiState: StateFlow<HudUiState> = _uiState.asStateFlow()

    val importState: StateFlow<ImportProgressState> = importEngine.state

    /**
     * Emits initial module counts immediately, then throttles subsequent Room invalidation
     * updates to at most ~3x/sec during high-speed multi-million-row imports.
     */
    val moduleCounts: StateFlow<Map<String, Long>> = flow {
        var isFirstEmission = true
        identityRepository.moduleCountsFlow.conflate().collect { counts ->
            emit(counts)
            if (isFirstEmission) {
                isFirstEmission = false
            } else {
                delay(350L)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _searchTelemetry = MutableStateFlow(SearchTelemetry())
    val searchTelemetry: StateFlow<SearchTelemetry> = _searchTelemetry.asStateFlow()

    private val _universalSearchTelemetry = MutableStateFlow(SearchTelemetry())
    val universalSearchTelemetry: StateFlow<SearchTelemetry> = _universalSearchTelemetry.asStateFlow()

    private val _universalModuleFilter = MutableStateFlow<String?>(null)
    val universalModuleFilter: StateFlow<String?> = _universalModuleFilter.asStateFlow()

    private var searchJob: Job? = null
    private var universalSearchJob: Job? = null
    private var unlockedInSession: Boolean = false

    init {
        viewModelScope.launch {
            securityRepository.securitySettingsFlow.collect { settings ->
                _uiState.update { prev ->
                    val nextAuth = when {
                        !settings.isConfigured -> {
                            unlockedInSession = false
                            HudAuthScreenState.FirstLaunchSetup
                        }
                        unlockedInSession -> HudAuthScreenState.Unlocked
                        else -> HudAuthScreenState.Locked(settings.biometricEnabled)
                    }
                    prev.copy(
                        authState = nextAuth,
                        biometricEnabled = settings.biometricEnabled
                    )
                }
            }
        }

        // Refresh module records cleanly on first committed batch, pause, or completion
        // without contending with SQLite WAL writes on every intermediate batch.
        viewModelScope.launch {
            var previewLoadedForCurrentRun = false
            var lastStatus = ImportStatus.IDLE
            importEngine.state.collect { imp ->
                if (imp.status == ImportStatus.RUNNING && lastStatus != ImportStatus.RUNNING && lastStatus != ImportStatus.PAUSED) {
                    previewLoadedForCurrentRun = false
                }
                val currentModule = _uiState.value.selectedModule
                if (currentModule != null && imp.activeModuleId == currentModule.id) {
                    val shouldRefreshFirstBatch =
                        imp.status == ImportStatus.RUNNING && !previewLoadedForCurrentRun && imp.processedRecords > 0L
                    val shouldRefreshTerminalOrPaused =
                        (imp.status == ImportStatus.COMPLETED || imp.status == ImportStatus.PAUSED) &&
                            imp.status != lastStatus

                    if (shouldRefreshFirstBatch || shouldRefreshTerminalOrPaused) {
                        if (shouldRefreshFirstBatch) {
                            previewLoadedForCurrentRun = true
                        }
                        if (_searchTelemetry.value.query.isEmpty()) {
                            refreshModuleDefaultRecords(currentModule)
                        }
                    }
                }
                lastStatus = imp.status
            }
        }
    }

    fun completeInitialSetup(password: String, confirmPassword: String, enableBiometrics: Boolean) {
        if (password.length < 4) {
            _uiState.update { it.copy(authError = "Password must be at least 4 characters.") }
            return
        }
        if (password != confirmPassword) {
            _uiState.update { it.copy(authError = "Passwords do not match.") }
            return
        }
        viewModelScope.launch {
            unlockedInSession = true
            securityRepository.saveInitialCredentials(password, enableBiometrics)
            _uiState.update {
                it.copy(
                    authState = HudAuthScreenState.Unlocked,
                    authError = null,
                    biometricEnabled = enableBiometrics
                )
            }
        }
    }

    fun unlockWithPassword(password: String) {
        viewModelScope.launch {
            val valid = securityRepository.verifyPassword(password)
            if (valid) {
                unlockedInSession = true
                _uiState.update {
                    it.copy(
                        authState = HudAuthScreenState.Unlocked,
                        authError = null
                    )
                }
            } else {
                _uiState.update {
                    it.copy(authError = "Access denied: Invalid master cipher.")
                }
            }
        }
    }

    fun onBiometricUnlockSuccess() {
        unlockedInSession = true
        _uiState.update {
            it.copy(
                authState = HudAuthScreenState.Unlocked,
                authError = null
            )
        }
    }

    fun onBiometricUnlockError(message: String) {
        _uiState.update { it.copy(authError = message) }
    }

    fun clearAuthError() {
        _uiState.update { it.copy(authError = null) }
    }

    fun lockHud() {
        unlockedInSession = false
        _uiState.update {
            it.copy(
                selectedModule = null,
                authState = HudAuthScreenState.Locked(it.biometricEnabled),
                authError = null
            )
        }
    }

    fun openModule(module: HudModule) {
        _uiState.update { it.copy(selectedModule = module) }
        _searchTelemetry.value = SearchTelemetry()
        refreshModuleDefaultRecords(module)
    }

    fun closeModuleToGrid() {
        searchJob?.cancel()
        _uiState.update { it.copy(selectedModule = null) }
    }

    fun updateSearchQuery(query: String) {
        val module = _uiState.value.selectedModule ?: return
        _searchTelemetry.update { it.copy(query = query, isSearching = query.isNotBlank()) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (query.isNotBlank()) {
                delay(45L)
            }
            identityRepository.executeVisibleHighSpeedSearch(
                moduleId = module.id,
                rawQuery = query,
                cachedModuleCount = moduleCounts.value[module.id],
                onStageUpdate = { telemetry ->
                    if (_uiState.value.selectedModule == module) {
                        _searchTelemetry.value = telemetry
                    }
                }
            )
        }
    }

    fun updateUniversalSearchQuery(
        query: String,
        moduleId: String? = _universalModuleFilter.value
    ) {
        _universalSearchTelemetry.update { it.copy(query = query, isSearching = query.isNotBlank()) }
        universalSearchJob?.cancel()
        universalSearchJob = viewModelScope.launch {
            if (query.isNotBlank()) {
                delay(45L)
            }
            val totalAll = moduleCounts.value.values.sum()
            identityRepository.executeUniversalSearch(
                rawQuery = query,
                targetModuleId = moduleId,
                totalRecordsAcrossAll = totalAll,
                onStageUpdate = { telemetry ->
                    _universalSearchTelemetry.value = telemetry
                }
            )
        }
    }

    fun setUniversalModuleFilter(moduleId: String?) {
        _universalModuleFilter.value = moduleId
        val currentQuery = _universalSearchTelemetry.value.query
        if (currentQuery.isNotBlank()) {
            updateUniversalSearchQuery(currentQuery, moduleId)
        }
    }

    fun clearUniversalSearch() {
        universalSearchJob?.cancel()
        _universalSearchTelemetry.value = SearchTelemetry()
    }

    fun startCsvImportFromUri(uri: Uri) {
        val module = _uiState.value.selectedModule ?: return
        importEngine.importCsvFromUri(module.id, uri)
    }

    fun startSyntheticBenchmarkStream(recordCount: Int = 100_000) {
        val module = _uiState.value.selectedModule ?: return
        importEngine.runHighSpeedCsvStreamBenchmark(module.id, recordCount)
    }

    fun pauseCsvImport() {
        importEngine.pauseImport()
    }

    fun resumeCsvImport() {
        importEngine.resumeImport()
    }

    fun cancelCsvImport() {
        importEngine.cancelImport()
    }

    fun purgeCurrentModuleRecords() {
        val module = _uiState.value.selectedModule ?: return
        viewModelScope.launch {
            identityRepository.clearModule(module.id)
            refreshModuleDefaultRecords(module)
        }
    }

    private fun refreshModuleDefaultRecords(module: HudModule) {
        searchJob?.cancel()
        val activeQuery = _searchTelemetry.value.query
        searchJob = viewModelScope.launch {
            identityRepository.executeVisibleHighSpeedSearch(
                moduleId = module.id,
                rawQuery = activeQuery,
                cachedModuleCount = null,
                onStageUpdate = { telemetry ->
                    if (_uiState.value.selectedModule == module) {
                        _searchTelemetry.value = telemetry
                    }
                }
            )
        }
    }
}
