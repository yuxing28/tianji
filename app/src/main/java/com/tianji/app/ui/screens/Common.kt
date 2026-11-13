package com.tianji.app.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.tianji.app.data.HistoryEntry
import com.tianji.app.data.HistoryStore
import com.tianji.app.data.LlmConfig
import com.tianji.app.data.LlmConfigStore
import com.tianji.app.engine.BaziEngine
import com.tianji.app.engine.FengshuiEngine
import com.tianji.app.engine.MarriageEngine
import com.tianji.app.engine.TarotEngine
import com.tianji.app.llm.LlmClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Composable
fun SectionCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
fun ModuleCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private const val BASE_STYLE =
    "你是「天机」App 的玄学解读师。用户看到的排盘事实全部由本地确定性引擎计算，你负责把事实翻译成人话。" +
        "必须遵守：1) 只引用给出的排盘事实，禁止编造额外的干支、牌或数据；2) 温暖、克制、不下绝对化断语；" +
        "3) 术语第一次出现时用一句话顺带解释；4) 输出分两部分：先写专业解读，把该讲透的内容讲透，" +
        "篇幅由内容决定，不要压缩、不要为短而短；最后另起一段，以「大白话总结」开头，" +
        "用初中生能听懂的平常话把上面的要点讲成几句话，这一段不许再出现任何术语；" +
        "5) 全程只输出简体中文，禁止出现任何英文单词、字母或拼音，塔罗牌名一律用中文译名（如「愚者」「战车」）；最后一行固定是「以上解读仅供娱乐参考」。思考过程也请用简体中文。" +
        "像人说话，别像 AI：句子长短自然错落，别每句都差不多长；同样的词可以重复用，别刻意换同义词；" +
        "少用排比和列举，一口气别超过三个要点；别用「不是A，而是B」这类句式；" +
        "别用「以下是」「总结：」这类冒号提示语，段落之间自然过渡；" +
        "宁可深入讲两三个有依据的点，不要面面俱到。"

fun systemPrompt(moduleHint: String): String = BASE_STYLE + moduleHint

/** 解读正文排版：首行缩进两字、行距舒展、两端对齐；「大白话总结」做小节标题；免责声明弱化。 */
private val proseIndent = TextIndent(firstLine = 2.em)

private fun isBulletLine(p: String): Boolean =
    p.startsWith("-") || p.startsWith("•") || p.startsWith("·") || p.startsWith("【") || p.startsWith("「") ||
        Regex("^\\d+[.、．)）]").containsMatchIn(p.take(5))

