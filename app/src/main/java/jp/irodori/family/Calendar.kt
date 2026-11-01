package jp.irodori.family

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.composed
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalTime

private val HH = 52.dp

fun initCursor(mode: String): String = if (mode == "week" && dowOf(todayStr()) == 0) plusDays(todayStr(), 1) else todayStr()

fun moveCursor(mode: String, cursor: String, dir: Int): String = when (mode) {
    "month" -> parseD(cursor).withDayOfMonth(1).plusMonths(dir.toLong()).toString()
    "week" -> plusDays(weekStart(cursor), 7L * dir)
    else -> plusDays(cursor, dir.toLong())
}

private fun fs(q: Q): TextUnit = listOf(9f, 10.5f, 12f)[q.s.textSize.coerceIn(0, 2)].sp

/** 左右スワイプで前後に移動 */
/* 最新の表示月を使うよう rememberUpdatedState で毎回更新(以前は最初の月のまま→10月/12月から先に進まない不具合) */
fun Modifier.swipe(onDir: (Int) -> Unit): Modifier = composed {
    val cb by rememberUpdatedState(onDir)
    pointerInput(Unit) {
        var total = 0f
        detectHorizontalDragGestures(onDragStart = { total = 0f }, onDragEnd = { if (total < -120) cb(1) else if (total > 120) cb(-1) }) { _, d -> total += d }
    }
}

@Composable
fun CalendarScreen(q: Q, mode: String, setMode: (String) -> Unit, cursor: String, setCursor: (String) -> Unit, open: (Sheet) -> Unit) {
    val seg: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(P.card).padding(4.dp)) {
            listOf("month" to "月", "week" to "週", "day" to "日").forEach { (k, n) ->
                Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (mode == k) P.tintR else Color.Transparent)
                    .clickable {
                        setMode(k)
                        if (k == "week" && cursor == todayStr() && dowOf(cursor) == 0) setCursor(plusDays(cursor, 1))
                    }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    T(n, 14.sp, if (mode == k) P.rasp else P.sub, true)
                }
            }
        }
    }
    if (mode == "day") {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
            seg(); Spacer(Modifier.height(8.dp))
            DayView(q, cursor, open, Modifier.weight(1f).swipe { setCursor(moveCursor(mode, cursor, it)) })
        }
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 90.dp)) {
        seg()
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (mode == "week") { val st = weekStart(cursor); T("${mdShort(st)}（月）〜 ${mdShort(plusDays(st, 6))}（日）", 13.sp, bold = true) }
            Spacer(Modifier.weight(1f))
            T("文字", 12.sp, P.sub)
            listOf("小", "中", "大").forEachIndexed { i, n ->
                Box(Modifier.clip(CircleShape).background(if (q.s.textSize == i) P.tintI else P.card).clickable { Repo.setSettings { it.copy(textSize = i) } }.padding(horizontal = 10.dp, vertical = 4.dp)) {
                    T(n, 12.sp, if (q.s.textSize == i) P.indigo else P.ink, true)
                }
            }
            if (mode == "month") Box(Modifier.clip(CircleShape).background(if (q.s.showTime) P.tintI else P.card).clickable { Repo.setSettings { it.copy(showTime = !it.showTime) } }.padding(horizontal = 10.dp, vertical = 4.dp)) {
                T("時刻", 12.sp, if (q.s.showTime) P.indigo else P.ink, true)
            }
        }
        val pick: (String) -> Unit = { ds -> if (ds == cursor) open(Sheet.Day(ds)) else setCursor(ds) }
        if (mode == "month") MonthView(q, cursor, pick, Modifier.swipe { setCursor(moveCursor(mode, cursor, it)) })
        else WeekView(q, cursor, pick, open, Modifier.swipe { setCursor(moveCursor(mode, cursor, it)) })
        Legend(q)
        Spacer(Modifier.height(12.dp))
        DayListCard(q, cursor, open)
    }
}

