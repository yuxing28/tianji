package com.tianji.app.engine

import java.time.LocalDate

/**
 * 姻缘模块 —— 自 docs/reference/yinyuan/bazi-matching.md（合婚规则与权重）
 * 与 fortune-sticks.md（100 签签诗、签号规则、分级）移植（MIT）。
 * 文档给出维度权重与分数区间但未给逐项分值，以下逐项分值为透明可核的固定口径。
 */
object MarriageEngine {

    // ------------------------------------------------------------------
    // 合婚
    // ------------------------------------------------------------------

    private val GAN = "甲乙丙丁戊己庚辛壬癸"
    private val ZHI = "子丑寅卯辰巳午未申酉戌亥"

    private val GAN_WUXING = "木木火火土土金金水水"
    private val ZHI_WUXING = mapOf(
        "子" to "水", "丑" to "土", "寅" to "木", "卯" to "木", "辰" to "土", "巳" to "火",
        "午" to "火", "未" to "土", "申" to "金", "酉" to "金", "戌" to "土", "亥" to "水",
    )

    // 天干五合：无序对 -> 化
    private val GAN_HE = mapOf(
        "甲己" to "土", "乙庚" to "金", "丙辛" to "水", "丁壬" to "木", "戊癸" to "火",
    )
    private val GAN_CHONG = setOf("甲庚", "乙辛", "丙壬", "丁癸")

    private val ZHI_LIUHE = mapOf(
        "子丑" to "土", "寅亥" to "木", "卯戌" to "火", "辰酉" to "金", "巳申" to "水", "午未" to "土",
    )
    private val SANHE_GROUPS = listOf(
        Triple("申子辰", "水局", "志同道合，默契极深"),
        Triple("寅午戌", "火局", "热情奔放，互相成就"),
        Triple("巳酉丑", "金局", "互相成就，稳固长久"),
        Triple("亥卯未", "木局", "温柔相待，共同成长"),
    )
    private val ZHI_CHONG = listOf("子午", "丑未", "寅申", "卯酉", "辰戌", "巳亥")
    private val ZHI_HAI = listOf("子未", "丑午", "寅巳", "卯辰", "申亥", "酉戌")
    private val SELF_XING = setOf("辰", "午", "酉", "亥")

    // 六十甲子纳音
    private val NAYIN_ROWS = listOf(
        "甲子" to "海中金", "乙丑" to "海中金", "甲寅" to "大溪水", "乙卯" to "大溪水",
        "甲辰" to "覆灯火", "乙巳" to "覆灯火", "甲午" to "砂石金", "乙未" to "砂石金",
        "甲申" to "泉中水", "乙酉" to "泉中水", "甲戌" to "山头火", "乙亥" to "山头火",
        "丙子" to "涧下水", "丁丑" to "涧下水", "丙寅" to "炉中火", "丁卯" to "炉中火",
        "丙辰" to "沙中土", "丁巳" to "沙中土", "丙午" to "天河水", "丁未" to "天河水",
        "丙申" to "山下火", "丁酉" to "山下火", "丙戌" to "屋上土", "丁亥" to "屋上土",
        "戊子" to "霹雳火", "己丑" to "霹雳火", "戊寅" to "城墙土", "己卯" to "城墙土",
        "戊辰" to "大林木", "己巳" to "大林木", "戊午" to "天上火", "己未" to "天上火",
        "戊申" to "大驿土", "己酉" to "大驿土", "戊戌" to "平地木", "己亥" to "平地木",
        "庚子" to "壁上土", "辛丑" to "壁上土", "庚寅" to "松柏木", "辛卯" to "松柏木",
        "庚辰" to "白蜡金", "辛巳" to "白蜡金", "庚午" to "路旁土", "辛未" to "路旁土",
        "庚申" to "石榴木", "辛酉" to "石榴木", "庚戌" to "钗钏金", "辛亥" to "钗钏金",
        "壬子" to "桑柘木", "癸丑" to "桑柘木", "壬寅" to "金箔金", "癸卯" to "金箔金",
        "壬辰" to "长流水", "癸巳" to "长流水", "壬午" to "杨柳木", "癸未" to "杨柳木",
        "壬申" to "剑锋金", "癸酉" to "剑锋金", "壬戌" to "大海水", "癸亥" to "大海水",
    )

    private val GZ_INDEX: Map<String, Int> = buildMap {
        for (i in 0 until 60) put("${GAN[i % 10]}${ZHI[i % 12]}", i)
    }
    private val NAYIN: Map<String, String> = NAYIN_ROWS.toMap()

