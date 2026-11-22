package com.example.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.R
import com.example.data.local.AriseLocalDatabase
import com.example.data.local.LocalProfileEntity
import com.example.data.local.LocalRoutineEntity
import com.example.data.local.LocalSessionEntity
import com.example.data.local.SecurePrefsStore
import com.example.data.local.WidgetSnapshot
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.example.model.ExerciseCatalog
import com.example.model.HunterProfile
import com.example.model.HunterProgressionEngine
import com.example.model.HunterRank
import com.example.model.LoggedSet
import com.example.model.RoutineExercise
import com.example.model.WorkoutRoutine
import com.example.model.WorkoutSession
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private const val TAG = "WorkoutRepository"

class WorkoutRepository(
    private val db: FirebaseFirestore,
    private val localDb: AriseLocalDatabase,
    private val secureStore: SecurePrefsStore,
    private val appContext: Context
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        localDb = AriseLocalDatabase.getInstance(context.applicationContext),
        secureStore = SecurePrefsStore(context.applicationContext),
        appContext = context.applicationContext
    )

    private val auth = Firebase.auth
    private val dao = localDb.dao()
    private val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Utente non autenticato con Google.")
    }

    fun isOnline(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun todayDateKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    // --- PROFILE OBSERVATION (Firestore real-time + Room offline cache) ---
    fun observeProfile(userId: String): Flow<HunterProfile> = flow {
        if (userId.isBlank()) throw IllegalStateException("UID utente non valido")
        val docRef = db.collection("users").document(userId)

        val remoteFlow = docRef.snapshots()
            .map { snap ->
                if (snap.exists()) {
                    val profile = snap.toObject(
                        HunterProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                    if (profile != null) {
                        val updatedMs = profile.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
                        bgScope.launch {
                            dao.upsertProfile(
                                LocalProfileEntity(
                                    userId = userId,
                                    displayName = profile.displayName,
                                    hunterRank = profile.hunterRank,
                                    level = profile.level,
                                    totalXp = profile.totalXp,
                                    strStat = profile.strStat,
                                    endStat = profile.endStat,
                                    vitStat = profile.vitStat,
                                    agiStat = profile.agiStat,
                                    currentStreakDays = profile.currentStreakDays,
                                    dailyTargetVolumeKg = profile.dailyTargetVolumeKg,
                                    dailyQuestCompleted = profile.dailyQuestCompleted,
                                    lastQuestDate = profile.lastQuestDate,
                                    updatedAtMs = updatedMs,
                                    pendingSync = false
                                )
                            )
                        }
                        profile
                    } else null
                } else null
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val toLog = firestoreError ?: (error as? Exception)
                if (toLog != null && auth.currentUser?.uid == userId) {
                    handleFirestoreError(toLog, OperationType.GET, "users/$userId")
                }
                emit(null)
            }

        emitAll(
            combine(dao.observeProfile(userId), remoteFlow) { local, remote ->
                val resolved = when {
                    local != null && local.pendingSync -> local.toDomain()
                    remote != null -> remote
                    local != null -> local.toDomain()
                    else -> HunterProfile(
                        id = userId,
                        userId = userId,
                        displayName = auth.currentUser?.displayName ?: "Hunter",
                        lastQuestDate = todayDateKey()
                    )
                }
                resolved
            }
        )
    }

    // --- ROUTINES OBSERVATION ---
    fun observeRoutines(userId: String): Flow<List<WorkoutRoutine>> = flow {
        if (userId.isBlank()) throw IllegalStateException("UID utente non valido")
        val colRef = db.collection("users").document(userId).collection("routines")

        val remoteFlow = colRef.snapshots()
            .map { snap ->
                val remoteList = snap.toObjects(
                    WorkoutRoutine::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                bgScope.launch {
                    val pendingIds = dao.getPendingRoutines(userId).map { it.id }.toSet()
                    val entities = remoteList
                        .filter { it.id !in pendingIds }
                        .map { r ->
                            LocalRoutineEntity(
                                id = r.id,
                                userId = userId,
                                name = r.name,
                                splitLabel = r.splitLabel,
                                targetDifficultyRank = r.targetDifficultyRank,
                                exercises = r.exercises,
                                updatedAtMs = r.updatedAt?.toDate()?.time ?: System.currentTimeMillis(),
                                pendingSync = false
                            )
                        }
                    if (entities.isNotEmpty()) dao.upsertRoutines(entities)
                }
                remoteList
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val toLog = firestoreError ?: (error as? Exception)
                if (toLog != null && auth.currentUser?.uid == userId) {
                    handleFirestoreError(toLog, OperationType.LIST, "users/$userId/routines")
                }
                emit(emptyList())
            }

        emitAll(
            combine(dao.observeRoutines(userId), remoteFlow) { local, remote ->
                val merged = if (local.isNotEmpty()) local.map { it.toDomain() } else remote
                merged.sortedByDescending { it.updatedAt?.toDate()?.time ?: 0L }
            }
        )
    }

    // --- SESSIONS OBSERVATION ---
    fun observeSessions(userId: String): Flow<List<WorkoutSession>> = flow {
        if (userId.isBlank()) throw IllegalStateException("UID utente non valido")
        val colRef = db.collection("users").document(userId).collection("sessions")

        val remoteFlow = colRef.snapshots()
            .map { snap ->
                val remoteList = snap.toObjects(
                    WorkoutSession::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                bgScope.launch {
                    val pendingIds = dao.getPendingSessions(userId).map { it.id }.toSet()
                    val entities = remoteList
                        .filter { it.id !in pendingIds }
                        .map { s ->
                            LocalSessionEntity(
                                id = s.id,
                                userId = userId,
                                routineId = s.routineId,
                                routineName = s.routineName,
                                status = s.status,
                                startedAtMs = s.startedAtMs,
                                completedAtMs = s.completedAtMs,
                                totalVolumeKg = s.totalVolumeKg,
                                previousVolumeKg = s.previousVolumeKg,
                                improvementPercent = s.improvementPercent,
                                xpEarned = s.xpEarned,
                                estimatedCalories = s.estimatedCalories,
                                feelingNote = s.feelingNote,
                                healthSynced = s.healthSynced,
                                loggedSets = s.loggedSets,
                                updatedAtMs = s.updatedAt?.toDate()?.time ?: s.startedAtMs,
                                pendingSync = false
                            )
                        }
                    if (entities.isNotEmpty()) dao.upsertSessions(entities)
                }
                remoteList
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val toLog = firestoreError ?: (error as? Exception)
                if (toLog != null && auth.currentUser?.uid == userId) {
                    handleFirestoreError(toLog, OperationType.LIST, "users/$userId/sessions")
                }
                emit(emptyList())
            }

        emitAll(
            combine(dao.observeSessions(userId), remoteFlow) { local, remote ->
                val merged = if (local.isNotEmpty()) local.map { it.toDomain() } else remote
                merged.sortedByDescending { it.startedAtMs }
            }
        )
    }

    suspend fun ensureInitialHunterData(userId: String, displayName: String): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val existingLocal = dao.getProfile(uid)
            val docRef = db.collection("users").document(uid)
            val remoteSnap = runCatching { docRef.get().await() }.getOrNull()

            if (remoteSnap == null || !remoteSnap.exists()) {
                val safeName = displayName.trim().take(80).ifBlank { "Hunter" }
                val today = todayDateKey()
                val newProfile = HunterProfile(
                    id = uid,
                    userId = uid,
                    displayName = safeName,
                    hunterRank = "E",
                    level = 1,
                    totalXp = 0,
                    strStat = 10,
                    endStat = 10,
                    vitStat = 10,
                    agiStat = 10,
                    currentStreakDays = 0,
                    dailyTargetVolumeKg = 3000.0,
                    dailyQuestCompleted = false,
                    lastQuestDate = today
                )
                if (existingLocal == null) {
                    dao.upsertProfile(
                        LocalProfileEntity(
                            userId = uid,
                            displayName = safeName,
                            hunterRank = "E",
                            level = 1,
                            totalXp = 0,
                            strStat = 10,
                            endStat = 10,
                            vitStat = 10,
                            agiStat = 10,
                            currentStreakDays = 0,
                            dailyTargetVolumeKg = 3000.0,
                            dailyQuestCompleted = false,
                            lastQuestDate = today,
                            updatedAtMs = System.currentTimeMillis(),
                            pendingSync = !isOnline()
                        )
                    )
                }
                runCatching { docRef.set(newProfile.toCreateMap()).await() }
                    .onFailure { Log.w(TAG, "Profile remote bootstrap queued offline", it) }

                // Seed two starter routines only if none exist yet
                val remoteRoutines = runCatching {
                    db.collection("users").document(uid).collection("routines").get().await()
                }.getOrNull()
                if (remoteRoutines != null && remoteRoutines.isEmpty) {
                    seedStarterRoutines(uid)
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    private suspend fun seedStarterRoutines(uid: String) {
        val pushExercises = listOf(
            ExerciseCatalog.presets[0], // Bench Press
            ExerciseCatalog.presets[8], // OHP
            ExerciseCatalog.presets[1], // Incline DB
            ExerciseCatalog.presets[9], // Lateral Raise
            ExerciseCatalog.presets[13] // Tricep Pushdown
        ).map {
            RoutineExercise(
                exerciseId = it.id,
                name = it.name,
                muscleGroup = it.muscleGroup,
                targetSets = it.defaultSets,
                targetReps = it.defaultReps,
                suggestedWeightKg = it.defaultWeightKg,
                restSeconds = it.defaultRestSec
            )
        }
        val pullLegsExercises = listOf(
            ExerciseCatalog.presets[2], // Squat
            ExerciseCatalog.presets[3], // RDL
            ExerciseCatalog.presets[6], // Barbell Row
            ExerciseCatalog.presets[7], // Lat Pulldown
            ExerciseCatalog.presets[12] // Bicep Curl
        ).map {
            RoutineExercise(
                exerciseId = it.id,
                name = it.name,
                muscleGroup = it.muscleGroup,
                targetSets = it.defaultSets,
                targetReps = it.defaultReps,
                suggestedWeightKg = it.defaultWeightKg,
                restSeconds = it.defaultRestSec
            )
        }
        saveRoutine(
            WorkoutRoutine(
                userId = uid,
                name = "Dungeon A: Spinta (Push)",
                splitLabel = "Petto / Spalle / Tricipiti",
                targetDifficultyRank = "D",
                exercises = pushExercises
            )
        )
        saveRoutine(
            WorkoutRoutine(
                userId = uid,
                name = "Dungeon B: Trazione & Gambe",
                splitLabel = "Gambe / Schiena / Bicipiti",
                targetDifficultyRank = "C",
                exercises = pullLegsExercises
            )
        )
    }

    suspend fun saveRoutine(routine: WorkoutRoutine): Result<String> {
        val routineId = routine.id.takeIf { it.isNotBlank() } ?: "rt_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val path = "users/${auth.currentUser?.uid ?: "unauth"}/routines/$routineId"
        return try {
            val uid = requireUserId()
            require(routine.name.isNotBlank()) { "Il nome della scheda non può essere vuoto" }
            require(routine.exercises.isNotEmpty()) { "Aggiungi almeno un esercizio" }

            val now = System.currentTimeMillis()
            val sanitizedExercises = routine.exercises.take(30)
            val entity = LocalRoutineEntity(
                id = routineId,
                userId = uid,
                name = routine.name.trim().take(80),
                splitLabel = routine.splitLabel.trim().take(40),
                targetDifficultyRank = HunterRank.fromCode(routine.targetDifficultyRank).code,
                exercises = sanitizedExercises,
                updatedAtMs = now,
                pendingSync = true
            )
            dao.upsertRoutine(entity)

            val docRef = db.collection("users").document(uid).collection("routines").document(routineId)
            val domain = entity.toDomain()
            try {
                if (routine.id.isBlank()) {
                    docRef.set(domain.toCreateMap()).await()
                } else {
                    val existing = docRef.get().await()
                    if (existing.exists()) {
                        docRef.update(
                            mapOf(
                                "name" to domain.name,
                                "splitLabel" to domain.splitLabel,
                                "targetDifficultyRank" to domain.targetDifficultyRank,
                                "exercises" to domain.exercises.map { it.toMap() },
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    } else {
                        docRef.set(domain.toCreateMap()).await()
                    }
                }
                dao.upsertRoutine(entity.copy(pendingSync = false))
            } catch (e: CancellationException) {
                throw e
            } catch (remoteErr: Exception) {
                Log.w(TAG, "Routine saved offline (pendingSync=true)", remoteErr)
            }
            Result.success(routineId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteRoutine(routineId: String): Result<Unit> {
        val path = "users/${auth.currentUser?.uid ?: "unauth"}/routines/$routineId"
        return try {
            val uid = requireUserId()
            require(routineId.isNotBlank())
            dao.hardDeleteRoutine(routineId)
            runCatching {
                db.collection("users").document(uid).collection("routines").document(routineId).delete().await()
            }.onFailure { Log.w(TAG, "Remote routine delete failed or offline", it) }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun completeWorkoutSession(
        routine: WorkoutRoutine,
        startedAtMs: Long,
        loggedSets: List<LoggedSet>,
        feelingNote: String,
        previousSessions: List<WorkoutSession>,
        currentProfile: HunterProfile
    ): Result<WorkoutSession> {
        val sessionId = "ses_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val path = "users/${auth.currentUser?.uid ?: "unauth"}/sessions/$sessionId"
        return try {
            val uid = requireUserId()
            val completedAtMs = System.currentTimeMillis()
            val totalVolume = loggedSets.sumOf { it.weightKg * it.actualReps }

            val previousSameRoutine = previousSessions
                .firstOrNull { it.routineId == routine.id && it.status == "COMPLETED" }
            val previousVolume = previousSameRoutine?.totalVolumeKg ?: 0.0
            val improvementPct = if (previousVolume > 0.0) {
                ((totalVolume - previousVolume) / previousVolume) * 100.0
            } else if (totalVolume > 0.0) 10.0 else 0.0

            val durationMinutes = ((completedAtMs - startedAtMs) / 60_000.0).coerceIn(10.0, 180.0)
            val estCalories = (durationMinutes * 6.2 + totalVolume * 0.015).toInt().coerceIn(50, 2000)

            val today = todayDateKey()
            val todayDoneBefore = currentProfile.lastQuestDate == today && currentProfile.dailyQuestCompleted
            val todayAccumulatedVol = totalVolume + previousSessions
                .filter { it.status == "COMPLETED" && sameLocalDay(it.completedAtMs, completedAtMs) }
                .sumOf { it.totalVolumeKg }
            val dailyQuestNowComplete = todayAccumulatedVol >= currentProfile.dailyTargetVolumeKg

            val xpEarned = HunterProgressionEngine.calculateSessionXp(
                totalVolumeKg = totalVolume,
                improvementPercent = improvementPct,
                setsCount = loggedSets.size,
                dailyQuestCompletedNow = !todayDoneBefore && dailyQuestNowComplete
            )

            val session = WorkoutSession(
                id = sessionId,
                userId = uid,
                routineId = routine.id,
                routineName = routine.name.take(80),
                status = "COMPLETED",
                startedAtMs = startedAtMs,
                completedAtMs = completedAtMs,
                totalVolumeKg = totalVolume,
                previousVolumeKg = previousVolume,
                improvementPercent = improvementPct,
                xpEarned = xpEarned,
                estimatedCalories = estCalories,
                feelingNote = feelingNote.take(300),
                healthSynced = false,
                loggedSets = loggedSets.take(120)
            )

            // Save locally first (zero-loss offline guarantee)
            val localSession = LocalSessionEntity(
                id = sessionId,
                userId = uid,
                routineId = session.routineId,
                routineName = session.routineName,
                status = session.status,
                startedAtMs = session.startedAtMs,
                completedAtMs = session.completedAtMs,
                totalVolumeKg = session.totalVolumeKg,
                previousVolumeKg = session.previousVolumeKg,
                improvementPercent = session.improvementPercent,
                xpEarned = session.xpEarned,
                estimatedCalories = session.estimatedCalories,
                feelingNote = session.feelingNote,
                healthSynced = false,
                loggedSets = session.loggedSets,
                updatedAtMs = completedAtMs,
                pendingSync = true
            )
            dao.upsertSession(localSession)

            // Update Hunter profile progression & RPG attributes
            val newTotalXp = currentProfile.totalXp + xpEarned
            val (newLevel, xpProgress) = HunterProgressionEngine.levelAndRemainderFromXp(newTotalXp)
            val newRank = HunterRank.fromLevel(newLevel).code

            val peakOneRm = loggedSets.maxOfOrNull {
                HunterProgressionEngine.estimatedOneRepMax(it.weightKg, it.actualReps)
            } ?: 0.0
            val newStr = maxOf(currentProfile.strStat, 10 + (peakOneRm / 5.0).toInt())
            val newEnd = currentProfile.endStat + (totalVolume / 1000.0).toInt().coerceAtLeast(1)
            val newStreak = if (currentProfile.lastQuestDate != today) currentProfile.currentStreakDays + 1 else currentProfile.currentStreakDays.coerceAtLeast(1)
            val newVit = currentProfile.vitStat + (if (dailyQuestNowComplete) 2 else 1)
            val newAgi = currentProfile.agiStat + (if (improvementPct > 0) 2 else 1)

            val localProfile = LocalProfileEntity(
                userId = uid,
                displayName = currentProfile.displayName,
                hunterRank = newRank,
                level = newLevel,
                totalXp = newTotalXp,
                strStat = newStr,
                endStat = newEnd,
                vitStat = newVit,
                agiStat = newAgi,
                currentStreakDays = newStreak,
                dailyTargetVolumeKg = currentProfile.dailyTargetVolumeKg,
                dailyQuestCompleted = dailyQuestNowComplete || todayDoneBefore,
                lastQuestDate = today,
                updatedAtMs = completedAtMs,
                pendingSync = true
            )
            dao.upsertProfile(localProfile)

            // Update Glance Widget snapshot immediately
            secureStore.saveWidgetSnapshot(
                WidgetSnapshot(
                    rank = newRank,
                    level = newLevel,
                    xpProgress = xpProgress,
                    todayVolumeKg = todayAccumulatedVol,
                    dailyTargetKg = currentProfile.dailyTargetVolumeKg,
                    questCompleted = dailyQuestNowComplete || todayDoneBefore,
                    streakDays = newStreak
                )
            )

            // Push to Firestore if online (safe fail -> stays pendingSync=true for later sync)
            try {
                db.collection("users").document(uid).collection("sessions")
                    .document(sessionId).set(session.toCreateMap()).await()
                dao.upsertSession(localSession.copy(pendingSync = false))

                db.collection("users").document(uid).update(
                    mapOf(
                        "hunterRank" to newRank,
                        "level" to newLevel,
                        "totalXp" to newTotalXp,
                        "strStat" to newStr,
                        "endStat" to newEnd,
                        "vitStat" to newVit,
                        "agiStat" to newAgi,
                        "currentStreakDays" to newStreak,
                        "dailyQuestCompleted" to (dailyQuestNowComplete || todayDoneBefore),
                        "lastQuestDate" to today,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                dao.upsertProfile(localProfile.copy(pendingSync = false))
            } catch (e: CancellationException) {
                throw e
            } catch (remoteErr: Exception) {
                Log.w(TAG, "Completed session queued locally for cloud sync", remoteErr)
            }

            Result.success(session)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun markSessionHealthSynced(sessionId: String): Result<Unit> {
        val path = "users/${auth.currentUser?.uid ?: "unauth"}/sessions/$sessionId"
        return try {
            val uid = requireUserId()
            dao.getSession(sessionId)?.let { local ->
                dao.upsertSession(local.copy(healthSynced = true))
            }
            runCatching {
                db.collection("users").document(uid).collection("sessions")
                    .document(sessionId)
                    .update(
                        mapOf(
                            "healthSynced" to true,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteSession(
        session: WorkoutSession,
        currentProfile: HunterProfile,
        allSessions: List<WorkoutSession>
    ): Result<Unit> {
        val path = "users/${auth.currentUser?.uid ?: "unauth"}/sessions/${session.id}"
        return try {
            val uid = requireUserId()
            require(session.id.isNotBlank()) { "ID sessione non valido" }

            dao.hardDeleteSession(session.id)

            val now = System.currentTimeMillis()
            val today = todayDateKey()
            val remainingTodayVol = allSessions
                .filter { it.id != session.id && it.status == "COMPLETED" && sameLocalDay(it.completedAtMs, now) }
                .sumOf { it.totalVolumeKg }
            val dailyQuestComplete = remainingTodayVol >= currentProfile.dailyTargetVolumeKg

            val newTotalXp = (currentProfile.totalXp - session.xpEarned).coerceAtLeast(0)
            val (newLevel, xpProgress) = HunterProgressionEngine.levelAndRemainderFromXp(newTotalXp)
            val newRank = HunterRank.fromLevel(newLevel).code

            val updatedLocalProfile = LocalProfileEntity(
                userId = uid,
                displayName = currentProfile.displayName,
                hunterRank = newRank,
                level = newLevel,
                totalXp = newTotalXp,
                strStat = currentProfile.strStat,
                endStat = currentProfile.endStat,
                vitStat = currentProfile.vitStat,
                agiStat = currentProfile.agiStat,
                currentStreakDays = currentProfile.currentStreakDays,
                dailyTargetVolumeKg = currentProfile.dailyTargetVolumeKg,
                dailyQuestCompleted = dailyQuestComplete,
                lastQuestDate = currentProfile.lastQuestDate.ifBlank { today },
                updatedAtMs = now,
                pendingSync = true
            )
            dao.upsertProfile(updatedLocalProfile)

            secureStore.saveWidgetSnapshot(
                WidgetSnapshot(
                    rank = newRank,
                    level = newLevel,
                    xpProgress = xpProgress,
                    todayVolumeKg = remainingTodayVol,
                    dailyTargetKg = currentProfile.dailyTargetVolumeKg,
                    questCompleted = dailyQuestComplete,
                    streakDays = currentProfile.currentStreakDays
                )
            )

            val sessionDoc = db.collection("users").document(uid)
                .collection("sessions").document(session.id)
            val profileDoc = db.collection("users").document(uid)

            val deleteTask = sessionDoc.delete()
            if (isOnline()) {
                try {
                    deleteTask.await()
                    profileDoc.update(
                        mapOf(
                            "hunterRank" to newRank,
                            "level" to newLevel,
                            "totalXp" to newTotalXp,
                            "dailyQuestCompleted" to dailyQuestComplete,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                    dao.upsertProfile(updatedLocalProfile.copy(pendingSync = false))
                } catch (e: CancellationException) {
                    throw e
                } catch (remoteErr: Exception) {
                    Log.w(TAG, "Remote session delete queued/failed", remoteErr)
                }
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun flushPendingOfflineWrites(userId: String): Int {
        if (!isOnline() || userId.isBlank()) return 0
        var syncedCount = 0
        try {
            val pendingProfile = dao.getProfile(userId)
            if (pendingProfile != null && pendingProfile.pendingSync) {
                val docRef = db.collection("users").document(userId)
                val snap = docRef.get().await()
                if (snap.exists()) {
                    docRef.update(
                        mapOf(
                            "hunterRank" to pendingProfile.hunterRank,
                            "level" to pendingProfile.level,
                            "totalXp" to pendingProfile.totalXp,
                            "strStat" to pendingProfile.strStat,
                            "endStat" to pendingProfile.endStat,
                            "vitStat" to pendingProfile.vitStat,
                            "agiStat" to pendingProfile.agiStat,
                            "currentStreakDays" to pendingProfile.currentStreakDays,
                            "dailyQuestCompleted" to pendingProfile.dailyQuestCompleted,
                            "lastQuestDate" to pendingProfile.lastQuestDate,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } else {
                    docRef.set(pendingProfile.toDomain().toCreateMap()).await()
                }
                dao.upsertProfile(pendingProfile.copy(pendingSync = false))
                syncedCount++
            }

            for (r in dao.getPendingRoutines(userId)) {
                val docRef = db.collection("users").document(userId).collection("routines").document(r.id)
                val domain = r.toDomain()
                val exists = docRef.get().await().exists()
                if (exists) {
                    docRef.update(
                        mapOf(
                            "name" to domain.name,
                            "splitLabel" to domain.splitLabel,
                            "targetDifficultyRank" to domain.targetDifficultyRank,
                            "exercises" to domain.exercises.map { it.toMap() },
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } else {
                    docRef.set(domain.toCreateMap()).await()
                }
                dao.upsertRoutine(r.copy(pendingSync = false))
                syncedCount++
            }

            for (s in dao.getPendingSessions(userId)) {
                val docRef = db.collection("users").document(userId).collection("sessions").document(s.id)
                if (!docRef.get().await().exists()) {
                    docRef.set(s.toDomain().toCreateMap()).await()
                }
                dao.upsertSession(s.copy(pendingSync = false))
                syncedCount++
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Offline flush paused due to connectivity/rules", e)
        }
        return syncedCount
    }

    private fun sameLocalDay(aMs: Long, bMs: Long): Boolean {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return fmt.format(Date(aMs)) == fmt.format(Date(bMs))
    }
}
