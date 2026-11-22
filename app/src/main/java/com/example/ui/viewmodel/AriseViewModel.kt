package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.ai.AiCoachGateway
import com.example.ai.DiscoveredModelsResult
import com.example.ai.SetAdvice
import com.example.ai.WorkoutCoachContext
import com.example.camera.PoseFrameMetrics
import com.example.data.local.AiProviderType
import com.example.data.local.AriseLocalDatabase
import com.example.data.local.SecurePrefsStore
import com.example.data.local.SystemPreferences
import com.example.data.repository.WorkoutRepository
import com.example.integrations.DailyQuestNotifier
import com.example.integrations.HealthConnectManager
import com.example.integrations.HealthConnectStatus
import com.example.model.HunterProfile
import com.example.model.HunterProgressionEngine
import com.example.model.LoggedSet
import com.example.model.RoutineExercise
import com.example.model.WorkoutRoutine
import com.example.model.WorkoutSession
import com.example.widget.AriseSystemWidget
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope

private const val TAG = "AriseViewModel"

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class ActiveSetRow(
    val setNumber: Int,
    val targetReps: Int,
    val actualReps: Int,
    val weightKg: Double,
    val rpe: Int,
    val toFailure: Boolean,
    val completed: Boolean
)

data class ActiveExerciseBlock(
    val exercise: RoutineExercise,
    val displayNameOverride: String? = null,
    val sets: List<ActiveSetRow>
)

data class ActiveWorkoutState(
    val routine: WorkoutRoutine,
    val startedAtMs: Long,
    val blocks: List<ActiveExerciseBlock>,
    val selectedExerciseIndex: Int = 0,
    val restSecondsRemaining: Int = 0,
    val restTimerRunning: Boolean = false,
    val userFeeling: String = "",
    val poseEnabled: Boolean = false,
    val latestPoseMetrics: PoseFrameMetrics? = null,
    val coachStreamingText: String = "",
    val coachLoading: Boolean = false,
    val coachError: String? = null,
    val structuredAdvice: SetAdvice? = null,
    val isFinishing: Boolean = false
)

data class SettingsActionState(
    val discoveredModels: DiscoveredModelsResult? = null,
    val isDiscovering: Boolean = false,
    val statusMessage: String? = null,
    val isError: Boolean = false,
    val healthStatus: HealthConnectStatus = HealthConnectStatus.NOT_SUPPORTED,
    val healthPermissionsGranted: Boolean = false
)

