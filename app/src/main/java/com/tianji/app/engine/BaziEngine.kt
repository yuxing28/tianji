package com.tianji.app.engine

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * 四柱八字排盘引擎 —— 自 docs/reference/bazi/pai_pan.py 逐行移植（MIT）。
 * 锚点：1990-05-15 = 庚辰日；1990-02-03 10:00 → 年柱己巳；1990-05-15 → 辛巳月；
 * 庚日午时 = 壬午；1990 男阳年顺排；1990 农历四月廿一 = 1990-05-15。
 */
object BaziEngine {

    // ------------------------------------------------------------------
    // 常量
    // ------------------------------------------------------------------

    private const val GAN = "甲乙丙丁戊己庚辛壬癸"
    private const val ZHI = "子丑寅卯辰巳午未申酉戌亥"

    private val JIE_DEFS = listOf(
        Triple(315.0, "立春", 2),
        Triple(345.0, "惊蛰", 3),
        Triple(15.0, "清明", 4),
        Triple(45.0, "立夏", 5),
        Triple(75.0, "芒种", 6),
        Triple(105.0, "小暑", 7),
        Triple(135.0, "立秋", 8),
        Triple(165.0, "白露", 9),
        Triple(195.0, "寒露", 10),
        Triple(225.0, "立冬", 11),
        Triple(255.0, "大雪", 0),
        Triple(285.0, "小寒", 1),
    )
    private val ZHONGQI_LONGS = listOf(0.0, 30.0, 60.0, 90.0, 120.0, 150.0, 180.0, 210.0, 240.0, 270.0, 300.0, 330.0)

    // 年上起月：年干下标 -> 寅月天干下标
    private val YUE_GAN_YIN = intArrayOf(2, 4, 6, 8, 0, 2, 4, 6, 8, 0)
    // 五鼠遁：日干下标 -> 子时天干下标
    private val ZI_SHI_GAN = intArrayOf(0, 2, 4, 6, 8, 0, 2, 4, 6, 8)

    private val CANGGAN = mapOf(
        "子" to listOf("癸水"),
        "丑" to listOf("己土", "癸水", "辛金"),
        "寅" to listOf("甲木", "丙火", "戊土"),
        "卯" to listOf("乙木"),
        "辰" to listOf("戊土", "乙木", "癸水"),
        "巳" to listOf("丙火", "庚金", "戊土"),
        "午" to listOf("丁火", "己土"),
        "未" to listOf("己土", "丁火", "乙木"),
        "申" to listOf("庚金", "壬水", "戊土"),
        "酉" to listOf("辛金"),
        "戌" to listOf("戊土", "辛金", "丁火"),
        "亥" to listOf("壬水", "甲木"),
    )

    private val SHISHEN_SAME = arrayOf("比肩", "食神", "偏财", "偏官", "偏印")
    private val SHISHEN_DIFF = arrayOf("劫财", "伤官", "正财", "正官", "正印")

    private val GAN_LABELS = listOf("年干", "月干", "日干", "时干")
    private val ZHI_LABELS = listOf("年支", "月支", "日支", "时支")
    private val MDH_ZHI_LABELS = listOf("月支", "日支", "时支")

    private val SHICHEN_BOUNDARIES_MIN = intArrayOf(60, 180, 300, 420, 540, 660, 780, 900, 1020, 1140, 1260, 1380)
    private val SHICHEN_MID = mapOf(
        "子" to (0 to 0), "丑" to (2 to 0), "寅" to (4 to 0), "卯" to (6 to 0),
        "辰" to (8 to 0), "巳" to (10 to 0), "午" to (12 to 0), "未" to (14 to 0),
        "申" to (16 to 0), "酉" to (18 to 0), "戌" to (20 to 0), "亥" to (22 to 0),
    )

    private val LUNAR_DAY_NAMES = listOf(
        "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
        "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
        "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十",
    )
    private val LUNAR_MONTH_NAMES = listOf(
        "正月", "二月", "三月", "四月", "五月", "六月",
        "七月", "八月", "九月", "十月", "十一月", "十二月",
    )

