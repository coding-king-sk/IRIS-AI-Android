package com.irisx.ai.core.agent

import com.irisx.ai.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Optional cloud brain (OpenAI-compatible chat completions with tool calling).
 * Only reached when: network is up, localOnly == false, and an API key exists.
 */
class LlmClient(private val settings: SettingsStore) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    sealed interface Decision {
        data class Speak(val text: String) : Decision
        data class Call(val call: ToolCall) : Decision
        data class Failed(val reason: String) : Decision
    }

    suspend fun decide(utterance: String, tools: List<IrisTool>): Decision =
        withContext(Dispatchers.IO) {
            if (settings.apiKey.isBlank()) return@withContext Decision.Failed("NO API KEY")

            val payload = JSONObject()
                .put("model", settings.model)
                .put("messages", messages(utterance))
                .put("tools", toolSchema(tools))
                .put("tool_choice", "auto")
                .put("temperature", 0.4)

            val request = Request.Builder()
                .url(settings.baseUrl)
                .addHeader("Authorization", "Bearer ${settings.apiKey}")
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            runCatching {
                http.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@use Decision.Failed("CLOUD ${response.code}")
                    }
                    parse(body)
                }
            }.getOrElse { Decision.Failed("CLOUD UNREACHABLE") }
        }

    private fun messages(utterance: String): JSONArray {
        val persona = buildString {
            append("You are IRIS, a voice-first assistant living on the user's Android phone. ")
            append("Answer in one or two short spoken sentences. ")
            if (settings.hinglishMode) {
                append("Reply in natural Hinglish (Roman script) when the user speaks Hinglish. ")
            }
            append("Prefer calling a tool when the user asks for a device action.")
        }
        return JSONArray()
            .put(JSONObject().put("role", "system").put("content", persona))
            .put(JSONObject().put("role", "user").put("content", utterance))
    }

    private fun toolSchema(tools: List<IrisTool>): JSONArray {
        val array = JSONArray()
        tools.forEach { tool ->
            val props = JSONObject()
            tool.params.forEach { (name, desc) ->
                props.put(name, JSONObject().put("type", "string").put("description", desc))
            }
            array.put(
                JSONObject()
                    .put("type", "function")
                    .put(
                        "function",
                        JSONObject()
                            .put("name", tool.name)
                            .put("description", tool.description)
                            .put(
                                "parameters",
                                JSONObject()
                                    .put("type", "object")
                                    .put("properties", props)
                            )
                    )
            )
        }
        return array
    }

    private fun parse(body: String): Decision {
        val message = JSONObject(body)
            .optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?: return Decision.Failed("BAD RESPONSE")

        val toolCalls = message.optJSONArray("tool_calls")
        if (toolCalls != null && toolCalls.length() > 0) {
            val fn = toolCalls.getJSONObject(0).optJSONObject("function")
            val name = fn?.optString("name").orEmpty()
            val argsJson = fn?.optString("arguments").orEmpty()
            val args = mutableMapOf<String, String>()
            runCatching {
                val obj = JSONObject(if (argsJson.isBlank()) "{}" else argsJson)
                obj.keys().forEach { key -> args[key] = obj.optString(key) }
            }
            if (name.isNotBlank()) return Decision.Call(ToolCall(name, args))
        }

        val text = message.optString("content").trim()
        return if (text.isBlank()) Decision.Failed("EMPTY REPLY") else Decision.Speak(text)
    }
}
