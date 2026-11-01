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
)

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
)

@Serializable
data class Weather(
    val at: Long = 0,
    val place: String = "",
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