    private fun pairKey(a: String, b: String): String =
        if (GAN.indexOf(a) <= GAN.indexOf(b)) "$a$b" else "$b$a"

    private fun zhiKey(a: String, b: String): String {
        val ia = ZHI.indexOf(a)
        val ib = ZHI.indexOf(b)
        return if (ia <= ib) "$a$b" else "$b$a"
    }

    private fun sheng(a: String, b: String): Boolean =
        (GAN_WUXING.indexOfFirst { it == a.first() } + 1) % 5 == GAN_WUXING.indexOfFirst { it == b.first() }

    private fun wuxingSheng(a: String, b: String): Boolean {
        val order = "木火土金水"
        return (order.indexOf(a) + 1) % 5 == order.indexOf(b)
    }

    data class MatchFactor(val dimension: String, val weight: Int, val score: Int, val notes: List<String>)

    data class MatchResult(val total: Int, val band: String, val bandNote: String, val factors: List<MatchFactor>)

    private fun clamp(v: Int) = v.coerceIn(0, 100)

    fun match(a: BaziEngine.BaziResult, b: BaziEngine.BaziResult): MatchResult {
        val factors = ArrayList<MatchFactor>()

        // 1. 天干关系 15%
        var s1 = 50
        val n1 = ArrayList<String>()
        for ((ga, gb, label) in listOf(
            Triple(a.yearPillar.gan, b.yearPillar.gan, "年干"),
            Triple(a.dayPillar.gan, b.dayPillar.gan, "日干"),
        )) {
            val he = GAN_HE[pairKey(ga, gb)]
            if (he != null) {
                val bonus = if (label == "年干") 25 else 25
                s1 += bonus
                n1.add("${label}${ga}${gb}合（化$he）—— 天生有缘，互相吸引")
            }
            val chong = GAN_CHONG.contains(pairKey(ga, gb))
            if (chong) {
                s1 -= 20
                n1.add("${label}${ga}${gb}冲 —— 性格棱角需要磨合")
            }
        }
        factors.add(MatchFactor("天干关系", 15, clamp(s1), n1.ifEmpty { listOf("年干/日干无合无冲") }))

        // 2. 地支六合/三合 20%（年支）
        var s2 = 30
        val n2 = ArrayList<String>()
        val ya = a.yearPillar.zhi
        val yb = b.yearPillar.zhi
        ZHI_LIUHE[zhiKey(ya, yb)]?.let {
            s2 += 35
            n2.add("年支$ya$yb 合（化$it）—— 互相包容，暗中有默契")
        }
        SANHE_GROUPS.firstOrNull { (g, _, desc) -> ya in g && yb in g && ya != yb }?.let { (g, ju, desc) ->
            s2 += 35
            n2.add("年支入$g $ju —— $desc")
        }
        factors.add(MatchFactor("地支六合/三合", 20, clamp(s2), n2.ifEmpty { listOf("年支无合局") }))

        // 3. 地支六冲/六害 15%（年支，扣分项）
        var s3 = 100
        val n3 = ArrayList<String>()
        if (ZHI_CHONG.contains(zhiKey(ya, yb))) {
            s3 -= 50
            n3.add("年支$ya$yb 冲 —— 冲突明显，需互相退让；冲有时反而激荡出火花")
        }
        if (ZHI_HAI.contains(zhiKey(ya, yb))) {
            s3 -= 30
            n3.add("年支$ya$yb 害 —— 暗中损害，不易察觉的摩擦")
        }
        if (ya == yb && ya in SELF_XING) {
            s3 -= 20
            n3.add("年支${ya}${yb}自刑 —— 各自纠结，影响关系")
        } else if (ya != yb && (("子" in setOf(ya, yb) && "卯" in setOf(ya, yb)))) {
            s3 -= 20
            n3.add("年支子卯相刑（无礼之刑）—— 缺乏尊重，言语伤人")
        }
        factors.add(MatchFactor("地支六冲/六害", 15, clamp(s3), n3.ifEmpty { listOf("年支无冲无害") }))

        // 4. 日柱配合 25%
        val rel = BaziEngine.shishenOf(GAN.indexOf(a.dayPillar.gan), GAN.indexOf(b.dayPillar.gan))
        val relBase = mapOf(
            "正官" to 90, "正印" to 85, "正财" to 85, "食神" to 80, "比肩" to 70,
            "偏印" to 65, "偏财" to 65, "伤官" to 55, "偏官" to 50, "劫财" to 45,
        )
        var s4 = relBase[rel] ?: 60
        val n4 = ArrayList<String>()
        val relText = mapOf(
            "正官" to "被保护的感觉，传统良配",
            "正印" to "温暖的包容，安全感的来源",
            "正财" to "温和的吸引，稳定配对",
            "食神" to "一方付出多，照顾另一方",
            "比肩" to "像朋友一样平等相处",
            "偏印" to "被照顾但有压力",
            "偏财" to "一方主导，一方被吸引",
            "伤官" to "热烈但容易摩擦",
            "偏官" to "一方强势，需要磨合",
            "劫财" to "既亲密又较劲",
        )
        n4.add("日主关系：${a.dayPillar.gan} 见 ${b.dayPillar.gan} 为「$rel」—— ${relText[rel] ?: ""}")
        val da = a.dayPillar.zhi
        val db = b.dayPillar.zhi
        if (ZHI_LIUHE[zhiKey(da, db)] != null) {
            s4 += 10
            n4.add("日支$da$db 合 —— 亲密默契")
        }
        if (ZHI_CHONG.contains(zhiKey(da, db))) {
            s4 -= 25
            n4.add("日支$da$db 冲 —— 生活习惯差异大")
        }
        if (ZHI_HAI.contains(zhiKey(da, db))) {
            s4 -= 10
            n4.add("日支$da$db 害 —— 亲近中暗藏摩擦")
        }
        factors.add(MatchFactor("日柱配合", 25, clamp(s4), n4))

        // 5. 五行互补 15%
        fun elementCounts(r: BaziEngine.BaziResult): MutableMap<String, Int> {
            val c = mutableMapOf("木" to 0, "火" to 0, "土" to 0, "金" to 0, "水" to 0)
            for (p in listOf(r.yearPillar, r.monthPillar, r.dayPillar, r.hourPillar)) {
                if (p.gan == "未知") continue
                c[GAN_WUXING[GAN.indexOf(p.gan)].toString()] =
                    (c[GAN_WUXING[GAN.indexOf(p.gan)].toString()] ?: 0) + 1
                val zw = ZHI_WUXING[p.zhi] ?: continue
                c[zw] = (c[zw] ?: 0) + 1
            }
            return c
        }
        val ca = elementCounts(a)
        val cb = elementCounts(b)
        var s5 = 40
        val n5 = ArrayList<String>()
        for (wx in listOf("木", "火", "土", "金", "水")) {
            if ((ca[wx] ?: 0) == 0 && (cb[wx] ?: 0) >= 2) {
                s5 += 15
                n5.add("对方${wx}旺，恰补你八字所缺")
            } else if ((cb[wx] ?: 0) == 0 && (ca[wx] ?: 0) >= 2) {
                s5 += 15
                n5.add("你${wx}旺，恰补对方八字所缺")
            }
        }
        if (n5.isEmpty()) n5.add("两人五行结构相近，互补有限")
        factors.add(MatchFactor("五行互补", 15, clamp(s5), n5))

        // 6. 纳音配对 10%（年柱纳音）
        val nayA = NAYIN["${a.yearPillar.gan}${a.yearPillar.zhi}"] ?: "未知"
        val nayB = NAYIN["${b.yearPillar.gan}${b.yearPillar.zhi}"] ?: "未知"
        var s6: Int
        val n6: String
        if (nayA == nayB) {
            s6 = 90
            n6 = "同为「$nayA」—— 天生相似，默契度高"
        } else {
            val ea = nayA.last().toString()
            val eb = nayB.last().toString()
            when {
                wuxingSheng(ea, eb) || wuxingSheng(eb, ea) -> {
                    s6 = 75
                    n6 = "纳音 $nayA 与 $nayB 五行相生 —— 互补互利"
                }
                wuxingSheng(ea, eb).not() && wuxingSheng(eb, ea).not() && ea != eb &&
                    (orderOf(eb) - orderOf(ea) + 5) % 5 == 2 -> {
                    s6 = 45
                    n6 = "纳音 $nayA 克 $nayB —— 需要更多磨合"
                }
                wuxingSheng(ea, eb).not() && wuxingSheng(eb, ea).not() && ea != eb -> {
                    s6 = 45
                    n6 = "纳音 $nayB 克 $nayA —— 需要更多磨合"
                }
                else -> {
                    s6 = 65
                    n6 = "纳音 $nayA 与 $nayB 同五行不同局 —— 平平"
                }
            }
        }
        factors.add(MatchFactor("纳音配对", 10, s6, listOf(n6)))

        val total = factors.sumOf { it.weight * it.score } / 100
        val (band, note) = when {
            total >= 90 -> "天作之合" to "姻缘极深，好好珍惜"
            total >= 75 -> "佳偶天成" to "底子很好，用心经营可长久"
            total >= 60 -> "尚可配对" to "有缘但需磨合，多沟通"
            total >= 45 -> "需要用心" to "挑战较大但非不可，重在经营"
            total >= 30 -> "有缘无分" to "缘分较浅，随缘为好"
            else -> "不建议强求" to "天然冲突较多，在一起需极大努力"
        }
        return MatchResult(total, band, note, factors)
    }

