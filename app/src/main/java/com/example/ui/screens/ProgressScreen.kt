package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.HunterProfile
import com.example.model.HunterProgressionEngine
import com.example.model.HunterRank
import com.example.model.WorkoutSession
import com.example.ui.components.SystemPanelCard
import com.example.ui.components.rankColor
import com.example.ui.theme.AbyssalSurface
import com.example.ui.theme.FailureCrimson
import com.example.ui.theme.FatigueAmber
import com.example.ui.theme.HudCardSurface
import com.example.ui.theme.MonoMetricMedium
import com.example.ui.theme.MonoMetricSmall
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.QuestEmerald
import com.example.ui.theme.ShadowViolet
import com.example.ui.theme.SystemCyan
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProgressScreen(
    profileState: UiState<HunterProfile>,
    sessionsState: UiState<List<WorkoutSession>>,
    onDeleteSession: (WorkoutSession) -> Unit,
    modifier: Modifier = Modifier
) {
    var sessionPendingDelete by remember { mutableStateOf<WorkoutSession?>(null) }
    val profile = (profileState as? UiState.Success)?.data
    val sessions = (sessionsState as? UiState.Success)?.data.orEmpty()
        .filter { it.status == "COMPLETED" }

    val personalRecords = sessions
        .flatMap { it.loggedSets }
        .groupBy { it.exerciseName }
        .mapValues { (_, sets) ->
            sets.maxByOrNull { HunterProgressionEngine.estimatedOneRepMax(it.weightKg, it.actualReps) }
        }
        .entries
        .mapNotNull { (name, best) -> best?.let { name to it } }
        .sortedByDescending { it.second.weightKg }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(modifier = Modifier.widthIn(max = 720.dp)) {
                Text("AWAKENING LOG", style = MaterialTheme.typography.labelLarge, color = SystemCyan)
                Text("LIVELLI & PROGRESSI", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            }
        }

        item {
            val calcLevel = if (profile != null) {
                HunterProgressionEngine.levelAndRemainderFromXp(profile.totalXp).first
            } else {
                1
            }
            val currentRank = HunterRank.fromLevel(calcLevel)
            val nextRank = HunterRank.entries.firstOrNull { it.minLevel > calcLevel }
            SystemPanelCard(
                modifier = Modifier.widthIn(max = 720.dp),
                glowAccent = ShadowViolet
            ) {
                Text("SCALA GRADI HUNTER", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HunterRank.entries.forEach { r ->
                        val active = r == currentRank
                        val unlocked = calcLevel >= r.minLevel
                        val accent = rankColor(r)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (active) accent.copy(alpha = 0.24f) else AbyssalSurface)
                                .border(
                                    width = if (active) 1.5.dp else 1.dp,
                                    color = if (unlocked) accent else TextSecondary.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = r.code,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (unlocked) accent else TextSecondary
                            )
                            Text(
                                text = "Lv${r.minLevel}",
                                style = MonoMetricSmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = if (nextRank != null) {
                        "Prossimo grado: Rank ${nextRank.code} (${nextRank.titleIt}) al Lv.${nextRank.minLevel}"
                    } else {
                        "Grado massimo raggiunto: Rank S (Monarca)"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }

        item {
            SystemPanelCard(
                modifier = Modifier.widthIn(max = 720.dp),
                glowAccent = SystemCyan
            ) {
                Text("ANDAMENTO VOLUME (KG)", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Confronto progressivo delle ultime sessioni completate",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(14.dp))
                if (sessions.size < 2) {
                    Text(
                        text = "Completa almeno 2 sessioni per visualizzare la curva di progressione.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                } else {
                    VolumeProgressionChart(
                        volumes = sessions.take(10).reversed().map { it.totalVolumeKg.toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                    )
                }
            }
        }

        if (personalRecords.isNotEmpty()) {
            item {
                SystemPanelCard(
                    modifier = Modifier.widthIn(max = 720.dp),
                    glowAccent = FatigueAmber
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = FatigueAmber)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("RECORD PERSONALI (1RM STIMATO)", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    personalRecords.take(8).forEach { (exerciseName, bestSet) ->
                        val est1Rm = HunterProgressionEngine.estimatedOneRepMax(bestSet.weightKg, bestSet.actualReps)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(exerciseName, style = MaterialTheme.typography.bodyMedium, color = Color.White, modifier = Modifier.weight(1f))
                            Text(
                                text = "${bestSet.weightKg}kg × ${bestSet.actualReps} (1RM ~${est1Rm.toInt()}kg)",
                                style = MonoMetricSmall,
                                color = FatigueAmber
                            )
                        }
                    }
                }
            }
        }

        item {
            Column(modifier = Modifier.widthIn(max = 720.dp)) {
                Text("STORICO COMPLETO", style = MaterialTheme.typography.titleLarge, color = Color.White)
            }
        }

        if (sessions.isEmpty()) {
            item {
                SystemPanelCard(modifier = Modifier.widthIn(max = 720.dp)) {
                    Text(
                        text = "Nessuna sessione completata registrata.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        } else {
            items(sessions, key = { it.id }) { session ->
                val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALIAN)
                SystemPanelCard(
                    modifier = Modifier.widthIn(max = 720.dp),
                    glowAccent = if (session.improvementPercent >= 0) QuestEmerald else FatigueAmber
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(session.routineName, style = MaterialTheme.typography.titleMedium, color = Color.White)
                            Text(
                                text = "${fmt.format(Date(session.completedAtMs))} • ${session.loggedSets.size} serie",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${session.totalVolumeKg.toInt()} kg", style = MonoMetricMedium, color = SystemCyan)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (session.healthSynced) {
                                    Icon(
                                        imageVector = Icons.Default.HealthAndSafety,
                                        contentDescription = "Sincronizzato Health Connect",
                                        tint = QuestEmerald
                                    )
                                }
                                Text(
                                    text = "+${session.xpEarned} XP",
                                    style = MonoMetricSmall,
                                    color = QuestEmerald
                                )
                            }
                        }
                        IconButton(
                            onClick = { sessionPendingDelete = session },
                            modifier = Modifier.testTag("progress_delete_session_${session.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Elimina allenamento",
                                tint = FailureCrimson
                            )
                        }
                    }
                }
            }
        }
    }

    sessionPendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { sessionPendingDelete = null },
            containerColor = HudCardSurface,
            title = {
                Text("ELIMINARE ALLENAMENTO?", style = MaterialTheme.typography.titleLarge, color = Color.White)
            },
            text = {
                Text(
                    text = "Vuoi cancellare \"${target.routineName}\" (${target.totalVolumeKg.toInt()} kg • +${target.xpEarned} XP)? La sessione verrà rimossa dallo storico e gli XP guadagnati verranno scalati.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteSession(target)
                        sessionPendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FailureCrimson, contentColor = Color.White),
                    modifier = Modifier.testTag("confirm_delete_session_dialog_button")
                ) {
                    Text("ELIMINA", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionPendingDelete = null }) {
                    Text("ANNULLA", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun VolumeProgressionChart(
    volumes: List<Float>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val maxVal = (volumes.maxOrNull() ?: 1f).coerceAtLeast(1f)
        val minVal = (volumes.minOrNull() ?: 0f) * 0.8f
        val range = (maxVal - minVal).coerceAtLeast(1f)
        val stepX = if (volumes.size > 1) size.width / (volumes.size - 1) else size.width

        val path = Path()
        val points = volumes.mapIndexed { idx, v ->
            val x = idx * stepX
            val normalizedY = ((v - minVal) / range).coerceIn(0f, 1f)
            val y = size.height - (normalizedY * (size.height - 20f)) - 10f
            Offset(x, y)
        }

        points.forEachIndexed { idx, pt ->
            if (idx == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
        }

        drawPath(
            path = path,
            color = SystemCyan,
            style = Stroke(width = 5f, cap = StrokeCap.Round)
        )
        points.forEach { pt ->
            drawCircle(color = ShadowViolet, radius = 9f, center = pt)
            drawCircle(color = SystemCyan, radius = 5f, center = pt)
        }
    }
}
