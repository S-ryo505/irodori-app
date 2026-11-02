package jp.irodori.family

import kotlinx.serialization.Serializable

/*
 * データの形は Web版いろどり(index.html) と同じ項目名にしてある。
 * Web版で「書き出し」したJSONをそのまま読み込めて、Firebase の共有データも互換。
 */

@Serializable
data class Member(
    val id: String,
    val name: String,
    val color: String,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
)

@Serializable
data class Comment(val id: String, val by: String = "", val text: String = "", val at: Long = 0)

@Serializable
data class Event(
    val id: String,
    val date: String,
    val endDate: String = "",
    val start: String = "",
    val end: String = "",
    val allDay: Boolean = false,
    val title: String = "",
    val members: List<String> = emptyList(),
    val location: String = "",
    val outdoor: Boolean = false,
    val cat: String = "plan",
    val memo: String = "",
    val images: List<String> = emptyList(),
    val comments: List<Comment> = emptyList(),
    /** 個別のお知らせ: 開始の何分前か(0=開始時, 1440=前日)。終日は当日8時が基準 */
    val remind: List<Int> = emptyList(),
    val seriesId: String = "",
    val edited: Boolean = false,
    val sample: Boolean = false,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
    val autoRemoved: Boolean = false,
)

@Serializable
data class Lesson(
    val id: String,
    val name: String = "",
    val member: String = "",
    val days: List<Int> = emptyList(),
    val start: String = "16:00",
    val end: String = "17:00",
    val location: String = "",
    val outdoor: Boolean = false,
    val auto: Boolean = true,
    val skipHoliday: Boolean = true,
    val remind: List<Int> = emptyList(),
    val sample: Boolean = false,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
)

@Serializable
data class Stock(
    val id: String,
    val name: String = "",
    val owner: String = "",
    val days: Int = 30,
    val lastBought: String = "",
    val lead: Int = 5,
    val memo: String = "",
    val shopTrip: String = "",
    val prevBought: String = "",
    val sample: Boolean = false,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
)

@Serializable
data class Lunch(
    val id: String,
    val status: Map<String, String> = emptyMap(),
    val menus: Map<String, String> = emptyMap(),
    val sample: Boolean = false,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
)

@Serializable
data class Todo(
    val id: String,
    val text: String = "",
    val owner: String = "",
    val due: String = "",
    val done: Boolean = false,
    val doneAt: Long = 0,
    val createdAt: Long = 0,
    val sample: Boolean = false,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
)

@Serializable
data class ShopItem(
    val id: String,
    val text: String = "",
    val done: Boolean = false,
    val trip: String = "",
    val by: String = "",
    val sample: Boolean = false,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
)

@Serializable
data class AppData(
    val members: List<Member> = defaultMembers(),
    val events: List<Event> = emptyList(),
    val lessons: List<Lesson> = emptyList(),
    val stocks: List<Stock> = emptyList(),
    val lunch: List<Lunch> = emptyList(),
    val todos: List<Todo> = emptyList(),
    val shop: List<ShopItem> = emptyList(),
    val wishes: List<Wish> = emptyList(),
)

/** やりたい・行きたいリスト(家族で共有) */
@Serializable
data class Wish(
    val id: String,
    val title: String = "",
    val kind: String = "do",
    val by: String = "",
    val url: String = "",
    val memo: String = "",
    val images: List<String> = emptyList(),
    val likes: List<String> = emptyList(),
    val done: Boolean = false,
    val doneAt: Long = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
)
val WISH_KINDS = linkedMapOf("do" to "やりたい", "go" to "行きたい", "eat" to "食べたい", "want" to "ほしい")
val REMIND_OPTS = listOf(0 to "開始時", 10 to "10分前", 30 to "30分前", 60 to "1時間前", 120 to "2時間前", 1440 to "前日")
fun remindLabel(m: Int): String = REMIND_OPTS.firstOrNull { it.first == m }?.second ?: if (m % 60 == 0) "${m / 60}時間前" else "${m}分前"

fun defaultMembers() = listOf(
    Member("papa", "パパ", "#34408E"),
    Member("mama", "ママ", "#C8457A"),
    Member("k1", "長女", "#2E84A3"),
    Member("k2", "次女", "#E98D4D"),
    Member("k3", "三女", "#9A8BCF"),
)

/** この端末だけの設定(家族共有しない) */
@Serializable
data class Settings(
    val viewer: String = "all",
    val startView: String = "month",
    val notifyToday: String = "07:00",
    val notifyTomorrow: String = "20:30",
    val notifyOn: Boolean = true,
    val shopDays: List<Int> = listOf(6),
    val textSize: Int = 0,
    val showTime: Boolean = false,
    val useGps: Boolean = true,
    val locName: String = "",
    val locLat: Double = 0.0,
    val locLon: Double = 0.0,
    val fbConfig: String = "",
    val familyId: String = "",
    val charMember: String = "k2",
    val voiceRate: Float = 1.05f,
    val voicePitch: Float = 1.3f,
    val seeded: Boolean = false,
    val hideSampleNote: Boolean = false,
    /** きょう画面の並び順・非表示(この端末だけ) */
    val homeOrder: List<String> = listOf("today", "hourly", "chara", "tomorrow", "shop", "todo", "week"),
    val homeHidden: List<String> = emptyList(),
    /** この先1週間: list=きょうから7日の一覧 / grid=月〜日の7列(ウィジェットと同じ) */
    val weekStyle: String = "list",
    /** ウィジェットで表示する人(ウィジェット上の丸ボタンで切り替え) */
    val widgetViewer: String = "all",
    /** 個別のお知らせを受け取る範囲: mine=自分に関係する予定 / all=全員 / off=受け取らない */
    val remindScope: String = "mine",
)

@Serializable
data class Weather(
    val at: Long = 0,
    val place: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val curTemp: Double? = null,
    val curCode: Int? = null,
    val dTime: List<String> = emptyList(),
    val dCode: List<Int> = emptyList(),
    val dMax: List<Double> = emptyList(),
    val dMin: List<Double> = emptyList(),
    val dPop: List<Int> = emptyList(),
    val dWind: List<Double> = emptyList(),
    val hTime: List<String> = emptyList(),
    val hTemp: List<Double> = emptyList(),
    val hPop: List<Int> = emptyList(),
    val hCode: List<Int> = emptyList(),
    val hWind: List<Double> = emptyList(),
)

data class DayWx(val code: Int, val max: Double, val min: Double, val pop: Int, val wind: Double)
data class HourWx(val h: Int, val t: Double, val pop: Int, val code: Int, val wind: Double)
data class Advice(val lv: Int, val text: String)
data class LunchInfo(val m: Member, val st: String, val menu: String)
data class ShopRow(val kind: String, val id: String, val name: String, val on: Boolean, val sub: String)
data class Trip(val d: String, val n: String, val isToday: Boolean)
