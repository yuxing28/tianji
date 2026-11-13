package com.tianji.app.llm

import com.tianji.app.data.LlmConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

@Serializable
data class ChatMessage(val role: String, val content: String)

@Serializable
private data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = true,
)

@Serializable
private data class AnthroRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: String? = null,
    val messages: List<ChatMessage>,
    val stream: Boolean = true,
)

@Serializable
private data class StreamChunk(val choices: List<StreamChoice> = emptyList())

@Serializable
private data class StreamChoice(val delta: StreamDelta = StreamDelta())

@Serializable
private data class StreamDelta(
    val content: String? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null,
)

@Serializable
private data class AnthroEvent(
    val type: String = "",
    val delta: AnthroDelta? = null,
)

@Serializable
private data class AnthroDelta(
    val type: String? = null,
    val text: String? = null,
    val thinking: String? = null,
)

@Serializable
private data class ChatResponse(val choices: List<ChatRespChoice> = emptyList())

@Serializable
private data class ChatRespChoice(val message: ChatMessage? = null)

@Serializable
private data class AnthroResponse(val content: List<AnthroBlock> = emptyList())

@Serializable
private data class AnthroBlock(val type: String? = null, val text: String? = null)

object LlmClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        // 部分网关对 HTTP/2 的 SSE 流式响应支持不佳（连接被提前正常关闭），强制 1.1
        .protocols(listOf(Protocol.HTTP_1_1))
        .build()

    // encodeDefaults 必须为 true，否则 stream=true 这类默认值不会序列化，服务端会退回非流式响应
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mediaType = "application/json".toMediaType()

    /**
     * 流式调用。OpenAI 兼容走 /chat/completions（baseUrl 需含 /v1 或类似版本路径）；
     * Anthropic 走 /messages（baseUrl 需含 /v1）。
     */
    suspend fun stream(
        config: LlmConfig,
        system: String,
        prompt: String,
        onDelta: (String) -> Unit,
        onReasoning: (String) -> Unit = {},
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(config.isConfigured) { "LLM 未配置" }
            if (config.isAnthropic) streamAnthropic(config, system, prompt, onDelta, onReasoning)
            else streamOpenAI(config, system, prompt, onDelta, onReasoning)
        }
    }

    /** 探活：发一条极小请求，成功返回耗时毫秒。 */
    suspend fun test(config: LlmConfig): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            require(config.isConfigured) { "请先填好地址、Key 与模型名" }
            val t0 = System.currentTimeMillis()
            val content = if (config.isAnthropic) {
                val body = json.encodeToString(
                    AnthroRequest(
                        model = config.model.trim(),
                        maxTokens = 64,
                        messages = listOf(ChatMessage("user", "回复：OK")),
                        stream = false,
                    ),
                )
                val request = Request.Builder()
                    .url(config.baseUrl.trimEnd('/') + "/messages")
                    .header("x-api-key", config.apiKey.trim())
                    .header("anthropic-version", "2023-06-01")
                    .post(body.toRequestBody(mediaType))
                    .build()
                val resp = execute(request)
                json.decodeFromString<AnthroResponse>(resp)
                    .content.firstOrNull { it.type == "text" }?.text.orEmpty()
            } else {
                val body = json.encodeToString(
                    ChatRequest(
                        model = config.model.trim(),
                        messages = listOf(ChatMessage("user", "回复：OK")),
                        stream = false,
                    ),
                )
                val request = Request.Builder()
                    .url(config.baseUrl.trimEnd('/') + "/chat/completions")
                    .header("Authorization", "Bearer ${config.apiKey.trim()}")
                    .post(body.toRequestBody(mediaType))
                    .build()
                val resp = execute(request)
                json.decodeFromString<ChatResponse>(resp)
                    .choices.firstOrNull()?.message?.content.orEmpty()
            }
            require(content.isNotBlank()) { "模型未返回内容，请检查模型名" }
            System.currentTimeMillis() - t0
        }
    }

    private suspend fun execute(request: Request): String {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val detail = response.body?.string()?.take(200) ?: ""
                throw IOException("HTTP ${response.code}${if (detail.isNotBlank()) "：$detail" else ""}")
            }
            return response.body?.string() ?: throw IOException("空响应")
        }
    }

    private fun streamOpenAI(
        config: LlmConfig,
        system: String,
        prompt: String,
        onDelta: (String) -> Unit,
        onReasoning: (String) -> Unit,
    ): String {
        val body = json.encodeToString(
            ChatRequest(
                model = config.model.trim(),
                messages = listOf(
                    ChatMessage("system", system),
                    ChatMessage("user", prompt),
                ),
            ),
        )
        android.util.Log.d(
            "TianjiLLM",
            "openai req model=${config.model} sysLen=${system.length} promptLen=${prompt.length} head=${prompt.take(160).replace("\n", "|")}",
        )
        val request = Request.Builder()
            .url(config.baseUrl.trimEnd('/') + "/chat/completions")
            .header("Authorization", "Bearer ${config.apiKey.trim()}")
            .post(body.toRequestBody(mediaType))
            .build()

        client.newCall(request).execute().use { response ->
            checkResponse(response)
            val source = response.body?.source() ?: throw IOException("空响应")
            val full = StringBuilder()
            while (true) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data == "[DONE]") break
                val delta = runCatching {
                    json.decodeFromString<StreamChunk>(data)
                        .choices.firstOrNull()?.delta
                }.getOrNull()
                if (!delta?.content.isNullOrEmpty()) {
                    full.append(delta!!.content)
                    onDelta(delta.content)
                } else if (!delta?.reasoningContent.isNullOrEmpty()) {
                    onReasoning(delta!!.reasoningContent)
                }
            }
            return full.toString()
        }
    }

    private fun streamAnthropic(
        config: LlmConfig,
        system: String,
        prompt: String,
        onDelta: (String) -> Unit,
        onReasoning: (String) -> Unit,
    ): String {
        android.util.Log.d(
            "TianjiLLM",
            "anthro req model=${config.model} sysLen=${system.length} promptLen=${prompt.length} head=${prompt.take(160).replace("\n", "|")}",
        )
        val body = json.encodeToString(
            AnthroRequest(
                model = config.model.trim(),
                maxTokens = 8192,
                system = system,
                messages = listOf(ChatMessage("user", prompt)),
            ),
        )
        val request = Request.Builder()
            .url(config.baseUrl.trimEnd('/') + "/messages")
            .header("x-api-key", config.apiKey.trim())
            .header("anthropic-version", "2023-06-01")
            .post(body.toRequestBody(mediaType))
            .build()

        client.newCall(request).execute().use { response ->
            checkResponse(response)
            val source = response.body?.source() ?: throw IOException("空响应")
            val full = StringBuilder()
            while (true) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data.isEmpty()) continue
                val event = runCatching { json.decodeFromString<AnthroEvent>(data) }.getOrNull()
                    ?: continue
                when (event.type) {
                    "content_block_delta" -> {
                        val d = event.delta
                        if (!d?.text.isNullOrEmpty()) {
                            full.append(d!!.text)
                            onDelta(d.text)
                        } else if (!d?.thinking.isNullOrEmpty()) {
                            onReasoning(d!!.thinking)
                        }
                    }
                    "message_stop" -> return full.toString()
                    "error" -> throw IOException("模型返回错误：${data.take(200)}")
                }
            }
            return full.toString()
        }
    }

    private fun checkResponse(response: okhttp3.Response) {
        if (!response.isSuccessful) {
            val detail = response.body?.string()?.take(200) ?: ""
            throw IOException("HTTP ${response.code}${if (detail.isNotBlank()) "：$detail" else ""}")
        }
    }
}
