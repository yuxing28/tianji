package com.tianji.app.engine

import java.time.LocalDate
import kotlin.math.abs

/**
 * 风水模块 —— 自 docs/reference/fengshui/scripts_kanyu_core.py 与 scripts_kanyu_bazhai.py
 * 移植（MIT）：二十四山、命卦（立春分年）、游年八宅、洛书飞泊、元运。
 */
object FengshuiEngine {

    private const val GAN = "甲乙丙丁戊己庚辛壬癸"
    private const val ZHI = "子丑寅卯辰巳午未申酉戌亥"
    private const val SHENGXIAO = "鼠牛虎兔龙蛇马羊猴鸡狗猪"

    // 二十四山，自壬起顺时针；第 i 山中心度数 = (345 + 15i) % 360
    const val MOUNTAINS = "壬子癸丑艮寅甲卯乙辰巽巳丙午丁未坤申庚酉辛戌乾亥"
    private val PALACES = listOf("坎", "艮", "震", "巽", "离", "坤", "兑", "乾")
    val PALACES_NINE = PALACES + "中"
    private val PALACE_NUM = mapOf(
        "坎" to 1, "坤" to 2, "震" to 3, "巽" to 4, "中" to 5,
        "乾" to 6, "兑" to 7, "艮" to 8, "离" to 9,
    )
    private val NUM_PALACE = PALACE_NUM.entries.associate { (k, v) -> v to k }
    val PALACE_DIR = mapOf(
        "坎" to "北", "艮" to "东北", "震" to "东", "巽" to "东南",
        "离" to "南", "坤" to "西南", "兑" to "西", "乾" to "西北", "中" to "中",
    )

    val STAR_NAMES = mapOf(
        1 to "一白贪狼", 2 to "二黑巨门", 3 to "三碧禄存", 4 to "四绿文曲", 5 to "五黄廉贞",
        6 to "六白武曲", 7 to "七赤破军", 8 to "八白左辅", 9 to "九紫右弼",
    )

    // 洛书飞泊路径：中→乾→兑→艮→离→坎→坤→震→巽
    private val LUOSHU_PATH = listOf("中", "乾", "兑", "艮", "离", "坎", "坤", "震", "巽")

    // ------------------------------------------------------------------
    // 二十四山
    // ------------------------------------------------------------------

    private fun mountainIndex(m: String): Int {
        val i = MOUNTAINS.indexOf(m)
        require(i >= 0) { "未知山名: $m（应为二十四山之一: $MOUNTAINS）" }
        return i
    }

    fun mountainPalace(m: String): String = PALACES[mountainIndex(m) / 3]

    fun degToMountain(degIn: Double): Pair<String, Double> {
        val deg = ((degIn % 360) + 360) % 360
        val i = (((deg - 337.5 + 360) % 360) / 15).toInt()
        val m = MOUNTAINS[i].toString()
        val center = (345 + 15.0 * i) % 360
        val off = (((deg - center + 180) % 360) + 360) % 360 - 180
        return m to off
    }

    /** 接受山名（如「子」）或度数（如「182.5」）。返回 (山, 度数或 null)。 */
    fun parseDirection(s: String): Pair<String, Double?> {
        val t = s.trim().trimEnd('°', '度')
        if (t.length == 1 && t[0] in MOUNTAINS) return t to null
        val deg = t.toDoubleOrNull() ?: throw IllegalArgumentException("无法识别方向 \"$s\"：请给二十四山名或罗盘度数")
        val (m, _) = degToMountain(deg)
        return m to ((deg % 360) + 360) % 360
    }

    // ------------------------------------------------------------------
    // 九宫飞泊 / 元运
    // ------------------------------------------------------------------

    /** 从中宫起 center 星，沿洛书路径顺飞或逆飞。 */
    fun fly(center: Int, forward: Boolean = true): Map<String, Int> {
        val out = LinkedHashMap<String, Int>()
        LUOSHU_PATH.forEachIndexed { k, pal ->
            val step = if (forward) k else -k
            out[pal] = ((center - 1 + step) % 9 + 9) % 9 + 1
        }
        return out
    }

    /** 三元九运：1864 年起一运，每运 20 年（年份以立春为界）。 */
    fun yunOfYear(year: Int): Int = Math.floorMod((year - 1864) / 20, 9) + 1

    fun yunRange(yun: Int): Pair<Int, Int> {
        val start = 1864 + (yun - 1) * 20
        return start to start + 19
    }

