package jp.irodori.family

import java.time.LocalDate
import kotlin.math.roundToInt

/** 画面・ウィジェット・通知で共通の「読み取り専用の計算」。データの写しを受け取って計算するだけ */
class Q(val d: AppData, val s: Settings, val w: Weather?) {

    val members: List<Member> get() = d.members.filter { !it.deleted }
    fun member(id: String): Member? = d.members.firstOrNull { it.id == id && !it.deleted }
    val viewer: String get() = if (s.viewer == "all" || member(s.viewer) != null) s.viewer else "all"

    // ---------- 予定 ----------
    fun pass(e: Event, v: String = viewer): Boolean = v == "all" || e.members.isEmpty() || v in e.members
    fun eventsOn(day: String, v: String = viewer): List<Event> = d.events
        .filter { !it.deleted && it.date <= day && it.endDate.ifBlank { it.date } >= day && pass(it, v) }
        .sortedWith(compareBy<Event>({ if (it.allDay || it.start.isBlank()) 0 else 1 }, { it.start }, { it.title }))
    fun colors(e: Event): List<String> = e.members.mapNotNull { member(it)?.color }.ifEmpty { listOf("#7DAA92") }
    fun color(e: Event): String = colors(e).first()
    fun names(e: Event): String = e.members.mapNotNull { member(it)?.name }.joinToString("・")
    fun timeTxt(e: Event): String = if (e.allDay || e.start.isBlank()) "終日" else e.start + if (e.end.isNotBlank()) "–" + e.end else ""

    // ---------- 給食 ----------
    fun lunchOf(mid: String): Lunch? = d.lunch.firstOrNull { it.id == mid && !it.deleted }
    fun lunchInfo(day: String, v: String = viewer): List<LunchInfo> = members.filter { v == "all" || it.id == v }.mapNotNull { m ->
        val l = lunchOf(m.id) ?: return@mapNotNull null
        val st = l.status[day] ?: return@mapNotNull null
        LunchInfo(m, st, l.menus[day] ?: "")
    }

    // ---------- ストック ----------
    fun stockVisible(x: Stock): Boolean = !x.deleted && (viewer == "all" || x.owner.isBlank() || x.owner == viewer)
    fun remain(x: Stock): Long = if (x.lastBought.isBlank()) 0 else x.days - daysBetween(x.lastBought, todayStr())
    fun buyBy(x: Stock): String = if (x.lastBought.isBlank()) todayStr() else plusDays(x.lastBought, (x.days - x.lead).toLong())

    // ---------- お買い物 ----------
    private fun shopDays(): List<Int> = s.shopDays.ifEmpty { listOf(6) }
    fun nextShop(from: LocalDate): LocalDate {
        for (i in 0..14) { val x = from.plusDays(i.toLong()); if (dow(x) in shopDays()) return x }
        return from.plusDays(7)
    }
    /** d=今回の買い物の日(きょうが買い物の日ならきょう) n=その次の買い物の日 */
    fun trip(): Trip {
        val t = LocalDate.now(); val dd = nextShop(t)
        return Trip(dd.toString(), nextShop(dd.plusDays(1)).toString(), dd == t)
    }
    fun shopRows(t: Trip = trip()): List<ShopRow> {
        val out = mutableListOf<ShopRow>()
        d.stocks.filter { stockVisible(it) }.forEach { x ->
            val on = x.shopTrip == t.d
            if (on || buyBy(x) <= t.n) {
                val r = remain(x)
                out += ShopRow("stock", x.id, x.name, on, if (on) "買った" else if (r > 0) "あと${r}日でなくなる" else "もう切れています")
            }
        }
        d.shop.filter { !it.deleted && (!it.done || it.trip == t.d) }.forEach { x ->
            out += ShopRow("shop", x.id, x.text, x.done, member(x.by)?.let { it.name + "が追加" } ?: "")
        }
        return out.sortedBy { if (it.on) 1 else 0 }
    }