    // 神煞表（口径同 references/shensha-table.md）
    private val TIANYI = mapOf(
        "甲" to listOf("丑", "未"), "戊" to listOf("丑", "未"),
        "乙" to listOf("子", "申"), "己" to listOf("子", "申"),
        "丙" to listOf("亥", "酉"), "丁" to listOf("亥", "酉"),
        "壬" to listOf("卯", "巳"), "癸" to listOf("卯", "巳"),
        "庚" to listOf("丑", "未", "寅", "午"), "辛" to listOf("寅", "午"),
    )
    private val TIANDE = mapOf(
        "寅" to ("gan" to "丁"), "卯" to ("zhi" to "申"), "辰" to ("gan" to "壬"),
        "巳" to ("gan" to "辛"), "午" to ("zhi" to "亥"), "未" to ("gan" to "甲"),
        "申" to ("gan" to "癸"), "酉" to ("zhi" to "寅"), "戌" to ("gan" to "丙"),
        "亥" to ("gan" to "乙"), "子" to ("gan" to "己"), "丑" to ("gan" to "庚"),
    )
    private val YUEDE = mapOf(
        "寅" to "丙", "午" to "丙", "戌" to "丙",
        "申" to "壬", "子" to "壬", "辰" to "壬",
        "亥" to "甲", "卯" to "甲", "未" to "甲",
        "巳" to "庚", "酉" to "庚", "丑" to "庚",
    )
    private val WENCHANG = mapOf(
        "甲" to "巳", "乙" to "午", "丙" to "申", "丁" to "酉", "戊" to "申",
        "己" to "酉", "庚" to "亥", "辛" to "子", "壬" to "寅", "癸" to "卯",
    )
    private val XUETANG = mapOf(
        "甲" to "亥", "乙" to "午", "丙" to "寅", "丁" to "酉", "戊" to "寅",
        "己" to "酉", "庚" to "巳", "辛" to "子", "壬" to "申", "癸" to "卯",
    )
    private val CIGUAN = mapOf(
        "甲" to "寅", "乙" to "卯", "丙" to "巳", "丁" to "午", "戊" to "巳",
        "己" to "午", "庚" to "申", "辛" to "酉", "壬" to "亥", "癸" to "子",
    )
    private val SANHE = mapOf(
        "寅" to "火", "午" to "火", "戌" to "火",
        "申" to "水", "子" to "水", "辰" to "水",
        "巳" to "金", "酉" to "金", "丑" to "金",
        "亥" to "木", "卯" to "木", "未" to "木",
    )
    private val JIANGXING = mapOf("火" to "午", "水" to "子", "金" to "酉", "木" to "卯")
    private val HUAGAI = mapOf("火" to "戌", "水" to "辰", "金" to "丑", "木" to "未")
    private val YIMA = mapOf("火" to "申", "水" to "寅", "金" to "亥", "木" to "巳")
    private val JIESHA = mapOf("水" to "巳", "火" to "亥", "金" to "寅", "木" to "申")
    private val ZAISHA = mapOf("水" to "午", "火" to "子", "金" to "卯", "木" to "酉")
    private val WANGSHEN = mapOf("水" to "亥", "火" to "巳", "金" to "申", "木" to "寅")
    private val TAOHUA = mapOf("火" to "卯", "水" to "酉", "金" to "午", "木" to "子")
    private val GUCHEN = mapOf(
        "亥" to "寅", "子" to "寅", "丑" to "寅", "寅" to "巳", "卯" to "巳", "辰" to "巳",
        "巳" to "申", "午" to "申", "未" to "申", "申" to "亥", "酉" to "亥", "戌" to "亥",
    )
    private val GUASU = mapOf(
        "亥" to "戌", "子" to "戌", "丑" to "戌", "寅" to "丑", "卯" to "丑", "辰" to "丑",
        "巳" to "辰", "午" to "辰", "未" to "辰", "申" to "未", "酉" to "未", "戌" to "未",
    )
    private val JINYU = mapOf(
        "甲" to "辰", "乙" to "巳", "丙" to "未", "丁" to "申", "戊" to "未",
        "己" to "申", "庚" to "戌", "辛" to "亥", "壬" to "丑", "癸" to "寅",
    )
    private val YANGBLADE = mapOf(
        "甲" to "卯", "乙" to "寅", "丙" to "午", "丁" to "巳", "戊" to "午",
        "己" to "巳", "庚" to "酉", "辛" to "子", "壬" to "子", "癸" to "亥",
    )
    private val YUANCHEN_FORWARD = mapOf(
        "子" to "未", "丑" to "申", "寅" to "酉", "卯" to "戌", "辰" to "亥", "巳" to "子",
        "午" to "丑", "未" to "寅", "申" to "卯", "酉" to "辰", "戌" to "巳", "亥" to "午",
    )
    private val YUANCHEN_REVERSE = mapOf(
        "子" to "巳", "丑" to "午", "寅" to "未", "卯" to "申", "辰" to "酉", "巳" to "戌",
        "午" to "亥", "未" to "子", "申" to "丑", "酉" to "寅", "戌" to "卯", "亥" to "辰",
    )
    private val XUEREN = mapOf(
        "寅" to "午", "卯" to "未", "辰" to "申", "巳" to "酉", "午" to "戌", "未" to "亥",
        "申" to "子", "酉" to "丑", "戌" to "寅", "亥" to "卯", "子" to "辰", "丑" to "巳",
    )

    // ------------------------------------------------------------------
    // 时间 / 儒略日（北京 UTC+8）
    // ------------------------------------------------------------------

    private val BJ = ZoneOffset.ofHours(8)
    private const val J2000 = 2451545.0
    private const val UNIX_JD = 2440587.5

    /** TT−UTC 近似秒，Espenak/Meeus 分段多项式，1900–2100 足够到分钟级。 */
    private fun deltaTSec(year: Double): Double {
        val t = year - 2000.0
        return when {
            year < 1920 -> {
                val t1 = year - 1900.0
                -2.79 + 1.494119 * t1 - 0.0598939 * t1 * t1 + 0.0061966 * t1 * t1 * t1 - 0.000197 * t1 * t1 * t1 * t1
            }
            year < 1941 -> {
                val t1 = year - 1920.0
                21.20 + 0.84493 * t1 - 0.076100 * t1 * t1 + 0.0020936 * t1 * t1 * t1
            }
            year < 1961 -> {
                val t1 = year - 1950.0
                29.07 + 0.407 * t1 - t1 * t1 / 233.0 + t1 * t1 * t1 / 2547.0
            }
            year < 1986 -> {
                val t1 = year - 1975.0
                45.45 + 1.067 * t1 - t1 * t1 / 260.0 - t1 * t1 * t1 / 718.0
            }
            year < 2005 -> {
                val t1 = year - 2000.0
                63.86 + 0.3345 * t1 - 0.060374 * t1 * t1 + 0.0017275 * t1 * t1 * t1 +
                    0.000651814 * t1 * t1 * t1 * t1 + 0.00002373599 * t1 * t1 * t1 * t1 * t1
            }
            year < 2050 -> 62.92 + 0.32217 * t + 0.005589 * t * t
            else -> -20.0 + 32.0 * ((year - 1820.0) / 100.0) * ((year - 1820.0) / 100.0) - 0.5628 * (2150.0 - year)
        }
    }

    private fun datetimeUtcFromJd(jd: Double): ZonedDateTime {
        val epochMilli = ((jd - UNIX_JD) * 86400.0 * 1000.0).roundToLong()
        return Instant.ofEpochMilli(epochMilli).atZone(ZoneOffset.UTC)
    }

    private fun jdTTToBeijing(jdTT: Double): ZonedDateTime {
        val year = 2000.0 + (jdTT - J2000) / 365.242189
        val utcJd = jdTT - deltaTSec(year) / 86400.0
        return datetimeUtcFromJd(utcJd).withZoneSameInstant(BJ)
    }