    fun ganzhiOfYear(year: Int): String = "${GAN[Math.floorMod(year - 4, 10)]}${ZHI[Math.floorMod(year - 4, 12)]}"

    fun zhiOfYear(year: Int): String = ZHI[Math.floorMod(year - 4, 12)].toString()

    fun shengxiaoOfYear(year: Int): String = SHENGXIAO[Math.floorMod(year - 4, 12)].toString()

    // ------------------------------------------------------------------
    // 八宅：命卦 + 游年
    // ------------------------------------------------------------------

    private val BAZHAI_ORDER = listOf("乾", "坎", "艮", "震", "巽", "离", "坤", "兑")

    // 游年歌诀（《八宅明镜》）：自本卦之后依 坎艮震巽离坤兑乾 顺序读出
    private val YOUNIAN_SONG = mapOf(
        "乾" to "六天五祸绝延生", "坎" to "五天生延绝祸六", "艮" to "六绝祸生延天五",
        "震" to "延生祸绝五天六", "巽" to "天五六祸生绝延", "离" to "六五绝延祸生天",
        "坤" to "天延绝生祸五六", "兑" to "生祸延绝六五天",
    )
    private val YOUNIAN_NAMES = mapOf(
        "生" to "生气", "延" to "延年", "天" to "天医", "祸" to "祸害",
        "六" to "六煞", "五" to "五鬼", "绝" to "绝命",
    )
    val YOUNIAN_ORDER = listOf("生气", "延年", "天医", "伏位", "祸害", "六煞", "五鬼", "绝命")
    private val YOUNIAN_JI = setOf("生气", "延年", "天医", "伏位")
    val YOUNIAN_NOTE = mapOf(
        "生气" to "上吉（贪狼木）", "延年" to "上吉（武曲金）", "天医" to "中吉（巨门土）",
        "伏位" to "小吉（辅弼木）", "祸害" to "小凶（禄存土）", "六煞" to "次凶（文曲水）",
        "五鬼" to "大凶（廉贞火）", "绝命" to "大凶（破军金）",
    )
    private val EAST_FOUR = setOf("坎", "离", "震", "巽")

    /** 返回 {游年名: 方位卦}。 */
    fun younian(gua: String): Map<String, String> {
        val i = BAZHAI_ORDER.indexOf(gua)
        val others = (1..7).map { BAZHAI_ORDER[(i + it) % 8] }
        val table = mutableMapOf("伏位" to gua)
        others.forEachIndexed { idx, t ->
            table[YOUNIAN_NAMES[YOUNIAN_SONG[gua]!![idx].toString()]!!] = t
        }
        return table
    }

    fun eastWest(gua: String): String = if (gua in EAST_FOUR) "东四" else "西四"

    /** 未寄宫的命卦数（1–9）。 */
    fun minguaRaw(year: Int, male: Boolean): Int {
        val n = if (male) {
            Math.floorMod(11 - Math.floorMod(year, 9), 9)
        } else {
            Math.floorMod(Math.floorMod(year, 9) + 4, 9)
        }
        return if (n == 0) 9 else n
    }

    /** 命卦数（年份须已按立春调整）。男命五寄坤(2)，女命五寄艮(8)。 */
    fun minguaNumber(year: Int, male: Boolean): Int {
        val n = minguaRaw(year, male)
        return if (n == 5) (if (male) 2 else 8) else n
    }

    data class MinGua(
        val year: Int,        // 按立春调整后的年份
        val male: Boolean,
        val raw: Int,         // 未寄宫数
        val number: Int,      // 命卦数
        val gua: String,      // 卦名
        val group: String,    // 东四 / 西四
    ) {
        val jigong: String
            get() = if (raw != 5) "" else if (male) "男命五寄坤" else "女命五寄艮"
    }

    fun minguaOf(year: Int, male: Boolean): MinGua {
        val n = minguaNumber(year, male)
        val gua = NUM_PALACE[n]!!
        return MinGua(year, male, minguaRaw(year, male), n, gua, eastWest(gua))
    }

    /** 立春分年：立春前出生按前一年计（与八字年柱同界）。 */
    fun lichunAdjustedYear(date: LocalDate): Int {
        val lichun = BaziEngine.solarTermLocalDate(date.year, 315.0)
        return if (!date.isBefore(lichun)) date.year else date.year - 1
    }

    // ------------------------------------------------------------------
    // 玄空飞星（《沈氏玄空学》口径，自 upstream feixing.py 移植）
    // ------------------------------------------------------------------

