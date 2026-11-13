package com.tianji.app.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tianji.app.data.AppSettingsStore
import com.tianji.app.data.HistoryStore
import com.tianji.app.data.LlmConfig
import com.tianji.app.data.LlmConfigStore
import com.tianji.app.data.LLM_PRESETS
import com.tianji.app.engine.BaziEngine
import com.tianji.app.engine.FengshuiEngine
import com.tianji.app.engine.MarriageEngine
import com.tianji.app.engine.TarotEngine
import com.tianji.app.llm.LlmClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Locale

// ---------------------------------------------------------------------------
// 输入辅助
// ---------------------------------------------------------------------------

private val SHICHEN_OPTIONS = listOf("未知", "子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥")

private val DATE_RE = Regex("""^\d{4}-\d{1,2}-\d{1,2}$""")

private fun parseDate(s: String): LocalDate {
    val t = s.trim()
    if (!DATE_RE.matches(t)) throw IllegalArgumentException("日期格式应为 YYYY-MM-DD")
    val p = t.split("-").map { it.toInt() }
    return LocalDate.of(p[0], p[1], p[2])
}

private fun shichenOrNull(s: String): String? = if (s == "未知") null else s

private val SHICHEN_TIME = mapOf(
    "子" to "23-01", "丑" to "01-03", "寅" to "03-05", "卯" to "05-07",
    "辰" to "07-09", "巳" to "09-11", "午" to "11-13", "未" to "13-15",
    "申" to "15-17", "酉" to "17-19", "戌" to "19-21", "亥" to "21-23",
)