    private fun beijing(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): ZonedDateTime =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, BJ)

    /** 格里历儒略日数（中午，整数）。用于日柱。 */
    fun jdn(year: Int, month: Int, day: Int): Long {
        val a = (14 - month) / 12
        val y = year + 4800 - a
        val m = month + 12 * a - 3
        return day.toLong() + (153L * m + 2) / 5 + 365L * y + y / 4 - y / 100 + y / 400 - 32045
    }

    // ------------------------------------------------------------------
    // 太阳视黄经 / 二十四节气
    // ------------------------------------------------------------------

    /** 太阳视黄经（度）。Meeus AA 第 25 章简式，1900–2100 约 0.01° 以内。 */
    fun sunApparentLongitude(jdTT: Double): Double {
        val t = (jdTT - J2000) / 36525.0
        val l0 = 280.46646 + 36000.76983 * t + 0.0003032 * t * t
        val m = 357.52911 + 35999.05029 * t - 0.0001537 * t * t
        val mr = Math.toRadians(m)
        val c = (1.914602 - 0.004817 * t - 0.000014 * t * t) * sin(mr) +
            (0.019993 - 0.000101 * t) * sin(2.0 * mr) +
            0.000289 * sin(3.0 * mr)
        val omega = 125.04 - 1934.136 * t
        val lam = l0 + c - 0.00569 - 0.00478 * sin(Math.toRadians(omega))
        return mod360(lam)
    }

    private fun mod360(x: Double): Double {
        val r = x % 360.0
        return if (r < 0) r + 360.0 else r
    }

    private fun lonDelta(actual: Double, target: Double): Double {
        val d = mod360(actual - target + 180.0) - 180.0
        return if (d > 180.0) d - 360.0 else d
    }

    private val solarTermCache = ConcurrentHashMap<Long, Double>()

    /** 给定公历年、目标黄经，返回该年对应定气的 TT 儒略日。立春 = 315°。 */
    fun solarTermJd(year: Int, longitude: Double): Double {
        val lon = mod360(longitude)
        val key = year.toLong() * 360L + lon.roundToInt().toLong()
        solarTermCache[key]?.let { return it }
        val deltaLon = mod360(lon - 280.46646)
        val jd0 = J2000 + (year - 2000) * 365.242189 + deltaLon / 0.98564736
        var lo = jd0 - 8.0
        var hi = jd0 + 8.0
        repeat(52) {
            val mid = (lo + hi) / 2.0
            if (lonDelta(sunApparentLongitude(mid), lon) < 0) lo = mid else hi = mid
        }
        val result = (lo + hi) / 2.0
        solarTermCache[key] = result
        return result
    }

    private fun solarTermBeijing(year: Int, longitude: Double): ZonedDateTime =
        jdTTToBeijing(solarTermJd(year, longitude))

    /** 测试/展示用：节气所在公历日期。 */
    fun solarTermLocalDate(year: Int, longitude: Double): LocalDate =
        jdTTToBeijing(solarTermJd(year, longitude)).toLocalDate()

    private data class JieEvent(val dt: ZonedDateTime, val name: String, val zhiIndex: Int, val lon: Double, val year: Int)

    private fun jieEventsAround(year: Int): List<JieEvent> {
        val events = ArrayList<JieEvent>(36)
        for (y in year - 1..year + 1) {
            for ((lon, name, zhiI) in JIE_DEFS) {
                events.add(JieEvent(solarTermBeijing(y, lon), name, zhiI, lon, y))
            }
        }
        return events.sortedBy { it.dt.toInstant().toEpochMilli() }
    }

    // ------------------------------------------------------------------
    // 定朔（Meeus 第 49 章）
    // ------------------------------------------------------------------

    /** 新月力学时 JDE。k=0 对应 2000-01-06 附近朔。精度约 1–2 分钟。 */
    fun newMoonJde(k: Int): Double {
        val t = k / 1236.85
        var jde = 2451550.09766 +
            29.530588861 * k +
            0.00015437 * t * t -
            0.000000150 * t * t * t +
            0.00000000073 * t * t * t * t
        val e = 1.0 - 0.002516 * t - 0.0000074 * t * t
        val m = Math.toRadians(2.5534 + 29.10535670 * k - 0.0000014 * t * t - 0.00000011 * t * t * t)
        val mp = Math.toRadians(
            201.5643 + 385.81693528 * k + 0.0107582 * t * t + 0.00001238 * t * t * t - 0.000000058 * t * t * t * t
        )
        val f = Math.toRadians(
            160.7108 + 390.67050284 * k - 0.0016118 * t * t - 0.00000227 * t * t * t + 0.000000011 * t * t * t * t
        )
        val omega = Math.toRadians(124.7746 - 1.56375588 * k + 0.0020672 * t * t + 0.00000215 * t * t * t)
        val args = doubleArrayOf(
            mp, m, 2 * mp, 2 * f, mp - m, mp + m, 2 * m, mp - 2 * f, mp + 2 * f,
            2 * mp + m, 3 * mp, m + 2 * f, m - 2 * f, 2 * mp - m, omega, mp + 2 * m,
            2 * mp - 2 * f, 3 * m, mp + m - 2 * f, 2 * mp + 2 * f, mp + m + 2 * f,
            mp - m + 2 * f, mp - m - 2 * f, 3 * mp + m, 4 * mp,
        )
        val coef = doubleArrayOf(
            -0.40720, 0.17241 * e, 0.01608, 0.01039, 0.00739 * e, -0.00514 * e, 0.00208 * e * e,
            -0.00111, -0.00057, 0.00056 * e, -0.00042, 0.00042 * e, 0.00038 * e, -0.00024 * e,
            -0.00017, -0.00007, 0.00004, 0.00004, 0.00003, 0.00003, -0.00003, 0.00003,
            -0.00002, -0.00002, 0.00002,
        )
        for (i in args.indices) jde += coef[i] * sin(args[i])
        val aDeg = doubleArrayOf(
            299.77 + 0.107408 * k - 0.009173 * t * t, 251.88 + 0.016321 * k, 251.83 + 26.651886 * k,
            349.42 + 36.412478 * k, 84.66 + 18.206239 * k, 141.74 + 53.303771 * k, 207.14 + 2.453732 * k,
            154.84 + 7.306860 * k, 34.52 + 27.261239 * k, 207.19 + 0.121824 * k, 291.34 + 1.844379 * k,
            161.72 + 24.198154 * k, 239.56 + 25.513099 * k, 331.55 + 3.592518 * k,
        )
        val ac = intArrayOf(325, 165, 164, 126, 110, 62, 60, 56, 47, 42, 40, 37, 35, 23)
        for (i in aDeg.indices) jde += ac[i] * 1e-6 * sin(Math.toRadians(aDeg[i]))
        return jde
    }

    private fun kNear(jd: Double): Int = Math.round((jd - 2451550.09766) / 29.530588861).toInt()

    private fun lastNewMoonKOnOrBefore(jdTT: Double): Int {
        var k = kNear(jdTT)
        while (newMoonJde(k) > jdTT) k--
        while (newMoonJde(k + 1) <= jdTT) k++
        return k
    }

    // ------------------------------------------------------------------
    // 农历（冬至十一月 + 无中气置闰）
    // ------------------------------------------------------------------

    data class LunarMonth(
        val year: Int,
        val month: Int,
        val leap: Boolean,
        val start: LocalDate,
        val days: Int,
        val shuoJd: Double,
    )

    private fun zhongqiJds(year: Int): List<Double> {
        val out = ArrayList<Double>(36)
        for (y in year - 1..year + 1) for (lon in ZHONGQI_LONGS) out.add(solarTermJd(y, lon))
        return out
    }

    private val lunarSegCache = ConcurrentHashMap<Int, List<LunarMonth>>()

    /** 从「dongzhi_year 的冬至所在十一月」起到下一年冬至月之前的各农历月。 */
    fun lunarMonthsBetweenDongzhi(dongzhiYear: Int): List<LunarMonth> {
        lunarSegCache[dongzhiYear]?.let { return it }
        val y = dongzhiYear
        val dz0 = solarTermJd(y, 270.0)
        val dz1 = solarTermJd(y + 1, 270.0)
        val k0 = lastNewMoonKOnOrBefore(dz0)
        val k1 = lastNewMoonKOnOrBefore(dz1)
        val moons = (k0..k1).map { newMoonJde(it) }
        val n = moons.size - 1

        val zhong = zhongqiJds(y)
        var leapIndex = -1
        if (n == 13) {
            for (i in 0 until n) {
                var has = false
                for (z in zhong) {
                    if (moons[i] <= z && z < moons[i + 1]) {
                        has = true
                        break
                    }
                }
                if (!has) {
                    leapIndex = i
                    break
                }
            }
        }

        val months = ArrayList<LunarMonth>(n)
        var monthNum = 11
        var lunarYear = y
        for (i in 0 until n) {
            val isLeap = i == leapIndex
            if (i > 0 && !isLeap) {
                monthNum += 1
                if (monthNum == 13) {
                    monthNum = 1
                    lunarYear = y + 1
                }
            }
            val start = jdTTToBeijing(moons[i]).toLocalDate()
            val nextD = jdTTToBeijing(moons[i + 1]).toLocalDate()
            val days = ChronoUnit.DAYS.between(start, nextD).toInt()
            months.add(LunarMonth(lunarYear, monthNum, isLeap, start, days, moons[i]))
        }
        val result = months.toList()
        lunarSegCache[dongzhiYear] = result
        return result
    }

    private fun lunarMonthsCoveringSolarYear(solarYear: Int): List<LunarMonth> {
        val a = lunarMonthsBetweenDongzhi(solarYear - 1)
        val b = lunarMonthsBetweenDongzhi(solarYear)
        val seen = HashSet<String>()
        val out = ArrayList<LunarMonth>(a.size + b.size)
        for (m in a + b) {
            val key = "${m.start}|${m.year}|${m.month}|${m.leap}"
            if (seen.add(key)) out.add(m)
        }
        return out.sortedBy { it.start.toEpochDay() }
    }

    data class LunarDate(val year: Int, val month: Int, val day: Int, val leap: Boolean)

    /** 公历日 → 农历。 */
    fun solarToLunar(y: Int, m: Int, d: Int): LunarDate {
        val target = LocalDate.of(y, m, d)
        val months = ArrayList<LunarMonth>()
        if (y - 1 >= 1890) months.addAll(lunarMonthsBetweenDongzhi(y - 2))
        months.addAll(lunarMonthsCoveringSolarYear(y))
        for (info in months) {
            val end = info.start.plusDays(info.days.toLong())
            if (!target.isBefore(info.start) && target.isBefore(end)) {
                return LunarDate(info.year, info.month, (ChronoUnit.DAYS.between(info.start, target) + 1).toInt(), info.leap)
            }
        }
        throw IllegalArgumentException("无法把 $y-$m-$d 换成农历（超出推算范围）")
    }

    /** 农历日 → 公历。闰月必须 leap=true。 */
    fun lunarToSolar(ly: Int, lm: Int, ld: Int, leap: Boolean = false): LocalDate {
        val months = lunarMonthsBetweenDongzhi(ly - 1) + lunarMonthsBetweenDongzhi(ly)
        val found = months.firstOrNull { it.year == ly && it.month == lm && it.leap == leap }
            ?: throw IllegalArgumentException("找不到农历 ${ly}年${if (leap) "闰" else ""}${monthName(lm)}")
        if (ld < 1 || ld > found.days) {
            throw IllegalArgumentException("农历 ${ly}年${if (leap) "闰" else ""}${monthName(lm)}只有 ${found.days} 天")
        }
        return found.start.plusDays((ld - 1).toLong())
    }

    fun formatLunar(ld: LunarDate): String =
        "${ld.year}年${if (ld.leap) "闰" else ""}${monthName(ld.month)}${dayName(ld.day)}"

    private fun monthName(m: Int): String = LUNAR_MONTH_NAMES.getOrElse(m - 1) { "${m}月" }
    private fun dayName(d: Int): String = LUNAR_DAY_NAMES.getOrElse(d - 1) { "$d" }

    // ------------------------------------------------------------------
    // 干支 / 十神 / 藏干
    // ------------------------------------------------------------------

    fun gzFromIndex(idx: Int): Pair<String, String> {
        val i = Math.floorMod(idx, 60)
        return GAN[i % 10].toString() to ZHI[i % 12].toString()
    }

    /** 立春后的年干支序号。1984=甲子=0。 */
    fun yearGzIndex(year: Int): Int = Math.floorMod(year - 4, 60)

    /** 日柱序号。锚点 1990-05-15 = 16 庚辰。 */
    fun dayGzIndex(year: Int, month: Int, day: Int): Int = Math.floorMod((jdn(year, month, day) + 49).toInt(), 60)

    /** 十神：rel = (他干五行 - 日干五行) % 5。 */
    fun shishenOf(dayGanIdx: Int, otherGanIdx: Int?): String {
        if (otherGanIdx == null) return "—"
        val rel = Math.floorMod(otherGanIdx / 2 - dayGanIdx / 2, 5)
        return if (dayGanIdx % 2 == otherGanIdx % 2) SHISHEN_SAME[rel] else SHISHEN_DIFF[rel]
    }

    private fun formatCanggan(zhi: String): String = (CANGGAN[zhi] ?: emptyList()).joinToString("、")

    fun kongwangZhi(dayGan: String, dayZhi: String): Pair<String, String> {
        val start = Math.floorMod(ZHI.indexOf(dayZhi) - GAN.indexOf(dayGan), 12)
        return ZHI[Math.floorMod(start - 2, 12)].toString() to ZHI[Math.floorMod(start - 1, 12)].toString()
    }

    // ------------------------------------------------------------------
    // 神煞
    // ------------------------------------------------------------------

    private fun positions(needles: Collection<String>, values: List<String?>, labels: List<String>): List<String> =
        labels.filterIndexed { i, _ -> values[i] != null && values[i]!! in needles }

    private fun sanheNeedles(yearZhi: String, dayZhi: String, table: Map<String, String>): Set<String> =
        setOf(table[SANHE[yearZhi]]!!, table[SANHE[dayZhi]]!!)

    fun computeShensha(
        yearGan: String, yearZhi: String,
        monthGan: String, monthZhi: String,
        dayGan: String, dayZhi: String,
        hourGan: String?, hourZhi: String?,
        sex: String,
    ): List<String> {
        val gans = listOf(yearGan, monthGan, dayGan, hourGan)
        val zhis = listOf(yearZhi, monthZhi, dayZhi, hourZhi)
        val mdhZhis = listOf(monthZhi, dayZhi, hourZhi)
        val lines = ArrayList<String>()

        fun add(name: String, pos: List<String>, method: String) {
            if (pos.isNotEmpty()) lines.add("- $name — 见于${pos.joinToString("、")} — $method")
        }

        val tianyiNeed = (TIANYI[yearGan]!! + TIANYI[dayGan]!!).toSet()
        add("天乙贵人", positions(tianyiNeed, zhis, ZHI_LABELS), "年干/日干查地支")

        val (tdKind, tdWord) = TIANDE[monthZhi]!!
        if (tdKind == "gan") add("天德", positions(setOf(tdWord), gans, GAN_LABELS), "月支查天干")
        else add("天德", positions(setOf(tdWord), zhis, ZHI_LABELS), "月支查地支")

        add("月德", positions(setOf(YUEDE[monthZhi]!!), gans, GAN_LABELS), "月支三合")

        val wenchangNeed = setOf(WENCHANG[yearGan]!!, WENCHANG[dayGan]!!)
        add("文昌", positions(wenchangNeed, zhis, ZHI_LABELS), "年干/日干查地支")
        add("学堂", positions(setOf(XUETANG[dayGan]!!), zhis, ZHI_LABELS), "日干长生")
        add("词馆", positions(setOf(CIGUAN[dayGan]!!), zhis, ZHI_LABELS), "日干临官")

        add("将星", positions(sanheNeedles(yearZhi, dayZhi, JIANGXING), zhis, ZHI_LABELS), "年支/日支三合")
        add("华盖", positions(sanheNeedles(yearZhi, dayZhi, HUAGAI), zhis, ZHI_LABELS), "年支/日支")
        add("驿马", positions(sanheNeedles(yearZhi, dayZhi, YIMA), zhis, ZHI_LABELS), "年支/日支")

        val tianyiPrev = ZHI[Math.floorMod(ZHI.indexOf(monthZhi) - 1, 12)].toString()
        add("天医", positions(setOf(tianyiPrev), zhis, ZHI_LABELS), "月支前一位")
        add("禄神", positions(setOf(CIGUAN[dayGan]!!), zhis, ZHI_LABELS), "日干")
        add("金舆", positions(setOf(JINYU[dayGan]!!), zhis, ZHI_LABELS), "日干")
        add("羊刃", positions(setOf(YANGBLADE[dayGan]!!), zhis, ZHI_LABELS), "日干")
        add("劫煞", positions(sanheNeedles(yearZhi, dayZhi, JIESHA), zhis, ZHI_LABELS), "年支/日支")
        add("灾煞", positions(sanheNeedles(yearZhi, dayZhi, ZAISHA), zhis, ZHI_LABELS), "年支/日支")
        add("亡神", positions(sanheNeedles(yearZhi, dayZhi, WANGSHEN), zhis, ZHI_LABELS), "年支/日支")
        add("桃花/咸池", positions(sanheNeedles(yearZhi, dayZhi, TAOHUA), zhis, ZHI_LABELS), "年支/日支")
        add("孤辰", positions(setOf(GUCHEN[yearZhi]!!), zhis, ZHI_LABELS), "年支")
        add("寡宿", positions(setOf(GUASU[yearZhi]!!), zhis, ZHI_LABELS), "年支")

        val (kw1, kw2) = kongwangZhi(dayGan, dayZhi)
        add("空亡/旬空", positions(setOf(kw1, kw2), zhis, ZHI_LABELS), "日柱旬空")

        val yangYear = GAN.indexOf(yearGan) % 2 == 0
        val yuanMap = if (yangYear == (sex == "男")) YUANCHEN_FORWARD else YUANCHEN_REVERSE
        add("元辰", positions(setOf(yuanMap[yearZhi]!!), mdhZhis, MDH_ZHI_LABELS), "年支阴阳男女")
        add("血刃", positions(setOf(XUEREN[monthZhi]!!), zhis, ZHI_LABELS), "月支")
        return lines
    }

    // ------------------------------------------------------------------
    // 时辰
    // ------------------------------------------------------------------

    /** 钟点 → 时支。23:00–01:00 子，其后每两小时一支。 */
    fun hourToShichen(hour: Int, minute: Int): String {
        val total = hour * 60 + minute
        if (total >= 23 * 60 || total < 60) return "子"
        return ZHI[((hour + 1) / 2) % 12].toString()
    }

    fun nearShichenBoundary(hour: Int, minute: Int): Boolean {
        val total = hour * 60 + minute
        return SHICHEN_BOUNDARIES_MIN.any { abs(total - it) <= 15 }
    }

    // ------------------------------------------------------------------
    // 排盘
    // ------------------------------------------------------------------

    data class Pillar(val gan: String, val zhi: String, val shishen: String, val canggan: String)
    data class DayunRow(val seq: String, val ages: String, val gz: String)

    data class BaziResult(
        val solarText: String,
        val lunarText: String,
        val shichenText: String,
        val sex: String,
        val place: String?,
        val zishi: String,
        val yearPillar: Pillar,
        val monthPillar: Pillar,
        val dayPillar: Pillar,
        val hourPillar: Pillar,
        val forward: Boolean,
        val qiyunText: String,
        val qiyunYears: Int,
        val qiyunMonths: Int,
        val dayunRows: List<DayunRow>,
        val liunianYears: List<Int>,
        val currentYear: Int,
        val currentGz: String,
        val warnings: List<String>,
        val hourUnknown: Boolean,
        val yearNum: Int,
        val jieName: String,
        val shenshaLines: List<String>,
    )

    private data class MonthInfo(
        val gan: String, val zhi: String, val jieName: String, val lon: Double,
        val prevJie: ZonedDateTime, val nextJie: ZonedDateTime?,
    )

    /** 立春时刻分年。1990-02-03 10:00 → 己巳。 */
    private fun resolveYearPillar(birthDt: ZonedDateTime): Quad {
        val calYear = birthDt.year
        val lichun = solarTermBeijing(calYear, 315.0)
        val yearNum = if (!birthDt.isBefore(lichun)) calYear else calYear - 1
        val (gan, zhi) = gzFromIndex(yearGzIndex(yearNum))
        return Quad(yearNum, gan, zhi, lichun)
    }

    private data class Quad(val yearNum: Int, val gan: String, val zhi: String, val lichun: ZonedDateTime)

    /** 十二节分月 + 年上起月。1990-05-15 → 辛巳。 */
    private fun resolveMonthPillar(birthDt: ZonedDateTime, yearGanIdx: Int): MonthInfo {
        val events = jieEventsAround(birthDt.year)
        var current: JieEvent? = null
        var nextJie: JieEvent? = null
        var prevJie: JieEvent? = null
        for ((i, ev) in events.withIndex()) {
            if (!ev.dt.isAfter(birthDt)) {
                current = ev
            } else if (nextJie == null) {
                nextJie = ev
                if (i >= 1) prevJie = events[i - 1]
                break
            }
        }
        val cur = current ?: throw IllegalArgumentException("找不到出生时刻之前的节气")
        if (prevJie == null) {
            prevJie = cur
            nextJie = jieEventsAround(birthDt.year + 1).firstOrNull { it.dt.isAfter(birthDt) }
        }
        val yueZhi = ZHI[cur.zhiIndex].toString()
        val step = Math.floorMod(cur.zhiIndex - 2, 12)
        val yueGan = GAN[Math.floorMod(YUE_GAN_YIN[yearGanIdx] + step, 10)].toString()
        return MonthInfo(yueGan, yueZhi, cur.name, cur.lon, prevJie.dt, nextJie?.dt)
    }

    /** 早晚子时日界。返回 (gan, zhi, index, usedDate, zishiLabel)。 */
    private fun resolveDayPillar(solarDate: LocalDate, hour: Int?, minute: Int?): Penta {
        var used = solarDate
        var zishi = "无"
        if (hour != null) {
            if (hour >= 23) {
                used = used.plusDays(1)
                zishi = "夜子时"
            } else if (hour == 0) {
                zishi = "早子时"
            }
        }
        val idx = dayGzIndex(used.year, used.monthValue, used.dayOfMonth)
        val (gan, zhi) = gzFromIndex(idx)
        return Penta(gan, zhi, idx, used, zishi)
    }

    private data class Penta(val gan: String, val zhi: String, val idx: Int, val used: LocalDate, val zishi: String)

    private fun resolveHourPillar(dayGanIdx: Int, shichen: String): Pair<String, String> {
        val zhiI = ZHI.indexOf(shichen)
        val ganI = Math.floorMod(ZI_SHI_GAN[dayGanIdx] + zhiI, 10)
        return GAN[ganI].toString() to ZHI[zhiI].toString()
    }

    /** 天数 ÷ 3 = 岁；余 1 天≈4 个月，余 2 天≈8 个月。不足一天按比例折月。 */
    fun qiyunFromDays(daysIn: Double): Pair<Int, Int> {
        val days = abs(daysIn)
        val whole = floor(days).toInt()
        val frac = days - whole
        var years = whole / 3
        var months = (whole % 3) * 4 + Math.round(frac * 4).toInt()
        if (months >= 12) {
            years += months / 12
            months %= 12
        }
        return years to months
    }

    /** 从月柱下一柱（顺）或上一柱（逆）起排。月柱本身是起运前小运。 */
    fun buildDayun(monthGan: String, monthZhi: String, forward: Boolean, qiyunYears: Int, qiyunMonths: Int, steps: Int = 8): List<DayunRow> {
        var gi = GAN.indexOf(monthGan)
        var zi = ZHI.indexOf(monthZhi)
        val delta = if (forward) 1 else -1
        val rows = ArrayList<DayunRow>()
        val startAge = qiyunYears
        if (qiyunYears > 0 || qiyunMonths > 0) {
            val preEnd = if (qiyunMonths == 0) "${qiyunYears}岁" else "${qiyunYears}岁${qiyunMonths}个月"
            rows.add(DayunRow("起运前", "0岁-$preEnd", "$monthGan$monthZhi（小运）"))
        }
        for (n in 1..steps) {
            gi = Math.floorMod(gi + delta, 10)
            zi = Math.floorMod(zi + delta, 12)
            val a0 = startAge + (n - 1) * 10
            rows.add(DayunRow("$n", "$a0-${a0 + 9}岁", "${GAN[gi]}${ZHI[zi]}"))
        }
        return rows
    }

    fun liunianGz(year: Int): String {
        val (g, z) = gzFromIndex(yearGzIndex(year))
        return "$g$z"
    }

    /** 核心排盘。solarDate 为公历；hour/minute 为北京钟表，可空。 */
    fun compute(
        solarDate: LocalDate,
        hour: Int? = null,
        minute: Int? = null,
        shichenIn: String? = null,
        sex: String = "男",
        place: String? = null,
        now: ZonedDateTime = ZonedDateTime.now(BJ),
    ): BaziResult {
        val warnings = ArrayList<String>()
        val clockKnown = hour != null
        val shichenKnown = !shichenIn.isNullOrEmpty()
        var shichen = shichenIn
        var h = hour
        var m = minute

        if (clockKnown) {
            shichen = hourToShichen(h!!, m ?: 0)
        } else if (shichenKnown) {
            val mid = SHICHEN_MID[shichen]!!
            h = mid.first
            m = mid.second
        } else {
            h = 12
            m = 0
            warnings.add("时辰未知")
        }

        val birthDt = beijing(solarDate.year, solarDate.monthValue, solarDate.dayOfMonth, h!!, m ?: 0)

        val yr = resolveYearPillar(birthDt)
        val mi = resolveMonthPillar(birthDt, GAN.indexOf(yr.gan))

        val dp: Penta = if (clockKnown) {
            resolveDayPillar(solarDate, h, m)
        } else if (shichenKnown && shichen == "子") {
            val r = resolveDayPillar(solarDate, 0, 0)
            warnings.add("未提供具体时刻，子时按早子时（当日日柱）处理")
            r
        } else {
            resolveDayPillar(solarDate, null, null)
        }

        var hGan: String? = null
        var hZhi: String? = null
        var zishi: String
        val hourUnknown: Boolean
        if (clockKnown || shichenKnown) {
            val hp = resolveHourPillar(GAN.indexOf(dp.gan), shichen!!)
            hGan = hp.first
            hZhi = hp.second
            hourUnknown = false
            zishi = dp.zishi
        } else {
            hourUnknown = true
            zishi = "无"
        }

        val dayGanI = GAN.indexOf(dp.gan)
        val yearPillar = Pillar(yr.gan, yr.zhi, shishenOf(dayGanI, GAN.indexOf(yr.gan)), formatCanggan(yr.zhi))
        val monthPillar = Pillar(mi.gan, mi.zhi, shishenOf(dayGanI, GAN.indexOf(mi.gan)), formatCanggan(mi.zhi))
        val dayPillar = Pillar(dp.gan, dp.zhi, "—", formatCanggan(dp.zhi))
        val hourPillar = if (hourUnknown) Pillar("未知", "未知", "未知", "未知")
        else Pillar(hGan!!, hZhi!!, shishenOf(dayGanI, GAN.indexOf(hGan)), formatCanggan(hZhi))

        val yangYear = GAN.indexOf(yr.gan) % 2 == 0
        val forward = if (sex == "男") yangYear else !yangYear
        // 1990-05-15 男（庚午阳年）必须顺排

        val jieForQiyun = (if (forward) mi.nextJie else mi.prevJie)
            ?: throw IllegalArgumentException("无法定位起运所用的节")
        val deltaSec = ChronoUnit.MILLIS.between(birthDt, jieForQiyun) / 1000.0
        val qiyunDays = abs(deltaSec) / 86400.0
        val (qy, qm) = qiyunFromDays(qiyunDays)
        val dayunRows = buildDayun(mi.gan, mi.zhi, forward, qy, qm)

        // 节气交界 / 立春前后：距节或立春 24 小时内
        if (mi.nextJie != null && abs(ChronoUnit.MILLIS.between(birthDt, mi.nextJie) / 1000.0) <= 86400.0) {
            warnings.add("节气交界")
        }
        if (abs(ChronoUnit.MILLIS.between(birthDt, mi.prevJie) / 1000.0) <= 86400.0 && "节气交界" !in warnings) {
            warnings.add("节气交界")
        }
        var nearLichun = abs(ChronoUnit.MILLIS.between(birthDt, yr.lichun) / 1000.0) <= 86400.0
        if (!nearLichun) {
            for (y in intArrayOf(birthDt.year - 1, birthDt.year + 1)) {
                val lc = solarTermBeijing(y, 315.0)
                if (abs(ChronoUnit.MILLIS.between(birthDt, lc) / 1000.0) <= 86400.0) {
                    nearLichun = true
                    break
                }
            }
        }
        if (nearLichun) warnings.add("立春前后")

        if (place != null && clockKnown && nearShichenBoundary(h, m ?: 0)) {
            warnings.add("校正后可能跨时辰")
        }

        val currentYear = now.year
        val birthYear = solarDate.year
        val endYear = maxOf(currentYear, birthYear)
        val liunianYears = (birthYear..endYear).toList()

        var lunarText: String
        try {
            lunarText = formatLunar(solarToLunar(solarDate.year, solarDate.monthValue, solarDate.dayOfMonth))
        } catch (exc: IllegalArgumentException) {
            lunarText = "未知"
            warnings.add(exc.message ?: "农历换算失败")
        }

        val solarText: String
        val shichenText: String
        if (clockKnown) {
            solarText = "%04d-%02d-%02d %02d:%02d".format(solarDate.year, solarDate.monthValue, solarDate.dayOfMonth, h, m)
            shichenText = "$shichen / %02d:%02d".format(h, m)
        } else if (shichenKnown) {
            solarText = "%04d-%02d-%02d".format(solarDate.year, solarDate.monthValue, solarDate.dayOfMonth)
            shichenText = shichen!!
        } else {
            solarText = "%04d-%02d-%02d".format(solarDate.year, solarDate.monthValue, solarDate.dayOfMonth)
            shichenText = "未知"
        }

        val qiyunText = if (qm != 0) "${qy}岁${qm}个月" else "${qy}岁"

        val shenshaLines = computeShensha(
            yr.gan, yr.zhi, mi.gan, mi.zhi, dp.gan, dp.zhi,
            if (hourUnknown) null else hGan, if (hourUnknown) null else hZhi, sex,
        )

        return BaziResult(
            solarText = solarText,
            lunarText = lunarText,
            shichenText = shichenText,
            sex = sex,
            place = place,
            zishi = zishi,
            yearPillar = yearPillar,
            monthPillar = monthPillar,
            dayPillar = dayPillar,
            hourPillar = hourPillar,
            forward = forward,
            qiyunText = qiyunText,
            qiyunYears = qy,
            qiyunMonths = qm,
            dayunRows = dayunRows,
            liunianYears = liunianYears,
            currentYear = currentYear,
            currentGz = liunianGz(currentYear),
            warnings = warnings.toList(),
            hourUnknown = hourUnknown,
            yearNum = yr.yearNum,
            jieName = mi.jieName,
            shenshaLines = shenshaLines,
        )
    }

    // ------------------------------------------------------------------
    // 五行分布 / 原局合冲刑害（wuxing-tables.md 口径，脚本层不产出、App 层补齐）
    // ------------------------------------------------------------------

    private const val GAN_WUXING = "木木火火土土金金水水"

    /** 五行计数（天干+地支本气各 1）与藏干加权分（本气0.6/中气0.3/余气0.1；单藏支1.0，双藏支0.7/0.3）。 */
    fun wuxingStats(r: BaziResult): Pair<Map<String, Int>, Map<String, Double>> {
        val counts = mutableMapOf("木" to 0, "火" to 0, "土" to 0, "金" to 0, "水" to 0)
        val weights = mutableMapOf("木" to 0.0, "火" to 0.0, "土" to 0.0, "金" to 0.0, "水" to 0.0)
        for (p in listOf(r.yearPillar, r.monthPillar, r.dayPillar, r.hourPillar)) {
            if (p.gan == "未知") continue
            val gw = GAN_WUXING[GAN.indexOf(p.gan)].toString()
            counts[gw] = (counts[gw] ?: 0) + 1
            weights[gw] = (weights[gw] ?: 0.0) + 1.0
            val hidden = CANGGAN[p.zhi] ?: emptyList()
            val ws = when (hidden.size) {
                1 -> listOf(1.0)
                2 -> listOf(0.7, 0.3)
                else -> listOf(0.6, 0.3, 0.1)
            }
            hidden.forEachIndexed { i, h ->
                val w = h.last().toString()
                weights[w] = (weights[w] ?: 0.0) + ws[i]
            }
        }
        return counts to weights
    }

    /** 原局天干五合/相冲、地支六合/六冲/相害/三合半合/三会/三刑自刑。 */
    fun inChartRelations(r: BaziResult): List<String> {
        val gans = listOf(r.yearPillar.gan, r.monthPillar.gan, r.dayPillar.gan, r.hourPillar.gan)
        val zhis = listOf(r.yearPillar.zhi, r.monthPillar.zhi, r.dayPillar.zhi, r.hourPillar.zhi)
        val lg = listOf("年干", "月干", "日干", "时干")
        val lz = listOf("年支", "月支", "日支", "时支")
        val lines = ArrayList<String>()

        val ganHe = mapOf("甲己" to "土", "乙庚" to "金", "丙辛" to "水", "丁壬" to "木", "戊癸" to "火")
        val ganChong = setOf("甲庚", "乙辛", "丙壬", "丁癸")
        fun pk(a: String, b: String) = if (GAN.indexOf(a) <= GAN.indexOf(b)) "$a$b" else "$b$a"
        fun zk(a: String, b: String) = if (ZHI.indexOf(a) <= ZHI.indexOf(b)) "$a$b" else "$b$a"

        for (i in 0..3) for (j in i + 1..3) {
            if (gans[i] == "未知" || gans[j] == "未知") continue
            val k = pk(gans[i], gans[j])
            ganHe[k]?.let { lines.add("- ${lg[i]}${gans[i]} 与 ${lg[j]}${gans[j]} 五合（化$it）") }
            if (k in ganChong) lines.add("- ${lg[i]}${gans[i]} 与 ${lg[j]}${gans[j]} 相冲")
        }

        // 午未合化五行历来有合火/合土两说，从上游主注作合火，另注争议
        val zhiLiuhe = mapOf("子丑" to "土", "寅亥" to "木", "卯戌" to "火", "辰酉" to "金", "巳申" to "水", "午未" to "火")
        val zhiLiuheNote = mapOf("午未" to "（一说化土，历来有争议）")
        val zhiChong = setOf("子午", "丑未", "寅申", "卯酉", "辰戌", "巳亥")
        val zhiHai = setOf("子未", "丑午", "寅巳", "卯辰", "申亥", "酉戌")
        for (i in 0..3) for (j in i + 1..3) {
            if (zhis[i] == "未知" || zhis[j] == "未知") continue
            val k = zk(zhis[i], zhis[j])
            zhiLiuhe[k]?.let {
                lines.add("- ${lz[i]}${zhis[i]} 与 ${lz[j]}${zhis[j]} 六合（化$it）${zhiLiuheNote[k] ?: ""}")
            }
            if (k in zhiChong) lines.add("- ${lz[i]}${zhis[i]} 与 ${lz[j]}${zhis[j]} 六冲")
            if (k in zhiHai) lines.add("- ${lz[i]}${zhis[i]} 与 ${lz[j]}${zhis[j]} 相害")
        }

        val present = zhis.filter { it != "未知" }.toSet()
        val sanhe = mapOf("申子辰" to ("水局" to "子"), "寅午戌" to ("火局" to "午"), "巳酉丑" to ("金局" to "酉"), "亥卯未" to ("木局" to "卯"))
        for ((g, ju) in sanhe) {
            val hit = g.map { it.toString() }.filter { it in present }
            when {
                hit.size == 3 -> lines.add("- $g 三合${ju.first}成局")
                hit.size == 2 && ju.second in hit -> lines.add("- ${hit.joinToString("；")} 半合${ju.first}（缺一支，力量减半）")
            }
        }
        val sanhui = mapOf("寅卯辰" to "东方木", "巳午未" to "南方火", "申酉戌" to "西方金", "亥子丑" to "北方水")
        for ((g, fang) in sanhui) {
            if (g.map { it.toString() }.all { it in present }) lines.add("- $g 三会$fang（方局，力量大于三合）")
        }
        if (setOf("寅", "巳", "申").all { it in present }) lines.add("- 寅巳申 三刑（无恩之刑）")
        if (setOf("丑", "戌", "未").all { it in present }) lines.add("- 丑戌未 三刑（持势之刑）")
        if ("子" in present && "卯" in present) lines.add("- 子卯 相刑（无礼之刑）")
        for (z in listOf("辰", "午", "酉", "亥")) {
            if (zhis.count { it == z } >= 2) lines.add("- $z$z 自刑")
        }
        if (lines.isEmpty()) lines.add("- 无明显合冲刑害")
        return lines
    }

    // ------------------------------------------------------------------
    // 报告（给 LLM 的排盘事实）
    // ------------------------------------------------------------------

    fun formatReport(r: BaziResult): String {
        val lines = ArrayList<String>()
        lines.add("## 输入")
        lines.add("- 阳历：${r.solarText}")
        lines.add("- 农历：${r.lunarText}")
        lines.add("- 时辰：${r.shichenText}")
        lines.add("- 性别：${r.sex}")
        if (!r.place.isNullOrEmpty()) lines.add("- 出生地：${r.place}")
        lines.add("- 子时：${r.zishi}")
        lines.add("")
        lines.add("## 四柱")
        lines.add("| | 年柱 | 月柱 | 日柱 | 时柱 |")
        lines.add("|---|---|---|---|---|")
        lines.add("| 天干 | ${r.yearPillar.gan} | ${r.monthPillar.gan} | ${r.dayPillar.gan} | ${r.hourPillar.gan} |")
        lines.add("| 地支 | ${r.yearPillar.zhi} | ${r.monthPillar.zhi} | ${r.dayPillar.zhi} | ${r.hourPillar.zhi} |")
        lines.add("| 十神 | ${r.yearPillar.shishen} | ${r.monthPillar.shishen} | ${r.dayPillar.shishen} | ${r.hourPillar.shishen} |")
        lines.add("| 藏干 | ${r.yearPillar.canggan} | ${r.monthPillar.canggan} | ${r.dayPillar.canggan} | ${r.hourPillar.canggan} |")
        lines.add("")
        lines.add("## 大运")
        lines.add("- 方向：${if (r.forward) "顺排" else "逆排"}")
        lines.add("- 起运：${r.qiyunText}")
        lines.add("| 大运序 | 年龄范围 | 干支 |")
        lines.add("|---|---|---|")
        for (row in r.dayunRows) lines.add("| ${row.seq} | ${row.ages} | ${row.gz} |")
        lines.add("")
        lines.add("## 流年")
        lines.add("- 当前：${r.currentYear}年${r.currentGz}")
        for (y in r.liunianYears) lines.add("- ${y}年${liunianGz(y)}")
        lines.add("")
        lines.add("## 神煞")
        lines.addAll(r.shenshaLines)
        lines.add("")
        val (wxCounts, wxWeights) = wuxingStats(r)
        val fmt = { v: Double -> if (v == floor(v)) v.toInt().toString() else "%.1f".format(v) }
        lines.add("## 五行分布")
        lines.add("- 个数（天干+地支本气各1）：木${wxCounts["木"]} 火${wxCounts["火"]} 土${wxCounts["土"]} 金${wxCounts["金"]} 水${wxCounts["水"]}")
        lines.add("- 加权分（含藏干：本气0.6/中气0.3/余气0.1；单藏支1.0，双藏支0.7/0.3）：木${fmt(wxWeights["木"]!!)} 火${fmt(wxWeights["火"]!!)} 土${fmt(wxWeights["土"]!!)} 金${fmt(wxWeights["金"]!!)} 水${fmt(wxWeights["水"]!!)}")
        lines.add("- 两口径仅供参考，判断旺衰以「得令得地得势」为准，不要只看个数")
        lines.add("")
        lines.add("## 原局关系")
        lines.addAll(inChartRelations(r))
        lines.add("")
        lines.add("## 警告")
        if (r.warnings.isNotEmpty()) {
            val seen = HashSet<String>()
            for (w in r.warnings) if (seen.add(w)) lines.add("- $w")
        } else {
            lines.add("- 无")
        }
        lines.add("")
        return lines.joinToString("\n")
    }
}
