package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ai.AdviceActionType
import com.example.camera.PoseCameraOverlay
import com.example.camera.PoseFrameMetrics
import com.example.ui.components.SystemPanelCard
import com.example.ui.theme.AbyssalSurface
import com.example.ui.theme.FailureCrimson
import com.example.ui.theme.FatigueAmber
import com.example.ui.theme.HudCardSurface
import com.example.ui.theme.MonoMetricLarge
import com.example.ui.theme.MonoMetricMedium
import com.example.ui.theme.MonoMetricSmall
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.QuestEmerald
import com.example.ui.theme.ShadowViolet
import com.example.ui.theme.SystemCyan
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.ActiveSetRow
import com.example.ui.viewmodel.ActiveWorkoutState
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActiveWorkoutScreen(
    state: ActiveWorkoutState,
    onSelectExercise: (Int) -> Unit,
    onUpdateWeight: (Int, Int, Double) -> Unit,
    onUpdateReps: (Int, Int, Int) -> Unit,
    onUpdateRpeFailure: (Int, Int, Int, Boolean) -> Unit,
    onToggleSetCompleted: (Int, Int) -> Unit,
    onStopTimer: () -> Unit,
    onAddTimerSeconds: (Int) -> Unit,
    onTogglePose: (Boolean) -> Unit,
    onPoseMetrics: (PoseFrameMetrics) -> Unit,
    onFeelingChanged: (String) -> Unit,
    onAskCoach: (String, Int, Boolean) -> Unit,
    onApplyRecommendedWeight: (Double) -> Unit,
    onApplyExerciseSwap: (String) -> Unit,
    onFinishWorkout: () -> Unit,
    onAbandonWorkout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showExitConfirm by remember { mutableStateOf(false) }
    var manualRpe by remember { mutableIntStateOf(8) }
    var manualFailure by remember { mutableStateOf(false) }
    var coachQuestion by remember { mutableStateOf("") }
    var cameraDeniedMsg by remember { mutableStateOf<String?>(null) }

    BackHandler { showExitConfirm = true }

    val cameraPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraDeniedMsg = null
            onTogglePose(true)
        } else {
            cameraDeniedMsg = "Permesso fotocamera negato: usa il Feedback Manuale qui sotto."
            onTogglePose(false)
        }
    }

    val currentBlock = state.blocks.getOrNull(state.selectedExerciseIndex)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header + Finish/Exit controls
        item {
            Column(modifier = Modifier.widthIn(max = 760.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "DUNGEON IN CORSO",
                            style = MaterialTheme.typography.labelLarge,
                            color = SystemCyan
                        )
                        Text(
                            text = state.routine.name,
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = { showExitConfirm = true },
                            modifier = Modifier.testTag("abandon_workout_button")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Esci", tint = FailureCrimson)
                        }
                        Button(
                            onClick = onFinishWorkout,
                            enabled = !state.isFinishing,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = QuestEmerald,
                                contentColor = ObsidianBg
                            ),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("finish_workout_button")
                        ) {
                            Text("TERMINA", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }

        // Rest Timer HUD
        if (state.restTimerRunning) {
            item {
                SystemPanelCard(
                    modifier = Modifier.widthIn(max = 760.dp),
                    glowAccent = ShadowViolet
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Timer, contentDescription = null, tint = SystemCyan)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "RECUPERO: ${state.restSecondsRemaining}s",
                                style = MonoMetricLarge,
                                color = SystemCyan
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = { onAddTimerSeconds(state.restSecondsRemaining + 30) }) {
                                Text("+30s", color = ShadowViolet)
                            }
                            TextButton(onClick = onStopTimer) {
                                Text("SALTA", color = FatigueAmber)
                            }
                        }
                    }
                }
            }
        }

        // Exercise Selector Strip
        item {
            Row(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.blocks.forEachIndexed { idx, block ->
                    val selected = idx == state.selectedExerciseIndex
                    val doneCount = block.sets.count { it.completed }
                    val label = block.displayNameOverride ?: block.exercise.name
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) SystemCyan.copy(alpha = 0.2f) else HudCardSurface)
                            .border(
                                width = 1.dp,
                                color = if (selected) SystemCyan else Color.White.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelectExercise(idx) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("exercise_tab_$idx")
                    ) {
                        Text(label, style = MaterialTheme.typography.titleSmall, color = Color.White)
                        Text(
                            "$doneCount/${block.sets.size} serie",
                            style = MonoMetricSmall,
                            color = if (doneCount == block.sets.size) QuestEmerald else SystemCyan
                        )
                    }
                }
            }
        }

        // Active Exercise Set Logger
        if (currentBlock != null) {
            item {
                SystemPanelCard(
                    modifier = Modifier.widthIn(max = 760.dp),
                    glowAccent = SystemCyan
                ) {
                    val exTitle = currentBlock.displayNameOverride ?: currentBlock.exercise.name
                    Text(exTitle, style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = SystemCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Scheda fissa: ${currentBlock.exercise.targetSets} serie × ${currentBlock.exercise.targetReps} reps • Recupero ${currentBlock.exercise.restSeconds}s",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                    if (currentBlock.exercise.notes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Nota: ${currentBlock.exercise.notes}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ShadowViolet
                        )
                    }
                }
            }

            itemsIndexed(
                currentBlock.sets,
                key = { idx, row -> "${state.selectedExerciseIndex}_${row.setNumber}_$idx" }
            ) { setIdx, row ->
                SetLoggerRowCard(
                    exIndex = state.selectedExerciseIndex,
                    setIndex = setIdx,
                    row = row,
                    onUpdateWeight = onUpdateWeight,
                    onUpdateReps = onUpdateReps,
                    onUpdateRpeFailure = onUpdateRpeFailure,
                    onToggleCompleted = onToggleSetCompleted,
                    modifier = Modifier.widthIn(max = 760.dp)
                )
            }
        }

        // ALWAYS-ON SYSTEM AI COACH PANEL (Camera Pose OR Manual Feedback)
        item {
            SystemPanelCard(
                modifier = Modifier.widthIn(max = 760.dp),
                glowAccent = ShadowViolet
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = ShadowViolet)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("SYSTEM AI COACH LIVE", style = MaterialTheme.typography.titleMedium, color = Color.White)
                            Text("Consigli continui su tecnica, carico e cedimento", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Mode Switch: Manual Feedback (always available) vs Camera Pose Detection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !state.poseEnabled,
                        onClick = { onTogglePose(false) },
                        label = { Text("Feedback Manuale") },
                        leadingIcon = { Icon(Icons.Default.EditNote, contentDescription = null) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("mode_manual_feedback")
                    )
                    FilterChip(
                        selected = state.poseEnabled,
                        onClick = {
                            val hasPerm = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasPerm) {
                                cameraDeniedMsg = null
                                onTogglePose(true)
                            } else {
                                cameraPermLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        label = { Text("Telecamera Pose") },
                        leadingIcon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("mode_camera_pose")
                    )
                }

                if (!cameraDeniedMsg.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(cameraDeniedMsg!!, style = MaterialTheme.typography.bodyMedium, color = FatigueAmber)
                }

                AnimatedVisibility(visible = state.poseEnabled) {
                    Column {
                        Spacer(modifier = Modifier.height(10.dp))
                        PoseCameraOverlay(onMetricsUpdated = onPoseMetrics)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { onTogglePose(false) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Torna a Feedback Manuale")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Come ti senti in questa serie?", style = MaterialTheme.typography.labelMedium, color = SystemCyan)
                Spacer(modifier = Modifier.height(6.dp))

                val feelingPresets = listOf(
                    "Mi sento carico",
                    "Affaticato oggi",
                    "Non finisco la serie",
                    "Cedimento raggiunto",
                    "Fastidio articolare"
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    feelingPresets.forEach { preset ->
                        val active = state.userFeeling == preset
                        FilterChip(
                            selected = active,
                            onClick = {
                                onFeelingChanged(preset)
                                if (preset == "Cedimento raggiunto") manualFailure = true
                                onAskCoach(
                                    "Sensazione segnalata: $preset. Cosa mi consigli di fare subito?",
                                    manualRpe,
                                    manualFailure
                                )
                            },
                            label = { Text(preset) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("RPE Percepito: $manualRpe / 10", style = MonoMetricSmall, color = Color.White)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Cedimento", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Switch(
                            checked = manualFailure,
                            onCheckedChange = { manualFailure = it }
                        )
                    }
                }
                Slider(
                    value = manualRpe.toFloat(),
                    onValueChange = { manualRpe = it.roundToInt().coerceIn(1, 10) },
                    valueRange = 5f..10f,
                    steps = 4,
                    colors = SliderDefaults.colors(thumbColor = SystemCyan, activeTrackColor = SystemCyan)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = coachQuestion,
                        onValueChange = { coachQuestion = it },
                        placeholder = { Text("Chiedi all'AI (es. abbasso peso o cambio presa?)") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ai_coach_question_input")
                    )
                    Button(
                        onClick = {
                            val prompt = coachQuestion.ifBlank {
                                if (state.poseEnabled) {
                                    "Analizza la mia tecnica attuale e consigliami se mantenere, alzare o abbassare i kg."
                                } else {
                                    "Valuta il mio RPE $manualRpe e dimmi come gestire la prossima serie."
                                }
                            }
                            onAskCoach(prompt, manualRpe, manualFailure)
                            coachQuestion = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ShadowViolet),
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("ask_ai_coach_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Chiedi all'AI Coach")
                    }
                }

                if (state.coachLoading) {
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = SystemCyan)
                }

                if (!state.coachError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(state.coachError, style = MaterialTheme.typography.bodyMedium, color = FailureCrimson)
                }

                state.structuredAdvice?.let { advice ->
                    Spacer(modifier = Modifier.height(12.dp))
                    val actionColor = when (advice.parsedAction()) {
                        AdviceActionType.INCREASE_WEIGHT -> QuestEmerald
                        AdviceActionType.LOWER_WEIGHT, AdviceActionType.STOP_EARLY -> FatigueAmber
                        AdviceActionType.SWAP_EXERCISE -> ShadowViolet
                        AdviceActionType.KEEP_WEIGHT -> SystemCyan
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(AbyssalSurface)
                            .border(1.dp, actionColor.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "[DIRETTIVA SYSTEM] ${advice.headline}",
                            style = MaterialTheme.typography.titleSmall,
                            color = actionColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(advice.rationale, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (advice.recommendedWeightKg > 0.0) {
                                Button(
                                    onClick = { onApplyRecommendedWeight(advice.recommendedWeightKg) },
                                    colors = ButtonDefaults.buttonColors(containerColor = actionColor, contentColor = ObsidianBg),
                                    modifier = Modifier.testTag("apply_ai_weight_button")
                                ) {
                                    Text("APPLICA ${advice.recommendedWeightKg} KG", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                            if (advice.alternativeExercise.isNotBlank()) {
                                OutlinedButton(
                                    onClick = { onApplyExerciseSwap(advice.alternativeExercise) },
                                    modifier = Modifier.testTag("apply_ai_swap_button")
                                ) {
                                    Text("ADATTA: ${advice.alternativeExercise}")
                                }
                            }
                        }
                    }
                }

                if (state.coachStreamingText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = state.coachStreamingText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(AbyssalSurface)
                            .padding(12.dp)
                    )
                }
            }
        }
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            containerColor = HudCardSurface,
            title = { Text("Abbandonare la sessione?", color = Color.White) },
            text = { Text("I dati non ancora terminati andranno persi.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    showExitConfirm = false
                    onAbandonWorkout()
                }) {
                    Text("ABBANDONA", color = FailureCrimson)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) {
                    Text("CONTINUA", color = SystemCyan)
                }
            }
        )
    }
}

@Composable
private fun SetLoggerRowCard(
    exIndex: Int,
    setIndex: Int,
    row: ActiveSetRow,
    onUpdateWeight: (Int, Int, Double) -> Unit,
    onUpdateReps: (Int, Int, Int) -> Unit,
    onUpdateRpeFailure: (Int, Int, Int, Boolean) -> Unit,
    onToggleCompleted: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var weightText by remember(row.weightKg) { mutableStateOf(row.weightKg.toString()) }
    var repsText by remember(row.actualReps) { mutableStateOf(row.actualReps.toString()) }
    val borderColor = if (row.completed) QuestEmerald else Color.White.copy(alpha = 0.14f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (row.completed) QuestEmerald.copy(alpha = 0.10f) else HudCardSurface)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SERIE ${row.setNumber} • Target ${row.targetReps} reps",
                style = MonoMetricMedium,
                color = if (row.completed) QuestEmerald else SystemCyan
            )
            FilterChip(
                selected = row.toFailure,
                onClick = { onUpdateRpeFailure(exIndex, setIndex, if (!row.toFailure) 10 else 8, !row.toFailure) },
                label = { Text(if (row.toFailure) "Cedimento" else "RPE ${row.rpe}") }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val next = (row.weightKg - 2.5).coerceAtLeast(0.0)
                    onUpdateWeight(exIndex, setIndex, next)
                },
                modifier = Modifier.height(48.dp)
            ) {
                Text("-2.5")
            }

            OutlinedTextField(
                value = weightText,
                onValueChange = {
                    weightText = it
                    it.toDoubleOrNull()?.let { v -> onUpdateWeight(exIndex, setIndex, v) }
                },
                label = { Text("Kg") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )

            OutlinedButton(
                onClick = {
                    val next = row.weightKg + 2.5
                    onUpdateWeight(exIndex, setIndex, next)
                },
                modifier = Modifier.height(48.dp)
            ) {
                Text("+2.5")
            }

            OutlinedTextField(
                value = repsText,
                onValueChange = {
                    val clean = it.filter(Char::isDigit)
                    repsText = clean
                    clean.toIntOrNull()?.let { r -> onUpdateReps(exIndex, setIndex, r) }
                },
                label = { Text("Reps") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(74.dp)
            )

            IconButton(
                onClick = { onToggleCompleted(exIndex, setIndex) },
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (row.completed) QuestEmerald else AbyssalSurface)
                    .border(1.dp, if (row.completed) QuestEmerald else SystemCyan, RoundedCornerShape(10.dp))
                    .testTag("complete_set_${exIndex}_$setIndex")
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Completa serie",
                    tint = if (row.completed) ObsidianBg else SystemCyan
                )
            }
        }
    }
}
