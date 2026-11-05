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
import kotlinx.coroutines.coroutineScope
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
        runCatching { MonthWx2Widget().updateAll(ctx) }
        runCatching { MonthTodayWidget().updateAll(ctx) }
        runCatching { MonthCharaWidget().updateAll(ctx) }
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
        val q = Repo.q().forWidget()
        val mid = q.talkMember()
        val f = File(context.filesDir, "char_img_${mid}.png")
        val bmp = if (f.exists()) runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull() else null
        val speak = speakAction(context)
        provideContent { TodayContent(q, bmp?.let { ImageProvider(it) } ?: ImageProvider(R.drawable.mascot), speak) }
    }
}
class TodayWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = TodayWidget() }

/** キャラの絵(人ごとに選んだ画像があればそれ、なければ標準) */
fun charaImage(context: Context, q: Q): ImageProvider {
    val f = File(context.filesDir, "char_img_${q.talkMember()}.png")
    val bmp = if (f.exists()) runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull() else null
    return bmp?.let { ImageProvider(it) } ?: ImageProvider(R.drawable.mascot)
}
/** タップでその場で話す(アプリは開かない)。夕方以降は「あした」の話 */
fun speakAction(context: Context): androidx.glance.action.Action {
    val night = java.time.LocalTime.now().hour >= 17
    return androidx.glance.appwidget.action.actionStartService(
        android.content.Intent(context, SpeakService::class.java).putExtra("w", if (night) "tomorrow" else "today"), isForegroundService = true)
}

@Composable
private fun TodayContent(q: Q, chara: ImageProvider, speak: androidx.glance.action.Action) {
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
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { WhoChips(q); Spacer(GlanceModifier.defaultWeight()); RefreshChip(q) }
        Spacer(GlanceModifier.height(4.dp))
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
            if (b.isNotEmpty()) item { Text("${q.s.bentoIcon.ifBlank { "🍱" }} お弁当: " + b.joinToString("・") { it.m.name }, style = ts(13, WC.rasp, true)) }
        }
        Row(GlanceModifier.fillMaxWidth().background(WC.bg2).cornerRadius(16.dp).padding(6.dp)
            .clickable(speak),
            verticalAlignment = Alignment.CenterVertically) {
            Image(chara, contentDescription = "キャラ", modifier = GlanceModifier.size(52.dp))
            Spacer(GlanceModifier.width(6.dp))
            Column(GlanceModifier.defaultWeight()) {
                Text(bubble, style = ts(12, WC.ink), maxLines = 3)
                Text("タップするとお話しするよ🔊", style = ts(10, WC.sub))
            }
        }
    }
}

/* ============ 週(月〜日の7列、全員分) ============ */
class WeekWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Repo.init(context)
        Repo.scope.launch { Wx.refresh(context, false) }
        val q = Repo.q().forWidget()
        provideContent { WeekContent(q) }
    }
}
class WeekWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = WeekWidget() }

/** 週の始まりは月曜。きょうが日曜なら次の週を出す */
fun weekStart(ds: String): String {
    val w = dowOf(ds)
    return when { w == 0 && ds == todayStr() -> plusDays(ds, 1); w == 0 -> plusDays(ds, -6); else -> plusDays(ds, (1 - w).toLong()) }
}

/** 1件の予定を、その人の色の帯で表示 */
/** 2人以上の予定用: 上下に色を分けた背景画像(色の組み合わせごとに作って使い回す) */
private val splitCache = HashMap<String, android.graphics.Bitmap>()
private fun splitBitmap(cols: List<String>): android.graphics.Bitmap = splitCache.getOrPut(cols.joinToString()) {
    val band = 24
    val bmp = android.graphics.Bitmap.createBitmap(4, band * cols.size, android.graphics.Bitmap.Config.ARGB_8888)
    cols.forEachIndexed { i, c -> for (y in i * band until (i + 1) * band) for (x in 0 until 4) bmp.setPixel(x, y, parseColor(c)) }
    bmp
}