@Composable
fun Legend(q: Q) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        (q.members.filter { q.viewer == "all" || it.id == q.viewer }.map { it.name to hexColor(it.color) } + ("家族" to hexColor("#7DAA92"))).forEach { (n, c) ->
            Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(c)); Spacer(Modifier.width(3.dp)); T(n, 11.sp, P.sub) }
        }
    }
}

@Composable
fun EvLabel(q: Q, e: Event, size: TextUnit, withTime: Boolean, onClick: (() -> Unit)? = null) {
    val cols = q.colors(e).map { hexColor(it) }
    var m = Modifier.fillMaxWidth().padding(vertical = 1.dp).clip(RoundedCornerShape(4.dp)).stripes(cols, cols.first().copy(alpha = 0.16f))
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(m.padding(start = (3 * cols.size + 2).dp, end = 1.dp, top = 1.dp, bottom = 2.dp)) {
        if (withTime) T(if (e.allDay || e.start.isBlank()) "終日" else e.start.removePrefix("0"), size * 0.92f, P.sub)
        T(e.title, size, P.ink, true)
    }
}

@Composable
fun MonthView(q: Q, cursor: String, onPick: (String) -> Unit, modifier: Modifier) {
    val c = parseD(cursor); val first = c.withDayOfMonth(1)
    val start = first.minusDays(dow(first).toLong())
    val weeks = (dow(first) + c.lengthOfMonth() + 6) / 7
    val t = todayStr()
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(P.card)) {
        Row(Modifier.padding(vertical = 6.dp)) {
            DAY.forEachIndexed { i, n -> T(n, 11.sp, if (i == 0) P.hol else if (i == 6) P.sat else P.sub, true, Modifier.weight(1f), center = true) }
        }
        for (w in 0 until weeks) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                for (i in 0 until 7) {
                    val dd = start.plusDays((w * 7 + i).toLong()); val ds = dd.toString()
                    val other = dd.monthValue != c.monthValue
                    val evs = q.eventsOn(ds)
                    val wx = q.wxDay(ds)
                    val bento = q.lunchInfo(ds).filter { it.st == "b" }
                    Column(Modifier.weight(1f).fillMaxHeight().defaultMinSize(minHeight = 86.dp)
                        .background(if (ds == cursor) P.tintO else if (other) P.card2 else P.card)
                        .border(0.5.dp, P.line).clickable { onPick(ds) }.padding(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(22.dp).clip(CircleShape).background(if (ds == t) P.rasp else Color.Transparent), contentAlignment = Alignment.Center) {
                                T("${dd.dayOfMonth}", 12.sp, if (ds == t) Color.White else dayCol(ds, P).copy(alpha = if (other) 0.45f else 1f), true)
                            }
                            Spacer(Modifier.weight(1f))
                            if (wx != null) T(wxEmoji(wx.code), 10.sp)
                        }
                        if (bento.isNotEmpty()) T("弁" + if (q.viewer == "all") bento.joinToString("") { it.m.name.take(1) } else "", 9.sp, P.rasp, true,
                            Modifier.clip(RoundedCornerShape(5.dp)).background(P.tintR).padding(horizontal = 3.dp))
                        evs.forEach { e -> Box(Modifier.fillMaxWidth().background(Color.Transparent).then(if (other) Modifier.padding(0.dp) else Modifier)) { EvLabel(q, e, fs(q), q.s.showTime) } }
                    }
                }
            }
        }
    }
}

