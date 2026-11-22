package com.example.ai

import kotlinx.serialization.Serializable

enum class AdviceActionType {
    INCREASE_WEIGHT,
    KEEP_WEIGHT,
    LOWER_WEIGHT,
    STOP_EARLY,
    SWAP_EXERCISE
}

@Serializable
data class SetAdvice(
    val actionType: String = "KEEP_WEIGHT",
    val recommendedWeightKg: Double = 0.0,
    val headline: String = "",
    val rationale: String = "",
    val alternativeExercise: String = ""
) {
    fun parsedAction(): AdviceActionType =
        AdviceActionType.entries.firstOrNull { it.name.equals(actionType, ignoreCase = true) }
            ?: AdviceActionType.KEEP_WEIGHT
}

data class WorkoutCoachContext(
    val exerciseName: String,
    val muscleGroup: String,
    val setNumber: Int,
    val totalSets: Int,
    val targetReps: Int,
    val currentWeightKg: Double,
    val userFeeling: String,
    val rpe: Int,
    val hitFailure: Boolean,
    val poseTelemetry: String,
    val recentHistorySummary: String
)

data class DiscoveredModelsResult(
    val providerName: String,
    val models: List<String>,
    val recommendedModel: String
)
