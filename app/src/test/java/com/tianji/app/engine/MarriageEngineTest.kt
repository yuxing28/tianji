package com.tianji.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MarriageEngineTest {

    private fun bazi(y: Int, m: Int, d: Int, sex: String): BaziEngine.BaziResult =
        BaziEngine.compute(LocalDate.of(y, m, d), 12, 0, null, sex)

    @Test
    fun `纳音相克方向 炉中火克海中金`() {
        // 1984 甲子年生 → 海中金；1986 丙寅年生 → 炉中火
        val a = bazi(1984, 6, 1, "男")
        val b = bazi(1986, 6, 1, "男")
        val r = MarriageEngine.match(a, b)
        val nayin = r.factors.first { it.dimension == "纳音配对" }
        assertTrue(nayin.notes.joinToString().contains("炉中火 克 海中金"))
    }

    @Test
    fun `纳音相克另一方向 涧下水克覆灯火`() {
        // 1996 丙子 → 涧下水；2025 乙巳 → 覆灯火（水克火）
        val a = bazi(1996, 6, 1, "男")
        val b = bazi(2025, 6, 1, "女")
        val r = MarriageEngine.match(a, b)
        val nayin = r.factors.first { it.dimension == "纳音配对" }
        assertTrue(nayin.notes.joinToString().contains("涧下水 克 覆灯火"))
    }

    @Test
    fun `纳音相生 大林木生覆灯火 不说克`() {
        // 1988 戊辰 → 大林木；2025 乙巳 → 覆灯火（木生火）
        val a = bazi(1988, 6, 1, "男")
        val b = bazi(2025, 6, 1, "女")
        val r = MarriageEngine.match(a, b)
        val nayin = r.factors.first { it.dimension == "纳音配对" }
        assertTrue(nayin.notes.joinToString().contains("相生"))
    }

    @Test
    fun `求签编号稳定且在 1-100`() {
        val n1 = MarriageEngine.stickNo("小明", "姻缘", 3, LocalDate.of(2026, 11, 7))
        val n2 = MarriageEngine.stickNo("小明", "姻缘", 3, LocalDate.of(2026, 11, 7))
        assertEquals(n1, n2)
        assertTrue(n1 in 1..100)
        assertEquals("上上签", MarriageEngine.gradeOf(1))
        assertEquals("中签", MarriageEngine.gradeOf(50))
        assertEquals("下下转上上", MarriageEngine.gradeOf(100))
    }
}
