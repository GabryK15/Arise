package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.ExerciseCatalog
import com.example.model.HunterRank
import com.example.model.RoutineExercise
import com.example.model.WorkoutRoutine
import com.example.ui.components.HunterRankBadge
import com.example.ui.components.SystemPanelCard
import com.example.ui.theme.AbyssalSurface
import com.example.ui.theme.FailureCrimson
import com.example.ui.theme.HudCardSurface
import com.example.ui.theme.MonoMetricSmall
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ShadowViolet
import com.example.ui.theme.SystemCyan
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.UiState
import java.util.UUID

@Composable
fun RoutinesScreen(
    routinesState: UiState<List<WorkoutRoutine>>,
    onSaveRoutine: (WorkoutRoutine) -> Unit,
    onDeleteRoutine: (String) -> Unit,
    onStartWorkout: (WorkoutRoutine) -> Unit,
    modifier: Modifier = Modifier
) {
    var editingRoutine by remember { mutableStateOf<WorkoutRoutine?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ObsidianBg,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingRoutine = null
                    showEditor = true
                },
                containerColor = SystemCyan,
                contentColor = ObsidianBg,
                modifier = Modifier.testTag("add_routine_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Nuova Scheda")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Column(modifier = Modifier.widthIn(max = 720.dp)) {
                    Text(
                        text = "DUNGEON ROUTINES",
                        style = MaterialTheme.typography.labelLarge,
                        color = ShadowViolet
                    )
                    Text(
                        text = "LE TUE SCHEDE",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White
                    )
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
                            text = "Serie e ripetizioni restano fisse da scheda (salvo cedimento o adattamento AI).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }

            when (routinesState) {
                is UiState.Loading -> {
                    item { CircularProgressIndicator(color = SystemCyan) }
                }
                is UiState.Error -> {
                    item {
                        Text(
                            text = routinesState.message,
                            color = FailureCrimson,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                is UiState.Success -> {
                    val routines = routinesState.data
                    if (routines.isEmpty()) {
                        item {
                            SystemPanelCard(modifier = Modifier.widthIn(max = 720.dp)) {
                                Text(
                                    text = "Nessuna scheda presente. Tocca + per inserire la tua prima scheda di allenamento.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextSecondary
                                )
                            }
                        }
                    } else {
                        items(routines, key = { it.id }) { routine ->
                            SystemPanelCard(
                                modifier = Modifier.widthIn(max = 720.dp),
                                glowAccent = ShadowViolet
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        HunterRankBadge(rankCode = routine.targetDifficultyRank)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = routine.name,
                                                style = MaterialTheme.typography.titleLarge,
                                                color = Color.White
                                            )
                                            Text(
                                                text = routine.splitLabel.ifBlank { "${routine.exercises.size} esercizi" },
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = TextSecondary
                                            )
                                        }
                                    }
                                    Row {
                                        IconButton(
                                            onClick = {
                                                editingRoutine = routine
                                                showEditor = true
                                            },
                                            modifier = Modifier.testTag("edit_routine_${routine.id}")
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Modifica", tint = SystemCyan)
                                        }
                                        IconButton(
                                            onClick = { onDeleteRoutine(routine.id) },
                                            modifier = Modifier.testTag("delete_routine_${routine.id}")
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Elimina", tint = FailureCrimson)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                routine.exercises.forEach { ex ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "• ${ex.name}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "${ex.targetSets}x${ex.targetReps} @ ${ex.suggestedWeightKg}kg",
                                            style = MonoMetricSmall,
                                            color = SystemCyan
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { onStartWorkout(routine) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SystemCyan,
                                        contentColor = ObsidianBg
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("launch_routine_${routine.id}")
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("INIZIA ALLENAMENTO", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        RoutineEditorDialog(
            initial = editingRoutine,
            onDismiss = { showEditor = false },
            onSave = { saved ->
                onSaveRoutine(saved)
                showEditor = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoutineEditorDialog(
    initial: WorkoutRoutine?,
    onDismiss: () -> Unit,
    onSave: (WorkoutRoutine) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var splitLabel by remember { mutableStateOf(initial?.splitLabel.orEmpty()) }
    var selectedRank by remember { mutableStateOf(initial?.targetDifficultyRank ?: "C") }
    val exercises = remember {
        mutableStateListOf<RoutineExercise>().apply {
            if (initial != null) addAll(initial.exercises)
        }
    }

    var customName by remember { mutableStateOf("") }
    var customMuscle by remember { mutableStateOf("Generale") }
    var customSets by remember { mutableStateOf("3") }
    var customReps by remember { mutableStateOf("10") }
    var customWeight by remember { mutableStateOf("40") }
    var customRest by remember { mutableStateOf("90") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HudCardSurface,
        title = {
            Text(
                text = if (initial == null) "NUOVA SCHEDA HUNTER" else "MODIFICA SCHEDA",
                style = MaterialTheme.typography.titleLarge,
                color = SystemCyan
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome Scheda (es. Push Heavy)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("routine_name_input")
                )
                OutlinedTextField(
                    value = splitLabel,
                    onValueChange = { splitLabel = it },
                    label = { Text("Gruppi Muscolari (es. Petto / Spalle)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Grado Difficoltà:", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HunterRank.entries.forEach { r ->
                        FilterChip(
                            selected = selectedRank == r.code,
                            onClick = { selectedRank = r.code },
                            label = { Text(r.code) }
                        )
                    }
                }

                Text("Aggiungi dal Catalogo:", style = MaterialTheme.typography.labelMedium, color = SystemCyan)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ExerciseCatalog.presets.take(8).forEach { cat ->
                        Text(
                            text = "+ ${cat.name.substringBefore(" ")}",
                            style = MonoMetricSmall,
                            color = Color.White,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(AbyssalSurface)
                                .border(1.dp, SystemCyan.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .clickable {
                                    exercises.add(
                                        RoutineExercise(
                                            exerciseId = cat.id,
                                            name = cat.name,
                                            muscleGroup = cat.muscleGroup,
                                            targetSets = cat.defaultSets,
                                            targetReps = cat.defaultReps,
                                            suggestedWeightKg = cat.defaultWeightKg,
                                            restSeconds = cat.defaultRestSec
                                        )
                                    )
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }

                Text("Oppure Esercizio Personalizzato:", style = MaterialTheme.typography.labelMedium, color = ShadowViolet)
                OutlinedTextField(
                    value = customName,
                    onValueChange = { customName = it },
                    label = { Text("Nome Esercizio") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = customSets,
                        onValueChange = { customSets = it.filter(Char::isDigit) },
                        label = { Text("Serie") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = customReps,
                        onValueChange = { customReps = it.filter(Char::isDigit) },
                        label = { Text("Reps") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = customWeight,
                        onValueChange = { customWeight = it },
                        label = { Text("Kg") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedButton(
                    onClick = {
                        if (customName.isNotBlank()) {
                            exercises.add(
                                RoutineExercise(
                                    exerciseId = "custom_${UUID.randomUUID().toString().take(8)}",
                                    name = customName.trim(),
                                    muscleGroup = customMuscle,
                                    targetSets = (customSets.toIntOrNull() ?: 3).coerceIn(1, 20),
                                    targetReps = (customReps.toIntOrNull() ?: 10).coerceIn(1, 100),
                                    suggestedWeightKg = (customWeight.toDoubleOrNull() ?: 20.0).coerceAtLeast(0.0),
                                    restSeconds = (customRest.toIntOrNull() ?: 90).coerceIn(15, 600)
                                )
                            )
                            customName = ""
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_custom_exercise_button")
                ) {
                    Text("+ AGGIUNGI ESERCIZIO ALLA SCHEDA")
                }

                if (exercises.isNotEmpty()) {
                    Text(
                        text = "Esercizi inseriti (${exercises.size}):",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                    exercises.forEachIndexed { idx, ex ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(AbyssalSurface)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ex.name, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                                Text(
                                    "${ex.targetSets} serie × ${ex.targetReps} reps (fisse) • ${ex.suggestedWeightKg} kg",
                                    style = MonoMetricSmall,
                                    color = SystemCyan
                                )
                            }
                            IconButton(onClick = { exercises.removeAt(idx) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Rimuovi", tint = FailureCrimson)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val base = initial ?: WorkoutRoutine()
                    onSave(
                        base.copy(
                            name = name.trim().ifBlank { "Scheda Hunter" },
                            splitLabel = splitLabel.trim(),
                            targetDifficultyRank = selectedRank,
                            exercises = exercises.toList()
                        )
                    )
                },
                enabled = name.isNotBlank() && exercises.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = SystemCyan, contentColor = ObsidianBg),
                modifier = Modifier.testTag("save_routine_button")
            ) {
                Text("SALVA SCHEDA")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ANNULLA", color = TextSecondary)
            }
        }
    )
}
