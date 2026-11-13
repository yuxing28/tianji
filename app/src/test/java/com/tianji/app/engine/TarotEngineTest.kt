package com.tianji.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 对齐上游 tarot-skill tests/test_draw.py 契约。 */
class TarotEngineTest {

    @Test
    fun `同 seed 同时段 完全可复现`() {
        val a = TarotEngine.draw("three", "事业", 123, "night")
        val b = TarotEngine.draw("three", "事业", 123, "night")
        assertEquals(a.cards.map { it.card }, b.cards.map { it.card })
        assertEquals(a.cards.map { it.orientation }, b.cards.map { it.orientation })
    }

    @Test
    fun `马蹄七张 无重复牌 位次首尾正确`() {
        val h = TarotEngine.draw("horseshoe", seed = 7, timeFactorIn = "morning")
        val cards = h.cards.map { it.card }
        assertEquals(7, cards.size)
        assertEquals(cards.size, cards.toSet().size)
        assertEquals("远期过去", h.cards.first().position)
        assertEquals("结果", h.cards.last().position)
    }

    @Test
    fun `牌均在 78 张全集内`() {
        assertEquals(78, TarotEngine.CARDS.size)
        assertEquals(78, TarotEngine.CARDS.toSet().size)
        for (key in TarotEngine.SPREADS.map { it.key }) {
            val r = TarotEngine.draw(key, seed = 42)
            assertTrue(r.cards.all { it.card in TarotEngine.CARDS.toSet() })
        }
    }
}
