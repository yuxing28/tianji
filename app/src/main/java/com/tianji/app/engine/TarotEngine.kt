package com.tianji.app.engine

import java.security.SecureRandom
import java.time.LocalTime
import kotlin.random.Random

/** 塔罗抽牌 —— 自 docs/reference/tarot/draw.py 移植（MIT）：加权、无重复、可复现。 */
object TarotEngine {

    val MAJORS = listOf(
        "愚者", "魔术师", "女祭司", "女皇", "皇帝", "教皇", "恋人", "战车", "力量", "隐士",
        "命运之轮", "正义", "倒吊人", "死神", "节制", "恶魔", "高塔", "星星", "月亮", "太阳",
        "审判", "世界",
    )
    val SUITS = linkedMapOf("权杖" to "火", "圣杯" to "水", "宝剑" to "风", "星币" to "土")
    val RANKS = listOf("Ace", "二", "三", "四", "五", "六", "七", "八", "九", "十", "侍从", "骑士", "皇后", "国王")
    val MINORS: List<String> = SUITS.keys.flatMap { s -> RANKS.map { r -> s + r } }

    private val MAJOR_ELEMENTS = "风 风 水 土 火 土 风 水 火 土 火 风 水 水 火 土 火 风 水 火 火 土".split(" ")

    val ELEMENTS: Map<String, String> = buildMap {
        MAJORS.forEachIndexed { i, c -> put(c, MAJOR_ELEMENTS[i]) }
        for (c in MINORS) put(c, SUITS.entries.first { c.startsWith(it.key) }.value)
    }

    val CARDS: List<String> = MAJORS + MINORS

    data class SpreadPosition(val name: String, val majorBoost: Boolean, val uprightBias: Boolean)
    data class Spread(val key: String, val name: String, val positions: List<SpreadPosition>)

    val SPREADS: List<Spread> = listOf(
        Spread("single", "单张牌", listOf(SpreadPosition("当前指引", true, false))),
        Spread(
            "three", "三牌阵", listOf(
                SpreadPosition("过去", false, false),
                SpreadPosition("现在", true, false),
                SpreadPosition("未来", false, true),
            )
        ),
        Spread(
            "diamond", "五牌阵", listOf(
                SpreadPosition("核心", true, false),
                SpreadPosition("根源", false, false),
                SpreadPosition("阻力", false, false),
                SpreadPosition("潜力", false, false),
                SpreadPosition("建议", true, true),
            )
        ),
        Spread(
            "moon", "月亮牌阵", listOf(
                SpreadPosition("新月", true, false),
                SpreadPosition("上弦", false, false),
                SpreadPosition("满月", true, false),
                SpreadPosition("下弦", false, false),
            )
        ),
        Spread(
            "horseshoe", "马蹄形", listOf(
                SpreadPosition("远期过去", false, false),
                SpreadPosition("近期过去", false, false),
                SpreadPosition("当前", true, false),
                SpreadPosition("近期未来", false, false),
                SpreadPosition("外部影响", true, false),
                SpreadPosition("建议", false, true),
                SpreadPosition("结果", true, true),
            )
        ),
        Spread(
            "celtic", "凯尔特十字", listOf(
                SpreadPosition("核心", true, false),
                SpreadPosition("交叉", false, false),
                SpreadPosition("意识目标", false, false),
                SpreadPosition("根基过去", false, false),
                SpreadPosition("近期过去", true, false),
                SpreadPosition("近期未来", false, false),
                SpreadPosition("自我", false, false),
                SpreadPosition("环境", false, false),
                SpreadPosition("希望与恐惧", false, false),
                SpreadPosition("结果", true, true),
            )
        ),
    )

    val TIME_BOOST: Map<String, Set<String>> = mapOf(
        "morning" to setOf("火", "风"),
        "afternoon" to setOf("水", "土"),
        "night" to setOf("major"),
    )

    fun timeFactor(hour: Int = LocalTime.now().hour): String =
        if (hour in 6..11) "morning" else if (hour in 12..17) "afternoon" else "night"

    fun makeSeed(question: String): Long {
        val buf = ByteArray(8)
        SecureRandom().nextBytes(buf)
        var s = 0L
        for (b in buf) s = (s shl 8) or (b.toLong() and 0xff)
        return s xor (System.nanoTime() * 31L) xor question.fold(1125899906842597L) { acc, c -> acc * 31 + c.code }
    }

    data class DrawnCard(val position: String, val card: String, val orientation: String, val isMajor: Boolean)

    data class DrawResult(
        val seed: Long,
        val spreadKey: String,
        val spreadName: String,
        val question: String,
        val timeFactor: String,
        val cards: List<DrawnCard>,
    )

    private fun weight(card: String, majorBoost: Boolean, boosted: Set<String>): Double {
        val major = card in MAJORS
        val base = if (majorBoost && major) 60.0 / 28.0 else 1.0
        val hit = (("major" in boosted) && major) || (ELEMENTS[card]!! in boosted)
        return base * if (hit) 1.08 else 1.0
    }

    fun draw(spreadKey: String, question: String = "", seed: Long? = null, timeFactorIn: String? = null): DrawResult {
        val spread = SPREADS.first { it.key == spreadKey }
        val finalSeed = seed ?: makeSeed(question)
        val factor = timeFactorIn ?: timeFactor()
        val boosted = TIME_BOOST[factor]!!
        val rng = Random(finalSeed)
        val pool = CARDS.toMutableList()
        val cards = ArrayList<DrawnCard>(spread.positions.size)
        for (pos in spread.positions) {
            val weights = pool.map { weight(it, pos.majorBoost, boosted) }
            var r = rng.nextDouble() * weights.sum()
            var picked = pool.indices.last()
            for (i in pool.indices) {
                r -= weights[i]
                if (r < 0) {
                    picked = i
                    break
                }
            }
            val card = pool.removeAt(picked)
            cards.add(
                DrawnCard(
                    position = pos.name,
                    card = card,
                    orientation = if (rng.nextDouble() < (if (pos.uprightBias) 0.7 else 0.6)) "正位" else "逆位",
                    isMajor = card in MAJORS,
                )
            )
        }
        return DrawResult(finalSeed, spreadKey, spread.name, question, factor, cards)
    }
}
