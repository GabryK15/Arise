package com.example.ai

import android.util.Log
import com.example.data.local.AiProviderType
import com.example.data.local.SecurePrefsStore
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "AiCoachGateway"

class AiCoachGateway(
    private val secureStore: SecurePrefsStore
) {
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val adviceSchema = Schema.obj(
        properties = mapOf(
            "actionType" to Schema.enumeration(
                values = listOf("INCREASE_WEIGHT", "KEEP_WEIGHT", "LOWER_WEIGHT", "STOP_EARLY", "SWAP_EXERCISE"),
                description = "Recommended action for the next set or current exercise"
            ),
            "recommendedWeightKg" to Schema.double(description = "Suggested weight in kilograms"),
            "headline" to Schema.string(description = "Short Italian tactical directive (max 10 words)"),
            "rationale" to Schema.string(description = "Concise Italian coaching explanation based on physiology and RPE"),
            "alternativeExercise" to Schema.string(description = "Optional temporary exercise swap for today if pain/fatigue reported")
        ),
        optionalProperties = listOf("alternativeExercise")
    )

    private val structuredGeminiModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = adviceSchema
            }
        )
    }

    private val streamingGeminiModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.8-flash")
    }

    private class StopCollectException : CancellationException("Snapshot read")

    suspend fun requestStructuredSetAdvice(ctx: WorkoutCoachContext): Result<SetAdvice> {
        return try {
            val (provider, apiKey, modelId) = readConfigSafely()
            val prompt = buildStructuredPrompt(ctx)

            val rawJson = withTimeout(60_000L) {
                if (provider == AiProviderType.GEMINI_BUILTIN || apiKey.isBlank()) {
                    val resp = structuredGeminiModel.generateContent(prompt)
                    resp.text ?: throw IllegalStateException("Il modello ha restituito una risposta vuota.")
                } else {
                    callByokCompletion(
                        provider = provider,
                        apiKey = apiKey,
                        modelId = modelId,
                        systemPrompt = "Sei il System AI Coach di ARISE System. Rispondi SOLO con JSON valido senza markdown, con campi: actionType (INCREASE_WEIGHT|KEEP_WEIGHT|LOWER_WEIGHT|STOP_EARLY|SWAP_EXERCISE), recommendedWeightKg (numero), headline (stringa max 10 parole in italiano), rationale (stringa in italiano), alternativeExercise (stringa).",
                        userPrompt = prompt
                    )
                }
            }

            val cleaned = extractJsonObject(rawJson)
            val json = JSONObject(cleaned)
            val advice = SetAdvice(
                actionType = json.optString("actionType", "KEEP_WEIGHT"),
                recommendedWeightKg = json.optDouble("recommendedWeightKg", ctx.currentWeightKg),
                headline = json.optString("headline", "Mantieni il carico attuale"),
                rationale = json.optString("rationale", "Concentrati sulla tecnica pulita."),
                alternativeExercise = json.optString("alternativeExercise", "")
            )
            Result.success(advice)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI advice timed out", e)
            Result.failure(Exception("Richiesta AI scaduta (timeout 60s)."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI advice failed", e)
            Result.failure(e)
        }
    }

    fun streamLiveCoachChat(ctx: WorkoutCoachContext, question: String): Flow<String> = flow {
        val (provider, apiKey, modelId) = readConfigSafely()
        val prompt = buildLiveCoachingPrompt(ctx, question)

        if (provider == AiProviderType.GEMINI_BUILTIN || apiKey.isBlank()) {
            val sb = StringBuilder()
            withTimeout(60_000L) {
                streamingGeminiModel.generateContentStream(prompt).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        sb.append(piece)
                        emit(sb.toString())
                    }
                }
            }
            if (sb.isBlank()) throw IllegalStateException("Risposta AI vuota.")
        } else {
            val fullText = withTimeout(60_000L) {
                callByokCompletion(
                    provider = provider,
                    apiKey = apiKey,
                    modelId = modelId,
                    systemPrompt = "Sei il System AI Coach stile Solo Leveling per allenamento in palestra. Dai consigli pratici, sicuri e diretti in italiano su esecuzione, gestione carico (kg), cedimento, RPE e varianti temporanee.",
                    userPrompt = prompt
                )
            }
            emit(fullText)
        }
    }.flowOn(Dispatchers.IO)

    suspend fun discoverModels(provider: AiProviderType, rawApiKey: String): Result<DiscoveredModelsResult> =
        withContext(Dispatchers.IO) {
            try {
                if (provider == AiProviderType.GEMINI_BUILTIN) {
                    return@withContext Result.success(
                        DiscoveredModelsResult(
                            providerName = provider.displayName,
                            models = listOf("gemini-3.8-flash", "gemini-3.5-flash-lite", "gemini-4-argon"),
                            recommendedModel = "gemini-3.8-flash"
                        )
                    )
                }
                val key = rawApiKey.trim()
                require(key.isNotBlank()) { "Inserisci una chiave API valida." }

                val request = when (provider) {
                    AiProviderType.GROQ -> Request.Builder()
                        .url("https://api.groq.com/openai/v1/models")
                        .addHeader("Authorization", "Bearer $key")
                        .get().build()

                    AiProviderType.OPENAI -> Request.Builder()
                        .url("https://api.openai.com/v1/models")
                        .addHeader("Authorization", "Bearer $key")
                        .get().build()

                    AiProviderType.ANTHROPIC -> Request.Builder()
                        .url("https://api.anthropic.com/v1/models")
                        .addHeader("x-api-key", key)
                        .addHeader("anthropic-version", "2023-06-01")
                        .get().build()

                    AiProviderType.GEMINI_BYOK -> Request.Builder()
                        .url("https://generativelanguage.googleapis.com/v1beta/models?key=$key")
                        .get().build()

                    AiProviderType.GEMINI_BUILTIN -> throw IllegalStateException()
                }

                val bodyStr = httpClient.newCall(request).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        throw IllegalStateException("HTTP ${resp.code}: ${text.take(180)}")
                    }
                    text
                }

                val json = JSONObject(bodyStr)
                val ids = mutableListOf<String>()
                if (provider == AiProviderType.GEMINI_BYOK) {
                    val arr = json.optJSONArray("models") ?: JSONArray()
                    for (i in 0 until arr.length()) {
                        val name = arr.optJSONObject(i)?.optString("name").orEmpty().removePrefix("models/")
                        if (name.contains("gemini")) ids.add(name)
                    }
                } else {
                    val arr = json.optJSONArray("data") ?: JSONArray()
                    for (i in 0 until arr.length()) {
                        val id = arr.optJSONObject(i)?.optString("id").orEmpty()
                        if (id.isNotBlank()) ids.add(id)
                    }
                }

                val filtered = ids.filterNot {
                    it.contains("whisper", true) || it.contains("tts", true) || it.contains("embedding", true)
                }.sorted()
                val recommended = pickRecommendedModel(provider, filtered)
                Result.success(
                    DiscoveredModelsResult(
                        providerName = provider.displayName,
                        models = filtered.ifEmpty { listOf(provider.defaultModel) },
                        recommendedModel = recommended
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Model discovery failed for ${provider.id}", e)
                Result.failure(e)
            }
        }

    private fun pickRecommendedModel(provider: AiProviderType, models: List<String>): String {
        val preferred = when (provider) {
            AiProviderType.GROQ -> listOf("llama-3.3-70b-versatile", "llama-3.1-70b-versatile", "mixtral-8x7b-32768", "llama-3.1-8b-instant")
            AiProviderType.OPENAI -> listOf("gpt-4.1", "gpt-4o", "gpt-4o-mini", "o4-mini")
            AiProviderType.ANTHROPIC -> listOf("claude-sonnet-4", "claude-3-7-sonnet", "claude-3-5-sonnet")
            AiProviderType.GEMINI_BYOK -> listOf("gemini-2.5-flash", "gemini-2.5-pro")
            AiProviderType.GEMINI_BUILTIN -> listOf("gemini-3.8-flash")
        }
        for (pref in preferred) {
            val match = models.firstOrNull { it.contains(pref, ignoreCase = true) }
            if (match != null) return match
        }
        return models.firstOrNull() ?: provider.defaultModel
    }

    private suspend fun readConfigSafely(): Triple<AiProviderType, String, String> {
        var active = AiProviderType.GEMINI_BUILTIN
        var model = active.defaultModel
        try {
            secureStore.preferencesFlow.collect { prefs ->
                active = prefs.activeProvider
                model = prefs.selectedModels[active] ?: active.defaultModel
                throw StopCollectException()
            }
        } catch (_: StopCollectException) {
            // Expected single-shot collection stop
        }
        val key = secureStore.getDecryptedApiKey(active)
        return Triple(active, key, model)
    }

    private fun callByokCompletion(
        provider: AiProviderType,
        apiKey: String,
        modelId: String,
        systemPrompt: String,
        userPrompt: String
    ): String {
        val jsonMedia = "application/json; charset=utf-8".toMediaType()
        val request = when (provider) {
            AiProviderType.GROQ, AiProviderType.OPENAI -> {
                val url = if (provider == AiProviderType.GROQ) {
                    "https://api.groq.com/openai/v1/chat/completions"
                } else {
                    "https://api.openai.com/v1/chat/completions"
                }
                val payload = JSONObject().apply {
                    put("model", modelId)
                    put("temperature", 0.35)
                    put(
                        "messages",
                        JSONArray().apply {
                            put(JSONObject().put("role", "system").put("content", systemPrompt))
                            put(JSONObject().put("role", "user").put("content", userPrompt))
                        }
                    )
                }
                Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .post(payload.toString().toRequestBody(jsonMedia))
                    .build()
            }

            AiProviderType.ANTHROPIC -> {
                val payload = JSONObject().apply {
                    put("model", modelId)
                    put("max_tokens", 700)
                    put("system", systemPrompt)
                    put(
                        "messages",
                        JSONArray().apply {
                            put(JSONObject().put("role", "user").put("content", userPrompt))
                        }
                    )
                }
                Request.Builder()
                    .url("https://api.anthropic.com/v1/messages")
                    .addHeader("x-api-key", apiKey)
                    .addHeader("anthropic-version", "2023-06-01")
                    .post(payload.toString().toRequestBody(jsonMedia))
                    .build()
            }

            AiProviderType.GEMINI_BYOK -> {
                val payload = JSONObject().apply {
                    put(
                        "contents",
                        JSONArray().put(
                            JSONObject().put(
                                "parts",
                                JSONArray().put(JSONObject().put("text", "$systemPrompt\n\n$userPrompt"))
                            )
                        )
                    )
                }
                Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent?key=$apiKey")
                    .post(payload.toString().toRequestBody(jsonMedia))
                    .build()
            }

            AiProviderType.GEMINI_BUILTIN -> throw IllegalStateException()
        }

        val responseStr = httpClient.newCall(request).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("${provider.displayName} HTTP ${resp.code}: ${raw.take(200)}")
            }
            raw
        }

        val root = JSONObject(responseStr)
        return when (provider) {
            AiProviderType.GROQ, AiProviderType.OPENAI -> {
                root.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
            }

            AiProviderType.ANTHROPIC -> {
                root.getJSONArray("content")
                    .getJSONObject(0)
                    .getString("text")
            }

            AiProviderType.GEMINI_BYOK -> {
                root.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
            }

            AiProviderType.GEMINI_BUILTIN -> ""
        }
    }

    private fun extractJsonObject(raw: String): String {
        val trimmed = raw.trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        return if (start >= 0 && end > start) trimmed.substring(start, end + 1) else trimmed
    }

    private fun buildStructuredPrompt(ctx: WorkoutCoachContext): String = """
        Analizza la serie appena eseguita o in corso in palestra e decidi la strategia ottimale mantenendo fisse serie e ripetizioni programmate salvo cedimenti o dolore.
        Esercizio: ${ctx.exerciseName} (${ctx.muscleGroup})
        Serie corrente: ${ctx.setNumber} di ${ctx.totalSets} | Ripetizioni target fisse: ${ctx.targetReps}
        Peso attuale: ${ctx.currentWeightKg} kg
        RPE percepito: ${ctx.rpe}/10 | Cedimento raggiunto: ${if (ctx.hitFailure) "SÌ" else "NO"}
        Stato/Sensazione utente: ${ctx.userFeeling.ifBlank { "Normale" }}
        Telemetria Pose Telecamera: ${ctx.poseTelemetry.ifBlank { "Non attiva (feedback manuale)" }}
        Storico recente esercizio: ${ctx.recentHistorySummary.ifBlank { "Prima rilevazione" }}
    """.trimIndent()

    private fun buildLiveCoachingPrompt(ctx: WorkoutCoachContext, question: String): String = """
        Sei l'AI Coach in tempo reale di ARISE System durante la seduta in palestra.
        Regola fondamentale: serie (${ctx.totalSets}) e ripetizioni (${ctx.targetReps}) della scheda restano invariate salvo cedimento, rischio infortunio o adattamento giornaliero mirato.
        Contesto attuale:
        - Esercizio: ${ctx.exerciseName} (${ctx.muscleGroup}), Serie ${ctx.setNumber}/${ctx.totalSets}, Carico ${ctx.currentWeightKg} kg
        - RPE: ${ctx.rpe}/10, Cedimento: ${if (ctx.hitFailure) "SÌ" else "NO"}
        - Sensazione fisica: ${ctx.userFeeling.ifBlank { "Non specificata" }}
        - Analisi Pose Telecamera: ${ctx.poseTelemetry.ifBlank { "Feedback manuale" }}
        - Storico recente: ${ctx.recentHistorySummary.ifBlank { "Nessuno" }}

        Domanda / Segnalazione dell'atleta: "$question"

        Fornisci in italiano una risposta chiara, concreta e breve (max 4 punti):
        1) Corretta esecuzione / respirazione / cue tecnico
        2) Cosa fare subito con il carico (mantenere, alzare kg, abbassare kg o fermarsi prima)
        3) Eventuale modifica solo per oggi se l'atleta è stanco o avverte fastidio.
    """.trimIndent()
}