    private fun orderOf(wx: String): Int = "木火土金水".indexOf(wx)

    fun matchReport(a: BaziEngine.BaziResult, b: BaziEngine.BaziResult, r: MatchResult): String {
        val lines = ArrayList<String>()
        lines.add("## 双方四柱")
        lines.add("- 甲方：${a.yearPillar.gan}${a.yearPillar.zhi} ${a.monthPillar.gan}${a.monthPillar.zhi} ${a.dayPillar.gan}${a.dayPillar.zhi} ${a.hourPillar.gan}${a.hourPillar.zhi}（${a.sex}）")
        lines.add("- 乙方：${b.yearPillar.gan}${b.yearPillar.zhi} ${b.monthPillar.gan}${b.monthPillar.zhi} ${b.dayPillar.gan}${b.dayPillar.zhi} ${b.hourPillar.gan}${b.hourPillar.zhi}（${b.sex}）")
        lines.add("")
        lines.add("## 合婚评分：${r.total} 分（${r.band}）")
        lines.add("- ${r.bandNote}")
        lines.add("")
        lines.add("## 各维度")
        for (f in r.factors) {
            lines.add("- ${f.dimension}（权重${f.weight}%）：${f.score} 分")
            for (n in f.notes) lines.add("  - $n")
        }
        lines.add("")
        lines.add("> 分数只是参考工具，不是判决书。任何分数区间都加上「经营」和「成长」的建议。")
        return lines.joinToString("\n")
    }

