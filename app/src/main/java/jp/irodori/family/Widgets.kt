package jp.irodori.family

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider as DayNight
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.time.LocalDate
import kotlin.math.roundToInt

/* ============ ウィジェット共通 ============ */
object Widgets {
    private var job: Job? = null
    /** データが変わったら少し待ってから全ウィジェットを描き直す */
    fun refresh(ctx: Context) {
        job?.cancel()
        job = Repo.scope.launch { delay(400); refreshNow(ctx) }
    }
    suspend fun refreshNow(ctx: Context) {
        runCatching { TodayWidget().updateAll(ctx) }
        runCatching { WeekWidget().updateAll(ctx) }
        runCatching { MonthWidget().updateAll(ctx) }
        runCatching { ListWidget().updateAll(ctx) }
    }
}

private object WC {
    val bg = DayNight(day = Color(0xFFFFFFFF), night = Color(0xFF25242F))
    val bg2 = DayNight(day = Color(0xFFFBF8F4), night = Color(0xFF2B2A37))
    val ink = DayNight(day = Color(0xFF2B2A3B), night = Color(0xFFF2EEF7))
    val sub = DayNight(day = Color(0xFF7B7689), night = Color(0xFFABA5BC))
    val rasp = DayNight(day = Color(0xFFC8457A), night = Color(0xFFE47AA3))
    val teal = DayNight(day = Color(0xFF2E84A3), night = Color(0xFF5FB3CF))
    val orange = DayNight(day = Color(0xFFE98D4D), night = Color(0xFFF2A26C))
    val rain = DayNight(day = Color(0xFF4C86C0), night = Color(0xFF7FB0E0))
    val todayBg = DayNight(day = Color(0xFFFDEDE0), night = Color(0xFF3A2C24))
    val hol = DayNight(day = Color(0xFFD0536E), night = Color(0xFFEE7D93))
    val sat = DayNight(day = Color(0xFF3F78B5), night = Color(0xFF7FAEE3))
    fun hex(h: String) = ColorProvider(Color(parseColor(h)))
}

private fun ts(size: Int, color: ColorProvider = WC.ink, bold: Boolean = false) =
    TextStyle(color = color, fontSize = size.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)

private fun dayColor(ds: String): ColorProvider = when {
    HOLI.containsKey(ds) || dowOf(ds) == 0 -> WC.hol
    dowOf(ds) == 6 -> WC.sat
    else -> WC.ink
}

private val openApp get() = actionStartActivity<MainActivity>()

/* ============ きょう(天気・予定・アドバイス・キャラ) ============ */
class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Repo.init(context)
        val q = Repo.q()
        val mid = q.talkMember()
        val f = File(context.filesDir, "char_img_${mid}.png")
        val bmp = if (f.exists()) runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull() else null
        provideContent { TodayContent(q, bmp?.let { ImageProvider(it) } ?: ImageProvider(R.drawable.mascot)) }
    }
}
class TodayWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = TodayWidget() }

private val KEY_SPEAK = ActionParameters.Key<String>("speak")

