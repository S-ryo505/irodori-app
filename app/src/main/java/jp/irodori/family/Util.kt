package jp.irodori.family

import java.time.LocalDate
import java.time.temporal.ChronoUnit

val DAY = listOf("日", "月", "火", "水", "木", "金", "土")

fun todayStr(): String = LocalDate.now().toString()
fun parseD(s: String): LocalDate = LocalDate.parse(s)
/** 日曜=0 … 土曜=6 (Web版と同じ) */
fun dow(d: LocalDate): Int = d.dayOfWeek.value % 7
fun dowOf(s: String): Int = dow(parseD(s))
fun plusDays(s: String, n: Long): String = parseD(s).plusDays(n).toString()
fun daysBetween(a: String, b: String): Long = ChronoUnit.DAYS.between(parseD(a), parseD(b))
fun md(s: String): String { val d = parseD(s); return "${d.monthValue}月${d.dayOfMonth}日(${DAY[dow(d)]})" }
fun mdShort(s: String): String { val d = parseD(s); return "${d.monthValue}/${d.dayOfMonth}" }

fun toMin(t: String): Int? {
    if (t.isBlank()) return null
    val p = t.split(":")
    val h = p.getOrNull(0)?.toIntOrNull() ?: return null
    return h * 60 + (p.getOrNull(1)?.toIntOrNull() ?: 0)
}
fun normTime(t: String?): String {
    if (t == null) return ""
    val m = Regex("^(\\d{1,2}):(\\d{2})$").find(t.trim()) ?: return ""
    return m.groupValues[1].padStart(2, '0') + ":" + m.groupValues[2]
}
fun isDate(s: String?): Boolean = s != null && Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(s)

private val ALPHA = "abcdefghijkmnpqrstuvwxyz23456789"
fun uid(): String = (1..7).map { ALPHA.random() }.joinToString("") + java.lang.Long.toString(System.currentTimeMillis(), 36).takeLast(5)
fun randomId(n: Int): String = (1..n).map { ALPHA.random() }.joinToString("")

fun <T> List<T>.upsert(x: T, id: (T) -> String): List<T> {
    val i = indexOfFirst { id(it) == id(x) }
    return if (i >= 0) toMutableList().also { it[i] = x } else this + x
}

fun parseColor(hex: String): Int = try { android.graphics.Color.parseColor(hex) } catch (e: Exception) { 0xFF7DAA92.toInt() }

/** 祝日(2026〜2027) 振替・国民の休日を含む。内閣府の公表値と照合のこと */
val HOLI = mapOf(
    "2026-01-01" to "元日", "2026-01-12" to "成人の日", "2026-02-11" to "建国記念の日", "2026-02-23" to "天皇誕生日",
    "2026-03-20" to "春分の日", "2026-04-29" to "昭和の日", "2026-05-03" to "憲法記念日", "2026-05-04" to "みどりの日",
    "2026-05-05" to "こどもの日", "2026-05-06" to "振替休日", "2026-07-20" to "海の日", "2026-08-11" to "山の日",
    "2026-09-21" to "敬老の日", "2026-09-22" to "国民の休日", "2026-09-23" to "秋分の日", "2026-10-12" to "スポーツの日",
    "2026-11-03" to "文化の日", "2026-11-23" to "勤労感謝の日",
    "2027-01-01" to "元日", "2027-01-11" to "成人の日", "2027-02-11" to "建国記念の日", "2027-02-23" to "天皇誕生日",
    "2027-03-21" to "春分の日", "2027-03-22" to "振替休日", "2027-04-29" to "昭和の日", "2027-05-03" to "憲法記念日",
    "2027-05-04" to "みどりの日", "2027-05-05" to "こどもの日", "2027-07-19" to "海の日", "2027-08-11" to "山の日",
    "2027-09-20" to "敬老の日", "2027-09-23" to "秋分の日", "2027-10-11" to "スポーツの日", "2027-11-03" to "文化の日",
    "2027-11-23" to "勤労感謝の日",
)

val CATS = linkedMapOf("plan" to "予定", "school" to "学校", "lesson" to "習い事", "club" to "部活", "out" to "おでかけ", "hosp" to "病院", "other" to "その他")
val PALETTE = listOf(
    "#34408E", "#4F6BD8", "#6C9BD2", "#2E84A3", "#3BA7A0", "#7DAA92", "#4E9A5B", "#9BB547",
    "#E0B43A", "#E98D4D", "#E06A4E", "#C9453B", "#C8457A", "#D9879F", "#E87FB8", "#B05CC6",
    "#9A8BCF", "#7A5FA8", "#B8925A", "#8B6B4E", "#5C6B7A", "#3E4553", "#9AA3AF", "#F2A7A0",
)

fun wxKind(c: Int?): String = when {
    c == null -> "cloud"
    c <= 1 -> "sun"
    c == 2 -> "partly"
    c == 3 -> "cloud"
    c <= 48 -> "fog"
    c >= 95 -> "thunder"
    c in 71..77 || c == 85 || c == 86 -> "snow"
    else -> "rain"
}
val WXL = mapOf("sun" to "晴れ", "partly" to "晴れ時々くもり", "cloud" to "くもり", "fog" to "きり", "rain" to "雨", "snow" to "雪", "thunder" to "雷雨")
val WXE = mapOf("sun" to "☀️", "partly" to "⛅", "cloud" to "☁️", "fog" to "🌫️", "rain" to "☔", "snow" to "❄️", "thunder" to "⛈️")
fun wxEmoji(c: Int?): String = WXE[wxKind(c)] ?: "☁️"

private val OUTDOOR = Regex("公園|部活|サッカー|野球|ソフトボール|テニス|陸上|運動会|体育祭|遠足|BBQ|バーベキュー|キャンプ|散歩|外遊び|グラウンド|校庭|マラソン|ピクニック|釣り|登山|ハイキング|ゴルフ|花火|祭|動物園|遊園地|河川敷|海|プール")
fun isOutdoor(e: Event): Boolean = e.outdoor || OUTDOOR.containsMatchIn(e.title + e.location)