@Composable
fun InterpretationBody(text: String) {
    val paras = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    val prose = MaterialTheme.typography.bodyLarge.copy(
        lineHeight = 28.sp,
        letterSpacing = 0.3.sp,
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        paras.forEach { p ->
            when {
                p.startsWith("大白话总结") -> {
                    Spacer(Modifier.height(2.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "大白话总结",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    val rest = p.removePrefix("大白话总结").trimStart('：', ':', ' ')
                    if (rest.isNotEmpty()) {
                        Text(
                            rest,
                            style = if (isBulletLine(rest)) prose else prose.copy(textIndent = proseIndent),
                            textAlign = TextAlign.Justify,
                        )
                    }
                }
                p.startsWith("以上解读仅供娱乐参考") -> Text(
                    p,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                isBulletLine(p) -> Text(p, style = prose)
                else -> Text(
                    p,
                    style = prose.copy(textIndent = proseIndent),
                    textAlign = TextAlign.Justify,
                )
            }
        }
    }
}

enum class AiState { IDLE, RUNNING, DONE, ERROR }

/** 应用级解读状态：按模块保存，切 tab / 进出设置不丢失，流式请求在应用级作用域继续跑。 */
object InterpretHub {
    class UiState {
        var status by mutableStateOf(AiState.IDLE)
        var text by mutableStateOf("")
        var thinking by mutableStateOf("")
        var err by mutableStateOf<String?>(null)
        var lastFacts by mutableStateOf<String?>(null)
    }

    private val states = mutableMapOf<String, UiState>()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun get(module: String): UiState = states.getOrPut(module) { UiState() }
}

/** 应用级排盘/抽签结果：切 tab、进出设置后仍在。 */
object ChartHub {
    var bazi by mutableStateOf<BaziEngine.BaziResult?>(null)
    var matchPair by mutableStateOf<Pair<BaziEngine.BaziResult, BaziEngine.BaziResult>?>(null)
    var match by mutableStateOf<MarriageEngine.MatchResult?>(null)
    var stick by mutableStateOf<MarriageEngine.Stick?>(null)
    var tarot by mutableStateOf<TarotEngine.DrawResult?>(null)
    var fengshui by mutableStateOf<Pair<String?, FengshuiEngine.BazhaiResult>?>(null)
}

/** 底部 tab 再次点击当前页时，让该页滚回顶部。 */
object AppTabs {
    val retapTicks = mutableStateMapOf<String, Int>()

    fun retap(route: String) {
        retapTicks[route] = (retapTicks[route] ?: 0) + 1
    }
}

@Composable
fun rememberTabScroll(route: String): ScrollState {
    val sc = rememberScrollState()
    LaunchedEffect(sc) {
        var seen = AppTabs.retapTicks[route] ?: 0
        snapshotFlow { AppTabs.retapTicks[route] ?: 0 }.collect { t ->
            if (t != seen) {
                seen = t
                sc.animateScrollTo(0)
            }
        }
    }
    return sc
}

private fun runInterpret(
    scope: CoroutineScope,
    context: Context,
    config: LlmConfig,
    facts: String,
    moduleHint: String,
    module: String,
    input: String,
    setState: (AiState) -> Unit,
    onDelta: (String) -> Unit,
    onReasoning: (String) -> Unit,
    onError: (String?) -> Unit,
) {
    setState(AiState.RUNNING)
    val appContext = context.applicationContext
    scope.launch(Dispatchers.IO) {
        // 显式声明事实已给全，防止模型把事实清单当成"待补材料"而回复"请把结果发过来"
        val prompt = "以下是本地引擎算出的既定事实，已确定、不可更改，请直接据此解读，不要再向用户索要任何信息：\n\n$facts"
        val result = LlmClient.stream(config, systemPrompt(moduleHint), prompt, onDelta, onReasoning)
        val out = result.getOrNull().orEmpty()
        if (result.isSuccess && out.isNotEmpty()) {
            HistoryStore.add(appContext, HistoryEntry(System.currentTimeMillis(), module, input, out))
            setState(AiState.DONE)
        } else {
            onError(
                if (result.isSuccess) "模型未返回正文（可能在深度思考阶段被断开），请重试"
                else result.exceptionOrNull()?.message
            )
            setState(AiState.ERROR)
        }
    }
}

/** 引擎事实 → LLM 流式解读。facts 为 null 时提示先完成输入。成功后自动存入解读历史。 */
@Composable
fun AiInterpretSection(
    facts: String?,
    moduleHint: String,
    onGoSettings: () -> Unit,
    label: String = "AI 解读",
    module: String = "解读",
    inputSummary: String = "",
) {
    val context = LocalContext.current
    val config by LlmConfigStore.flow(context).collectAsState(initial = LlmConfig())
    val s = InterpretHub.get(module)

    // facts 变了，上一份结果就不再对应新输入，回到初始态（进行中的等跑完再清）
    if (s.lastFacts != null && s.lastFacts != facts && s.status != AiState.RUNNING) {
        s.status = AiState.IDLE
        s.text = ""
        s.thinking = ""
        s.err = null
        s.lastFacts = null
    }

    fun go(c: LlmConfig) {
        val f = facts ?: return
        if (s.status == AiState.RUNNING) return
        s.lastFacts = f
        s.text = ""
        s.thinking = ""
        s.err = null
        runInterpret(
            InterpretHub.scope, context, c, f, moduleHint, module, inputSummary,
            { s.status = it },
            { s.text += it },
            { s.thinking += it },
            { s.err = it },
        )
    }

    @Composable
    fun ThinkingText() {
        if (s.thinking.isNotEmpty()) {
            Text("思考中…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(
                s.thinking.takeLast(140),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text("正在连接模型…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(8.dp))
            if (s.status == AiState.RUNNING) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            }
        }
        Spacer(Modifier.height(8.dp))
        if (!config.isConfigured) {
            Text(
                "尚未配置 LLM。解读需要接入一个 OpenAI 兼容接口（本地引擎排盘不依赖网络）。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onGoSettings, modifier = Modifier.fillMaxWidth()) { Text("去设置") }
            return@SectionCard
        }
        when (s.status) {
            AiState.IDLE -> {
                if (facts == null) {
                    Text(
                        "先完成上方输入并计算，即可生成 AI 解读。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Button(onClick = { go(config) }, modifier = Modifier.fillMaxWidth()) { Text("开始解读") }
                }
            }
            AiState.RUNNING, AiState.DONE -> {
                if (s.text.isNotEmpty()) {
                    InterpretationBody(s.text)
                    if (s.status == AiState.RUNNING) Text("…", style = MaterialTheme.typography.bodyLarge)
                } else if (s.status == AiState.RUNNING) {
                    ThinkingText()
                }
                if (s.status == AiState.DONE) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { go(config) }, modifier = Modifier.weight(1f)) { Text("重新解读") }
                        FilledTonalButton(
                            onClick = {
                                val shareText = buildString {
                                    append("【天机 · ")
                                    append(module)
                                    append("解读】")
                                    if (inputSummary.isNotBlank()) {
                                        append('\n')
                                        append(inputSummary)
                                    }
                                    append("\n\n")
                                    append(s.text)
                                    append('\n')
                                }
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "天机 · ${module}解读")
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                }
                                context.startActivity(Intent.createChooser(send, "分享解读"))
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("分享")
                        }
                    }
                }
            }
            AiState.ERROR -> {
                Text(
                    "解读失败：${s.err ?: "未知错误"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { go(config) }, modifier = Modifier.fillMaxWidth()) { Text("重试") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LabeledChips(
    label: String,
    options: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { o ->
            FilterChip(selected = selected == o, onClick = { onSelect(o) }, label = { Text(o) })
        }
    }
}

/** 四柱网格：一列一柱。triples = (天干, 地支, 十神)。 */
@Composable
fun PillarGrid(pillars: List<Triple<String, String, String>>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        listOf("年柱", "月柱", "日柱", "时柱").forEachIndexed { i, title ->
            val p = pillars.getOrNull(i)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Column(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(p?.first ?: "—", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
                        Text(p?.second ?: "—", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(p?.third ?: "—", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun KV(key: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            key,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp),
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