    // ------------------------------------------------------------------
    // 姻缘签
    // ------------------------------------------------------------------

    data class Stick(val no: Int, val grade: String, val poem: String, val jieJie: String? = null, val yinyuanJie: String? = null)

    fun gradeOf(no: Int): String = when (no) {
        in 1..10 -> "上上签"
        in 11..30 -> "上签"
        in 31..45 -> "中上签"
        in 46..55 -> "中签"
        in 56..70 -> "中下签"
        in 71..85 -> "下签"
        else -> if (no == 100) "下下转上上" else "下下签"
    }

    /** 签号 = (名字编码和 + 问题编码和 + 时辰序号 + 日期天数) % 100 + 1，同日同人同问同签。 */
    fun stickNo(name: String, question: String, shichenIndex: Int, date: LocalDate): Int {
        val sum = name.sumOf { it.code } + question.sumOf { it.code } + shichenIndex + date.toEpochDay()
        return (Math.floorMod(sum, 100L) + 1L).toInt()
    }

    private fun p(vararg lines: String) = lines.joinToString("\n")

    val STICKS: List<Stick> = listOf(
        Stick(1, "上上", p("月老亲牵红线来，", "前世姻缘今世开。", "莫愁前路无知己，", "有情之人自会来。"),
            "此签大吉。月老亲临，前世有约今生来赴。无论目前状态如何，正缘已在路上。",
            "你的姻缘运势极佳。如果是单身，近期有极大可能遇到心动之人；如果已有目标，大胆表白成功率很高。"),
        Stick(2, "上上", p("百年好合在今朝，", "金玉良缘不用求。", "春风得意马蹄疾，", "一日看尽长安花。"),
            "此签大吉。金玉良缘天注定，无需刻意追寻。春风正暖，万物可期。",
            "不用焦虑，不用刻意。你的正缘会在最自然的状态下出现，越是放松越容易遇到。"),
        Stick(3, "上上", p("双燕归来春正暖，", "桃花映面柳含烟。", "此情只应天上有，", "人间难得几回闻。"),
            "双燕齐飞，桃花盛开。姻缘之美，如天上人间。",
            "一段非常美好的感情正在向你走来。如果已经有人在身边，这段感情会更加甜蜜。"),
        Stick(4, "上上", p("缘定三生石上书，", "今朝得见旧时图。", "莫道相逢皆偶然，", "天机早在命中铺。"),
            "三生石上有名，此缘非偶然。命中注定的重逢。",
            "你遇到或即将遇到的那个人，和你有很深的缘分。可能有似曾相识的感觉。"),
        Stick(5, "上上", p("东风送暖百花开，", "喜鹊枝头报喜来。", "莫问姻缘何处觅，", "回头一笑在身旁。"),
            "东风送暖，喜报频传。回眸之间，缘分就在身边。",
            "注意身边的人，也许你一直在寻找的那个人就在你周围。"),
        Stick(6, "上上", p("红线牵来天作合，", "两心相悦不需说。", "但愿人长久万里，", "共看明月照山河。"),
            "天作之合，心意相通。千里共婵娟。",
            "你和对方之间有一种不需言语的默契，这是极好的姻缘征兆。"),
        Stick(7, "上上", p("金风玉露一相逢，", "便胜却人间无数。", "两情若是久长时，", "又岂在朝朝暮暮。"),
            "取自秦观名句。金风玉露之逢，胜过人间无数。",
            "即使目前是异地或有距离，真挚的感情不受距离限制。"),
        Stick(8, "上上", p("明月照人圆又满，", "清风送来桂花香。", "此生得遇知心人，", "不枉来人间一趟。"),
            "月圆花香，得遇知心。人生圆满之象。",
            "你即将遇到一个非常懂你的人，能读懂你的沉默，理解你的选择。"),
        Stick(9, "上上", p("春江水暖鸭先知，", "你心已动缘已至。", "不必犹豫不必等，", "勇敢迈出第一步。"),
            "春水先知，你的直觉是对的。",
            "如果你心里已经有了感觉，相信自己的直觉。对方可能也有同样的心思。"),
        Stick(10, "上上", p("天生一对地一双，", "命里姻缘不可挡。", "纵使千山万水隔，", "终究会到彼此旁。"),
            "命中姻缘，纵有千难万阻终能团圆。",
            "如果目前有困难，不要放弃。时间和真心会解决一切障碍。"),
        Stick(11, "上", p("花开时节又逢君，", "一见如故胜故人。", "且把闲愁都放下，", "笑迎新缘入门来。")),
        Stick(12, "上", p("小桥流水有人家，", "门前桃树正开花。", "若问此花开为谁，", "只等有缘人到来。")),
        Stick(13, "上", p("山重水复疑无路，", "柳暗花明又一村。", "姻缘也在转角处，", "不经意间遇见春。")),
        Stick(14, "上", p("一树梨花压海棠，", "春风十里不如你。", "此情可待成追忆，", "只是当时已惘然。")),
        Stick(15, "上", p("明月松间照，", "清泉石上流。", "竹喧归浣女，", "有缘自会逢。")),
        Stick(16, "上", p("踏破铁鞋无觅处，", "得来全不费功夫。", "寻寻觅觅多少年，", "回首灯火阑珊处。")),
        Stick(17, "上", p("好风凭借力，", "送我上青云。", "姻缘乘风至，", "何须苦追寻。")),
        Stick(18, "上", p("昨夜西风凋碧树，", "独上高楼望天涯。", "今日东风花又开，", "有缘人自远方来。")),
        Stick(19, "上", p("人面不知何处去，", "桃花依旧笑春风。", "来年花开人再来，", "对的那人在等候。")),
        Stick(20, "上", p("有美人兮见之不忘，", "一日不见兮思之如狂。", "凤飞翱翔兮四海求凰，", "奈何佳人兮不在东墙。", "——缘分将至，莫急莫慌。")),
        Stick(21, "上", p("江畔何人初见月，", "江月何年初照人。", "此刻相逢皆是缘，", "好好珍惜眼前人。")),
        Stick(22, "上", p("众里寻他千百度，", "蓦然回首，", "那人却在灯火阑珊处。")),
        Stick(23, "上", p("红豆生南国，", "春来发几枝。", "愿君多采撷，", "此物最相思。", "——相思有应，好事将近。")),
        Stick(24, "上", p("身无彩凤双飞翼，", "心有灵犀一点通。", "你与ta人心意连，", "只差一个开口功。")),
        Stick(25, "上", p("沉舟侧畔千帆过，", "病树前头万木春。", "旧情已过新缘来，", "放下执念自然开。")),
        Stick(26, "上", p("天阶夜色凉如水，", "坐看牵牛织女星。", "星河虽远鹊桥通，", "你的鹊桥正在建。")),
        Stick(27, "上", p("莫道桃花只在春，", "四季各有好风景。", "缘来不在早晚间，", "到了自然就分明。")),
        Stick(28, "上", p("春有百花秋有月，", "夏有凉风冬有雪。", "若无闲事挂心头，", "便是人间好时节。", "——姻缘亦如此，水到渠成。")),
        Stick(29, "上", p("青青子衿悠悠我心，", "纵我不往子宁不嗣音？", "你若勇敢迈一步，", "对方也在等你行。")),
        Stick(30, "上", p("人生若只如初见，", "何事秋风悲画扇。", "此签劝你重初心，", "好好珍惜眼前缘。")),
        Stick(31, "中上", p("云中谁寄锦书来，", "雁字回时月满楼。", "有心之人终会至，", "莫让犹豫误良候。")),
        Stick(32, "中上", p("欲穷千里目，", "更上一层楼。", "姻缘需主动，", "坐等不如走。")),
        Stick(33, "中上", p("酒逢知己千杯少，", "话不投机半句多。", "若觉对了就珍惜，", "莫等错过再蹉跎。")),
        Stick(34, "中上", p("独在异乡为异客，", "每逢佳节倍思亲。", "思念深处有姻缘，", "先把心安归处寻。")),
        Stick(35, "中上", p("海内存知己，", "天涯若比邻。", "莫愁前路远，", "有缘自会亲。")),
        Stick(36, "中上", p("月有阴晴圆缺，", "人有悲欢离合。", "此刻虽有缺憾，", "下一个月满时。")),
        Stick(37, "中上", p("云开月照见花开，", "莫向旁人怨苦哀。", "若问姻缘归甚处，", "东风送暖自然来。")),
        Stick(38, "中上", p("大江东去浪淘尽，", "千古风流人物。", "你的风流就在后，", "先把自己打磨好。")),
        Stick(39, "中上", p("竹外桃花三两枝，", "春江水暖你先知。", "姻缘已到三分二，", "再加努力便成事。")),
        Stick(40, "中上", p("不识庐山真面目，", "只缘身在此山中。", "也许答案在身边，", "退后一步看得清。")),
        Stick(41, "中上", p("采菊东篱下，", "悠然见南山。", "无心插柳柳成荫，", "感情之事莫强攀。")),
        Stick(42, "中上", p("莫听穿林打叶声，", "何妨吟啸且徐行。", "姻缘路上莫心急，", "慢慢走来风景好。")),
        Stick(43, "中上", p("问君能有几多愁，", "恰似一江春水向东流。", "愁水流尽桃花现，", "先把心上愁放走。")),
        Stick(44, "中上", p("今人不见古时月，", "今月曾经照古人。", "你要的那个人，", "也在月下等某人。")),
        Stick(45, "中上", p("两岸猿声啼不住，", "轻舟已过万重山。", "烦恼再多都会过，", "前方就是桃花岸。")),
        Stick(46, "中", p("横看成岭侧成峰，", "远近高低各不同。", "感情之事无绝对，", "换个角度看或通。")),
        Stick(47, "中", p("山穷水复疑无路，", "此路不通换条路。", "姻缘不是单行道，", "条条大路通幸福。")),
        Stick(48, "中", p("人间四月芳菲尽，", "山寺桃花始盛开。", "你的花期或稍晚，", "迟来的反而精彩。")),
        Stick(49, "中", p("飞流直下三千尺，", "疑是银河落九天。", "姻缘看似遥不可及，", "其实近在眼前间。")),
        Stick(50, "中", p("命里有时终须有，", "命里无时莫强求。", "该来自然会来到，", "不该来的求不到。", "——但求不到也别急，缘分有时会迟到。")),
        Stick(51, "中", p("落霞与孤鹜齐飞，", "秋水共长天一色。", "时机未到景色已美，", "享受当下不必焦虑。")),
        Stick(52, "中", p("不畏浮云遮望眼，", "自缘身在最高层。", "你看得比谁都清，", "只是缺少一点勇气。")),
        Stick(53, "中", p("黑发不知勤学早，", "白首方悔读书迟。", "感情也要早经营，", "莫等花落空叹息。")),
        Stick(54, "中", p("年年岁岁花相似，", "岁岁年年人不同。", "身边来来又去去，", "总有一人留到最后。")),
        Stick(55, "中", p("千里之行始于足下，", "姻缘之路从打招呼开始。", "不要想太多先行动，", "机会藏在日常中。")),
        Stick(56, "中下", p("昨夜雨疏风骤，", "浓睡不消残酒。", "近期桃花有些乱，", "分清正缘与过客。")),
        Stick(57, "中下", p("夕阳无限好，", "只是近黄昏。", "眼前人或非良人，", "长远来看需审慎。")),
        Stick(58, "中下", p("抽刀断水水更流，", "举杯消愁愁更愁。", "感情烦恼暂放下，", "先把自己的生活调好。")),
        Stick(59, "中下", p("蜡炬成灰泪始干，", "付出太多需看看，", "对方是否同等回报，", "一厢情愿难长久。")),
        Stick(60, "中下", p("此情可待成追忆，", "只是当时已惘然。", "如果总是在回忆里，", "就看不到眼前的缘。")),
        Stick(61, "中下", p("无可奈何花落去，", "似曾相识燕归来。", "旧缘已去新缘来，", "但你需要先放手。")),
        Stick(62, "中下", p("花开堪折直须折，", "莫待无花空折枝。", "身边有人就珍惜，", "不要总想着更好的。")),
        Stick(63, "中下", p("春蚕到死丝方尽，", "你太执着反而累。", "感情不是努力就行，", "还需要对的时机和人。")),
        Stick(64, "中下", p("相见时难别亦难，", "东风无力百花残。", "此段感情多波折，", "需要双方共同努力。")),
        Stick(65, "中下", p("商女不知亡国恨，", "隔江犹唱后庭花。", "也许你沉迷的桃花，", "并不是真正的正缘。")),
        Stick(66, "中下", p("多情自古伤离别，", "更那堪冷落清秋节。", "此刻不宜强求感情，", "先度过这段调整期。")),
        Stick(67, "中下", p("问世间情为何物，", "直教人生死相许。", "但签名提醒你一句，", "别为了感情丢了自己。")),
        Stick(68, "中下", p("两情若是久长时，", "又岂在朝朝暮暮。", "若目前有距离和阻碍，", "先把各自的日子过好。")),
        Stick(69, "中下", p("天长地久有时尽，", "此恨绵绵无绝期。", "这段缘分有隐忧，", "需要坦诚面对问题。")),
        Stick(70, "中下", p("曾经沧海难为水，", "除却巫山不是云。", "你心里住着一个人，", "但那个位置需要腾出来。")),
        Stick(71, "下", p("林花谢了春红，", "太匆匆。", "无奈朝来寒雨晚来风。", "姻缘暂时受阻，", "先顾好自己。")),
        Stick(72, "下", p("自是人生长恨水长东，", "近期感情不如意。", "但东流之水终入海，", "低谷之后是上坡。")),
        Stick(73, "下", p("剪不断理还乱，", "是离愁。", "别是一般滋味在心头。", "感情纠缠不清，", "需要做一个决定。")),
        Stick(74, "下", p("无言独上西楼，", "月如钩。", "寂寞梧桐深院锁清秋。", "此刻孤独是常态，", "但不会一直这样。")),
        Stick(75, "下", p("帘卷西风，", "人比黄花瘦。", "为情所困太久，", "该把重心移回自己。")),
        Stick(76, "下", p("物是人非事事休，", "欲语泪先流。", "那个人已经不是从前的人，", "你也需要往前走。")),
        Stick(77, "下", p("只恐双溪舴艋舟，", "载不动许多愁。", "感情的包袱太重，", "需要学会放下。")),
        Stick(78, "下", p("寻寻觅觅冷冷清清，", "凄凄惨惨戚戚。", "当下感情运势低谷，", "但低谷之后必回升。")),
        Stick(79, "下", p("知否知否，", "应是绿肥红瘦。", "你付出很多收获很少，", "也许方向需要调整。")),
        Stick(80, "下", p("花自飘零水自流，", "一种相思两处闲愁。", "有缘无分的阶段，", "强求不如等待。")),
        Stick(81, "下", p("莫道不销魂，", "帘卷西风人比黄花瘦。", "为情消瘦不值得，", "先爱自己再爱人。")),
        Stick(82, "下", p("渡口无人舟自横，", "你等的船暂时没来。", "与其苦等不如先上岸，", "沿途走走或遇新风景。")),
        Stick(83, "下", p("独上高楼望尽天涯路，", "满眼是雾看不清。", "此签劝你暂且歇息，", "迷雾散后再出发。")),
        Stick(84, "下", p("一寸相思一寸灰，", "思念太重伤了自己。", "把精力放在提升自己，", "蝴蝶自来花开之后。")),
        Stick(85, "下", p("此情可待成追忆，", "当下不宜再纠结。", "把它放在记忆里，", "带着经验继续走。")),
        Stick(86, "下下", p("风萧萧兮易水寒，", "壮士一去不复还。", "此签虽有壮烈气，", "但签名提醒你：", "这段缘分大概率无果，", "及时止损为上策。")),
        Stick(87, "下下", p("国破山河在，", "城春草木深。", "感觉感怀皆无用，", "重建自己为首要。")),
        Stick(88, "下下", p("前不见古人，", "后不见来者。", "念天地之悠悠，", "独怆然而涕下。", "——孤独只是暂时的，", "但此刻确实需要独自面对。")),
        Stick(89, "下下", p("相见争如不见，", "有情何似无情。", "此刻相见不如怀念，", "给彼此一些空间。")),
        Stick(90, "下下", p("多情却似总无情，", "唯觉樽前笑不成。", "感情之事勉强不得，", "该放手时需放手。")),
        Stick(91, "下下", p("此情应是长相守，", "你若无心我便休。", "对方无意则莫强求，", "天涯何处无芳草。")),
        Stick(92, "下下", p("人生到处知何似，", "应似飞鸿踏雪泥。", "留不住的缘分随它去，", "前方还有新天地。")),
        Stick(93, "下下", p("抽刀断水水更流，", "你越是想抓住越是失去。", "松开手让缘分自由，", "该回来的自然会回来。")),
        Stick(94, "下下", p("十年生死两茫茫，", "不思量自难忘。", "这段感情已成过往，", "活在过去不如活在未来。")),
        Stick(95, "下下", p("花谢花飞飞满天，", "红消香断有谁怜。", "近期桃花多是镜花水月，", "不宜轻信甜言蜜语。")),
        Stick(96, "下下", p("东风恶欢情薄，", "一怀愁绪几年离索。", "此签提醒你：", "这段感情反复折磨，", "是时候考虑放手。")),
        Stick(97, "下下", p("世情薄人情恶，", "雨送黄昏花易落。", "感情中多有不如意，", "但风雨总会过去。")),
        Stick(98, "下下", p("零落成泥碾作尘，", "只有香如故。", "虽然这段缘分已碎，", "但你从中获得了成长。")),
        Stick(99, "下下", p("莫道春风不解愁，", "春风也有打落花时。", "此刻虽是最难熬，", "但请记住：", "每一个下下签之后，", "都藏着一个上上签的开始。")),
        Stick(100, "下下转上上", p("塞翁失马焉知非福，", "破茧成蝶就在此刻。", "一百签为最后一签，", "也是轮回之签。", "失去不是终点，", "而是新缘分的起点。", "请记住：", "命由己造，福自己求。", "最好的姻缘，", "永远是你成为最好的自己之后，", "自然而然到来的那个。")),
    )

