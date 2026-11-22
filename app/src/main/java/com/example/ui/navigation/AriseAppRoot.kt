package com.example.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.auth.signOut
import com.example.data.local.SecurePrefsStore
import com.example.data.local.SystemPreferences
import com.example.integrations.BiometricAuthHelper
import com.example.model.WorkoutSession
import com.example.ui.screens.ActiveWorkoutScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.BiometricLockScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ProgressScreen
import com.example.ui.screens.RoutinesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.AbyssalSurface
import com.example.ui.theme.HudCardSurface
import com.example.ui.theme.MonoMetricLarge
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.QuestEmerald
import com.example.ui.theme.SystemCyan
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.AriseViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.first
import java.util.Locale

private enum class AriseTab(
    val label: String,
    val icon: ImageVector,
    val tag: String
) {
    DASHBOARD("Status", Icons.Default.Dashboard, "nav_dashboard"),
    ROUTINES("Schede", Icons.Default.FitnessCenter, "nav_routines"),
    PROGRESS("Livelli", Icons.AutoMirrored.Filled.TrendingUp, "nav_progress"),
    SETTINGS("Sistema", Icons.Default.Settings, "nav_settings")
}

@Composable
fun AriseAppRoot() {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = { currentUser = Firebase.auth.currentUser })
    } else {
        key(user.uid) {
            BiometricProtectedShell(
                currentUserId = user.uid,
                displayName = user.displayName ?: "Hunter"
            )
        }
    }
}

@Composable
private fun BiometricProtectedShell(
    currentUserId: String,
    displayName: String
) {
    val context = LocalContext.current
    val store = remember { SecurePrefsStore(context.applicationContext) }
    var unlocked by rememberSaveable(currentUserId) { mutableStateOf(false) }
    var checkedPrefs by remember { mutableStateOf(false) }
    var bioError by remember { mutableStateOf<String?>(null) }

    fun launchBiometricPrompt() {
        val activity = context as? FragmentActivity
        if (activity != null && BiometricAuthHelper.isBiometricAvailable(activity)) {
            BiometricAuthHelper.authenticate(
                activity = activity,
                onSuccess = {
                    bioError = null
                    unlocked = true
                },
                onError = { msg ->
                    bioError = msg
                }
            )
        } else {
            unlocked = true
        }
    }

    LaunchedEffect(currentUserId) {
        val prefs = runCatching { store.preferencesFlow.first() }.getOrDefault(SystemPreferences())
        if (!prefs.biometricLockEnabled) {
            unlocked = true
        } else if (!unlocked) {
            launchBiometricPrompt()
        }
        checkedPrefs = true
    }

    if (!checkedPrefs) {
        Box(modifier = Modifier.fillMaxSize().background(ObsidianBg))
    } else if (!unlocked) {
        BiometricLockScreen(
            errorText = bioError,
            onUnlockClick = { launchBiometricPrompt() }
        )
    } else {
        AuthenticatedAppShell(currentUserId = currentUserId, displayName = displayName)
    }
}

