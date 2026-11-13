package com.tianji.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 锚点来自 docs/reference/bazi/pai_pan.py 模块 docstring 的九项 QA 口径。
 */
class BaziEngineTest {

    @Test
    fun `日柱锚点 1990-05-15 = 庚辰（序号16）`() {
        assertEquals(16, BaziEngine.dayGzIndex(1990, 5, 15))
        val (gan, zhi) = BaziEngine.gzFromIndex(BaziEngine.dayGzIndex(1990, 5, 15))
        assertEquals("庚", gan)
        assertEquals("辰", zhi)
    }

    @Test
    fun `年柱锚点 1990-02-03 10点 立春前 年柱必须己巳`() {
        val r = BaziEngine.compute(LocalDate.of(1990, 2, 3), 10, 0, null, "男")
        assertEquals("己", r.yearPillar.gan)
        assertEquals("巳", r.yearPillar.zhi)
        assertEquals(1989, r.yearNum)
    }

    @Test
    fun `月柱锚点 1990-05-15 = 辛巳`() {
        val r = BaziEngine.compute(LocalDate.of(1990, 5, 15), 10, 0, null, "男")
        assertEquals("辛", r.monthPillar.gan)
        assertEquals("巳", r.monthPillar.zhi)
    }

    @Test
    fun `时柱锚点 庚日午时 = 壬午`() {
        val r = BaziEngine.compute(LocalDate.of(1990, 5, 15), 12, 0, null, "男")
        assertEquals("壬", r.hourPillar.gan)
        assertEquals("午", r.hourPillar.zhi)
    }

    @Test
    fun `十神锚点 庚日见丁 = 正官 庚日见己 = 正印`() {
        // 庚=6：丁=3 → rel=floorMod(1-3,5)=3（克我），阴阳不同 → 正官
        assertEquals("正官", BaziEngine.shishenOf(6, 3))
        // 己=5 → rel=floorMod(2-3,5)=4（生我），阴阳不同 → 正印
        assertEquals("正印", BaziEngine.shishenOf(6, 5))
        assertEquals("比肩", BaziEngine.shishenOf(6, 6))
        assertEquals("劫财", BaziEngine.shishenOf(6, 7)) // 辛：同五行不同阴阳
    }

    @Test
    fun `大运锚点 1990-05-15 男（庚午阳年）必须顺排`() {
        val r = BaziEngine.compute(LocalDate.of(1990, 5, 15), 10, 0, null, "男")
        assertTrue(r.forward)
        assertEquals("庚午", "${r.yearPillar.gan}${r.yearPillar.zhi}")
        // 顺排第 1 步从月柱辛巳后一柱壬午起
        assertEquals("壬午", r.dayunRows.first { it.seq == "1" }.gz)
    }

    @Test
    fun `农历锚点 1990-05-15 = 一九九〇年四月廿一（1990闰五月）`() {
        val lunar = BaziEngine.solarToLunar(1990, 5, 15)
        assertEquals(1990, lunar.year)
        assertEquals(4, lunar.month)
        assertEquals(21, lunar.day)
        assertEquals(false, lunar.leap)
        assertEquals("1990年四月廿一", BaziEngine.formatLunar(lunar))
        // 反向
        assertEquals(LocalDate.of(1990, 5, 15), BaziEngine.lunarToSolar(1990, 4, 21, false))
    }

    @Test
    fun `空亡锚点 庚辰日 旬空申酉`() {
        val (a, b) = BaziEngine.kongwangZhi("庚", "辰")
        assertTrue(setOf(a, b) == setOf("申", "酉"))
    }

    @Test
    fun `节气锚点 2024立春 落在 2024-02-04`() {
        assertEquals("2024-02-04", BaziEngine.solarTermLocalDate(2024, 315.0).toString())
    }

    @Test
    fun `完整排盘 1990-05-15 辰时女 四柱齐全且警告为空`() {
        val r = BaziEngine.compute(LocalDate.of(1990, 5, 15), 8, 30, null, "女")
        assertEquals("庚辰", "${r.dayPillar.gan}${r.dayPillar.zhi}")
        // 女命阳年 → 逆排
        assertTrue(!r.forward)
        assertTrue(r.shenshaLines.isNotEmpty())
        assertTrue(r.liunianYears.contains(r.currentYear))
        // 女逆排第 1 步 = 月柱辛巳前一柱 庚辰
        assertEquals("庚辰", r.dayunRows.first { it.seq == "1" }.gz)
        assertEquals(emptyList<String>(), r.warnings.filter { it != "节气交界" && it != "立春前后" })
    }

    // ---- 以下对齐上游 test_pai_pan.py ----

    @Test
    fun `夜子时 23点30分 日柱进次日 时支子`() {
        val r = BaziEngine.compute(LocalDate.of(1990, 5, 15), 23, 30, null, "男")
        assertEquals("辛巳", "${r.dayPillar.gan}${r.dayPillar.zhi}")
        assertEquals("子", r.hourPillar.zhi)
        assertEquals("夜子时", r.zishi)
    }

    @Test
    fun `早子时 0点30分 日柱仍当日 时支子`() {
        val r = BaziEngine.compute(LocalDate.of(1990, 5, 15), 0, 30, null, "男")
        assertEquals("庚辰", "${r.dayPillar.gan}${r.dayPillar.zhi}")
        assertEquals("子", r.hourPillar.zhi)
        assertEquals("早子时", r.zishi)
    }

    @Test
    fun `阴年男 1991-06-15 辛未 大运逆排`() {
        val r = BaziEngine.compute(LocalDate.of(1991, 6, 15), 12, 0, null, "男")
        assertEquals("辛未", "${r.yearPillar.gan}${r.yearPillar.zhi}")
        assertTrue(!r.forward)
    }

    @Test
    fun `时辰未知 保留年月日柱 时柱未知并警告`() {
        val r = BaziEngine.compute(LocalDate.of(1990, 5, 15), sex = "男")
        assertEquals("庚午", "${r.yearPillar.gan}${r.yearPillar.zhi}")
        assertEquals("辛巳", "${r.monthPillar.gan}${r.monthPillar.zhi}")
        assertEquals("庚辰", "${r.dayPillar.gan}${r.dayPillar.zhi}")
        assertTrue(r.hourUnknown)
        assertEquals("未知", r.hourPillar.gan)
        assertEquals("未知", r.hourPillar.zhi)
        assertTrue(r.warnings.contains("时辰未知"))
    }

    @Test
    fun `神煞命中与不命中 1990-05-15 午时男`() {
        val r = BaziEngine.compute(LocalDate.of(1990, 5, 15), 12, 0, null, "男")
        val s = r.shenshaLines.joinToString("\n")
        assertTrue(s.contains("天乙贵人"))
        assertTrue(s.contains("将星"))
        assertTrue(!s.contains("驿马"))
        assertTrue(!s.contains("桃花"))
    }

    @Test
    fun `表驱动神煞 日干甲见丑为天乙 甲子日空戌亥`() {
        // 上游 test_pai_pan.py ShenshaTests 同参数
        val tianyi = BaziEngine.computeShensha(
            "乙", "亥", "戊", "丑", "甲", "寅", null, null, "男",
        ).joinToString("\n")
        assertTrue(tianyi.contains("天乙贵人"))
        val kongwang = BaziEngine.computeShensha(
            "甲", "戌", "乙", "亥", "甲", "子", null, null, "男",
        ).joinToString("\n")
        assertTrue(kongwang.contains("空亡"))
    }
}