@Composable
fun WeekView(q: Q, cursor: String, onPick: (String) -> Unit, open: (Sheet) -> Unit, modifier: Modifier) {
    val st = weekStart(cursor); val t = todayStr()
    val size = listOf(10f, 11.5f, 13f)[q.s.textSize.coerceIn(0, 2)].sp
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(P.card)) {
        Row(Modifier.fillMaxWidth()) {
            for (i in 0 until 7) {
                val ds = plusDays(st, i.toLong()); val wx = q.wxDay(ds); val col = dayCol(ds, P)
                Column(Modifier.weight(1f).background(if (ds == cursor) P.tintO else Color.Transparent).border(0.5.dp, P.line).clickable { onPick(ds) }.padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    T(DAY[dowOf(ds)], 11.sp, col, true)
                    Box(Modifier.size(28.dp).clip(CircleShape).background(if (ds == t) P.rasp else Color.Transparent), contentAlignment = Alignment.Center) {
                        T("${parseD(ds).dayOfMonth}", 16.sp, if (ds == t) Color.White else col, true)
                    }
                    T(wx?.let { wxEmoji(it.code) } ?: " ", 12.sp)
                    T(wx?.let { "${it.pop}%" } ?: " ", 9.5.sp, P.rain)
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            for (i in 0 until 7) {
                val ds = plusDays(st, i.toLong())
                val bento = q.lunchInfo(ds).filter { it.st == "b" }
                Column(Modifier.weight(1f).fillMaxHeight().defaultMinSize(minHeight = 380.dp).background(if (ds == cursor) P.tintO.copy(alpha = 0.5f) else Color.Transparent)
                    .border(0.5.dp, P.line).clickable { onPick(ds) }.padding(horizontal = 2.dp, vertical = 4.dp)) {
                    HOLI[ds]?.let { T(it, 9.sp, P.hol, true) }
                    if (bento.isNotEmpty()) T("弁" + if (q.viewer == "all") bento.joinToString("") { it.m.name.take(1) } else "", 9.sp, P.rasp, true,
                        Modifier.clip(RoundedCornerShape(5.dp)).background(P.tintR).padding(horizontal = 3.dp))
                    q.eventsOn(ds).forEach { e -> EvLabel(q, e, size, true) { open(Sheet.View(e.id)) } }
                }
            }
        }
    }
}

@Composable
fun DayListCard(q: Q, ds: String, open: (Sheet) -> Unit) {
    CardBox {
        SecH(md(ds) + (HOLI[ds]?.let { "  $it" } ?: "")) { GhostBtn("時間で見る") { open(Sheet.GoDay(ds)) } }
        val evs = q.eventsOn(ds)
        if (evs.isEmpty()) T("予定はありません", 13.5.sp, P.sub)
        evs.forEach { e -> EventRow(q, e, ds) { open(Sheet.View(e.id)) } }
        LunchPills(q.lunchInfo(ds))
        val td = q.d.todos.filter { q.todoVisible(it) && it.due == ds } + q.pt.filter { q.mineVisible(it) && it.due == ds }
        if (td.isNotEmpty()) {
            Spacer(Modifier.height(8.dp)); T("この日までのToDo", 12.sp, P.sub, true)
            td.forEach { x -> CheckRow(x.done, x.text, q.todoSub(x)) { Repo.toggleTodo(x.id) } }
        }
    }
}

data class Placed(val e: Event, val s: Int, val en: Int, var col: Int = 0, var n: Int = 1)
fun layoutDay(evs: List<Event>): List<Placed> {
    val it = evs.map { e -> val s = toMin(e.start) ?: 0; var en = toMin(e.end) ?: (s + 60); if (en <= s) en = s + 60; Placed(e, s, maxOf(en, s + 25)) }
        .sortedWith(compareBy<Placed>({ it.s }, { -it.en }))
    val res = mutableListOf<Placed>(); var cl = mutableListOf<Placed>(); var ce = -1
    fun flush() {
        val cols = mutableListOf<Int>()
        cl.forEach { x -> var c = cols.indexOfFirst { end -> end <= x.s }; if (c < 0) { c = cols.size; cols += 0 }; cols[c] = x.en; x.col = c }
        cl.forEach { x -> x.n = cols.size }; res += cl; cl = mutableListOf(); ce = -1
    }
    it.forEach { x -> if (cl.isNotEmpty() && x.s >= ce) flush(); cl += x; ce = maxOf(ce, x.en) }
    if (cl.isNotEmpty()) flush()
    return res
}

@Composable
fun DayView(q: Q, ds: String, open: (Sheet) -> Unit, modifier: Modifier) {
    val evs = q.eventsOn(ds)
    val all = evs.filter { it.allDay || it.start.isBlank() }
    val timed = evs.filter { !it.allDay && it.start.isNotBlank() }
    val wx = q.wxDay(ds)
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    LaunchedEffect(ds) {
        val h = if (ds == todayStr()) maxOf(0, LocalTime.now().hour - 1) else 7
        scroll.scrollTo(with(density) { (HH * h).roundToPx() })
    }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
            T(mdShort(ds), 22.sp, dayCol(ds, P), true); Spacer(Modifier.width(8.dp))
            T("${DAY[dowOf(ds)]}曜日 ${HOLI[ds] ?: ""}", 13.sp, dayCol(ds, P))
            Spacer(Modifier.weight(1f))
            if (wx != null) T("${wxEmoji(wx.code)} ${wx.max.toInt()}°/${wx.min.toInt()}° ☂${wx.pop}%", 12.sp, P.sub)
        }
        if (all.isNotEmpty() || q.lunchInfo(ds).isNotEmpty()) {
            Column(Modifier.padding(bottom = 6.dp)) {
                all.forEach { e -> EvLabel(q, e, 13.sp, false) { open(Sheet.View(e.id)) } }
                LunchPills(q.lunchInfo(ds))
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(P.card).verticalScroll(scroll)) {
            BoxWithConstraints(Modifier.fillMaxWidth().height(HH * 24)) {
                val lab = 56.dp
                val colW = maxWidth - lab
                for (h in 0 until 24) {
                    Box(Modifier.offset(x = lab, y = HH * h).width(colW).height(HH).border(0.5.dp, P.line).clickable { open(Sheet.Edit(newEvent(q, ds, h), true)) })
                    if (h > 0) {
                        val hw = q.wxHours(ds, h, h).firstOrNull()
                        Column(Modifier.offset(y = HH * h - 7.dp).width(lab - 6.dp), horizontalAlignment = Alignment.End) {
                            T("$h:00", 11.sp, P.sub)
                            if (hw != null) T(wxEmoji(hw.code) + if (hw.pop >= 20) " ${hw.pop}%" else "", 9.sp, P.rain)
                        }
                    }
                }
                layoutDay(timed).forEach { o ->
                    val c = hexColor(q.color(o.e))
                    Column(Modifier.offset(x = lab + colW * (o.col.toFloat() / o.n) + 3.dp, y = HH * (o.s / 60f) + 1.dp)
                        .width(colW / o.n - 6.dp).height(HH * ((o.en - o.s) / 60f) - 3.dp)
                        .clip(RoundedCornerShape(8.dp)).background(c).clickable { open(Sheet.View(o.e.id)) }.padding(horizontal = 6.dp, vertical = 3.dp)) {
                        T(o.e.title, 12.sp, onColor(c), true)
                        T(q.timeTxt(o.e) + if (o.e.location.isNotBlank()) " ・${o.e.location}" else "", 10.5.sp, onColor(c).copy(alpha = 0.85f))
                    }
                }
                if (ds == todayStr()) {
                    val n = LocalTime.now()
                    Box(Modifier.offset(x = lab, y = HH * ((n.hour * 60 + n.minute) / 60f)).width(colW).height(2.dp).background(P.rasp))
                }
            }
        }
    }
}

fun newEvent(q: Q, ds: String, hour: Int? = null): Event = Event(
    id = uid(), date = ds,
    start = hour?.let { "${it.toString().padStart(2, '0')}:00" } ?: "",
    end = hour?.let { "${minOf(23, it + 1).toString().padStart(2, '0')}:00" } ?: "",
    members = if (q.viewer != "all") listOf(q.viewer) else emptyList(),
)
