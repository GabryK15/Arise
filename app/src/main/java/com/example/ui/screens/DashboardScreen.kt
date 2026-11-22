package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.HunterProfile
import com.example.model.HunterProgressionEngine
import com.example.model.HunterRank
import com.example.model.WorkoutRoutine
import com.example.model.WorkoutSession
import com.example.ui.components.HunterRankBadge
import com.example.ui.components.NeonXpProgressBar
import com.example.ui.components.StatHexPill
import com.example.ui.components.StatusConnectivityChip
import com.example.ui.components.SystemPanelCard
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
import com.example.ui.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    profileState: UiState<HunterProfile>,
    routinesState: UiState<List<WorkoutRoutine>>,
    sessionsState: UiState<List<WorkoutSession>>,
    isOnline: Boolean,
    onRefreshSync: () -> Unit,
    onStartWorkout: (WorkoutRoutine) -> Unit,
    onDeleteSession: (WorkoutSession) -> Unit,
    onNavigateToRoutines: () -> Unit,
    modifier: Modifier = Modifier
) {
    var sessionPendingDelete by remember { mutableStateOf<WorkoutSession?>(null) }

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "SYSTEM STATUS",
                            style = MaterialTheme.typography.labelLarge,
                            color = SystemCyan
                        )
                        Text(
                            text = "DASHBOARD HUNTER",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusConnectivityChip(isOnline = isOnline)
                        IconButton(
                            onClick = onRefreshSync,
                            modifier = Modifier.testTag("refresh_sync_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Sincronizza Cloud",
                                tint = SystemCyan
                            )
                        }
                    }
                }
            }
        }

        item {
            Column(modifier = Modifier.widthIn(max = 720.dp)) {
                when (profileState) {
                    is UiState.Loading -> {
                        SystemPanelCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(color = SystemCyan)
                            }
                        }
                    }
                    is UiState.Error -> {
                        SystemPanelCard(glowAccent = FailureCrimson) {
                            Text(
                                text = profileState.message,
                                color = FailureCrimson,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    is UiState.Success -> {
                        val profile = profileState.data
                        val (calcLevel, xpPct) = HunterProgressionEngine.levelAndRemainderFromXp(profile.totalXp)
                        val rank = HunterRank.fromLevel(calcLevel)
                        val sessions = (sessionsState as? UiState.Success)?.data.orEmpty()
                        val todayVol = sessions
                            .filter { it.status == "COMPLETED" && isSameDay(it.completedAtMs, System.currentTimeMillis()) }
                            .sumOf { it.totalVolumeKg }

                        HunterStatusWindowCard(
                            profile = profile,
                            rank = rank,
                            level = calcLevel,
                            xpProgress = xpPct
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        DailyQuestCard(
                            todayVolumeKg = todayVol,
                            targetVolumeKg = profile.dailyTargetVolumeKg,
                            completed = profile.dailyQuestCompleted || todayVol >= profile.dailyTargetVolumeKg,
                            streakDays = profile.currentStreakDays
                        )
                    }
                }
            }
        }

        item {
            Column(modifier = Modifier.widthIn(max = 720.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SCHEDE PRONTE (DUNGEON)",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Text(
                        text = "GESTISCI →",
                        style = MaterialTheme.typography.labelLarge,
                        color = SystemCyan,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onNavigateToRoutines)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .testTag("manage_routines_link")
                    )
                }
            }
        }

        when (routinesState) {
            is UiState.Success -> {
                val routines = routinesState.data
                if (routines.isEmpty()) {
                    item {
                        SystemPanelCard(
                            modifier = Modifier.widthIn(max = 720.dp),
                            glowAccent = ShadowViolet
                        ) {
                            Text(
                                text = "Nessuna scheda configurata.",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Crea la tua scheda personalizzata con serie, ripetizioni bloccate e suggerimento automatico dei kg.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = onNavigateToRoutines,
                                colors = ButtonDefaults.buttonColors(containerColor = ShadowViolet),
                                modifier = Modifier.testTag("empty_create_routine_button")
                            ) {
                                Text("CREA LA MIA SCHEDA", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                } else {
                    items(routines.take(4), key = { it.id }) { routine ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn() + slideInVertically { it / 4 }
                        ) {
                            RoutineQuickLaunchCard(
                                routine = routine,
                                onStart = { onStartWorkout(routine) },
                                modifier = Modifier.widthIn(max = 720.dp)
                            )
                        }
                    }
                }
            }
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
        }

        item {
            Column(modifier = Modifier.widthIn(max = 720.dp)) {
                Text(
                    text = "ULTIME SESSIONI & MIGLIORAMENTI",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
            }
        }

        when (sessionsState) {
            is UiState.Success -> {
                val completed = sessionsState.data.filter { it.status == "COMPLETED" }.take(5)
                if (completed.isEmpty()) {
                    item {
                        SystemPanelCard(modifier = Modifier.widthIn(max = 720.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.FitnessCenter,
                                    contentDescription = null,
                                    tint = SystemCyan
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Completa il tuo primo allenamento per sbloccare il confronto automatico e l'XP Hunter.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                } else {
                    items(completed, key = { it.id }) { session ->
                        SessionSummaryRowCard(
                            session = session,
                            onDeleteClick = { sessionPendingDelete = session },
                            modifier = Modifier.widthIn(max = 720.dp)
                        )
                    }
                }
            }
            else -> Unit
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
                    text = "Vuoi cancellare \"${target.routineName}\" (${target.totalVolumeKg.toInt()} kg • +${target.xpEarned} XP)? L'azione rimuoverà la sessione dallo storico e aggiornerà i tuoi XP.",
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
                    modifier = Modifier.testTag("confirm_delete_session_button")
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
private fun HunterStatusWindowCard(
    profile: HunterProfile,
    rank: HunterRank,
    level: Int,
    xpProgress: Float
) {
    SystemPanelCard(glowAccent = SystemCyan) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_system_banner),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.25f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(ObsidianBg.copy(alpha = 0.85f), Color.Transparent)
                        )
                    )
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = profile.displayName.uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Text(
                        text = "Titolo: ${rank.titleIt}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SystemCyan
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "LV. $level • ${profile.totalXp} XP",
                        style = MonoMetricMedium,
                        color = Color.White
                    )
                }
                HunterRankBadge(rankCode = rank.code)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("PROSSIMO LIVELLO", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text("${(xpProgress * 100).toInt()}%", style = MonoMetricSmall, color = SystemCyan)
        }
        Spacer(modifier = Modifier.height(6.dp))
        NeonXpProgressBar(progress = xpProgress)

        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatHexPill("STR", profile.strStat, SystemCyan, Modifier.weight(1f))
            StatHexPill("END", profile.endStat, ShadowViolet, Modifier.weight(1f))
            StatHexPill("VIT", profile.vitStat, QuestEmerald, Modifier.weight(1f))
            StatHexPill("AGI", profile.agiStat, FatigueAmber, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DailyQuestCard(
    todayVolumeKg: Double,
    targetVolumeKg: Double,
    completed: Boolean,
    streakDays: Int
) {
    val pct = if (targetVolumeKg > 0.0) (todayVolumeKg / targetVolumeKg).toFloat().coerceIn(0f, 1f) else 0f
    val accent = if (completed) QuestEmerald else ShadowViolet

    SystemPanelCard(glowAccent = accent) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = accent
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "DAILY QUEST: VOLUME GIORNALIERO",
                    style = MaterialTheme.typography.labelLarge,
                    color = accent
                )
            }
            Text(
                text = "STREAK ${streakDays}G",
                style = MonoMetricSmall,
                color = FatigueAmber
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = "${todayVolumeKg.toInt()} / ${targetVolumeKg.toInt()} kg",
                style = MonoMetricLarge,
                color = Color.White
            )
            Text(
                text = if (completed) "QUEST CLEAR +120 XP" else "${(pct * 100).toInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = if (completed) QuestEmerald else TextSecondary
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        NeonXpProgressBar(
            progress = pct,
            primaryColor = accent,
            secondaryColor = SystemCyan
        )
    }
}

@Composable
private fun RoutineQuickLaunchCard(
    routine: WorkoutRoutine,
    onStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    SystemPanelCard(modifier = modifier, glowAccent = ShadowViolet) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HunterRankBadge(rankCode = routine.targetDifficultyRank)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = routine.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${routine.splitLabel} • ${routine.exercises.size} esercizi",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Button(
                onClick = onStart,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SystemCyan,
                    contentColor = ObsidianBg
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("start_routine_${routine.id}")
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Avvia", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("AVVIA", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun SessionSummaryRowCard(
    session: WorkoutSession,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateStr = SimpleDateFormat("dd MMM • HH:mm", Locale.ITALIAN).format(Date(session.completedAtMs))
    val deltaColor = if (session.improvementPercent >= 0) QuestEmerald else FatigueAmber
    val deltaPrefix = if (session.improvementPercent >= 0) "+" else ""

    SystemPanelCard(modifier = modifier, glowAccent = deltaColor) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.routineName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Text(
                    text = "$dateStr • ${session.loggedSets.size} serie",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = null,
                        tint = deltaColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$deltaPrefix${String.format(Locale.US, "%.1f", session.improvementPercent)}%",
                        style = MonoMetricMedium,
                        color = deltaColor
                    )
                }
                Text(
                    text = "${session.totalVolumeKg.toInt()} kg • +${session.xpEarned} XP",
                    style = MonoMetricSmall,
                    color = SystemCyan
                )
            }
            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.testTag("dashboard_delete_session_${session.id}")
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

private fun isSameDay(a: Long, b: Long): Boolean {
    if (a <= 0L) return false
    val fmt = SimpleDateFormat("yyyyMMdd", Locale.US)
    return fmt.format(Date(a)) == fmt.format(Date(b))
}
