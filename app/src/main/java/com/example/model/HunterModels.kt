package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.roundToInt

enum class HunterRank(
    val code: String,
    val titleIt: String,
    val minLevel: Int
) {
    E("E", "Risvegliato", 1),
    D("D", "Cacciatore", 10),
    C("C", "Vanguard", 25),
    B("B", "Elite", 45),
    A("A", "Maestro d'Ombra", 70),
    S("S", "Monarca", 100);

    companion object {
        fun fromCode(raw: String?): HunterRank =
            entries.firstOrNull { it.code.equals(raw, ignoreCase = true) } ?: E

        fun fromLevel(level: Int): HunterRank =
            entries.lastOrNull { level >= it.minLevel } ?: E
    }
}

@Keep
@IgnoreExtraProperties
data class HunterProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Hunter",
    val hunterRank: String = "E",
    val level: Int = 1,
    val totalXp: Int = 0,
    val strStat: Int = 10,
    val endStat: Int = 10,
    val vitStat: Int = 10,
    val agiStat: Int = 10,
    val currentStreakDays: Int = 0,
    val dailyTargetVolumeKg: Double = 3000.0,
    val dailyQuestCompleted: Boolean = false,
    val lastQuestDate: String = "",
    @com.google.firebase.firestore.ServerTimestamp val createdAt: Timestamp? = null,
    @com.google.firebase.firestore.ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = buildMap {
        put("userId", userId)
        put("displayName", displayName.take(80).ifBlank { "Hunter" })
        put("hunterRank", hunterRank)
        put("level", level.coerceIn(1, 999))
        put("totalXp", totalXp.coerceAtLeast(0))
        put("strStat", strStat.coerceAtLeast(0))
        put("endStat", endStat.coerceAtLeast(0))
        put("vitStat", vitStat.coerceAtLeast(0))
        put("agiStat", agiStat.coerceAtLeast(0))
        put("currentStreakDays", currentStreakDays.coerceAtLeast(0))
        put("dailyTargetVolumeKg", dailyTargetVolumeKg.coerceAtLeast(0.0))
        put("dailyQuestCompleted", dailyQuestCompleted)
        if (lastQuestDate.isNotBlank()) put("lastQuestDate", lastQuestDate.take(20))
        put("createdAt", FieldValue.serverTimestamp())
        put("updatedAt", FieldValue.serverTimestamp())
    }
}

@Keep
@IgnoreExtraProperties
@Serializable
data class RoutineExercise(
    val exerciseId: String = "",
    val name: String = "",
    val muscleGroup: String = "",
    val targetSets: Int = 3,
    val targetReps: Int = 10,
    val suggestedWeightKg: Double = 20.0,
    val restSeconds: Int = 90,
    val notes: String = ""
) {
    fun toMap(): Map<String, Any> = mapOf(
        "exerciseId" to exerciseId,
        "name" to name.take(80),
        "muscleGroup" to muscleGroup.take(40),
        "targetSets" to targetSets.coerceIn(1, 20),
        "targetReps" to targetReps.coerceIn(1, 100),
        "suggestedWeightKg" to suggestedWeightKg.coerceAtLeast(0.0),
        "restSeconds" to restSeconds.coerceIn(15, 600),
        "notes" to notes.take(200)
    )
}