@Composable
private fun EvChip(q: Q, e: Event, withTime: Boolean, size: Int, lines: Int) {
    val cols = q.colors(e)
    val time = if (withTime && !e.allDay && e.start.isNotBlank()) e.start.removePrefix("0") + " " else ""
    if (q.s.evStyle == "tint") {
        val c = Color(parseColor(cols.first()))
        Text(time + e.title, maxLines = lines, style = ts(size, WC.ink, true),
            modifier = GlanceModifier.fillMaxWidth().background(ColorProvider(c.copy(alpha = 0.30f))).cornerRadius(4.dp).padding(horizontal = 2.dp, vertical = 1.dp))
    } else if (cols.size == 1) {
        Text(time + e.title, maxLines = lines, style = ts(size, ColorProvider(Color.White), true),
            modifier = GlanceModifier.fillMaxWidth().background(ColorProvider(Color(parseColor(cols.first())))).cornerRadius(4.dp).padding(horizontal = 2.dp, vertical = 1.dp))
    } else {
        Box(GlanceModifier.fillMaxWidth().cornerRadius(4.dp)) {
            Image(ImageProvider(splitBitmap(cols)), contentDescription = null, modifier = GlanceModifier.fillMaxSize(), contentScale = androidx.glance.layout.ContentScale.FillBounds)
            Text(time + e.title, maxLines = lines, style = ts(size, ColorProvider(Color.White), true), modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 1.dp))
        }
    }
}

/** 日のマス: きょうは濃いピンクの枠で囲む。大事な日・お弁当の日は背景色 */
@Composable
private fun DayCell(q: Q, ds: String, modifier: GlanceModifier, radius: Int, content: @Composable () -> Unit) {
    val isT = ds == todayStr()
    val bg: ColorProvider = q.dayBg(ds)?.let { ColorProvider(Color(parseColor(it)).copy(alpha = 0.45f)) } ?: if (isT) WC.todayBg else WC.bg2
    Box(modifier) {
        Box(GlanceModifier.fillMaxSize().background(if (isT) WC.rasp else bg).cornerRadius(radius.dp).padding(if (isT) 2.dp else 0.dp)) {
            Box(GlanceModifier.fillMaxSize().background(bg).cornerRadius((radius - 1).dp)) { content() }
        }
    }
}

/** 日付の数字。きょうはピンクの丸に白文字 */
@Composable
private fun DayNum(ds: String, text: String, size: Int, dim: Boolean = false) {
    if (ds == todayStr()) Text(text, maxLines = 1, style = ts(size, ColorProvider(Color.White), true),
        modifier = GlanceModifier.background(WC.rasp).cornerRadius(9.dp).padding(horizontal = 4.dp))
    else Text(text, maxLines = 1, style = ts(size, if (dim) WC.sub else dayColor(ds), true))
}