    fun drawStick(no: Int): Stick = STICKS[no - 1]

    /** 报告给 LLM 的求签事实。 */
    fun stickReport(no: Int, name: String, question: String): String {
        val s = drawStick(no)
        val lines = ArrayList<String>()
        lines.add("## 签文")
        lines.add("- 第${no}签 · ${s.grade}")
        lines.add("- 求签人：${if (name.isBlank()) "（未署名）" else name}")
        lines.add("- 所问：${if (question.isBlank()) "（未填问题）" else question}")
        lines.add("")
        lines.add("### 签诗")
        lines.add(s.poem)
        if (!s.jieJie.isNullOrEmpty()) lines.add("\n签解：${s.jieJie}")
        if (!s.yinyuanJie.isNullOrEmpty()) lines.add("\n姻缘解读（原文）：${s.yinyuanJie}")
        lines.add("")
        lines.add("## 解读风格要求")
        lines.add("- 上上/上签：语气积极温暖，鼓励行动，提醒珍惜和经营")
        lines.add("- 中签：语气平和中肯，指出当前状态，给出务实建议")
        lines.add("- 中下/下签：先共情安抚，把凶象转化为提醒和建议，始终留有希望和转机")
        lines.add("- 下下签结尾一定要有积极的转机暗示，下下签绝不等于「完蛋了」")
        lines.add("- 结尾统一加：签诗仅供娱乐参考，心诚则灵。同一问题今日不宜再签，给缘分一点时间。")
        return lines.joinToString("\n")
    }
}
