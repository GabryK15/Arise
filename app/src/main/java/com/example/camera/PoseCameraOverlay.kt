package com.example.camera

import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController.COORDINATE_SYSTEM_VIEW_REFERENCED
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.FailureCrimson
import com.example.ui.theme.HudCardSurface
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.QuestEmerald
import com.example.ui.theme.ShadowViolet
import com.example.ui.theme.SystemCyan
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions

@Composable
fun PoseCameraOverlay(
    onMetricsUpdated: (PoseFrameMetrics) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var useFrontCamera by remember { mutableStateOf(true) }
    var metrics by remember { mutableStateOf(PoseFrameMetrics()) }

    val poseDetector = remember {
        PoseDetection.getClient(
            PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                .build()
        )
    }

    val cameraController = remember {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
        }
    }

    DisposableEffect(poseDetector) {
        onDispose {
            poseDetector.close()
        }
    }

    DisposableEffect(lifecycleOwner, useFrontCamera) {
        cameraController.cameraSelector = if (useFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        val executor = ContextCompat.getMainExecutor(context)
        cameraController.setImageAnalysisAnalyzer(
            executor,
            MlKitAnalyzer(
                listOf(poseDetector),
                COORDINATE_SYSTEM_VIEW_REFERENCED,
                executor
            ) { result ->
                val pose = result?.getValue(poseDetector) ?: return@MlKitAnalyzer
                val analyzed = PoseFormAnalyzer.analyze(pose)
                metrics = analyzed
                onMetricsUpdated(analyzed)
            }
        )
        cameraController.bindToLifecycle(lifecycleOwner)
        onDispose {
            cameraController.clearImageAnalysisAnalyzer()
            cameraController.unbind()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ObsidianBg)
            .border(1.dp, if (metrics.qualityOk) SystemCyan.copy(alpha = 0.6f) else FailureCrimson, RoundedCornerShape(12.dp))
    ) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    controller = cameraController
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Skeleton HUD overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            val pts = metrics.landmarks
            val bones = listOf(
                PoseLandmark.LEFT_SHOULDER to PoseLandmark.RIGHT_SHOULDER,
                PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_ELBOW,
                PoseLandmark.LEFT_ELBOW to PoseLandmark.LEFT_WRIST,
                PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_ELBOW,
                PoseLandmark.RIGHT_ELBOW to PoseLandmark.RIGHT_WRIST,
                PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_HIP,
                PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_HIP,
                PoseLandmark.LEFT_HIP to PoseLandmark.RIGHT_HIP,
                PoseLandmark.LEFT_HIP to PoseLandmark.LEFT_KNEE,
                PoseLandmark.LEFT_KNEE to PoseLandmark.LEFT_ANKLE,
                PoseLandmark.RIGHT_HIP to PoseLandmark.RIGHT_KNEE,
                PoseLandmark.RIGHT_KNEE to PoseLandmark.RIGHT_ANKLE
            )
            bones.forEach { (a, b) ->
                val p1 = pts[a]
                val p2 = pts[b]
                if (p1 != null && p2 != null) {
                    drawLine(
                        color = ShadowViolet,
                        start = Offset(p1.x, p1.y),
                        end = Offset(p2.x, p2.y),
                        strokeWidth = 5f
                    )
                }
            }
            pts.values.forEach { pt ->
                drawCircle(
                    color = SystemCyan,
                    radius = 7f,
                    center = Offset(pt.x, pt.y)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "POSE HUD • Simmetria ${metrics.symmetryPercent}%",
                style = MaterialTheme.typography.labelSmall,
                color = if (metrics.qualityOk) QuestEmerald else FailureCrimson,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(HudCardSurface.copy(alpha = 0.85f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
            IconButton(
                onClick = { useFrontCamera = !useFrontCamera },
                modifier = Modifier
                    .testTag("switch_camera_button")
                    .clip(RoundedCornerShape(8.dp))
                    .background(HudCardSurface.copy(alpha = 0.85f))
            ) {
                Icon(
                    imageVector = Icons.Default.Cameraswitch,
                    contentDescription = "Cambia fotocamera",
                    tint = Color.White
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .background(ObsidianBg.copy(alpha = 0.78f))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = metrics.postureCue,
                style = MaterialTheme.typography.bodyMedium,
                color = if (metrics.qualityOk) SystemCyan else FailureCrimson
            )
        }
    }
}