@Composable
private fun WeekContent(q: Q) {
    val st = weekStart(todayStr())
    Column(GlanceModifier.fillMaxSize().background(WC.bg).cornerRadius(22.dp).padding(8.dp).clickable(openApp)) {
        Row(GlanceModifier.fillMaxWidth().padding(bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${mdShort(st)}〜${mdShort(plusDays(st, 6))}", style = ts(12, WC.sub, true), modifier = GlanceModifier.padding(start = 4.dp))
            Spacer(GlanceModifier.defaultWeight())
            RefreshChip(q)
            WhoChips(q)
        }
        Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
            for (i in 0 until 7) {
                val ds = plusDays(st, i.toLong())
                val evs = q.eventsOn(ds)
                val top = (q.wxDay(ds)?.let { wxEmoji(it.code) } ?: "") + q.bentoMark(ds) + q.dayIcons(ds).joinToString("")
                DayCell(q, ds, GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = 1.dp), 10) {
                    Column(GlanceModifier.fillMaxSize().padding(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        DayNum(ds, "${DAY[dowOf(ds)]}${parseD(ds).dayOfMonth}", 10)
                        if (top.isNotBlank()) Text(top, style = ts(14), maxLines = 1)
                        val n = if (top.isNotBlank()) 7 else 8
                        evs.take(n).forEach { e -> EvChip(q, e, true, 9, 3) }
                        if (evs.size > n) Text("+${evs.size - n}", style = ts(9, WC.sub, true))
                    }
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
            hs.forEachIndexed { idx, (day, h) ->
                val now = idx == 0
                Column(GlanceModifier.defaultWeight().then(if (now) GlanceModifier.background(WC.todayBg).cornerRadius(8.dp) else GlanceModifier), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (now) "いま" else if (day != t && h.h < 3) "翌${h.h}時" else "${h.h}時", style = ts(9, if (now) WC.rasp else WC.sub, now))
                    Text(wxEmoji(h.code), style = ts(13))
                    Text("${h.t.roundToInt()}°", style = ts(10, WC.ink, true))
                    Text("${h.pop}%", style = ts(9, if (h.pop >= 50) WC.rasp else WC.rain, h.pop >= 50))
                }
            }
        }
    }
}

/* ============ 月(6週×7日、全員分を小さく) 4種類 ============
 * 0: 月＋天気1行 / 1: 月＋天気2行(時間ごと＋1週間) / 2: きょうの予定＋月＋天気1行 / 3: 話すキャラ＋月＋天気1行 */
open class MonthBase(private val variant: Int) : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Repo.init(context)
        Repo.scope.launch { Wx.refresh(context, false) }
        val q = Repo.q().forWidget()
        val chara = if (variant == 3) charaImage(context, q) else null
        val speak = speakAction(context)
        provideContent { MonthContent(q, variant, chara, speak) }
    }
}
class MonthWidget : MonthBase(0)
class MonthWx2Widget : MonthBase(1)
class MonthTodayWidget : MonthBase(2)
class MonthCharaWidget : MonthBase(3)
class MonthWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = MonthWidget() }
class MonthWx2WidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = MonthWx2Widget() }
class MonthTodayWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = MonthTodayWidget() }
class MonthCharaWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = MonthCharaWidget() }

@Composable
private fun MonthContent(q: Q, variant: Int, chara: ImageProvider?, speak: androidx.glance.action.Action) {
    val now = LocalDate.now(); val first = now.withDayOfMonth(1)
    val start = first.minusDays(dow(first).toLong())
    val weeks = (dow(first) + now.lengthOfMonth() + 6) / 7
    Column(GlanceModifier.fillMaxSize().background(WC.bg).cornerRadius(22.dp).padding(6.dp)) {
        Row(GlanceModifier.fillMaxWidth().padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${now.year}年${now.monthValue}月", style = ts(13, WC.ink, true), modifier = GlanceModifier.padding(start = 4.dp).clickable(openApp))
            Spacer(GlanceModifier.defaultWeight())
            RefreshChip(q)
            WhoChips(q)
        }
        if (variant == 2) TodayStrip(q)
        if (variant == 3 && chara != null) CharaStrip(q, chara, speak)
        Column(GlanceModifier.fillMaxWidth().defaultWeight().clickable(openApp)) {
            Row(GlanceModifier.fillMaxWidth()) {
                for (i in 0 until 7) Text(DAY[i], style = ts(9, if (i == 0) WC.hol else if (i == 6) WC.sat else WC.sub, true), modifier = GlanceModifier.defaultWeight())
            }
            for (wk in 0 until weeks) {
                Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                    for (i in 0 until 7) {
                        val dd = start.plusDays((wk * 7 + i).toLong()); val ds = dd.toString()
                        val evs = q.eventsOn(ds)
                        val other = dd.monthValue != now.monthValue
                        val top = q.bentoMark(ds) + q.dayIcons(ds, 2).joinToString("")
                        val maxEv = if (variant >= 1) 3 else 4
                        DayCell(q, ds, GlanceModifier.defaultWeight().fillMaxHeight().padding(1.dp), 6) {
                            Column(GlanceModifier.fillMaxSize().padding(1.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    DayNum(ds, "${dd.dayOfMonth}", 9, other)
                                    if (top.isNotBlank()) Text(top, style = ts(11), maxLines = 1)
                                }
                                evs.take(maxEv).forEach { e -> EvChip(q, e, false, 8, 1) }
                                if (evs.size > maxEv) Text("+${evs.size - maxEv}", style = ts(8, WC.sub, true))
                            }
                        }
                    }
                }
            }
        }
        Spacer(GlanceModifier.height(3.dp))
        WxStrip(q)
        if (variant == 1) { Spacer(GlanceModifier.height(2.dp)); WeekWxRow(q) }
    }
}

