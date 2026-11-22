package com.example.camera

import android.graphics.PointF
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs
import kotlin.math.atan2

data class PoseFrameMetrics(
    val landmarks: Map<Int, PointF> = emptyMap(),
    val leftKneeAngleDeg: Int? = null,
    val rightKneeAngleDeg: Int? = null,
    val leftElbowAngleDeg: Int? = null,
    val rightElbowAngleDeg: Int? = null,
    val symmetryPercent: Int = 100,
    val postureCue: String = "Inquadra il corpo intero lateralmente o frontalmente",
    val qualityOk: Boolean = true
) {
    fun toAiSummary(): String {
        val parts = mutableListOf<String>()
        leftKneeAngleDeg?.let { parts.add("Ginocchio SX ${it}°") }
        rightKneeAngleDeg?.let { parts.add("Ginocchio DX ${it}°") }
        leftElbowAngleDeg?.let { parts.add("Gomito SX ${it}°") }
        rightElbowAngleDeg?.let { parts.add("Gomito DX ${it}°") }
        parts.add("Simmetria ${symmetryPercent}%")
        parts.add("Cue: $postureCue")
        return parts.joinToString(" | ")
    }
}

object PoseFormAnalyzer {

    fun analyze(pose: Pose): PoseFrameMetrics {
        val visible = mutableMapOf<Int, PointF>()
        for (lm in pose.allPoseLandmarks) {
            if (lm.inFrameLikelihood >= 0.45f) {
                visible[lm.landmarkType] = lm.position
            }
        }
        if (visible.size < 4) {
            return PoseFrameMetrics(
                landmarks = visible,
                postureCue = "Corpo non rilevato completamente: arretra leggermente",
                qualityOk = false
            )
        }

        val lKnee = angleBetween(
            visible[PoseLandmark.LEFT_HIP],
            visible[PoseLandmark.LEFT_KNEE],
            visible[PoseLandmark.LEFT_ANKLE]
        )
        val rKnee = angleBetween(
            visible[PoseLandmark.RIGHT_HIP],
            visible[PoseLandmark.RIGHT_KNEE],
            visible[PoseLandmark.RIGHT_ANKLE]
        )
        val lElbow = angleBetween(
            visible[PoseLandmark.LEFT_SHOULDER],
            visible[PoseLandmark.LEFT_ELBOW],
            visible[PoseLandmark.LEFT_WRIST]
        )
        val rElbow = angleBetween(
            visible[PoseLandmark.RIGHT_SHOULDER],
            visible[PoseLandmark.RIGHT_ELBOW],
            visible[PoseLandmark.RIGHT_WRIST]
        )

        val lShoulder = visible[PoseLandmark.LEFT_SHOULDER]
        val rShoulder = visible[PoseLandmark.RIGHT_SHOULDER]
        val lHip = visible[PoseLandmark.LEFT_HIP]
        val rHip = visible[PoseLandmark.RIGHT_HIP]

        val shoulderTilt = if (lShoulder != null && rShoulder != null) {
            abs(lShoulder.y - rShoulder.y)
        } else 0f
        val hipTilt = if (lHip != null && rHip != null) {
            abs(lHip.y - rHip.y)
        } else 0f

        val asymmetryPenalty = ((shoulderTilt + hipTilt) * 0.25f).toInt().coerceIn(0, 40)
        val symmetry = (100 - asymmetryPenalty).coerceIn(60, 100)

        val minKnee = listOfNotNull(lKnee, rKnee).minOrNull()
        val minElbow = listOfNotNull(lElbow, rElbow).minOrNull()

        val (cue, ok) = when {
            symmetry < 82 -> "Asimmetria rilevata ($symmetry%): livella spalle e bacino" to false
            minKnee != null && minKnee <= 95 -> "Profondità squat/gambe ottima (${minKnee}°)" to true
            minElbow != null && minElbow <= 85 -> "Chiusura ROM braccia completa (${minElbow}°)" to true
            else -> "Assetto stabile • Controlla l'eccentrica" to true
        }

        return PoseFrameMetrics(
            landmarks = visible,
            leftKneeAngleDeg = lKnee,
            rightKneeAngleDeg = rKnee,
            leftElbowAngleDeg = lElbow,
            rightElbowAngleDeg = rElbow,
            symmetryPercent = symmetry,
            postureCue = cue,
            qualityOk = ok
        )
    }

    private fun angleBetween(first: PointF?, mid: PointF?, last: PointF?): Int? {
        if (first == null || mid == null || last == null) return null
        val rad = atan2((last.y - mid.y).toDouble(), (last.x - mid.x).toDouble()) -
            atan2((first.y - mid.y).toDouble(), (first.x - mid.x).toDouble())
        var deg = abs(Math.toDegrees(rad))
        if (deg > 180.0) deg = 360.0 - deg
        return deg.toInt()
    }
}