@Composable
private fun TodayContent(q: Q, chara: ImageProvider) {
    val t = todayStr(); val now = LocalDate.now()
    val evs = q.eventsOn(t)
    val wx = q.wxDay(t)
    val night = java.time.LocalTime.now().hour >= 17
    val talkDay = if (night) plusDays(t, 1) else t
    val talkEvs = q.eventsOn(talkDay, q.talkMember() ?: "all")
    val bubble = buildString {
        append(if (night) "あしたは" else "きょうは")
        if (talkEvs.isEmpty()) append("予定なし！")
        else { append(talkEvs.take(2).joinToString("、") { it.title }); if (talkEvs.size > 2) append(" ほか"); append("があるよ") }
        q.dayAdvice(talkDay, talkEvs).firstOrNull()?.let { append("\n" + it.text.replace(Regex("（.*?）"), "")) }
    }
    Column(GlanceModifier.fillMaxSize().background(WC.bg).cornerRadius(22.dp).padding(12.dp)) {
        Row(GlanceModifier.fillMaxWidth().clickable(openApp), verticalAlignment = Alignment.CenterVertically) {
            Text("${now.monthValue}/${now.dayOfMonth}", style = ts(24, dayColor(t), true))
            Text(" (${DAY[dow(now)]})${HOLI[t]?.let { " $it" } ?: ""}", style = ts(13, dayColor(t), true))
            Spacer(GlanceModifier.defaultWeight())
            if (wx != null) {
                Text(wxEmoji(q.w?.curCode ?: wx.code), style = ts(22))
                Column(horizontalAlignment = Alignment.End) {
                    Text("${(q.w?.curTemp ?: wx.max).roundToInt()}°  ☂${wx.pop}%", style = ts(15, WC.ink, true))
                    Text("${wx.max.roundToInt()}°/${wx.min.roundToInt()}° ${q.w?.place ?: ""}", style = ts(10, WC.sub))
                }
            } else Text("天気: アプリを開いて取得", style = ts(11, WC.sub))
        }
        Spacer(GlanceModifier.height(6.dp))
        LazyColumn(GlanceModifier.defaultWeight()) {
            if (evs.isEmpty()) item { Text("きょうの予定はありません", style = ts(13, WC.sub)) }
            items(evs, itemId = { it.id.hashCode().toLong() }) { e ->
                Column(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp).clickable(openApp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(GlanceModifier.width(4.dp).height(26.dp).background(WC.hex(q.color(e))).cornerRadius(2.dp)) {}
                        Spacer(GlanceModifier.width(6.dp))
                        Text(if (e.allDay || e.start.isBlank()) "終日" else e.start, style = ts(12, WC.sub, true), modifier = GlanceModifier.width(40.dp))
                        Column {
                            Text(e.title, style = ts(13, WC.ink, true), maxLines = 1)
                            val nm = q.names(e)
                            if (nm.isNotBlank() || e.location.isNotBlank()) Text(listOf(nm, e.location).filter { it.isNotBlank() }.joinToString("・"), style = ts(10, WC.sub), maxLines = 1)
                        }
                    }
                    q.advice(e, t).firstOrNull()?.let { a -> Text("☂ " + a.text, style = ts(11, if (a.lv >= 3) WC.rasp else WC.teal, true), modifier = GlanceModifier.padding(start = 10.dp)) }
                }
            }
            val b = q.lunchInfo(t).filter { it.st == "b" }
            if (b.isNotEmpty()) item { Text("🍱 お弁当: " + b.joinToString("・") { it.m.name }, style = ts(12, WC.rasp, true)) }
        }
        Row(GlanceModifier.fillMaxWidth().background(WC.bg2).cornerRadius(16.dp).padding(6.dp)
            .clickable(actionStartActivity<MainActivity>(actionParametersOf(KEY_SPEAK to if (night) "tomorrow" else "today"))),
            verticalAlignment = Alignment.CenterVertically) {
            Image(chara, contentDescription = "キャラ", modifier = GlanceModifier.size(52.dp))
            Spacer(GlanceModifier.width(6.dp))
            Column(GlanceModifier.defaultWeight()) {
                Text(bubble, style = ts(12, WC.ink), maxLines = 3)
                Text("タップでお話しするよ", style = ts(10, WC.sub))
            }
        }
    }
}

/* ============ 週(月〜日の7列、全員分) ============ */
class WeekWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Repo.init(context)
        withTimeoutOrNull(12_000) { Wx.refresh(context, false) }
        val q = Repo.q()
        provideContent { WeekContent(q) }
    }
}
class WeekWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = WeekWidget() }