/** 1週間の天気(きょうから7日): 曜日・天気・最高|最低・降水確率 */
@Composable
private fun WeekWxRow(q: Q) {
    val t = todayStr()
    Row(GlanceModifier.fillMaxWidth().background(WC.bg2).cornerRadius(12.dp).padding(horizontal = 4.dp, vertical = 3.dp)) {
        for (i in 0 until 7) {
            val ds = plusDays(t, i.toLong()); val d = q.wxDay(ds)
            Column(GlanceModifier.defaultWeight().then(if (i == 0) GlanceModifier.background(WC.todayBg).cornerRadius(8.dp) else GlanceModifier), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (i == 0) "きょう" else "${parseD(ds).dayOfMonth}(${DAY[dowOf(ds)]})", style = ts(9, if (i == 0) WC.rasp else dayColor(ds), true), maxLines = 1)
                if (d != null) {
                    Text(wxEmoji(d.code), style = ts(13))
                    Row { Text("${d.max.roundToInt()}", style = ts(9, WC.hol, true)); Text("|", style = ts(9, WC.sub)); Text("${d.min.roundToInt()}", style = ts(9, WC.sat, true)) }
                    Text("☂${d.pop}%", style = ts(8, if (d.pop >= 50) WC.rasp else WC.rain))
                } else Text("-", style = ts(9, WC.sub))
            }
        }
    }
}

/** きょうの予定を横に並べる(最大4件) */
@Composable
private fun TodayStrip(q: Q) {
    val t = todayStr(); val evs = q.eventsOn(t)
    Row(GlanceModifier.fillMaxWidth().padding(bottom = 3.dp).background(WC.todayBg).cornerRadius(10.dp).padding(3.dp).clickable(openApp), verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.padding(horizontal = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("きょう", style = ts(9, WC.rasp, true))
            Text((q.bentoMark(t) + q.dayIcons(t).joinToString("")).ifBlank { mdShort(t) }, style = ts(11, WC.ink, true), maxLines = 1)
        }
        if (evs.isEmpty()) Text("予定はありません", style = ts(11, WC.sub), modifier = GlanceModifier.defaultWeight())
        evs.take(4).forEach { e ->
            Box(GlanceModifier.defaultWeight().padding(horizontal = 1.dp)) { EvChip(q, e, true, 10, 2) }
        }
        if (evs.size > 4) Text("+${evs.size - 4}", style = ts(9, WC.sub, true))
    }
}