    // ---------- ToDo ----------
    fun todoVisible(x: Todo): Boolean = !x.deleted && (viewer == "all" || x.owner.isBlank() || x.owner == viewer) &&
        !(x.done && System.currentTimeMillis() - x.doneAt > 7L * 86_400_000)
    fun todos(showDone: Boolean): List<Todo> = d.todos.filter { todoVisible(it) && (showDone || !it.done) }
        .sortedWith(compareBy<Todo>({ if (it.done) 1 else 0 }, { it.due.ifBlank { "9999" } }, { it.createdAt }))
    fun todoSub(x: Todo): String {
        val t = todayStr()
        val due = when { x.due.isBlank() -> ""; x.due == t -> "きょうまで"; x.due < t && !x.done -> md(x.due) + "まで(すぎています)"; else -> md(x.due) + "まで" }
        return listOfNotNull(member(x.owner)?.name, due.ifBlank { null }).joinToString("・")
    }

    // ---------- 天気 ----------
    fun wxDay(day: String): DayWx? {
        val x = w ?: return null; val i = x.dTime.indexOf(day); if (i < 0) return null
        return DayWx(x.dCode.getOrElse(i) { 3 }, x.dMax.getOrElse(i) { 0.0 }, x.dMin.getOrElse(i) { 0.0 }, x.dPop.getOrElse(i) { 0 }, x.dWind.getOrElse(i) { 0.0 })
    }
    fun wxHours(day: String, h0: Int, h1: Int): List<HourWx> {
        val x = w ?: return emptyList(); val out = mutableListOf<HourWx>()
        for (h in h0..h1) {
            val i = x.hTime.indexOf("${day}T${h.toString().padStart(2, '0')}:00"); if (i < 0) continue
            out += HourWx(h, x.hTemp.getOrElse(i) { 0.0 }, x.hPop.getOrElse(i) { 0 }, x.hCode.getOrElse(i) { 3 }, x.hWind.getOrElse(i) { 0.0 })
        }
        return out
    }

    /** 天気 × 予定 のアドバイス(ルール判定)。Web版と同じ閾値 */
    fun advice(e: Event, day: String = e.date): List<Advice> {
        val out = isOutdoor(e)
        val pop: Int; val wind: Double; val codes: List<Int>; val tmax: Double; val tmin: Double
        if (e.allDay || e.start.isBlank()) {
            val x = wxDay(day) ?: return emptyList()
            pop = x.pop; wind = x.wind; codes = listOf(x.code); tmax = x.max; tmin = x.min
        } else {
            val s0 = (toMin(e.start) ?: 0) / 60
            val en = toMin(e.end)?.let { maxOf(s0, (it + 59) / 60 - 1) } ?: (s0 + 1)
            val hs = wxHours(day, maxOf(0, s0 - 1), minOf(23, en)); if (hs.isEmpty()) return emptyList()
            pop = hs.maxOf { it.pop }; wind = hs.maxOf { it.wind }; codes = hs.map { it.code }; tmax = hs.maxOf { it.t }; tmin = hs.minOf { it.t }
        }
        val a = mutableListOf<Advice>(); var rain = false
        if (out && codes.any { it >= 95 }) { a += Advice(3, "雷雨の予報。屋外の予定は中止・延期も考えてね"); rain = true }
        else if (out && (pop >= 70 || codes.any { it in listOf(65, 67, 82) })) { a += Advice(3, "強い雨の可能性（降水確率${pop}%）。予定変更も考えておこう"); rain = true }
        if (out && wind >= 10) a += Advice(3, "風が強い（最大${wind.roundToInt()}m/s）。屋外は気をつけて")
        if (codes.any { it in 71..77 || it == 85 || it == 86 }) a += Advice(2, "雪の予報。足元に気をつけて、時間に余裕をもってね")
        else if (!rain && pop >= 50) a += Advice(2, "傘を持ってね（降水確率${pop}%）")
        else if (!rain && pop >= 30) a += Advice(1, "折りたたみ傘があると安心（${pop}%）")
        if (tmax >= 30) a += Advice(2, "暑くなりそう（${tmax.roundToInt()}℃）。水筒と帽子を")
        else if (tmin <= 5) a += Advice(1, "冷えるよ（${tmin.roundToInt()}℃）。上着を一枚")
        return a
    }
    fun dayAdvice(day: String, evs: List<Event>): List<Advice> {
        val all = evs.flatMap { advice(it, day) }.toMutableList()
        if (all.isEmpty()) wxDay(day)?.let { x ->
            if (x.pop >= 50) all += Advice(2, "${WXL[wxKind(x.code)]}の予報。出かけるなら傘を（${x.pop}%）")
            else if (x.max >= 30) all += Advice(2, "暑い日（${x.max.roundToInt()}℃）。水分をこまめに")
        }
        return all.sortedByDescending { it.lv }.distinctBy { it.text }
    }

