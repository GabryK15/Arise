package com.example.integrations

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import com.example.model.WorkoutSession
import java.time.Instant
import java.time.ZoneOffset

private const val TAG = "HealthConnectMgr"

enum class HealthConnectStatus {
    AVAILABLE,
    NOT_INSTALLED,
    NOT_SUPPORTED
}

class HealthConnectManager(private val context: Context) {

    val requiredPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class)
    )

    fun checkAvailability(): HealthConnectStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return HealthConnectStatus.NOT_SUPPORTED
        }
        return when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectStatus.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectStatus.NOT_INSTALLED
            else -> HealthConnectStatus.NOT_SUPPORTED
        }
    }

    suspend fun hasAllPermissions(): Boolean {
        if (checkAvailability() != HealthConnectStatus.AVAILABLE) return false
        return runCatching {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            granted.containsAll(requiredPermissions)
        }.getOrDefault(false)
    }

    suspend fun syncWorkoutSession(session: WorkoutSession): Result<Unit> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return Result.failure(UnsupportedOperationException("Health Connect richiede Android 8.0+."))
        }
        if (checkAvailability() != HealthConnectStatus.AVAILABLE) {
            return Result.failure(IllegalStateException("Health Connect non installato o non disponibile su questo dispositivo."))
        }
        return try {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            if (!granted.containsAll(requiredPermissions)) {
                return Result.failure(SecurityException("Permessi Health Connect non concessi."))
            }

            val startInstant = Instant.ofEpochMilli(session.startedAtMs.coerceAtLeast(1L))
            val endInstant = Instant.ofEpochMilli(
                session.completedAtMs.coerceAtLeast(session.startedAtMs + 60_000L)
            )
            val zoneOffset = ZoneOffset.systemDefault().rules.getOffset(startInstant)
            val deviceMeta = Metadata.manualEntry(device = Device(type = Device.TYPE_PHONE))

            val exerciseRecord = ExerciseSessionRecord(
                startTime = startInstant,
                startZoneOffset = zoneOffset,
                endTime = endInstant,
                endZoneOffset = zoneOffset,
                exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
                title = "ARISE: ${session.routineName}",
                notes = "Volume: ${session.totalVolumeKg.toInt()} kg | XP: +${session.xpEarned}",
                metadata = deviceMeta
            )

            val caloriesRecord = TotalCaloriesBurnedRecord(
                startTime = startInstant,
                startZoneOffset = zoneOffset,
                endTime = endInstant,
                endZoneOffset = zoneOffset,
                energy = Energy.kilocalories(session.estimatedCalories.toDouble().coerceAtLeast(10.0)),
                metadata = deviceMeta
            )

            client.insertRecords(listOf(exerciseRecord, caloriesRecord))
            Log.d(TAG, "Synced session ${session.id} to Health Connect")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Health Connect sync failed", e)
            Result.failure(e)
        }
    }
}
