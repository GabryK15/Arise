package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import com.example.data.local.AiProviderType
import com.example.data.local.SystemPreferences
import com.example.integrations.BiometricAuthHelper
import com.example.integrations.DailyQuestNotifier
import com.example.integrations.HealthConnectManager
import com.example.integrations.HealthConnectStatus
import com.example.ui.components.SystemPanelCard
import com.example.ui.theme.FailureCrimson
import com.example.ui.theme.FatigueAmber
import com.example.ui.theme.MonoMetricSmall
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.QuestEmerald
import com.example.ui.theme.ShadowViolet
import com.example.ui.theme.SystemCyan
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.SettingsActionState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    preferences: SystemPreferences,
    settingsState: SettingsActionState,
    onDiscoverAndSaveKey: (AiProviderType, String) -> Unit,
    onSelectProviderModel: (AiProviderType, String?) -> Unit,
    onClearKey: (AiProviderType) -> Unit,
    onToggleBiometric: (Boolean) -> Unit,
    onToggleHealthAutoSync: (Boolean) -> Unit,
    onRefreshHealthStatus: () -> Unit,
    onUpdateNotifications: (Boolean, Int, Int) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val healthManager = remember { HealthConnectManager(context) }

    var apiKeyInput by remember { mutableStateOf("") }
    var selectedProviderForKey by remember { mutableStateOf(AiProviderType.GROQ) }
    val detectedProvider = remember(apiKeyInput) { AiProviderType.detectFromApiKey(apiKeyInput) }

    val healthPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) {
        onRefreshHealthStatus()
    }

    val notifPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onUpdateNotifications(true, preferences.notificationHour, preferences.notificationMinute)
        }
    }

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
                Text("SYSTEM CONFIGURATION", style = MaterialTheme.typography.labelLarge, color = SystemCyan)
                Text("IMPOSTAZIONI & API AI", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            }
        }

        // 1. MULTI-PROVIDER AI & BYOK API SETTINGS
        item {
            SystemPanelCard(
                modifier = Modifier.widthIn(max = 720.dp),
                glowAccent = ShadowViolet
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = ShadowViolet)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("MOTORE AI & CHIAVI PERSONALI", style = MaterialTheme.typography.titleMedium, color = Color.White)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Gemini Sistema è attivo senza chiavi. Puoi aggiungere Groq, OpenAI, Claude o Gemini personale: il modello viene riconosciuto automaticamente.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text("Motore Attivo:", style = MaterialTheme.typography.labelMedium, color = SystemCyan)
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AiProviderType.entries.forEach { p ->
                        val active = preferences.activeProvider == p
                        val hasKey = p == AiProviderType.GEMINI_BUILTIN || !preferences.encryptedKeysMask[p].isNullOrBlank()
                        FilterChip(
                            selected = active,
                            onClick = {
                                if (hasKey) {
                                    onSelectProviderModel(p, preferences.selectedModels[p])
                                } else {
                                    selectedProviderForKey = p
                                }
                            },
                            label = {
                                Text(
                                    if (p == AiProviderType.GEMINI_BUILTIN) "Gemini Built-in"
                                    else p.displayName.substringBefore(" (")
                                )
                            },
                            modifier = Modifier.testTag("provider_chip_${p.id}")
                        )
                    }
                }

                val activeModel = preferences.selectedModels[preferences.activeProvider]
                    ?: preferences.activeProvider.defaultModel
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Attivo: ${preferences.activeProvider.displayName} • Modello: $activeModel",
                    style = MonoMetricSmall,
                    color = QuestEmerald
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text("Configura / Aggiorna Chiave API (Groq, OpenAI, Claude, Gemini):", style = MaterialTheme.typography.labelMedium, color = Color.White)
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AiProviderType.entries.filter { it != AiProviderType.GEMINI_BUILTIN }.forEach { p ->
                        val chosen = (detectedProvider ?: selectedProviderForKey) == p
                        FilterChip(
                            selected = chosen,
                            onClick = { selectedProviderForKey = p },
                            label = { Text(p.displayName.substringBefore(" (")) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                val effectiveProvider = detectedProvider ?: selectedProviderForKey
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text("Incolla API Key (${effectiveProvider.keyHint})") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("api_key_input")
                )

                if (detectedProvider != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Provider riconosciuto automaticamente: ${detectedProvider.displayName}",
                        style = MonoMetricSmall,
                        color = SystemCyan
                    )
                }

                val savedMask = preferences.encryptedKeysMask[effectiveProvider].orEmpty()
                if (savedMask.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Chiave cifrata salvata: $savedMask", style = MonoMetricSmall, color = TextSecondary)
                        OutlinedButton(onClick = { onClearKey(effectiveProvider) }) {
                            Text("Rimuovi")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        onDiscoverAndSaveKey(effectiveProvider, apiKeyInput)
                        apiKeyInput = ""
                    },
                    enabled = apiKeyInput.isNotBlank() && !settingsState.isDiscovering,
                    colors = ButtonDefaults.buttonColors(containerColor = SystemCyan, contentColor = ObsidianBg),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("detect_models_button")
                ) {
                    if (settingsState.isDiscovering) {
                        CircularProgressIndicator(color = ObsidianBg)
                    } else {
                        Text("RILEVA MODELLI AUTOMATICAMENTE & SALVA", style = MaterialTheme.typography.labelLarge)
                    }
                }

                settingsState.discoveredModels?.let { discovered ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Modelli disponibili (${discovered.providerName}):", style = MaterialTheme.typography.labelMedium, color = SystemCyan)
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        discovered.models.take(8).forEach { modelId ->
                            val isSelected = preferences.selectedModels[preferences.activeProvider] == modelId
                            FilterChip(
                                selected = isSelected,
                                onClick = { onSelectProviderModel(preferences.activeProvider, modelId) },
                                label = { Text(modelId) }
                            )
                        }
                    }
                }

                if (!settingsState.statusMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = settingsState.statusMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (settingsState.isError) FailureCrimson else QuestEmerald
                    )
                }
            }
        }

        // 2. BIOMETRIC AUTHENTICATION LOCK
        item {
            val bioAvailable = remember { BiometricAuthHelper.isBiometricAvailable(context) }
            SystemPanelCard(
                modifier = Modifier.widthIn(max = 720.dp),
                glowAccent = SystemCyan
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null, tint = SystemCyan)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("BLOCCO BIOMETRICO", style = MaterialTheme.typography.titleMedium, color = Color.White)
                            Text(
                                text = if (bioAvailable) "Proteggi schede e dati personali all'apertura" else "Nessuna biometria o PIN configurato sul dispositivo",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                    Switch(
                        checked = preferences.biometricLockEnabled,
                        enabled = bioAvailable,
                        onCheckedChange = onToggleBiometric,
                        modifier = Modifier.testTag("biometric_lock_switch")
                    )
                }
            }
        }

        // 3. HEALTH CONNECT INTEGRATION
        item {
            SystemPanelCard(
                modifier = Modifier.widthIn(max = 720.dp),
                glowAccent = QuestEmerald
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.HealthAndSafety, contentDescription = null, tint = QuestEmerald)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("ANDROID HEALTH CONNECT", style = MaterialTheme.typography.titleMedium, color = Color.White)
                            val desc = when (settingsState.healthStatus) {
                                HealthConnectStatus.AVAILABLE -> if (settingsState.healthPermissionsGranted) "Connesso • Sync automatico sessioni & kcal" else "Disponibile • Concedi permessi"
                                HealthConnectStatus.NOT_INSTALLED -> "Installa Health Connect dal Play Store"
                                HealthConnectStatus.NOT_SUPPORTED -> "Richiede Android 8.0+"
                            }
                            Text(desc, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        }
                    }
                    Switch(
                        checked = preferences.healthConnectAutoSync,
                        onCheckedChange = onToggleHealthAutoSync,
                        modifier = Modifier.testTag("health_sync_switch")
                    )
                }
                if (settingsState.healthStatus == HealthConnectStatus.AVAILABLE && !settingsState.healthPermissionsGranted) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { healthPermissionLauncher.launch(healthManager.requiredPermissions) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("grant_health_permissions_button")
                    ) {
                        Text("AUTORIZZA HEALTH CONNECT")
                    }
                }
            }
        }

        // 4. DAILY QUEST PUSH NOTIFICATIONS
        item {
            SystemPanelCard(
                modifier = Modifier.widthIn(max = 720.dp),
                glowAccent = FatigueAmber
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = FatigueAmber)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("NOTIFICHE DAILY QUEST", style = MaterialTheme.typography.titleMedium, color = Color.White)
                            Text(
                                text = String.format(
                                    "Promemoria obiettivo giornaliero alle %02d:%02d",
                                    preferences.notificationHour,
                                    preferences.notificationMinute
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                    Switch(
                        checked = preferences.dailyNotificationsEnabled,
                        onCheckedChange = { enable ->
                            if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    onUpdateNotifications(true, preferences.notificationHour, preferences.notificationMinute)
                                } else {
                                    notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            } else {
                                onUpdateNotifications(enable, preferences.notificationHour, preferences.notificationMinute)
                            }
                        },
                        modifier = Modifier.testTag("daily_notif_switch")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(9 to 0, 18 to 30, 20 to 0).forEach { (h, m) ->
                        FilterChip(
                            selected = preferences.notificationHour == h && preferences.notificationMinute == m,
                            onClick = { onUpdateNotifications(true, h, m) },
                            label = { Text(String.format("%02d:%02d", h, m)) }
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) {
                                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                DailyQuestNotifier.sendQuestAlert(
                                    context,
                                    "[SYSTEM] Test Notifica Quest",
                                    "Il sistema di avvisi Daily Quest è attivo e pronto!"
                                )
                            }
                        }
                    ) {
                        Text("Test")
                    }
                }
            }
        }

        // 5. SIGN OUT
        item {
            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("sign_out_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = FailureCrimson)
                Spacer(modifier = Modifier.width(8.dp))
                Text("DISCONNETTI ACCOUNT GOOGLE", color = FailureCrimson)
            }
        }
    }
}
