package com.tianji.app.data

data class LlmPreset(
    val id: String,
    val name: String,
    val baseUrl: String,
    val model: String,
    val anthropic: Boolean = false,
)

val LLM_PRESETS = listOf(
    LlmPreset("xfyun", "讯飞星火", "https://maas-api.cn-huabei-1.xf-yun.com/v2", "spark-x2.6"),
    LlmPreset("deepseek", "DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"),
    LlmPreset("qwen", "通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus"),
    LlmPreset("kimi", "Kimi", "https://api.moonshot.cn/v1", "moonshot-v1-8k"),
    LlmPreset("zhipu", "智谱", "https://open.bigmodel.cn/api/paas/v4", "glm-4-flash"),
    LlmPreset("openai", "OpenAI 兼容", "https://api.openai.com/v1", "gpt-4o-mini"),
    LlmPreset("anthropic", "Anthropic", "https://api.anthropic.com/v1", "claude-sonnet-4-5", anthropic = true),
)