    // 替卦诀：子癸甲申贪狼一路行；坤壬乙卯未巨门从头出；
    //         戌乾亥辰巽巳武曲位；艮丙辛酉丑破军；寅午庚丁右弼。
    private val TIGUA_STAR: Map<Char, Int> = buildMap {
        for ((ms, n) in listOf(
            "子癸甲申" to 1, "坤壬乙卯未" to 2, "戌乾亥辰巽巳" to 6,
            "艮丙辛酉丑" to 7, "寅午庚丁" to 9,
        )) for (m in ms) put(m, n)
    }

    /** 0 地元 / 1 天元 / 2 人元。 */
    fun mountainYuanlong(m: String): Int = mountainIndex(m) % 3

    /** 玄空二十四山阴阳：四正宫（坎震兑离）地元阳、天元人元阴；四隅宫相反。 */
    fun mountainYinyang(m: String): String {
        val yl = mountainYuanlong(m)
        return if (PALACE_NUM[mountainPalace(m)]!! % 2 == 1) {
            if (yl == 0) "+" else "-"
        } else {
            if (yl == 0) "-" else "+"
        }
    }

    /** 某宫某元龙之山。 */
    fun palaceMountain(palace: String, yuanlong: Int): String =
        MOUNTAINS[PALACES.indexOf(palace) * 3 + yuanlong].toString()

    fun oppositeMountain(m: String): String = MOUNTAINS[(mountainIndex(m) + 12) % 24].toString()

    fun neighborMountain(m: String, step: Int): String = MOUNTAINS[Math.floorMod(mountainIndex(m) + step, 24)].toString()

    /** 山星或向星入中的推导过程。 */
    data class StarEntry(
        val n: Int,        // 运盘落在坐/向宫的星
        val by: String,    // 定顺逆所依之山
        val forward: Boolean,
        val center: Int,   // 实际入中之星（替卦时为替星）
    ) {
        val replaced: Boolean get() = center != n
    }

    /** 入中之星 n 的顺逆：取 n 原宫中与坐/向同元龙之山的阴阳；五入中无宫可归，以坐/向山本身论。 */
    fun starDirection(n: Int, refMountain: String): Pair<Boolean, String> {
        val m = if (n == 5) refMountain else palaceMountain(NUM_PALACE[n]!!, mountainYuanlong(refMountain))
        return (mountainYinyang(m) == "+") to m
    }

    /** 以元运论星之旺衰（常用口径）。 */
    fun starStatus(n: Int, yun: Int): String {
        if (n == yun) return "当令旺星"
        val d = Math.floorMod(n - yun, 9)
        return when {
            d == 1 -> "生气（下运旺）"
            d == 2 -> "次生气"
            d == 8 -> "退气"
            n == 5 -> "五黄煞"
            d == 7 -> "衰气"
            else -> "死气"
        }
    }

    private fun entry(yunpan: Map<String, Int>, palace: String, ref: String, tigua: Boolean): StarEntry {
        val n = yunpan[palace]!!
        val (forward, by) = starDirection(n, ref)
        val center = if (tigua && n != 5) TIGUA_STAR[by[0]]!! else n
        return StarEntry(n, by, forward, center)
    }

    data class FeixingChart(
        val yun: Int,
        val sit: String,
        val facing: String,
        val sitPalace: String,
        val facePalace: String,
        val tigua: Boolean,
        val yunpan: Map<String, Int>,
        val shan: Map<String, Int>,
        val xiang: Map<String, Int>,
        val shanEntry: StarEntry,
        val xiangEntry: StarEntry,
        val patterns: List<String>,
    )

    fun feixingChart(yun: Int, sit: String, tigua: Boolean = false): FeixingChart {
        val facing = oppositeMountain(sit)
        val sitPal = mountainPalace(sit)
        val facePal = mountainPalace(facing)
        val yunpan = fly(yun, true)
        val se = entry(yunpan, sitPal, sit, tigua)
        val xe = entry(yunpan, facePal, facing, tigua)
        val ch = FeixingChart(
            yun, sit, facing, sitPal, facePal, tigua, yunpan,
            fly(se.center, se.forward), fly(xe.center, xe.forward), se, xe, emptyList(),
        )
        return ch.copy(patterns = chartPatterns(ch))
    }

    private fun consecutive3(stars: Set<Int>): Boolean =
        (1..9).any { a -> (0..2).map { k -> (a + k - 1) % 9 + 1 }.toSet() == stars }