/** 时辰选项：带公钟点提示，大众不用记子丑寅卯对应几点。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShichenChips(label: String, selected: String, onSelect: (String) -> Unit) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SHICHEN_OPTIONS.forEach { o ->
            val text = if (o == "未知") "未知" else "${o}时 ${SHICHEN_TIME[o]}"
            FilterChip(selected = selected == o, onClick = { onSelect(o) }, label = { Text(text) })
        }
    }
}

private const val BAZI_HINT =
    "本次解读基于本地八字排盘引擎输出的事实（四柱、十神、大运、流年、神煞、五行分布、原局关系）。" +
        "请按以下固定顺序分析，逐条展开、每步都引用盘面证据：\n" +
        "1. 日主强弱：先论得令（月支是否当令/生扶）、再论得地（坐下与地支通根，藏干加权）、后论得势（天干透出比劫印星多寡），三步都给结论。\n" +
        "2. 十神六亲：逐柱读十神组合与生克路径，落到性格、事业、财官、六亲。\n" +
        "3. 神煞逐条全覆盖：报告列出的每颗神煞都要点到并解释含义，不得跳过。\n" +
        "4. 五行平衡与调候：结合五行分布两口径，引用《穷通宝鉴》日主逐月调候用神（说明喜忌）。\n" +
        "5. 格局取用：按《子平真诠》以月令为主取格（月令透干者优先），旺极/衰极不成格时按《神峰通考》病药说论病与药。\n" +
        "6. 大运：逐运简评喜忌；大运天干管前五年是传统一家之言，须注明。\n" +
        "7. 流年：只谈当年趋势与注意事项，不下断语。\n" +
        "8. 宫位论：年柱祖上/早年、月柱父母兄弟、日柱自己与配偶、时柱子女晚年（依《千里命稿》），结合宫位星情交叉验证。\n" +
        "9. 历史校准与建议：把前述结论收束成 2-4 条可执行建议。\n" +
        "行文要求：明确提到所引典籍名（如「按《穷通宝鉴》…」）；排版用短段落，多用「-」列表；结尾输出「大白话总结」一段。"

private const val MARRIAGE_HINT =
    "本次解读基于本地合婚引擎的六维评分（天干合冲/地支关系/冲害刑/日柱互动/五行互补/纳音）。" +
        "重点：解释每项得分的依据，再给相处建议；分数是参考不是审判。"

private const val STICK_HINT =
    "本次解读基于本地求签引擎给出的签文。先用一句话点出签意，再结合求签人的问题给生活化建议。"

private const val TAROT_HINT =
    "本次解读基于本地塔罗引擎的抽牌结果（牌阵位置与正逆位）。请按以下纪律解读：\n" +
        "1. 逐张解读：单张先看元素与数字含义——权杖=火/行动激情、圣杯=水/情感关系、宝剑=风/思维冲突、星币=土/物质身体；数字牌旅程 Ace(种子)→2(权衡)→3(初步成形)→4(停驻)→5(冲突失落)→6(恢复馈赠)→7(评估坚持)→8(精进加速)→9(近完成)→10(完成交棒)；宫廷牌=人物状态：侍从(学习)→骑士(行动)→皇后(滋养)→国王(掌控)。\n" +
        "2. 逆位不用恐怖化，按五读法选最贴切一种：能量阻塞 / 过度内化 / 延迟 / 反转（由外转内或由内转外）/ 强度调低。\n" +
        "3. 整体结构：花色密度（3张以上主导→该元素是课题主线；某花色缺席→该领域本局缺位）；大阿卡纳比例（过半=灵魂议题、1-2张=日常事件点缀、无大卡=琐碎事务期）。\n" +
        "4. 牌与牌的关系（只输出结论，不逐条罗列推演过程）：愚人之旅相邻位、同数字牌共振、对位牌（如魔术师/女祭司、战车/力量、死神/审判）、同宫廷角色冲突、元素互作（火风相生、水土相生、火水相克、风土相克）。\n" +
        "5. 叙事弧串牌：起点(现状)→张力(阻力)→转折(关键牌)→出口(本周可执行的一个具体动作)→回响(一张收尾提示)。\n" +
        "6. 语言规则：禁止巴纳姆式放之四海皆准的话（例：「你在纠结」「需要平衡」单独成句），每个判断必须挂到具体牌或位置上；结尾给一句把主动权交还给问卜者的话（牌指方向，走不走由你）。\n" +
        "塔罗是自我梳理的工具，不是预言。"

private const val FENGSHUI_HINT =
    "本次解读基于本地风水引擎输出（命卦八宅、元运、玄空飞星盘、宅命配合、流年煞方）。" +
        "解读顺序：先玄空飞星——山星管人丁健康、向星管财禄，结合各宫旺衰与格局判断坐向利弊；再八宅——命卦四吉方如何利用、四凶方如何避开；" +
        "最后结合流年煞方（太岁/岁破/三煞/五黄/二黑）给当年注意事项，这些方位宜静不宜动。" +
        "建议优先级：峦头为先（看得见的形局），移形易位（换卧室/挪床位/换书桌位）优先于摆件化煞，不推销吉祥物。" +
        "提醒：精确坐向以罗盘实测为准，兼向超 3° 时各派主张替卦两盘并参。"

// ---------------------------------------------------------------------------
// 今日
// ---------------------------------------------------------------------------

private val QUOTES = listOf(
    "凡所有相，皆是虚妄。",
    "知命者不怨天，知己者不怨人。",
    "吉人自有天相，心安即是归处。",
    "祸福无门，惟人自召。",
)

@Composable
fun TodayScreen(
    onSettings: () -> Unit,
    onBazi: () -> Unit,
    onMarriage: () -> Unit,
    onTarot: () -> Unit,
    onFengshui: () -> Unit,
) {
    val now = remember { ZonedDateTime.now(ZoneOffset.ofHours(8)) }
    val lunarText = remember {
        runCatching {
            BaziEngine.formatLunar(BaziEngine.solarToLunar(now.year, now.monthValue, now.dayOfMonth))
        }.getOrDefault("")
    }
    val dayGz = remember {
        val (g, z) = BaziEngine.gzFromIndex(BaziEngine.dayGzIndex(now.year, now.monthValue, now.dayOfMonth))
        "$g$z"
    }
    val yearGz = remember { BaziEngine.liunianGz(now.year) }
    val quote = remember { QUOTES.random() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberTabScroll("today"))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("TIANJI", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("天机", style = MaterialTheme.typography.headlineMedium)
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "设置")
            }
        }
        Spacer(Modifier.height(12.dp))
        SectionCard {
            Row {
                Box(
                    Modifier
                        .width(2.dp)
                        .height(56.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(quote, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${yearGz}年 $lunarText · 日柱 $dayGz",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { ModuleCard(Icons.Outlined.GridView, "八字排盘", "生辰 → 命盘", onBazi) }
            Box(Modifier.weight(1f)) { ModuleCard(Icons.Outlined.FavoriteBorder, "姻缘测算", "合婚 · 求签", onMarriage) }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { ModuleCard(Icons.Outlined.Style, "塔罗占卜", "6 种牌阵", onTarot) }
            Box(Modifier.weight(1f)) { ModuleCard(Icons.Outlined.Explore, "风水堪舆", "命卦 · 八宅", onFengshui) }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "排盘由本地引擎计算 · 解读由 AI 生成 · 仅供娱乐参考",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ---------------------------------------------------------------------------
// 八字
// ---------------------------------------------------------------------------

@Composable
fun BaziScreen(onGoSettings: () -> Unit) {
    val scope = rememberCoroutineScope()
    var date by rememberSaveable { mutableStateOf("1990-05-15") }
    var shichen by rememberSaveable { mutableStateOf("未知") }
    var sex by rememberSaveable { mutableStateOf("男") }
    var err by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberTabScroll("bazi"))
            .padding(16.dp),
    ) {
        Text("八字排盘", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        SectionCard {
            OutlinedTextField(
                value = date, onValueChange = { date = it },
                label = { Text("出生日期（阳历 YYYY-MM-DD）") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            ShichenChips("时辰（两小时一辰，不确定可选未知）", shichen) { shichen = it }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("男", "女").forEach {
                    FilterChip(selected = sex == it, onClick = { sex = it }, label = { Text(it) })
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    scope.launch(Dispatchers.Default) {
                        try {
                            ChartHub.bazi = BaziEngine.compute(
                                parseDate(date),
                                shichenIn = shichenOrNull(shichen),
                                sex = sex,
                            )
                            err = null
                        } catch (e: Exception) {
                            err = e.message ?: "计算失败"
                            ChartHub.bazi = null
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("开始排盘") }
            err?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
        val r = ChartHub.bazi
        if (r != null) {
            Spacer(Modifier.height(12.dp))
            SectionCard {
                KV("阳历", r.solarText)
                KV("农历", r.lunarText)
                KV("时辰", r.shichenText)
                KV("性别", r.sex)
                KV("子时", r.zishi)
                Spacer(Modifier.height(10.dp))
                PillarGrid(
                    listOf(
                        Triple(r.yearPillar.gan, r.yearPillar.zhi, r.yearPillar.shishen),
                        Triple(r.monthPillar.gan, r.monthPillar.zhi, r.monthPillar.shishen),
                        Triple(r.dayPillar.gan, r.dayPillar.zhi, r.dayPillar.shishen),
                        Triple(r.hourPillar.gan, r.hourPillar.zhi, r.hourPillar.shishen),
                    ),
                )
                if (r.warnings.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "注意：${r.warnings.joinToString("、")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            SectionCard {
                Text("大运", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${if (r.forward) "顺排" else "逆排"} · 起运 ${r.qiyunText}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                r.dayunRows.forEach { KV(it.ages, it.gz) }
            }
            Spacer(Modifier.height(8.dp))
            AiInterpretSection(
                facts = BaziEngine.formatReport(r),
                moduleHint = BAZI_HINT,
                onGoSettings = onGoSettings,
                module = "八字",
                inputSummary = "$date · $sex · 时辰${shichen}",
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 姻缘
// ---------------------------------------------------------------------------

@Composable
fun MarriageScreen(onGoSettings: () -> Unit) {
    var mode by rememberSaveable { mutableStateOf("八字合婚") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberTabScroll("marriage"))
            .padding(16.dp),
    ) {
        Text("姻缘测算", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("八字合婚", "求签问姻缘").forEach {
                FilterChip(selected = mode == it, onClick = { mode = it }, label = { Text(it) })
            }
        }
        Spacer(Modifier.height(12.dp))
        if (mode == "八字合婚") MarriageMatchSection(onGoSettings) else MarriageStickSection(onGoSettings)
    }
}

@Composable
private fun BirthForm(
    title: String,
    date: String,
    onDate: (String) -> Unit,
    shichen: String,
    onShichen: (String) -> Unit,
    sex: String,
    onSex: (String) -> Unit,
) {
    SectionCard {
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = date, onValueChange = onDate,
            label = { Text("出生日期（阳历 YYYY-MM-DD）") },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        ShichenChips("时辰", shichen, onShichen)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("男", "女").forEach {
                FilterChip(selected = sex == it, onClick = { onSex(it) }, label = { Text(it) })
            }
        }
    }
}

@Composable
private fun MarriageMatchSection(onGoSettings: () -> Unit) {
    val scope = rememberCoroutineScope()
    var aDate by rememberSaveable { mutableStateOf("1990-05-15") }
    var aShichen by rememberSaveable { mutableStateOf("未知") }
    var aSex by rememberSaveable { mutableStateOf("男") }
    var bDate by rememberSaveable { mutableStateOf("1992-08-20") }
    var bShichen by rememberSaveable { mutableStateOf("未知") }
    var bSex by rememberSaveable { mutableStateOf("女") }
    var err by remember { mutableStateOf<String?>(null) }

    Column {
        BirthForm("甲方", aDate, { aDate = it }, aShichen, { aShichen = it }, aSex, { aSex = it })
        Spacer(Modifier.height(8.dp))
        BirthForm("乙方", bDate, { bDate = it }, bShichen, { bShichen = it }, bSex, { bSex = it })
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                scope.launch(Dispatchers.Default) {
                    try {
                        val a = BaziEngine.compute(parseDate(aDate), shichenIn = shichenOrNull(aShichen), sex = aSex)
                        val b = BaziEngine.compute(parseDate(bDate), shichenIn = shichenOrNull(bShichen), sex = bSex)
                        ChartHub.matchPair = a to b
                        ChartHub.match = MarriageEngine.match(a, b)
                        err = null
                    } catch (e: Exception) {
                        err = e.message ?: "计算失败"
                        ChartHub.matchPair = null
                        ChartHub.match = null
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("开始合婚") }
        err?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
        val p = ChartHub.matchPair
        val m = ChartHub.match
        if (p != null && m != null) {
            Spacer(Modifier.height(12.dp))
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${m.total}",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(m.band, style = MaterialTheme.typography.titleMedium)
                        Text(m.bandNote, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(12.dp))
                m.factors.forEach { f ->
                    KV("${f.dimension}（${f.weight}%）", "${f.score} 分")
                    f.notes.forEach { n ->
                        Text(
                            "· $n",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            AiInterpretSection(
                facts = MarriageEngine.matchReport(p.first, p.second, m),
                moduleHint = MARRIAGE_HINT,
                onGoSettings = onGoSettings,
                module = "合婚",
                inputSummary = "甲 $aDate($aSex) × 乙 $bDate($bSex)",
            )
        }
    }
}

@Composable
private fun MarriageStickSection(onGoSettings: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var question by rememberSaveable { mutableStateOf("") }
    var shaking by remember { mutableStateOf(false) }

    Column {
        SectionCard {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("署名（可不填）") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = question, onValueChange = { question = it },
                label = { Text("想问的姻缘问题") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { if (!shaking) { ChartHub.stick = null; shaking = true } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (shaking) "摇签中…" else "摇签") }
        }
        if (shaking) {
            Spacer(Modifier.height(12.dp))
            SectionCard {
                val transition = rememberInfiniteTransition(label = "shake")
                val rot by transition.animateFloat(
                    initialValue = -7f,
                    targetValue = 7f,
                    animationSpec = infiniteRepeatable(tween(90, easing = LinearEasing), RepeatMode.Reverse),
                    label = "rot",
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer { rotationZ = rot }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // 签支：高低错落插进筒里
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(18, 25, 21, 27, 17).forEach { h ->
                            Box(
                                Modifier
                                    .size(3.dp, h.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                        RoundedCornerShape(1.5.dp),
                                    ),
                            )
                        }
                    }
                    val tubeShape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 14.dp, bottomEnd = 14.dp)
                    Box(
                        Modifier
                            .offset(y = (-8).dp)
                            .size(52.dp, 56.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.surfaceContainerHigh,
                                        MaterialTheme.colorScheme.surfaceVariant,
                                    ),
                                ),
                                tubeShape,
                            )
                            .border(1.dp, MaterialTheme.colorScheme.outline, tubeShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("签", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "摇签中…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LaunchedEffect(Unit) {
                    delay(1500)
                    val idx = ((LocalTime.now().hour + 1) / 2) % 12
                    val no = MarriageEngine.stickNo(name, question, idx, LocalDate.now())
                    ChartHub.stick = MarriageEngine.drawStick(no)
                    shaking = false
                }
            }
        }
        val s = ChartHub.stick
        if (s != null) {
            Spacer(Modifier.height(12.dp))
            SectionCard {
                Text("第 ${s.no} 签 · ${s.grade}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Text(s.poem, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                s.jieJie?.let {
                    Spacer(Modifier.height(8.dp))
                    Text("解签：$it", style = MaterialTheme.typography.bodySmall)
                }
                s.yinyuanJie?.let {
                    Spacer(Modifier.height(4.dp))
                    Text("姻缘：$it", style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(8.dp))
            AiInterpretSection(
                facts = MarriageEngine.stickReport(s.no, name, question),
                moduleHint = STICK_HINT,
                onGoSettings = onGoSettings,
                module = "求签",
                inputSummary = "第${s.no}签 · ${if (name.isBlank()) "匿名" else name}问「${question.ifBlank { "姻缘" }}」",
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 塔罗
// ---------------------------------------------------------------------------

@Composable
fun TarotScreen(onGoSettings: () -> Unit) {
    val scope = rememberCoroutineScope()
    var spreadName by rememberSaveable { mutableStateOf("三牌阵") }
    var question by rememberSaveable { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberTabScroll("tarot"))
            .padding(16.dp),
    ) {
        Text("塔罗占卜", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        SectionCard {
            LabeledChips("牌阵", TarotEngine.SPREADS.map { it.name }, spreadName) { spreadName = it }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = question, onValueChange = { question = it },
                label = { Text("你想问什么（可不填）") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    scope.launch(Dispatchers.Default) {
                        val spread = TarotEngine.SPREADS.first { it.name == spreadName }
                        ChartHub.tarot = TarotEngine.draw(spread.key, question)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("抽牌") }
        }
        val r = ChartHub.tarot
        if (r != null) {
            Spacer(Modifier.height(12.dp))
            SectionCard {
                Text("${r.spreadName} · ${r.cards.size} 张", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    if (r.question.isBlank()) "问题：（未填写）" else "问题：${r.question}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                r.cards.chunked(5).forEachIndexed { rowIdx, rowCards ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    ) {
                        rowCards.forEachIndexed { colIdx, c ->
                            TarotCardView(c, stagger = (rowIdx * 5 + colIdx) * 130, drawKey = r)
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "抽牌种子 ${r.seed} · 时段 ${r.timeFactor}（同种子同时段可复现本局）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            AiInterpretSection(
                facts = tarotFacts(r),
                moduleHint = TAROT_HINT,
                onGoSettings = onGoSettings,
                module = "塔罗",
                inputSummary = "${r.spreadName} · ${if (r.question.isBlank()) "未填问题" else r.question}",
            )
        }
    }
}

private fun tarotFacts(r: TarotEngine.DrawResult): String {
    val lines = ArrayList<String>()
    lines.add("## 抽牌结果")
    lines.add("- 牌阵：${r.spreadName}（${r.cards.size} 张）")
    lines.add("- 问题：${if (r.question.isBlank()) "（未填写）" else r.question}")
    lines.add(
        "- 时段能量：${when (r.timeFactor) {
            "morning" -> "清晨（火、风元素增强）"
            "afternoon" -> "午后（水、土元素增强）"
            else -> "夜晚（大阿卡纳增强）"
        }}",
    )
    when (r.spreadKey) {
        "moon" -> lines.add("- 牌阵语义：新月=意图种子、上弦=行动考验、满月=觉察显现、下弦=释放放下（月相旅程）")
        "three" -> lines.add("- 牌阵语义：默认读 过去/现在/未来；若问题关于两人关系，也可改读 你/对方/关系（说明你采用了哪种）")
        "single" -> lines.add("- 牌阵语义：单张作当日透镜，回答「此刻最需要看见什么」")
    }
    for (c in r.cards) {
        lines.add("- [${c.position}] ${c.card}（${c.orientation}${if (c.isMajor) "，大阿卡纳" else ""}）")
    }
    lines.add("")
    return lines.joinToString("\n")
}

@Composable
private fun TarotCardView(c: TarotEngine.DrawnCard, stagger: Int, drawKey: Any) {
    var flipped by remember(drawKey) { mutableStateOf(false) }
    LaunchedEffect(drawKey) {
        delay(stagger.toLong())
        flipped = true
    }
    val angle by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
        label = "flip",
    )
    val showFace = angle >= 90f
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (showFace) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .size(56.dp, 92.dp)
                .graphicsLayer {
                    cameraDistance = 24f * density
                    rotationY = if (showFace) angle - 180f else angle
                },
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                if (showFace) {
                    val nameMod = Modifier.padding(horizontal = 4.dp)
                    Text(
                        c.card,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = if (c.orientation == "逆位") nameMod.graphicsLayer { rotationZ = 180f } else nameMod,
                    )
                    if (c.orientation == "逆位") {
                        Text(
                            "逆",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.TopEnd).padding(3.dp),
                        )
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(5.dp)
                            .border(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(6.dp)),
                    ) {
                        Text("✦", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "天机",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 2.sp,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Column(Modifier.alpha(if (showFace) 1f else 0f)) {
            Text(c.position, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${c.card} ${c.orientation}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 风水
// ---------------------------------------------------------------------------

private val SITTING_OPTIONS = listOf("不填") + FengshuiEngine.MOUNTAINS.map { m ->
    val s = m.toString()
    "${s}（${FengshuiEngine.PALACE_DIR[FengshuiEngine.mountainPalace(s)]}）"
}

@Composable
fun FengshuiScreen(onGoSettings: () -> Unit) {
    val scope = rememberCoroutineScope()
    var date by rememberSaveable { mutableStateOf("1990-05-15") }
    var sex by rememberSaveable { mutableStateOf("男") }
    var sitting by rememberSaveable { mutableStateOf("不填") }
    var degrees by rememberSaveable { mutableStateOf("") }
    var moveIn by rememberSaveable { mutableStateOf("") }
    var tigua by rememberSaveable { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberTabScroll("fengshui"))
            .padding(16.dp),
    ) {
        Text("风水堪舆", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        SectionCard {
            OutlinedTextField(
                value = date, onValueChange = { date = it },
                label = { Text("出生日期（阳历 YYYY-MM-DD）") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("男", "女").forEach {
                    FilterChip(selected = sex == it, onClick = { sex = it }, label = { Text(it) })
                }
            }
            Spacer(Modifier.height(8.dp))
            LabeledChips("房屋坐山（罗盘实测，可选到二十四山）", SITTING_OPTIONS, sitting) { sitting = it }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = degrees, onValueChange = { degrees = it },
                label = { Text("罗盘度数（可选，如 182.5，填了以度数为准）") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = moveIn, onValueChange = { moveIn = it },
                label = { Text("入住/大修年份（可选，如 2018，用于定宅运）") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = tigua, onClick = { tigua = !tigua },
                    label = { Text("用替卦排盘（兼向超 3° 时）") },
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    scope.launch(Dispatchers.Default) {
                        try {
                            val off = degrees.trim().toDoubleOrNull()
                            val mountain = when {
                                off != null -> FengshuiEngine.degToMountain(off).first
                                sitting != "不填" -> sitting.substring(0, 1)
                                else -> null
                            }
                            val offForJian = off?.let { FengshuiEngine.degToMountain(it).second }
                            val moveInYear = moveIn.trim().toIntOrNull()?.takeIf { it in 1864..2100 }
                            ChartHub.fengshui = mountain to FengshuiEngine.bazhai(
                                parseDate(date), sex == "男", mountain, tigua, offForJian, moveInYear,
                            )
                            err = null
                        } catch (e: Exception) {
                            err = e.message ?: "计算失败"
                            ChartHub.fengshui = null
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("开始分析") }
            err?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
        val h = ChartHub.fengshui
        if (h != null) {
            val (mountain, r) = h
            Spacer(Modifier.height(12.dp))
            SectionCard {
                Text("命卦", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                KV("出生年（立春分年）", "${r.minGua.year}年")
                if (r.minGua.jigong.isNotEmpty()) KV("寄宫", r.minGua.jigong)
                KV("本命卦", "${r.minGua.gua}（${r.minGua.number}数 · ${r.minGua.group}命）")
                Spacer(Modifier.height(8.dp))
                Text("命卦游年（八宅）", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                FengshuiEngine.YOUNIAN_ORDER.forEach { name ->
                    val gua = r.guaTable[name]
                    if (gua != null) {
                        KV(name, "${gua}方（${FengshuiEngine.PALACE_DIR[gua]}）· ${FengshuiEngine.YOUNIAN_NOTE[name] ?: ""}")
                    }
                }
                Spacer(Modifier.height(8.dp))
                KV("当前元运", "下元${r.currentYun}运（${r.yunRangeText}）")
                r.chart?.let { if (it.yun != r.currentYun) KV("宅运（按入住年立极）", "${it.yun}运 · 宅运盘不随交运改变") }
                KV("今年", "${r.yearGz}年（${r.yearShengxiao}）· 太岁在${r.yearZhi}")
                val ys = r.yearSha
                KV("岁破", ys.suipo)
                KV("三煞", ys.sansha)
                KV("流年五黄 / 二黑", "${ys.wuhuang}方 / ${ys.erhei}方")
            }
            r.chart?.let { ch ->
                Spacer(Modifier.height(8.dp))
                SectionCard {
                    Text(
                        "玄空飞星盘 · ${ch.yun}运 坐${ch.sit}向${ch.facing}${if (ch.tigua) "（替卦）" else ""}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(4.dp))
                    if (ch.patterns.isNotEmpty()) KV("格局", ch.patterns.joinToString("；"))
                    FengshuiEngine.PALACES_NINE.forEach { pal ->
                        KV(
                            "${pal}（${FengshuiEngine.PALACE_DIR[pal]}）",
                            "运${ch.yunpan[pal]} 山${ch.shan[pal]} 向${ch.xiang[pal]}",
                        )
                    }
                }
            }
            val ht = r.houseTable
            if (ht != null && r.houseGua != null && mountain != null) {
                Spacer(Modifier.height(8.dp))
                SectionCard {
                    Text(
                        "宅卦 · 坐${mountain}山（${r.houseGua}宅 · ${r.houseGroup}）",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(4.dp))
                    r.zhaiMingMatch?.let {
                        KV(
                            "宅命配合",
                            if (it) "宅与命同为${r.minGua.group}，相配" else "东西四不合，宅命不配（详见解读）",
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    FengshuiEngine.YOUNIAN_ORDER.forEach { name ->
                        val gua = ht[name]
                        if (gua != null) KV(name, "${gua}方（${FengshuiEngine.PALACE_DIR[gua]}）")
                    }
                }
            }
            if (r.jian.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                SectionCard {
                    Text("立向提示", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    r.jian.forEach { Text("⚠ $it", style = MaterialTheme.typography.bodySmall) }
                }
            }
            Spacer(Modifier.height(8.dp))
            AiInterpretSection(
                facts = FengshuiEngine.bazhaiReport(r, mountain),
                moduleHint = FENGSHUI_HINT,
                onGoSettings = onGoSettings,
                module = "风水",
                inputSummary = "$date · $sex · ${if (mountain == null) "未填坐山" else "坐${mountain}山"}",
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 设置
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onHistory: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved by remember { LlmConfigStore.flow(context) }.collectAsState(initial = LlmConfig())
    val themeMode by remember { AppSettingsStore.flow(context) }.collectAsState(initial = "system")
    var provider by remember(saved.provider) { mutableStateOf(saved.provider) }
    var baseUrl by remember(saved.baseUrl) { mutableStateOf(saved.baseUrl) }
    var apiKey by remember(saved.apiKey) { mutableStateOf(saved.apiKey) }
    var model by remember(saved.model) { mutableStateOf(saved.model) }
    var testing by remember { mutableStateOf(false) }
    var testOk by remember { mutableStateOf<Long?>(null) }
    var testedKey by remember { mutableStateOf<String?>(null) }
    var testErr by remember { mutableStateOf<String?>(null) }
    var savedHint by remember { mutableStateOf(false) }

    val currentKey = listOf(provider, baseUrl.trim(), apiKey.trim(), model.trim()).joinToString("|")
    val canTest = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank() && !testing
    val canSave = testOk != null && testedKey == currentKey && !testing

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("设置", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(12.dp))
        SectionCard {
            Text("模型服务", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                LLM_PRESETS.forEach { p ->
                    FilterChip(
                        selected = provider == p.id,
                        onClick = {
                            provider = p.id
                            baseUrl = p.baseUrl
                            model = p.model
                            testOk = null
                            testErr = null
                            savedHint = false
                        },
                        label = { Text(p.name) },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = baseUrl, onValueChange = { baseUrl = it; testOk = null; testErr = null; savedHint = false },
                label = { Text("API 地址（含版本路径）") },
                placeholder = { Text("https://api.deepseek.com/v1") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            var showKey by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = apiKey, onValueChange = { apiKey = it; testOk = null; testErr = null; savedHint = false },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { showKey = !showKey }) {
                        Text(if (showKey) "隐藏" else "显示", style = MaterialTheme.typography.labelMedium)
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = model, onValueChange = { model = it; testOk = null; testErr = null; savedHint = false },
                label = { Text("模型名") },
                placeholder = { Text("deepseek-chat / glm-4-flash / qwen-plus …") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val keyAtClick = currentKey
                            testing = true
                            testOk = null
                            testErr = null
                            val res = LlmClient.test(LlmConfig(baseUrl, apiKey, model, provider))
                            testing = false
                            res.onSuccess { testOk = it; testedKey = keyAtClick }
                                .onFailure { testErr = it.message ?: "连接失败" }
                        }
                    },
                    enabled = canTest,
                ) { Text(if (testing) "测试中…" else "测试连接") }
                if (testing) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            }
            testOk?.let {
                Spacer(Modifier.height(6.dp))
                Text("连接成功 · ${it}ms", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            testErr?.let {
                Spacer(Modifier.height(6.dp))
                Text("连接失败：$it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    scope.launch {
                        LlmConfigStore.save(context, LlmConfig(baseUrl, apiKey, model, provider))
                        savedHint = true
                    }
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("保存") }
            if (testOk == null && !testing) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "填好后先「测试连接」，通过后即可保存。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (testOk != null && testedKey != currentKey) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "配置已修改，请重新测试后再保存。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (savedHint) {
                Spacer(Modifier.height(6.dp))
                Text("已保存 · 仅存储在本机", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(12.dp))
        SectionCard {
            Text("主题", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf("system" to "跟随系统", "dark" to "深色", "light" to "浅色").forEach { (v, name) ->
                    FilterChip(
                        selected = themeMode == v,
                        onClick = { scope.launch { AppSettingsStore.save(context, v) } },
                        label = { Text(name) },
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        ModuleCard(Icons.Outlined.History, "解读历史", "过往的 AI 解读记录", onHistory)
        Spacer(Modifier.height(12.dp))
        Text(
            "出生信息只保存在本机；发送给 LLM 的仅为排盘所需最小字段；解读记录同样仅保存在本机。所有解读仅供娱乐参考。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------
// 解读历史
// ---------------------------------------------------------------------------

private fun historyTime(t: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(java.util.Date(t))

@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val entries by remember { HistoryStore.flow(context) }.collectAsState(initial = emptyList())

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("解读历史", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(12.dp))
        if (entries.isEmpty()) {
            SectionCard {
                Text(
                    "还没有解读记录。完成一次「开始解读」后会自动保存在这里，仅保存在本机。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        entries.forEach { e ->
            var open by remember(e.time) { mutableStateOf(false) }
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        e.module,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        historyTime(e.time),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(e.input.ifBlank { "（无输入）" }, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                if (open) InterpretationBody(e.result) else Text(
                    e.result,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = { open = !open }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text(if (open) "收起" else "展开全文")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (entries.isNotEmpty()) {
            OutlinedButton(
                onClick = { scope.launch { HistoryStore.clear(context) } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("清空记录") }
        }
    }
}