/** キャラと吹き出し。タップでその場で話す */
@Composable
private fun CharaStrip(q: Q, chara: ImageProvider, speak: androidx.glance.action.Action) {
    val night = java.time.LocalTime.now().hour >= 17
    val day = if (night) plusDays(todayStr(), 1) else todayStr()
    val evs = q.eventsOn(day, q.talkMember() ?: "all")
    val bubble = buildString {
        append(if (night) "あしたは" else "きょうは")
        if (evs.isEmpty()) append("予定なし！") else { append(evs.take(2).joinToString("、") { it.title }); if (evs.size > 2) append(" ほか"); append("があるよ") }
        q.dayAdvice(day, evs).firstOrNull()?.let { append(" " + it.text.replace(Regex("（.*?）"), "")) }
    }
    Row(GlanceModifier.fillMaxWidth().padding(bottom = 3.dp).background(WC.bg2).cornerRadius(14.dp).padding(4.dp).clickable(speak), verticalAlignment = Alignment.CenterVertically) {
        Image(chara, contentDescription = "キャラ", modifier = GlanceModifier.size(44.dp))
        Spacer(GlanceModifier.width(6.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(bubble, style = ts(11, WC.ink, true), maxLines = 2)
            Text("タップするとお話しするよ🔊", style = ts(9, WC.sub))
        }
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

/* ---------- ウィジェット上の「だれの予定」切り替え(丸をタップ) ---------- */
private val KEY_V = ActionParameters.Key<String>("v")
/** ウィジェット用の「表示する人」を使った計算(アプリ側の「見る人」とは別) */
fun Q.forWidget(): Q = Q(d, s.copy(viewer = s.widgetViewer), w, pt)

class ViewerAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Repo.init(context)
        val v = parameters[KEY_V] ?: "all"
        Repo.setSettings(refreshWidgets = false) { it.copy(widgetViewer = if (it.widgetViewer == v) "all" else v) }
        coroutineScope {
            launch { runCatching { TodayWidget().updateAll(context) } }
            launch { runCatching { WeekWidget().updateAll(context) } }
            launch { runCatching { MonthWidget().updateAll(context) } }
            launch { runCatching { MonthWx2Widget().updateAll(context) } }
            launch { runCatching { MonthTodayWidget().updateAll(context) } }
            launch { runCatching { MonthCharaWidget().updateAll(context) } }
        }
    }
}

@Composable
private fun WhoChips(q: Q) {
    val cur = q.viewer
    Row(verticalAlignment = Alignment.CenterVertically) {
        WChip("全", "#7B7689", cur == "all", "all")
        q.members.take(8).forEach { m -> WChip(m.name.take(1), m.color, cur == m.id, m.id) }
    }
}

@Composable
private fun WChip(label: String, hex: String, on: Boolean, v: String) {
    Box(GlanceModifier.padding(horizontal = 1.dp)) {
        Box(GlanceModifier.size(26.dp).cornerRadius(13.dp).background(if (on) WC.ink else WC.bg).padding(2.dp)
            .clickable(actionRunCallback<ViewerAction>(actionParametersOf(KEY_V to v))), contentAlignment = Alignment.Center) {
            Box(GlanceModifier.size(22.dp).cornerRadius(11.dp).background(ColorProvider(Color(parseColor(hex)))), contentAlignment = Alignment.Center) {
                Text(label, style = ts(11, ColorProvider(Color.White), true))
            }
        }
    }
}

/* ---------- 🔄 いますぐ最新にする(アプリは開かない)。横は最後に読めた時刻 ---------- */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Repo.init(context)
        BgSync.run(context, true)
    }
}

@Composable
private fun RefreshChip(q: Q) {
    val t = q.s.lastSync
    val label = if (t > 0) "🔄" + java.text.SimpleDateFormat("H:mm", java.util.Locale.JAPAN).format(java.util.Date(t)) else "🔄"
    Box(GlanceModifier.padding(end = 4.dp)) {
        Text(label, style = ts(10, WC.sub, true), modifier = GlanceModifier.background(WC.bg2).cornerRadius(10.dp).padding(horizontal = 6.dp, vertical = 3.dp)
            .clickable(actionRunCallback<RefreshAction>()))
    }
}

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
        Row(GlanceModifier.fillMaxWidth()) { Spacer(GlanceModifier.defaultWeight()); RefreshChip(q) }
        LazyColumn(GlanceModifier.fillMaxWidth().defaultWeight()) {
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