/** 週の始まりは月曜。きょうが日曜なら次の週を出す */
fun weekStart(ds: String): String {
    val w = dowOf(ds)
    return when { w == 0 && ds == todayStr() -> plusDays(ds, 1); w == 0 -> plusDays(ds, -6); else -> plusDays(ds, (1 - w).toLong()) }
}

@Composable
private fun WeekContent(q: Q) {
    val st = weekStart(todayStr()); val t = todayStr()
    Column(GlanceModifier.fillMaxSize().background(WC.bg).cornerRadius(22.dp).padding(8.dp).clickable(openApp)) {
        Text("${mdShort(st)}(月) 〜 ${mdShort(plusDays(st, 6))}(日)", style = ts(12, WC.sub, true), modifier = GlanceModifier.padding(start = 4.dp, bottom = 4.dp))
        Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
            for (i in 0 until 7) {
                val ds = plusDays(st, i.toLong())
                val evs = q.eventsOn(ds)
                val wx = q.wxDay(ds)
                val bento = q.lunchInfo(ds).any { it.st == "b" }
                Column(GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = 1.dp)
                    .background(if (ds == t) WC.todayBg else WC.bg2).cornerRadius(10.dp).padding(3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${DAY[dowOf(ds)]}${parseD(ds).dayOfMonth}" + (wx?.let { wxEmoji(it.code) } ?: ""), style = ts(10, dayColor(ds), true), maxLines = 1)
                    if (bento) Text("🍱弁当", style = ts(8, WC.rasp, true))
                    Text(evs.joinToString("\n") { (if (it.allDay || it.start.isBlank()) "" else it.start.removePrefix("0") + " ") + it.title }.ifBlank { " " },
                        style = ts(9, WC.ink, true))
                }
            }
        }
        Spacer(GlanceModifier.height(4.dp))
        WxStrip(q)
    }
}

/** 時間ごとの天気(3時間おき8つ): 時刻・天気・気温・降水確率。上に要約1行 */
@Composable
private fun WxStrip(q: Q) {
    val hs = q.hourlyFrom(3, 8)
    if (hs.isEmpty()) { Text("天気: アプリを開くと表示されます", style = ts(10, WC.sub)); return }
    val t = todayStr()
    Column(GlanceModifier.fillMaxWidth().background(WC.bg2).cornerRadius(12.dp).padding(horizontal = 4.dp, vertical = 3.dp)) {
        Text(q.wxLine(), style = ts(9, WC.sub, true), maxLines = 1)
        Row(GlanceModifier.fillMaxWidth()) {
            hs.forEach { (day, h) ->
                Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (day != t && h.h < 3) "翌${h.h}時" else "${h.h}時", style = ts(9, WC.sub))
                    Text(wxEmoji(h.code), style = ts(13))
                    Text("${h.t.roundToInt()}°", style = ts(10, WC.ink, true))
                    Text("${h.pop}%", style = ts(9, if (h.pop >= 50) WC.rasp else WC.rain, h.pop >= 50))
                }
            }
        }
    }
}

/* ============ 月(6週×7日、全員分を小さく) ============ */
class MonthWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Repo.init(context)
        withTimeoutOrNull(12_000) { Wx.refresh(context, false) }
        val q = Repo.q()
        provideContent { MonthContent(q) }
    }
}
class MonthWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = MonthWidget() }