    fun chartPatterns(ch: FeixingChart): List<String> {
        val pats = ArrayList<String>()
        val yun = ch.yun
        val sSit = ch.shan[ch.sitPalace]!!
        val sFace = ch.shan[ch.facePalace]!!
        val xSit = ch.xiang[ch.sitPalace]!!
        val xFace = ch.xiang[ch.facePalace]!!
        when {
            sSit == yun && xFace == yun -> pats.add("旺山旺向（当令山星到坐、向星到向）")
            sFace == yun && xSit == yun -> pats.add("上山下水（当令山星到向、向星到坐）")
            sFace == yun && xFace == yun -> pats.add("双星到向（当令山向二星同到向首）")
            sSit == yun && xSit == yun -> pats.add("双星到坐（当令山向二星同到坐山）")
        }
        for ((name, e) in listOf("山星" to ch.shanEntry, "向星" to ch.xiangEntry)) {
            if (e.center == 5) {
                pats.add(
                    if (e.forward) "${name}伏吟（五入中顺飞，与洛书元旦盘相同）"
                    else "${name}反吟（五入中逆飞，与洛书元旦盘相反）"
                )
            }
        }
        if (LUOSHU_PATH.all { ch.shan[it]!! + ch.yunpan[it]!! == 10 }) pats.add("山星与运星合十")
        if (LUOSHU_PATH.all { ch.xiang[it]!! + ch.yunpan[it]!! == 10 }) pats.add("向星与运星合十")
        val groups = listOf(setOf(1, 4, 7), setOf(2, 5, 8), setOf(3, 6, 9))
        if (LUOSHU_PATH.all { setOf(ch.yunpan[it]!!, ch.shan[it]!!, ch.xiang[it]!!) in groups }) {
            pats.add("父母三般卦（每宫运山向三星成 147/258/369）")
        }
        if (LUOSHU_PATH.all { consecutive3(setOf(ch.yunpan[it]!!, ch.shan[it]!!, ch.xiang[it]!!)) }) {
            pats.add("连珠三般卦（每宫运山向三星相连）")
        }
        return pats
    }

    /** 某宫的凶星提示。 */
    fun palaceFlags(shan: Int, xiang: Int, annual: Int? = null): List<String> {
        val flags = ArrayList<String>()
        if (5 == shan || 5 == xiang || annual == 5) flags.add("五黄")
        if (setOf(2, 5).all { it in setOf(shan, xiang, annual ?: 0) }) flags.add("二五交加")
        return flags
    }

    /** 按偏离坐山中线的度数给出兼向 / 空亡提示。 */
    fun jianWarnings(sit: String, off: Double): List<String> {
        val w = ArrayList<String>()
        val a = abs(off)
        if (a >= 6) {
            val nb = neighborMountain(sit, if (off > 0) 1 else -1)
            if (mountainPalace(nb) == mountainPalace(sit)) {
                w.add("接近$sit/$nb 交界（小空亡，同宫异元龙），立向不清，宜复测")
            } else {
                w.add("接近$sit/$nb 交界（大空亡，出卦），立向不清，宜复测")
            }
        }
        if (a > 3) w.add("兼向超过 3°，多数玄空派主张用替卦；各派兼向度数界限不一，宜两盘并参")
        return w
    }

    // ------------------------------------------------------------------
    // 流年紫白与年家煞方（自 upstream zibai.py 移植；年份以立春为界）
    // ------------------------------------------------------------------

    /** 流年中宫星：2024=三碧、2025=二黑、2026=一白。 */
    fun annualCenter(year: Int): Int = Math.floorMod(10 - Math.floorMod(year, 9), 9) + 1

    private val ZHI_DIR = mapOf(
        "子" to "北", "丑" to "东北", "寅" to "东北", "卯" to "东", "辰" to "东南", "巳" to "东南",
        "午" to "南", "未" to "西南", "申" to "西南", "酉" to "西", "戌" to "西北", "亥" to "西北",
    )

    fun chongZhi(zhi: String): String = ZHI[Math.floorMod(ZHI.indexOf(zhi) + 6, 12)].toString()

    // 三煞方（以年支三合局论；键为单支，同上游 dict.fromkeys 展开）
    private val SANSHA: Map<String, Pair<String, String>> = buildMap {
        for ((g, v) in listOf(
            "申子辰" to ("南" to "巳午未"), "寅午戌" to ("北" to "亥子丑"),
            "亥卯未" to ("西" to "申酉戌"), "巳酉丑" to ("东" to "寅卯辰"),
        )) for (b in g) put(b.toString(), v)
    }

