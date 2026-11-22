package com.example.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.MainActivity
import com.example.data.local.SecurePrefsStore
import com.example.data.local.WidgetSnapshot
import kotlinx.coroutines.flow.first

class AriseSystemWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = runCatching {
            SecurePrefsStore(context).widgetSnapshotFlow.first()
        }.getOrDefault(WidgetSnapshot())

        provideContent {
            SystemWidgetContent(snapshot)
        }
    }

    @Composable
    private fun SystemWidgetContent(snapshot: WidgetSnapshot) {
        val bgObsidian = ColorProvider(Color(0xFF05070D))
        val surfaceHud = ColorProvider(Color(0xFF121A2F))
        val cyan = ColorProvider(Color(0xFF00E5FF))
        val violet = ColorProvider(Color(0xFF8B5CF6))
        val emerald = ColorProvider(Color(0xFF10B981))
        val textMain = ColorProvider(Color(0xFFF1F5F9))
        val textDim = ColorProvider(Color(0xFF94A3B8))

        val pct = if (snapshot.dailyTargetKg > 0.0) {
            ((snapshot.todayVolumeKg / snapshot.dailyTargetKg) * 100.0).toInt().coerceIn(0, 100)
        } else 0

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(bgObsidian)
                .cornerRadius(16.dp)
                .padding(14.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = GlanceModifier
                        .background(surfaceHud)
                        .cornerRadius(8.dp)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "RANK ${snapshot.rank}",
                        style = TextStyle(
                            color = cyan,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                Spacer(modifier = GlanceModifier.width(8.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = "ARISE SYSTEM",
                        style = TextStyle(
                            color = textMain,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "Lv.${snapshot.level} • Streak ${snapshot.streakDays}g",
                        style = TextStyle(
                            color = textDim,
                            fontSize = 11.sp
                        )
                    )
                }
                Text(
                    text = if (snapshot.questCompleted) "CLEAR" else "$pct%",
                    style = TextStyle(
                        color = if (snapshot.questCompleted) emerald else violet,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(10.dp))

            Text(
                text = "Daily Quest: ${snapshot.todayVolumeKg.toInt()} / ${snapshot.dailyTargetKg.toInt()} kg",
                style = TextStyle(
                    color = textMain,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = "XP Livello: ${(snapshot.xpProgress * 100).toInt()}% • Tocca per allenarti",
                style = TextStyle(
                    color = cyan,
                    fontSize = 11.sp
                )
            )
        }
    }

    companion object {
        suspend fun refreshAll(context: Context) {
            runCatching { AriseSystemWidget().updateAll(context) }
        }
    }
}

class AriseSystemWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AriseSystemWidget()
}