@Composable
private fun MonthContent(q: Q) {
    val now = LocalDate.now(); val first = now.withDayOfMonth(1)
    val start = first.minusDays(dow(first).toLong())
    val weeks = (dow(first) + now.lengthOfMonth() + 6) / 7
    val t = todayStr()
    Column(GlanceModifier.fillMaxSize().background(WC.bg).cornerRadius(22.dp).padding(6.dp).clickable(openApp)) {
        Text("${now.year}年${now.monthValue}月", style = ts(13, WC.ink, true), modifier = GlanceModifier.padding(start = 4.dp, bottom = 2.dp))
        Row(GlanceModifier.fillMaxWidth()) {
            for (i in 0 until 7) Text(DAY[i], style = ts(9, if (i == 0) WC.hol else if (i == 6) WC.sat else WC.sub, true), modifier = GlanceModifier.defaultWeight())
        }
        for (wk in 0 until weeks) {
            Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                for (i in 0 until 7) {
                    val dd = start.plusDays((wk * 7 + i).toLong()); val ds = dd.toString()
                    val evs = q.eventsOn(ds)
                    val other = dd.monthValue != now.monthValue
                    Column(GlanceModifier.defaultWeight().fillMaxHeight().padding(1.dp).background(if (ds == t) WC.todayBg else WC.bg2).cornerRadius(6.dp).padding(2.dp)) {
                        Text("${dd.dayOfMonth}" + (q.wxDay(ds)?.let { wxEmoji(it.code) } ?: "") + if (q.lunchInfo(ds).any { it.st == "b" }) "🍱" else "", style = ts(9, if (other) WC.sub else dayColor(ds), true), maxLines = 1)
                        if (evs.isNotEmpty()) Text(evs.joinToString("\n") { it.title }, style = ts(8, if (other) WC.sub else WC.ink))
                    }
                }
            }
        }
        Spacer(GlanceModifier.height(3.dp))
        WxStrip(q)
    }
}

/* ============ お買い物 + ToDo(ウィジェットからチェックできる) ============ */
class ListWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Repo.init(context)
        val q = Repo.q()
        provideContent { ListContent(q) }
    }
}
class ListWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = ListWidget() }

private val KEY_ID = ActionParameters.Key<String>("id")
private val KEY_KIND = ActionParameters.Key<String>("kind")

class ToggleAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Repo.init(context)
        val id = parameters[KEY_ID] ?: return
        when (val k = parameters[KEY_KIND] ?: return) {
            "todo" -> Repo.toggleTodo(id)
            else -> Repo.toggleShop(k, id)
        }
        Repo.flush()
        Widgets.refreshNow(context)
    }
}

private sealed class LRow { data class Head(val t: String) : LRow(); data class Item(val kind: String, val id: String, val name: String, val sub: String, val on: Boolean) : LRow() }

@Composable
private fun ListContent(q: Q) {
    val tr = q.trip()
    val rows = mutableListOf<LRow>()
    rows += LRow.Head("お買い物 ${md(tr.d)}${if (tr.isToday) "（きょう）" else ""}")
    val sr = q.shopRows(tr)
    sr.forEach { rows += LRow.Item(it.kind, it.id, it.name, it.sub, it.on) }
    if (sr.isEmpty()) rows += LRow.Head("　買うものはありません")
    rows += LRow.Head("ToDo")
    val td = q.todos(false)
    td.forEach { rows += LRow.Item("todo", it.id, it.text, q.todoSub(it), it.done) }
    if (td.isEmpty()) rows += LRow.Head("　ToDoはありません")
    Column(GlanceModifier.fillMaxSize().background(WC.bg).cornerRadius(22.dp).padding(10.dp)) {
        LazyColumn(GlanceModifier.fillMaxSize()) {
            items(rows) { r ->
                when (r) {
                    is LRow.Head -> Text(r.t, style = ts(if (r.t.startsWith("　")) 11 else 13, if (r.t.startsWith("　")) WC.sub else WC.ink, true),
                        modifier = GlanceModifier.padding(top = 4.dp, bottom = 2.dp).clickable(openApp))
                    is LRow.Item -> CheckBox(
                        checked = r.on,
                        onCheckedChange = actionRunCallback<ToggleAction>(actionParametersOf(KEY_ID to r.id, KEY_KIND to r.kind)),
                        text = r.name + if (r.sub.isNotBlank()) "  ${r.sub}" else "",
                        style = ts(13, if (r.on) WC.sub else WC.ink, !r.on),
                        modifier = GlanceModifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