    private fun palaceOfStar(chart: Map<String, Int>, n: Int): String =
        chart.entries.first { it.value == n }.key

    data class YearSha(
        val year: Int,
        val ganzhi: String,
        val center: Int,   // 流年中宫星
        val taisui: String,
        val suipo: String,
        val sansha: String,
        val wuhuang: String,
        val erhei: String,
    )

    fun yearSha(year: Int): YearSha {
        val zhi = zhiOfYear(year)
        val po = chongZhi(zhi)
        val c = annualCenter(year)
        val ann = fly(c, true)
        val ss = SANSHA[zhi]!!
        return YearSha(
            year = year,
            ganzhi = ganzhiOfYear(year),
            center = c,
            taisui = "${ZHI_DIR[zhi]}（${zhi}山）",
            suipo = "${ZHI_DIR[po]}（${po}山）",
            sansha = "${ss.first}（${ss.second}）",
            wuhuang = PALACE_DIR[palaceOfStar(ann, 5)]!!,
            erhei = PALACE_DIR[palaceOfStar(ann, 2)]!!,
        )
    }

    // ------------------------------------------------------------------
    // 报告（给 LLM 的风水事实）
    // ------------------------------------------------------------------

    data class BazhaiResult(
        val minGua: MinGua,
        val guaTable: Map<String, String>,   // 游年名 -> 方位卦
        val currentYun: Int,
        val yunRangeText: String,
        val yearGz: String,
        val yearZhi: String,
        val yearShengxiao: String,
        val houseGua: String?,
        val houseGroup: String?,
        val houseTable: Map<String, String>?, // 宅卦游年（坐山推宅卦）
        val chart: FeixingChart?,             // 玄空飞星盘（有坐山才排）
        val yearSha: YearSha,                 // 流年煞方
        val zhaiMingMatch: Boolean?,          // 宅命配合（宅与命同属东四/西四）
        val jian: List<String>,               // 兼向/空亡提示（按罗盘度数）
    )

    fun bazhai(
        birthDate: LocalDate,
        male: Boolean,
        sittingMountain: String?,
        tigua: Boolean = false,
        offDegrees: Double? = null,
        moveInYear: Int? = null,
    ): BazhaiResult {
        val year = lichunAdjustedYear(birthDate)
        val mg = minguaOf(year, male)
        val now = LocalDate.now()
        val adjNow = lichunAdjustedYear(now)
        val yun = yunOfYear(adjNow)
        // 宅以入住或大修完工时间立极定运，宅运盘不随交运改变（《沈氏玄空学》）
        val chartYun = moveInYear?.let { yunOfYear(it) } ?: yun
        val (y0, y1) = yunRange(yun)
        var houseGua: String? = null
        var houseTable: Map<String, String>? = null
        var chart: FeixingChart? = null
        if (sittingMountain != null) {
            houseGua = mountainPalace(sittingMountain)
            houseTable = younian(houseGua)
            chart = feixingChart(chartYun, sittingMountain, tigua)
        }
        return BazhaiResult(
            minGua = mg,
            guaTable = younian(mg.gua),
            currentYun = yun,
            yunRangeText = "$y0-$y1",
            yearGz = ganzhiOfYear(adjNow),
            yearZhi = zhiOfYear(adjNow),
            yearShengxiao = shengxiaoOfYear(adjNow),
            houseGua = houseGua,
            houseGroup = houseGua?.let { eastWest(it) },
            houseTable = houseTable,
            chart = chart,
            yearSha = yearSha(adjNow),
            zhaiMingMatch = houseGua?.let { eastWest(it) == mg.group },
            jian = if (sittingMountain != null && offDegrees != null) jianWarnings(sittingMountain, offDegrees) else emptyList(),
        )
    }

