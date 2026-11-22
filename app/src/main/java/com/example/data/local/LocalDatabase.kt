package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.model.HunterProfile
import com.example.model.LoggedSet
import com.example.model.RoutineExercise
import com.example.model.WorkoutRoutine
import com.example.model.WorkoutSession
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Date

@Entity(tableName = "local_profiles")
data class LocalProfileEntity(
    @PrimaryKey val userId: String,
    val displayName: String,
    val hunterRank: String,
    val level: Int,
    val totalXp: Int,
    val strStat: Int,
    val endStat: Int,
    val vitStat: Int,
    val agiStat: Int,
    val currentStreakDays: Int,
    val dailyTargetVolumeKg: Double,
    val dailyQuestCompleted: Boolean,
    val lastQuestDate: String,
    val updatedAtMs: Long,
    val pendingSync: Boolean = false
) {
    fun toDomain(): HunterProfile = HunterProfile(
        id = userId,
        userId = userId,
        displayName = displayName,
        hunterRank = hunterRank,
        level = level,
        totalXp = totalXp,
        strStat = strStat,
        endStat = endStat,
        vitStat = vitStat,
        agiStat = agiStat,
        currentStreakDays = currentStreakDays,
        dailyTargetVolumeKg = dailyTargetVolumeKg,
        dailyQuestCompleted = dailyQuestCompleted,
        lastQuestDate = lastQuestDate,
        updatedAt = Timestamp(Date(updatedAtMs))
    )
}

@Entity(tableName = "local_routines")
data class LocalRoutineEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val splitLabel: String,
    val targetDifficultyRank: String,
    val exercises: List<RoutineExercise>,
    val updatedAtMs: Long,
    val pendingSync: Boolean = false,
    val pendingDelete: Boolean = false
) {
    fun toDomain(): WorkoutRoutine = WorkoutRoutine(
        id = id,
        userId = userId,
        name = name,
        splitLabel = splitLabel,
        targetDifficultyRank = targetDifficultyRank,
        exercises = exercises,
        updatedAt = Timestamp(Date(updatedAtMs))
    )
}

@Entity(tableName = "local_sessions")
data class LocalSessionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val routineId: String,
    val routineName: String,
    val status: String,
    val startedAtMs: Long,
    val completedAtMs: Long,
    val totalVolumeKg: Double,
    val previousVolumeKg: Double,
    val improvementPercent: Double,
    val xpEarned: Int,
    val estimatedCalories: Int,
    val feelingNote: String,
    val healthSynced: Boolean,
    val loggedSets: List<LoggedSet>,
    val updatedAtMs: Long,
    val pendingSync: Boolean = false
) {
    fun toDomain(): WorkoutSession = WorkoutSession(
        id = id,
        userId = userId,
        routineId = routineId,
        routineName = routineName,
        status = status,
        startedAtMs = startedAtMs,
        completedAtMs = completedAtMs,
        totalVolumeKg = totalVolumeKg,
        previousVolumeKg = previousVolumeKg,
        improvementPercent = improvementPercent,
        xpEarned = xpEarned,
        estimatedCalories = estimatedCalories,
        feelingNote = feelingNote,
        healthSynced = healthSynced,
        loggedSets = loggedSets,
        updatedAt = Timestamp(Date(updatedAtMs))
    )
}

class AriseTypeConverters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromRoutineExercises(value: List<RoutineExercise>): String =
        json.encodeToString(value)

    @TypeConverter
    fun toRoutineExercises(raw: String): List<RoutineExercise> =
        if (raw.isBlank()) emptyList() else runCatching { json.decodeFromString<List<RoutineExercise>>(raw) }.getOrDefault(emptyList())

    @TypeConverter
    fun fromLoggedSets(value: List<LoggedSet>): String =
        json.encodeToString(value)

    @TypeConverter
    fun toLoggedSets(raw: String): List<LoggedSet> =
        if (raw.isBlank()) emptyList() else runCatching { json.decodeFromString<List<LoggedSet>>(raw) }.getOrDefault(emptyList())
}

@Dao
interface AriseLocalDao {
    @Query("SELECT * FROM local_profiles WHERE userId = :userId LIMIT 1")
    fun observeProfile(userId: String): Flow<LocalProfileEntity?>

    @Query("SELECT * FROM local_profiles WHERE userId = :userId LIMIT 1")
    suspend fun getProfile(userId: String): LocalProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: LocalProfileEntity)

    @Query("SELECT * FROM local_routines WHERE userId = :userId AND pendingDelete = 0 ORDER BY updatedAtMs DESC")
    fun observeRoutines(userId: String): Flow<List<LocalRoutineEntity>>

    @Query("SELECT * FROM local_routines WHERE userId = :userId AND pendingSync = 1")
    suspend fun getPendingRoutines(userId: String): List<LocalRoutineEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRoutines(routines: List<LocalRoutineEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRoutine(routine: LocalRoutineEntity)

    @Query("DELETE FROM local_routines WHERE id = :routineId")
    suspend fun hardDeleteRoutine(routineId: String)

    @Query("SELECT * FROM local_sessions WHERE userId = :userId ORDER BY startedAtMs DESC")
    fun observeSessions(userId: String): Flow<List<LocalSessionEntity>>

    @Query("SELECT * FROM local_sessions WHERE userId = :userId AND pendingSync = 1")
    suspend fun getPendingSessions(userId: String): List<LocalSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSessions(sessions: List<LocalSessionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: LocalSessionEntity)

    @Query("SELECT * FROM local_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSession(sessionId: String): LocalSessionEntity?

    @Query("DELETE FROM local_sessions WHERE id = :sessionId")
    suspend fun hardDeleteSession(sessionId: String)
}

@Database(
    entities = [LocalProfileEntity::class, LocalRoutineEntity::class, LocalSessionEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(AriseTypeConverters::class)
abstract class AriseLocalDatabase : RoomDatabase() {
    abstract fun dao(): AriseLocalDao

    companion object {
        @Volatile
        private var INSTANCE: AriseLocalDatabase? = null

        fun getInstance(context: Context): AriseLocalDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AriseLocalDatabase::class.java,
                    "arise_system_offline.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