@Keep
@IgnoreExtraProperties
data class WorkoutRoutine(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val splitLabel: String = "",
    val targetDifficultyRank: String = "E",
    val exercises: List<RoutineExercise> = emptyList(),
    @com.google.firebase.firestore.ServerTimestamp val createdAt: Timestamp? = null,
    @com.google.firebase.firestore.ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "name" to name.take(80).ifBlank { "Scheda Hunter" },
        "splitLabel" to splitLabel.take(40),
        "targetDifficultyRank" to HunterRank.fromCode(targetDifficultyRank).code,
        "exercises" to exercises.take(30).map { it.toMap() },
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
@Serializable
data class LoggedSet(
    val exerciseId: String = "",
    val exerciseName: String = "",
    val setNumber: Int = 1,
    val targetReps: Int = 8,
    val actualReps: Int = 8,
    val weightKg: Double = 0.0,
    val rpe: Int = 8,
    val toFailure: Boolean = false,
    val feedbackMode: String = "MANUAL",
    val aiTip: String = ""
) {
    fun toMap(): Map<String, Any> = mapOf(
        "exerciseId" to exerciseId,
        "exerciseName" to exerciseName.take(80),
        "setNumber" to setNumber.coerceAtLeast(1),
        "targetReps" to targetReps.coerceAtLeast(1),
        "actualReps" to actualReps.coerceAtLeast(0),
        "weightKg" to weightKg.coerceAtLeast(0.0),
        "rpe" to rpe.coerceIn(1, 10),
        "toFailure" to toFailure,
        "feedbackMode" to feedbackMode.take(20),
        "aiTip" to aiTip.take(240)
    )
}

@Keep
@IgnoreExtraProperties
data class WorkoutSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val routineId: String = "",
    val routineName: String = "",
    val status: String = "IN_PROGRESS",
    val startedAtMs: Long = 0L,
    val completedAtMs: Long = 0L,
    val totalVolumeKg: Double = 0.0,
    val previousVolumeKg: Double = 0.0,
    val improvementPercent: Double = 0.0,
    val xpEarned: Int = 0,
    val estimatedCalories: Int = 0,
    val feelingNote: String = "",
    val healthSynced: Boolean = false,
    val loggedSets: List<LoggedSet> = emptyList(),
    @com.google.firebase.firestore.ServerTimestamp val createdAt: Timestamp? = null,
    @com.google.firebase.firestore.ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "routineId" to routineId.take(128),
        "routineName" to routineName.take(80).ifBlank { "Allenamento" },
        "status" to status,
        "startedAtMs" to startedAtMs.coerceAtLeast(0L),
        "completedAtMs" to completedAtMs.coerceAtLeast(0L),
        "totalVolumeKg" to totalVolumeKg.coerceAtLeast(0.0),
        "previousVolumeKg" to previousVolumeKg.coerceAtLeast(0.0),
        "improvementPercent" to improvementPercent,
        "xpEarned" to xpEarned.coerceAtLeast(0),
        "estimatedCalories" to estimatedCalories.coerceAtLeast(0),
        "feelingNote" to feelingNote.take(300),
        "healthSynced" to healthSynced,
        "loggedSets" to loggedSets.take(120).map { it.toMap() },
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

object HunterProgressionEngine {
    fun xpRequiredForLevel(level: Int): Int = 100 + (level - 1) * 15

    fun levelAndRemainderFromXp(totalXp: Int): Pair<Int, Float> {
        var remaining = totalXp.coerceAtLeast(0)
        var lvl = 1
        while (lvl < 999) {
            val needed = xpRequiredForLevel(lvl)
            if (remaining < needed) {
                return lvl to (remaining.toFloat() / needed.toFloat()).coerceIn(0f, 1f)
            }
            remaining -= needed
            lvl++
        }
        return 999 to 1f
    }

    fun estimatedOneRepMax(weightKg: Double, reps: Int): Double {
        if (weightKg <= 0.0 || reps <= 0) return 0.0
        if (reps == 1) return weightKg
        return weightKg * (1.0 + reps / 30.0)
    }

    fun calculateSessionXp(
        totalVolumeKg: Double,
        improvementPercent: Double,
        setsCount: Int,
        dailyQuestCompletedNow: Boolean
    ): Int {
        val baseXp = 60 + (totalVolumeKg / 80.0).roundToInt() + setsCount * 6
        val improvementBonus = if (improvementPercent > 0.0) {
            (improvementPercent * 4.5).roundToInt().coerceIn(10, 220)
        } else 0
        val questBonus = if (dailyQuestCompletedNow) 120 else 0
        return max(40, baseXp + improvementBonus + questBonus)
    }

    fun suggestNextWeightKg(
        currentWeightKg: Double,
        recentSetsForExercise: List<LoggedSet>,
        targetReps: Int
    ): Double {
        if (recentSetsForExercise.isEmpty()) return currentWeightKg
        val avgRpe = recentSetsForExercise.map { it.rpe }.average()
        val anyFailure = recentSetsForExercise.any { it.toFailure && it.actualReps < targetReps }
        val allHitTarget = recentSetsForExercise.all { it.actualReps >= targetReps }

        return when {
            anyFailure || avgRpe >= 9.6 -> ((currentWeightKg * 0.95) * 2.0).roundToInt() / 2.0
            allHitTarget && avgRpe <= 7.8 -> currentWeightKg + 2.5
            allHitTarget && avgRpe <= 8.5 -> currentWeightKg + 1.25
            else -> currentWeightKg
        }.coerceAtLeast(2.5)
    }
}

data class CatalogExercise(
    val id: String,
    val name: String,
    val muscleGroup: String,
    val defaultSets: Int,
    val defaultReps: Int,
    val defaultWeightKg: Double,
    val defaultRestSec: Int
)

object ExerciseCatalog {
    val presets: List<CatalogExercise> = listOf(
        CatalogExercise("bench_press", "Panca Piana Bilanciere", "Petto", 4, 8, 60.0, 120),
        CatalogExercise("incline_db_press", "Spinte Manubri Panca 30°", "Petto", 3, 10, 22.0, 90),
        CatalogExercise("squat", "Back Squat Bilanciere", "Gambe", 4, 6, 80.0, 150),
        CatalogExercise("rdl", "Stacco Rumeno (RDL)", "Femorali", 3, 8, 70.0, 120),
        CatalogExercise("deadlift", "Stacco da Terra", "Schiena", 3, 5, 100.0, 180),
        CatalogExercise("pullups", "Trazioni alla Sbarra", "Schiena", 4, 8, 10.0, 120),
        CatalogExercise("barbell_row", "Rematore Bilanciere", "Schiena", 4, 8, 55.0, 105),
        CatalogExercise("lat_pulldown", "Lat Machine Presa Larga", "Schiena", 3, 10, 50.0, 90),
        CatalogExercise("ohp", "Military Press in Piedi", "Spalle", 4, 8, 40.0, 120),
        CatalogExercise("lateral_raise", "Alzate Laterali Manubri", "Spalle", 4, 12, 10.0, 60),
        CatalogExercise("leg_press", "Leg Press 45°", "Gambe", 3, 10, 130.0, 120),
        CatalogExercise("leg_curl", "Leg Curl Sdraiato", "Femorali", 3, 12, 40.0, 75),
        CatalogExercise("bicep_curl", "Curl Bilanciere EZ", "Bicipiti", 3, 10, 27.5, 75),
        CatalogExercise("tricep_pushdown", "Pushdown Cavi Corda", "Tricipiti", 3, 12, 25.0, 60),
        CatalogExercise("cable_fly", "Croci ai Cavi Alti", "Petto", 3, 12, 15.0, 60)
    )
}