class AriseViewModel(
    private val repository: WorkoutRepository,
    private val secureStore: SecurePrefsStore,
    private val aiGateway: AiCoachGateway,
    private val healthManager: HealthConnectManager,
    private val appContext: Context,
    private val currentUserId: String,
    private val displayName: String
) : ViewModel() {

    val profileState: StateFlow<UiState<HunterProfile>> = repository.observeProfile(currentUserId)
        .map<HunterProfile, UiState<HunterProfile>> { UiState.Success(it) }
        .catch { error ->
            val fsErr = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsErr == null && error is CancellationException) throw error
            emit(UiState.Error((fsErr ?: error).message ?: "Errore caricamento profilo"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val routinesState: StateFlow<UiState<List<WorkoutRoutine>>> = repository.observeRoutines(currentUserId)
        .map<List<WorkoutRoutine>, UiState<List<WorkoutRoutine>>> { UiState.Success(it) }
        .catch { error ->
            val fsErr = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsErr == null && error is CancellationException) throw error
            emit(UiState.Error((fsErr ?: error).message ?: "Errore caricamento schede"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val sessionsState: StateFlow<UiState<List<WorkoutSession>>> = repository.observeSessions(currentUserId)
        .map<List<WorkoutSession>, UiState<List<WorkoutSession>>> { UiState.Success(it) }
        .catch { error ->
            val fsErr = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsErr == null && error is CancellationException) throw error
            emit(UiState.Error((fsErr ?: error).message ?: "Errore caricamento storico"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val preferencesState: StateFlow<SystemPreferences> = secureStore.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), SystemPreferences())

    private val _activeWorkout = MutableStateFlow<ActiveWorkoutState?>(null)
    val activeWorkout: StateFlow<ActiveWorkoutState?> = _activeWorkout.asStateFlow()

    private val _completedBannerSession = MutableStateFlow<WorkoutSession?>(null)
    val completedBannerSession: StateFlow<WorkoutSession?> = _completedBannerSession.asStateFlow()

    private val _onlineStatus = MutableStateFlow(repository.isOnline())
    val onlineStatus: StateFlow<Boolean> = _onlineStatus.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _settingsState = MutableStateFlow(SettingsActionState())
    val settingsState: StateFlow<SettingsActionState> = _settingsState.asStateFlow()

    private var timerJob: Job? = null
    private var coachJob: Job? = null

    init {
        viewModelScope.launch {
            repository.ensureInitialHunterData(currentUserId, displayName)
            val synced = repository.flushPendingOfflineWrites(currentUserId)
            if (synced > 0) {
                _snackbarMessage.value = "Sincronizzati $synced elementi salvati offline."
            }
            refreshHealthStatus()
            AriseSystemWidget.refreshAll(appContext)
        }
    }

    fun consumeSnackbar() {
        _snackbarMessage.value = null
    }

    fun dismissCompletedBanner() {
        _completedBannerSession.value = null
    }

    fun refreshConnectivityAndSync() {
        _onlineStatus.value = repository.isOnline()
        viewModelScope.launch {
            val synced = repository.flushPendingOfflineWrites(currentUserId)
            if (synced > 0) {
                _snackbarMessage.value = "Cloud Sync completato ($synced record)."
            }
        }
    }

    fun refreshHealthStatus() {
        viewModelScope.launch {
            val status = healthManager.checkAvailability()
            val granted = healthManager.hasAllPermissions()
            _settingsState.update {
                it.copy(healthStatus = status, healthPermissionsGranted = granted)
            }
        }
    }

    fun saveRoutine(routine: WorkoutRoutine) {
        viewModelScope.launch {
            repository.saveRoutine(routine)
                .onSuccess {
                    _snackbarMessage.value = if (repository.isOnline()) {
                        "Scheda salvata e sincronizzata nel cloud."
                    } else {
                        "Scheda salvata offline (sync automatico al ritorno online)."
                    }
                }
                .onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Errore salvataggio scheda."
                }
        }
    }

    fun deleteRoutine(routineId: String) {
        viewModelScope.launch {
            repository.deleteRoutine(routineId)
                .onSuccess { _snackbarMessage.value = "Scheda eliminata." }
                .onFailure { _snackbarMessage.value = it.localizedMessage ?: "Errore eliminazione." }
        }
    }

    fun deleteWorkoutSession(session: WorkoutSession) {
        val profile = (profileState.value as? UiState.Success)?.data
            ?: HunterProfile(id = currentUserId, userId = currentUserId, displayName = displayName)
        val allSessions = (sessionsState.value as? UiState.Success)?.data.orEmpty()
        viewModelScope.launch {
            repository.deleteSession(
                session = session,
                currentProfile = profile,
                allSessions = allSessions
            ).onSuccess {
                AriseSystemWidget.refreshAll(appContext)
                _snackbarMessage.value = "Allenamento eliminato."
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Errore eliminazione allenamento."
            }
        }
    }

    fun startWorkout(routine: WorkoutRoutine) {
        val pastSessions = (sessionsState.value as? UiState.Success)?.data.orEmpty()
        val blocks = routine.exercises.map { ex ->
            val pastSets = pastSessions
                .filter { it.status == "COMPLETED" }
                .flatMap { it.loggedSets }
                .filter { it.exerciseId == ex.exerciseId || it.exerciseName.equals(ex.name, true) }
                .take(ex.targetSets)

            val suggestedKg = HunterProgressionEngine.suggestNextWeightKg(
                currentWeightKg = pastSets.firstOrNull()?.weightKg ?: ex.suggestedWeightKg,
                recentSetsForExercise = pastSets,
                targetReps = ex.targetReps
            )

            val rows = (1..ex.targetSets).map { setNum ->
                ActiveSetRow(
                    setNumber = setNum,
                    targetReps = ex.targetReps,
                    actualReps = ex.targetReps,
                    weightKg = suggestedKg,
                    rpe = 8,
                    toFailure = false,
                    completed = false
                )
            }
            ActiveExerciseBlock(exercise = ex, sets = rows)
        }

        _activeWorkout.value = ActiveWorkoutState(
            routine = routine,
            startedAtMs = System.currentTimeMillis(),
            blocks = blocks
        )
    }

    fun selectExercise(index: Int) {
        _activeWorkout.update { state ->
            state?.copy(selectedExerciseIndex = index.coerceIn(0, (state.blocks.size - 1).coerceAtLeast(0)))
        }
    }

    fun updateSetWeight(exIndex: Int, setIndex: Int, weightKg: Double) {
        _activeWorkout.update { state ->
            state ?: return@update null
            val updatedBlocks = state.blocks.mapIndexed { bi, block ->
                if (bi != exIndex) block else {
                    block.copy(
                        sets = block.sets.mapIndexed { si, row ->
                            if (si != setIndex) row else row.copy(weightKg = weightKg.coerceAtLeast(0.0))
                        }
                    )
                }
            }
            state.copy(blocks = updatedBlocks)
        }
    }

    fun updateSetActualReps(exIndex: Int, setIndex: Int, reps: Int) {
        _activeWorkout.update { state ->
            state ?: return@update null
            val updatedBlocks = state.blocks.mapIndexed { bi, block ->
                if (bi != exIndex) block else {
                    block.copy(
                        sets = block.sets.mapIndexed { si, row ->
                            if (si != setIndex) row else row.copy(actualReps = reps.coerceIn(0, 100))
                        }
                    )
                }
            }
            state.copy(blocks = updatedBlocks)
        }
    }

    fun updateSetRpeAndFailure(exIndex: Int, setIndex: Int, rpe: Int, toFailure: Boolean) {
        _activeWorkout.update { state ->
            state ?: return@update null
            val updatedBlocks = state.blocks.mapIndexed { bi, block ->
                if (bi != exIndex) block else {
                    block.copy(
                        sets = block.sets.mapIndexed { si, row ->
                            if (si != setIndex) row else row.copy(rpe = rpe.coerceIn(1, 10), toFailure = toFailure)
                        }
                    )
                }
            }
            state.copy(blocks = updatedBlocks)
        }
    }

    fun toggleSetCompleted(exIndex: Int, setIndex: Int) {
        var shouldStartTimer = false
        var restSec = 90
        _activeWorkout.update { state ->
            state ?: return@update null
            val updatedBlocks = state.blocks.mapIndexed { bi, block ->
                if (bi != exIndex) block else {
                    restSec = block.exercise.restSeconds
                    block.copy(
                        sets = block.sets.mapIndexed { si, row ->
                            if (si != setIndex) row else {
                                val nextDone = !row.completed
                                if (nextDone) shouldStartTimer = true
                                row.copy(completed = nextDone)
                            }
                        }
                    )
                }
            }
            state.copy(blocks = updatedBlocks)
        }
        if (shouldStartTimer) startRestTimer(restSec)
    }

    fun startRestTimer(seconds: Int) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var remaining = seconds.coerceAtLeast(1)
            while (remaining > 0) {
                _activeWorkout.update { it?.copy(restSecondsRemaining = remaining, restTimerRunning = true) }
                delay(1000L)
                remaining--
            }
            _activeWorkout.update { it?.copy(restSecondsRemaining = 0, restTimerRunning = false) }
        }
    }

    fun stopRestTimer() {
        timerJob?.cancel()
        _activeWorkout.update { it?.copy(restSecondsRemaining = 0, restTimerRunning = false) }
    }

    fun togglePoseCamera(enabled: Boolean) {
        _activeWorkout.update { it?.copy(poseEnabled = enabled) }
    }

    fun updatePoseMetrics(metrics: PoseFrameMetrics) {
        _activeWorkout.update { it?.copy(latestPoseMetrics = metrics) }
    }

    fun updateUserFeeling(feeling: String) {
        _activeWorkout.update { it?.copy(userFeeling = feeling) }
    }

    fun applyAiWeightRecommendation(weightKg: Double) {
        val state = _activeWorkout.value ?: return
        val exIdx = state.selectedExerciseIndex
        _activeWorkout.update { current ->
            current ?: return@update null
            val updatedBlocks = current.blocks.mapIndexed { bi, block ->
                if (bi != exIdx) block else {
                    block.copy(
                        sets = block.sets.map { row ->
                            if (!row.completed) row.copy(weightKg = weightKg) else row
                        }
                    )
                }
            }
            current.copy(blocks = updatedBlocks)
        }
        _snackbarMessage.value = "Carico aggiornato a $weightKg kg sulle serie rimanenti."
    }

    fun applyTemporaryExerciseSwap(alternativeName: String) {
        if (alternativeName.isBlank()) return
        val state = _activeWorkout.value ?: return
        val exIdx = state.selectedExerciseIndex
        _activeWorkout.update { current ->
            current ?: return@update null
            val updatedBlocks = current.blocks.mapIndexed { bi, block ->
                if (bi != exIdx) block else block.copy(displayNameOverride = alternativeName)
            }
            current.copy(blocks = updatedBlocks)
        }
        _snackbarMessage.value = "Esercizio adattato per oggi: $alternativeName"
    }

    fun askAiCoach(question: String, rpe: Int, hitFailure: Boolean) {
        val state = _activeWorkout.value ?: return
        val block = state.blocks.getOrNull(state.selectedExerciseIndex) ?: return
        val currentRow = block.sets.firstOrNull { !it.completed } ?: block.sets.lastOrNull() ?: return

        val ctx = buildCoachContext(state, block, currentRow, rpe, hitFailure)
        coachJob?.cancel()
        coachJob = viewModelScope.launch {
            _activeWorkout.update {
                it?.copy(
                    coachLoading = true,
                    coachError = null,
                    coachStreamingText = ""
                )
            }

            // Launch structured set advice in parallel
            launch {
                aiGateway.requestStructuredSetAdvice(ctx)
                    .onSuccess { advice ->
                        _activeWorkout.update { it?.copy(structuredAdvice = advice) }
                    }
                    .onFailure { err ->
                        Log.w(TAG, "Structured advice unavailable", err)
                    }
            }

            aiGateway.streamLiveCoachChat(ctx, question)
                .catch { err ->
                    if (err is CancellationException) throw err
                    Log.e(TAG, "AI coach streaming failed", err)
                    _activeWorkout.update {
                        it?.copy(
                            coachLoading = false,
                            coachError = err.localizedMessage ?: "Errore comunicazione AI Coach."
                        )
                    }
                }
                .collect { text ->
                    _activeWorkout.update {
                        it?.copy(
                            coachLoading = false,
                            coachStreamingText = text,
                            coachError = null
                        )
                    }
                }
            _activeWorkout.update { it?.copy(coachLoading = false) }
        }
    }

    private fun buildCoachContext(
        state: ActiveWorkoutState,
        block: ActiveExerciseBlock,
        currentRow: ActiveSetRow,
        rpe: Int,
        hitFailure: Boolean
    ): WorkoutCoachContext {
        val pastSessions = (sessionsState.value as? UiState.Success)?.data.orEmpty()
        val pastSets = pastSessions
            .filter { it.status == "COMPLETED" }
            .flatMap { it.loggedSets }
            .filter { it.exerciseId == block.exercise.exerciseId }
            .take(5)

        val historySummary = if (pastSets.isEmpty()) {
            "Nessuna sessione precedente"
        } else {
            pastSets.joinToString("; ") { "${it.weightKg}kg x ${it.actualReps} (RPE ${it.rpe})" }
        }

        val poseSummary = if (state.poseEnabled && state.latestPoseMetrics != null) {
            state.latestPoseMetrics.toAiSummary()
        } else ""

        return WorkoutCoachContext(
            exerciseName = block.displayNameOverride ?: block.exercise.name,
            muscleGroup = block.exercise.muscleGroup,
            setNumber = currentRow.setNumber,
            totalSets = block.sets.size,
            targetReps = block.exercise.targetReps,
            currentWeightKg = currentRow.weightKg,
            userFeeling = state.userFeeling,
            rpe = rpe,
            hitFailure = hitFailure,
            poseTelemetry = poseSummary,
            recentHistorySummary = historySummary
        )
    }

    fun finishActiveWorkout(abandon: Boolean = false) {
        val state = _activeWorkout.value ?: return
        if (abandon) {
            timerJob?.cancel()
            coachJob?.cancel()
            _activeWorkout.value = null
            return
        }

        val completedSets = state.blocks.flatMap { block ->
            val name = block.displayNameOverride ?: block.exercise.name
            block.sets.filter { it.completed }.map { row ->
                LoggedSet(
                    exerciseId = block.exercise.exerciseId,
                    exerciseName = name,
                    setNumber = row.setNumber,
                    targetReps = row.targetReps,
                    actualReps = row.actualReps,
                    weightKg = row.weightKg,
                    rpe = row.rpe,
                    toFailure = row.toFailure,
                    feedbackMode = if (state.poseEnabled) "CAMERA_POSE" else "MANUAL",
                    aiTip = state.structuredAdvice?.headline.orEmpty()
                )
            }
        }

        if (completedSets.isEmpty()) {
            _snackbarMessage.value = "Completa almeno una serie prima di terminare il Dungeon."
            return
        }

        val profile = (profileState.value as? UiState.Success)?.data
            ?: HunterProfile(id = currentUserId, userId = currentUserId, displayName = displayName)
        val pastSessions = (sessionsState.value as? UiState.Success)?.data.orEmpty()

        viewModelScope.launch {
            _activeWorkout.update { it?.copy(isFinishing = true) }
            repository.completeWorkoutSession(
                routine = state.routine,
                startedAtMs = state.startedAtMs,
                loggedSets = completedSets,
                feelingNote = state.userFeeling,
                previousSessions = pastSessions,
                currentProfile = profile
            ).onSuccess { savedSession ->
                timerJob?.cancel()
                coachJob?.cancel()
                _activeWorkout.value = null
                _completedBannerSession.value = savedSession

                if (preferencesState.value.healthConnectAutoSync) {
                    healthManager.syncWorkoutSession(savedSession).onSuccess {
                        repository.markSessionHealthSynced(savedSession.id)
                    }
                }
                AriseSystemWidget.refreshAll(appContext)
            }.onFailure { err ->
                _activeWorkout.update { it?.copy(isFinishing = false) }
                _snackbarMessage.value = err.localizedMessage ?: "Errore salvataggio sessione."
            }
        }
    }

    // --- SETTINGS ACTIONS ---
    fun detectAndDiscoverModels(provider: AiProviderType, apiKey: String) {
        viewModelScope.launch {
            _settingsState.update { it.copy(isDiscovering = true, statusMessage = null, isError = false) }
            aiGateway.discoverModels(provider, apiKey)
                .onSuccess { result ->
                    secureStore.saveProviderConfig(provider, apiKey, result.recommendedModel)
                    _settingsState.update {
                        it.copy(
                            isDiscovering = false,
                            discoveredModels = result,
                            statusMessage = "${result.providerName} attivo • Modello rilevato: ${result.recommendedModel}",
                            isError = false
                        )
                    }
                }
                .onFailure { err ->
                    _settingsState.update {
                        it.copy(
                            isDiscovering = false,
                            statusMessage = err.localizedMessage ?: "Verifica chiave fallita.",
                            isError = true
                        )
                    }
                }
        }
    }

    fun selectProviderAndModel(provider: AiProviderType, modelId: String?) {
        viewModelScope.launch {
            secureStore.saveProviderConfig(provider, null, modelId)
            _settingsState.update {
                it.copy(statusMessage = "Motore attivo: ${provider.displayName}", isError = false)
            }
        }
    }

    fun clearProviderKey(provider: AiProviderType) {
        viewModelScope.launch {
            secureStore.saveProviderConfig(provider, "", provider.defaultModel)
            if (preferencesState.value.activeProvider == provider) {
                secureStore.setActiveProvider(AiProviderType.GEMINI_BUILTIN)
            }
            _settingsState.update {
                it.copy(statusMessage = "Chiave ${provider.displayName} rimossa.", isError = false)
            }
        }
    }

    fun setBiometricLock(enabled: Boolean) {
        viewModelScope.launch {
            secureStore.setBiometricLockEnabled(enabled)
        }
    }

    fun setHealthConnectAutoSync(enabled: Boolean) {
        viewModelScope.launch {
            secureStore.setHealthConnectAutoSync(enabled)
        }
    }

    fun updateNotificationSettings(enabled: Boolean, hour: Int, minute: Int) {
        viewModelScope.launch {
            secureStore.setDailyNotificationConfig(enabled, hour, minute)
            DailyQuestNotifier.scheduleDailyReminder(appContext, enabled, hour, minute)
            _snackbarMessage.value = if (enabled) {
                String.format("Promemoria Daily Quest impostato alle %02d:%02d", hour, minute)
            } else {
                "Notifiche giornaliere disattivate."
            }
        }
    }

    companion object {
        fun factory(currentUserId: String, displayName: String): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                    val dbId = app.getString(R.string.firestore_database_id)
                    val db = FirebaseFirestore.getInstance(dbId)
                    val localDb = AriseLocalDatabase.getInstance(app)
                    val secureStore = SecurePrefsStore(app)
                    AriseViewModel(
                        repository = WorkoutRepository(db, localDb, secureStore, app),
                        secureStore = secureStore,
                        aiGateway = AiCoachGateway(secureStore),
                        healthManager = HealthConnectManager(app),
                        appContext = app,
                        currentUserId = currentUserId,
                        displayName = displayName
                    )
                }
            }
    }
}