@Composable
private fun AuthenticatedAppShell(
    currentUserId: String,
    displayName: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val vm: AriseViewModel = viewModel(
        key = currentUserId,
        factory = AriseViewModel.factory(currentUserId, displayName)
    )

    val profileState by vm.profileState.collectAsStateWithLifecycle()
    val routinesState by vm.routinesState.collectAsStateWithLifecycle()
    val sessionsState by vm.sessionsState.collectAsStateWithLifecycle()
    val preferences by vm.preferencesState.collectAsStateWithLifecycle()
    val activeWorkout by vm.activeWorkout.collectAsStateWithLifecycle()
    val completedSession by vm.completedBannerSession.collectAsStateWithLifecycle()
    val isOnline by vm.onlineStatus.collectAsStateWithLifecycle()
    val snackbarMsg by vm.snackbarMessage.collectAsStateWithLifecycle()
    val settingsState by vm.settingsState.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableStateOf(AriseTab.DASHBOARD) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMsg) {
        val msg = snackbarMsg
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            vm.consumeSnackbar()
        }
    }

    val workout = activeWorkout
    if (workout != null) {
        Scaffold(
            containerColor = ObsidianBg,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            ActiveWorkoutScreen(
                state = workout,
                onSelectExercise = vm::selectExercise,
                onUpdateWeight = vm::updateSetWeight,
                onUpdateReps = vm::updateSetActualReps,
                onUpdateRpeFailure = vm::updateSetRpeAndFailure,
                onToggleSetCompleted = vm::toggleSetCompleted,
                onStopTimer = vm::stopRestTimer,
                onAddTimerSeconds = vm::startRestTimer,
                onTogglePose = vm::togglePoseCamera,
                onPoseMetrics = vm::updatePoseMetrics,
                onFeelingChanged = vm::updateUserFeeling,
                onAskCoach = vm::askAiCoach,
                onApplyRecommendedWeight = vm::applyAiWeightRecommendation,
                onApplyExerciseSwap = vm::applyTemporaryExerciseSwap,
                onFinishWorkout = { vm.finishActiveWorkout(abandon = false) },
                onAbandonWorkout = { vm.finishActiveWorkout(abandon = true) },
                modifier = Modifier.padding(innerPadding)
            )
        }
    } else {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().background(ObsidianBg)) {
            val useRail = maxWidth >= 680.dp

            Scaffold(
                containerColor = ObsidianBg,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    if (!useRail) {
                        NavigationBar(containerColor = AbyssalSurface) {
                            AriseTab.entries.forEach { tab ->
                                NavigationBarItem(
                                    selected = currentTab == tab,
                                    onClick = { currentTab = tab },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = { Text(tab.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = SystemCyan,
                                        selectedTextColor = SystemCyan,
                                        indicatorColor = SystemCyan.copy(alpha = 0.16f),
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary
                                    ),
                                    modifier = Modifier.testTag(tab.tag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useRail) {
                        NavigationRail(containerColor = AbyssalSurface) {
                            Spacer(modifier = Modifier.height(16.dp))
                            AriseTab.entries.forEach { tab ->
                                NavigationRailItem(
                                    selected = currentTab == tab,
                                    onClick = { currentTab = tab },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = { Text(tab.label) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = SystemCyan,
                                        selectedTextColor = SystemCyan,
                                        indicatorColor = SystemCyan.copy(alpha = 0.16f),
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary
                                    ),
                                    modifier = Modifier.testTag(tab.tag)
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        when (currentTab) {
                            AriseTab.DASHBOARD -> DashboardScreen(
                                profileState = profileState,
                                routinesState = routinesState,
                                sessionsState = sessionsState,
                                isOnline = isOnline,
                                onRefreshSync = vm::refreshConnectivityAndSync,
                                onStartWorkout = vm::startWorkout,
                                onDeleteSession = vm::deleteWorkoutSession,
                                onNavigateToRoutines = { currentTab = AriseTab.ROUTINES }
                            )

                            AriseTab.ROUTINES -> RoutinesScreen(
                                routinesState = routinesState,
                                onSaveRoutine = vm::saveRoutine,
                                onDeleteRoutine = vm::deleteRoutine,
                                onStartWorkout = vm::startWorkout
                            )

                            AriseTab.PROGRESS -> ProgressScreen(
                                profileState = profileState,
                                sessionsState = sessionsState,
                                onDeleteSession = vm::deleteWorkoutSession
                            )

                            AriseTab.SETTINGS -> SettingsScreen(
                                preferences = preferences,
                                settingsState = settingsState,
                                onDiscoverAndSaveKey = vm::detectAndDiscoverModels,
                                onSelectProviderModel = vm::selectProviderAndModel,
                                onClearKey = vm::clearProviderKey,
                                onToggleBiometric = vm::setBiometricLock,
                                onToggleHealthAutoSync = vm::setHealthConnectAutoSync,
                                onRefreshHealthStatus = vm::refreshHealthStatus,
                                onUpdateNotifications = vm::updateNotificationSettings,
                                onSignOut = { signOut(credentialManager, scope) }
                            )
                        }
                    }
                }
            }
        }
    }

    completedSession?.let { session ->
        QuestClearDialog(
            session = session,
            onDismiss = vm::dismissCompletedBanner
        )
    }
}

@Composable
private fun QuestClearDialog(
    session: WorkoutSession,
    onDismiss: () -> Unit
) {
    val deltaSign = if (session.improvementPercent >= 0) "+" else ""
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HudCardSurface,
        title = {
            Text(
                text = "[ QUEST CLEAR! ]",
                style = MaterialTheme.typography.headlineSmall,
                color = QuestEmerald
            )
        },
        text = {
            Column {
                Text(
                    text = session.routineName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "+${session.xpEarned} XP GUADAGNATI",
                    style = MonoMetricLarge,
                    color = SystemCyan
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Volume Totale: ${session.totalVolumeKg.toInt()} kg ($deltaSign${String.format(Locale.US, "%.1f", session.improvementPercent)}% vs precedente)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
                Text(
                    text = "Calorie stimate: ~${session.estimatedCalories} kcal • Serie: ${session.loggedSets.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = SystemCyan, contentColor = ObsidianBg),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("CONTINUA", style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}