    // ---------- 通知の文面 ----------
    fun summary(day: String): String {
        val evs = eventsOn(day)
        val lines = evs.take(8).map { e -> "${if (e.allDay || e.start.isBlank()) "終日" else e.start} ${e.title}" + names(e).let { if (it.isNotBlank()) "（$it）" else "" } }.toMutableList()
        val b = lunchInfo(day).filter { it.st == "b" }
        if (b.isNotEmpty()) lines += "お弁当: " + b.joinToString("・") { it.m.name }
        val td = d.todos.filter { todoVisible(it) && !it.done && it.due.isNotBlank() && it.due <= day }
        if (td.isNotEmpty()) lines += "ToDo: " + td.take(3).joinToString("、") { it.text } + if (td.size > 3) " ほか${td.size - 3}件" else ""
        val tr = trip()
        if (day == tr.d) {
            val n = shopRows(tr).filter { !it.on }
            if (n.isNotEmpty()) lines += "お買い物: " + n.take(4).joinToString("、") { it.name } + if (n.size > 4) " ほか${n.size - 4}品" else ""
        }
        dayAdvice(day, evs).firstOrNull()?.let { lines += it.text }
        return if (lines.isEmpty()) "予定はありません" else lines.joinToString("\n")
    }

    /** キャラが話す文 */
    fun talkMember(): String? = if (viewer != "all") viewer else (member(s.charMember)?.id ?: members.firstOrNull()?.id)
    fun talk(mid: String?, tomorrow: Boolean): String {
        val now = java.time.LocalDateTime.now()
        val day = if (tomorrow) plusDays(todayStr(), 1) else todayStr()
        val m = mid?.let { member(it) }
        val greet = when { now.hour < 10 -> "おはよう"; now.hour < 17 -> "こんにちは"; else -> "こんばんは" }
        val ww = if (tomorrow) "あした" else "きょう"
        val evs = eventsOn(day, mid ?: "all")
        val sb = StringBuilder("$greet、${m?.name ?: "みんな"}！")
        wxDay(day)?.let { sb.append("${ww}の天気は${WXL[wxKind(it.code)]}、最高${it.max.roundToInt()}度だよ。") }
        if (evs.isEmpty()) sb.append("${ww}は予定がないよ。ゆっくりしようね。")
        else {
            sb.append("${ww}は${evs.size}つ予定があるよ。")
            evs.take(5).forEach { e ->
                if (e.allDay || e.start.isBlank()) sb.append("${e.title}。")
                else { val p = e.start.split(":"); val h = p[0].toInt(); val mi = p.getOrNull(1)?.toIntOrNull() ?: 0; sb.append("${h}時${if (mi > 0) "${mi}分" else ""}から${e.title}。") }
            }
            if (evs.size > 5) sb.append("ほかにもあるよ。")
        }
        val li = lunchInfo(day, mid ?: "all")
        val bento = li.filter { it.st == "b" }
        val kyu = li.firstOrNull { it.st == "k" && it.menu.isNotBlank() }
        if (bento.isNotEmpty()) sb.append(if (mid != null) "お弁当の日だよ。忘れないでね。" else bento.joinToString("と") { it.m.name } + "はお弁当の日だよ。")
        else if (kyu != null && mid != null) sb.append("給食は${kyu.menu}だよ。")
        d.todos.filter { !it.deleted && !it.done && it.due.isNotBlank() && it.due <= day && (mid == null || it.owner.isBlank() || it.owner == mid) }
            .take(2).forEach { sb.append("${it.text}を忘れずにね。") }
        dayAdvice(day, evs).firstOrNull()?.let { sb.append(it.text.replace(Regex("（.*?）"), "") + "。") }
        sb.append(if (tomorrow) "準備できたら早めに寝よう！" else "いってらっしゃい！")
        return sb.toString().replace("。。", "。")
    }
}