    fun bazhaiReport(r: BazhaiResult, sittingMountain: String?): String {
        val lines = ArrayList<String>()
        lines.add("## 命卦")
        lines.add("- 出生（立春分年）：${r.minGua.year}年 · ${if (r.minGua.male) "男" else "女"}命")
        if (r.minGua.jigong.isNotEmpty()) lines.add("- ${r.minGua.jigong}")
        lines.add("- 本命卦：${r.minGua.gua}（${r.minGua.number}数 · ${r.minGua.group}命）")
        lines.add("")
        lines.add("## 元运")
        lines.add("- 当前为下元${r.currentYun}运（${r.yunRangeText}）")
        r.chart?.let { if (it.yun != r.currentYun) lines.add("- 宅运以入住年立极为${it.yun}运：宅运盘不随交运改变，星曜旺衰随时运变化") }
        lines.add("- 今年：${r.yearGz}年（${r.yearShengxiao}），太岁在${r.yearZhi}")
        r.chart?.let { ch ->
            val ann = fly(r.yearSha.center, true)
            lines.add("")
            lines.add("## 玄空飞星盘（${ch.yun}运 · 坐${ch.sit}向${ch.facing} · ${if (ch.tigua) "替卦盘" else "下卦盘"}）")
            lines.add("- 格局：${if (ch.patterns.isEmpty()) "无四大格局与特殊格局" else ch.patterns.joinToString("；")}")
            lines.add("| 宫（方位） | 运星 | 山星 | 向星 | 山星旺衰 | 向星旺衰 | 凶星提示 |")
            lines.add("|---|---|---|---|---|---|---|")
            for (pal in PALACES_NINE) {
                val flags = palaceFlags(ch.shan[pal]!!, ch.xiang[pal]!!, ann[pal]).joinToString("、").ifEmpty { "—" }
                lines.add(
                    "| ${pal}（${PALACE_DIR[pal]}） | ${ch.yunpan[pal]} | ${ch.shan[pal]} | ${ch.xiang[pal]} " +
                        "| ${starStatus(ch.shan[pal]!!, ch.yun)} | ${starStatus(ch.xiang[pal]!!, ch.yun)} | $flags |"
                )
            }
            lines.add("- 山星入中：${ch.shanEntry.n}，依${ch.shanEntry.by}山${if (ch.shanEntry.forward) "顺" else "逆"}飞${if (ch.shanEntry.replaced) "（替为${ch.shanEntry.center}）" else ""}")
            lines.add("- 向星入中：${ch.xiangEntry.n}，依${ch.xiangEntry.by}山${if (ch.xiangEntry.forward) "顺" else "逆"}飞${if (ch.xiangEntry.replaced) "（替为${ch.xiangEntry.center}）" else ""}")
        }
        if (r.zhaiMingMatch != null && r.houseGua != null) {
            lines.add("")
            lines.add("## 宅命配合")
            if (r.zhaiMingMatch) {
                lines.add("- 宅卦${r.houseGua}（${r.houseGroup}）与命卦${r.minGua.gua}（${r.minGua.group}）同组——宅命相配")
            } else {
                lines.add("- 宅卦${r.houseGua}（${r.houseGroup}）与命卦${r.minGua.gua}（${r.minGua.group}）东西四不合——宅命不配")
                val lucky = YOUNIAN_ORDER.filter { it in YOUNIAN_JI }.mapNotNull { n ->
                    r.guaTable[n]?.let { "${n}=${PALACE_DIR[it]}方" }
                }
                lines.add("- 化解思路：把卧室/书桌/常坐位优先安排在本人四吉方（${lucky.joinToString("、")}），避开宅之凶方")
            }
        }
        lines.add("")
        lines.add("## 命卦游年（八宅）")
        for (name in YOUNIAN_ORDER) {
            val gua = r.guaTable[name] ?: continue
            lines.add("- $name = ${gua}方（${PALACE_DIR[gua]}） ${YOUNIAN_NOTE[name]}")
        }
        if (sittingMountain != null && r.houseGua != null && r.houseTable != null) {
            lines.add("")
            lines.add("## 宅卦（坐${sittingMountain}山，${r.houseGua}宅 · ${r.houseGroup}）")
            for (name in YOUNIAN_ORDER) {
                val gua = r.houseTable[name] ?: continue
                lines.add("- $name = ${gua}方（${PALACE_DIR[gua]}）")
            }
        }
        val ys = r.yearSha
        lines.add("")
        lines.add("## 流年煞方（${ys.year}年${ys.ganzhi}）")
        lines.add("- 太岁：${ys.taisui}；岁破：${ys.suipo}")
        lines.add("- 三煞：${ys.sansha}")
        lines.add("- 流年五黄：${ys.wuhuang}方；二黑：${ys.erhei}方（流年中宫${ys.center}，${STAR_NAMES[ys.center]}）")
        lines.add("- 传统宜静不宜动：太岁、岁破、三煞、五黄之方不主动动土大修")
        if (r.jian.isNotEmpty()) {
            lines.add("")
            lines.add("## 立向提示")
            for (w in r.jian) lines.add("- ⚠ $w")
        }
        lines.add("")
        lines.add("> 风水内容仅供娱乐参考。")
        return lines.joinToString("\n")
    }
}
