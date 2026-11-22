package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.HunterRank
import com.example.ui.theme.AbyssalSurface
import com.example.ui.theme.FatigueAmber
import com.example.ui.theme.HudBorder
import com.example.ui.theme.HudCardSurface
import com.example.ui.theme.MonoMetricMedium
import com.example.ui.theme.MonoMetricSmall
import com.example.ui.theme.QuestEmerald
import com.example.ui.theme.RankAColor
import com.example.ui.theme.RankBColor
import com.example.ui.theme.RankCColor
import com.example.ui.theme.RankDColor
import com.example.ui.theme.RankEColor
import com.example.ui.theme.RankSColor
import com.example.ui.theme.ShadowViolet
import com.example.ui.theme.SystemCyan
import com.example.ui.theme.TextSecondary

fun rankColor(rank: HunterRank): Color = when (rank) {
    HunterRank.E -> RankEColor
    HunterRank.D -> RankDColor
    HunterRank.C -> RankCColor
    HunterRank.B -> RankBColor
    HunterRank.A -> RankAColor
    HunterRank.S -> RankSColor
}

@Composable
fun SystemPanelCard(
    modifier: Modifier = Modifier,
    glowAccent: Color = SystemCyan,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = HudCardSurface,
        border = BorderStroke(1.dp, glowAccent.copy(alpha = 0.35f)),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(
                            glowAccent.copy(alpha = 0.08f),
                            AbyssalSurface.copy(alpha = 0.45f)
                        )
                    )
                )
                .padding(16.dp),
            content = content
        )
    }
}

@Composable
fun HunterRankBadge(
    rankCode: String,
    modifier: Modifier = Modifier
) {
    val rank = HunterRank.fromCode(rankCode)
    val accent = rankColor(rank)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.16f))
            .border(1.5.dp, accent, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "RANK ${rank.code}",
            style = MaterialTheme.typography.labelLarge,
            color = accent,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun NeonXpProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    primaryColor: Color = SystemCyan,
    secondaryColor: Color = ShadowViolet
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "xp_bar"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(HudBorder)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Brush.horizontalGradient(listOf(primaryColor, secondaryColor)))
        )
    }
}

@Composable
fun StatHexPill(
    label: String,
    value: Int,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(AbyssalSurface)
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value.toString(),
            style = MonoMetricMedium,
            color = accent
        )
    }
}

@Composable
fun StatusConnectivityChip(isOnline: Boolean, modifier: Modifier = Modifier) {
    val color = if (isOnline) QuestEmerald else FatigueAmber
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            imageVector = if (isOnline) Icons.Default.CloudDone else Icons.Default.CloudOff,
            contentDescription = if (isOnline) "Cloud Online" else "Modalità Offline",
            tint = color,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = if (isOnline) "CLOUD SYNC" else "OFFLINE MODE",
            style = MonoMetricSmall,
            color = color
        )
    }
}
