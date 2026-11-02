package jp.irodori.family

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.util.Base64
import android.webkit.WebView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

private fun Modifier.screen() = this.fillMaxSize()
@Composable
private fun ScreenColumn(content: @Composable () -> Unit) {
    Column(Modifier.screen().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { content() }
}

/* ============================ きょう ============================ */
val HOME_SECTIONS = linkedMapOf(
    "today" to "きょうの予定と天気", "hourly" to "時間ごとの天気", "chara" to "キャラのお話し", "tomorrow" to "あしたの予定",
    "shop" to "お買い物", "todo" to "ToDo", "week" to "この先1週間", "wish" to "やりたい・行きたい",
)
/** 表示する順番(この端末の設定)。新しく増えた項目は最後に表示 */
fun homeVisible(s: Settings): List<String> {
    val base = s.homeOrder.filter { it in HOME_SECTIONS }.distinct()
    return (base + HOME_SECTIONS.keys.filter { it !in base }).filter { it !in s.homeHidden }
}
private fun saveHome(order: List<String>) = Repo.setSettings { st ->
    st.copy(homeOrder = order + HOME_SECTIONS.keys.filter { it !in order }, homeHidden = HOME_SECTIONS.keys.filter { it !in order })
}

@Composable
fun TodayScreen(q: Q, open: (Sheet) -> Unit) {
    val t = todayStr(); val tm = plusDays(t, 1); val now = LocalDate.now()
    val night = LocalTime.now().hour >= 17
    val wxErr by Repo.wxError.collectAsState()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var editing by remember { mutableStateOf(false) }
    ScreenColumn {
        if (q.d.events.any { it.sample && !it.deleted } && !q.s.hideSampleNote) {
            CardBox {
                Row { Column(Modifier.weight(1f)) {
                    T("サンプル入りで開いています", 14.sp, bold = true)
                    T("「例」マークの予定・習い事・給食・ストックは動作確認用です。設定 → データ → サンプルを消す で一括削除できます。", 13.sp, P.sub)
                }; T("×", 18.sp, P.sub, modifier = Modifier.clickable { Repo.setSettings { it.copy(hideSampleNote = true) } }.padding(6.dp)) }
            }
        }
        if (wxErr.isNotBlank()) T(wxErr, 13.sp, P.rasp, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(P.tintR).padding(12.dp))
        if (editing) { HomeEditor(q) { editing = false }; return@ScreenColumn }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { GhostBtn("⇅ 並べ替え・表示する項目") { editing = true } }

        homeVisible(q.s).forEach { k ->
            when (k) {
                "today" -> CardBox {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T("${now.monthValue}/${now.dayOfMonth}", 30.sp, dayCol(t, P), true)
                        T(" (${DAY[dow(now)]})", 15.sp, dayCol(t, P), true)
                        HOLI[t]?.let { T("  $it", 12.sp, P.hol) }
                        Spacer(Modifier.weight(1f))
                        val d = q.wxDay(t)
                        if (d != null) {
                            T(wxEmoji(q.w?.curCode ?: d.code), 34.sp)
                            Column(horizontalAlignment = Alignment.End) {
                                T("${(q.w?.curTemp ?: d.max).roundToInt()}°", 24.sp, bold = true)
                                T("${d.max.roundToInt()}°/${d.min.roundToInt()}° ☂${d.pop}%", 11.5.sp, P.sub)
                                T(q.w?.place ?: "", 11.5.sp, P.sub)
                            }
                        } else GhostBtn("天気を表示") { scope.launch { Wx.refresh(ctx, true) } }
                    }
                    Spacer(Modifier.height(8.dp))
                    DayBlock(q, t, open, "きょう")
                }
                "hourly" -> HourlyCard(q)
                "chara" -> CardBox {
                    val mid = q.talkMember()
                    val text by Speaker.lastText.collectAsState()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CharStage(mid, Modifier.size(130.dp, 150.dp))
                        Spacer(Modifier.width(12.dp))
                        T(text.ifBlank { "${q.member(mid ?: "")?.name ?: ""}のキャラだよ。ボタンを押すと、${if (night) "あした" else "きょう"}の予定をお話しするね。" }, 14.sp,
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(P.card2).padding(12.dp))
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Btn("🔊 きょう", P.orange, Modifier.weight(1f)) { Speaker.speak(q.talk(mid, false)) }
                        Btn("🔊 あした", P.indigo, Modifier.weight(1f)) { Speaker.speak(q.talk(mid, true)) }
                    }
                }
                "tomorrow" -> CardBox(outline = if (night) P.indigo else null) {
                    SecH("あした ${md(tm)}", q.wxDay(tm)?.let { "${wxEmoji(it.code)} ☂${it.pop}%" } ?: "")
                    DayBlock(q, tm, open, "あした")
                }
                "shop" -> ShopCard(q, open)
                "todo" -> TodoCard(q, false)
                "week" -> WeekCard(q, open)
                "wish" -> WishHomeCard(q, open)
            }
        }
    }
}

/** 時間ごとの天気(3時間おき)。ウィジェットと同じ並び */
@Composable
fun HourlyCard(q: Q) {
    val hs = q.hourlyFrom(3, 8); val t = todayStr()
    CardBox {
        SecH("時間ごとの天気", q.w?.place ?: "")
        if (hs.isEmpty()) { T("天気をまだ取得していません（設定 → 天気）", 13.sp, P.sub); return@CardBox }
        T(q.wxLine().replace("📍${q.w?.place ?: ""}  ", ""), 12.sp, P.sub, modifier = Modifier.padding(bottom = 6.dp))
        Row(Modifier.fillMaxWidth()) {
            hs.forEach { (day, h) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    T(if (day != t && h.h < 3) "翌${h.h}" else "${h.h}時", 11.sp, P.sub)
                    T(wxEmoji(h.code), 20.sp)
                    T("${h.t.roundToInt()}°", 13.sp, bold = true)
                    T("${h.pop}%", 11.sp, if (h.pop >= 50) P.rasp else P.rain, h.pop >= 50)
                }
            }
        }
        // 先の日(ウィジェットに入れられなかった分をアプリ側で)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            (1..4).forEach { i ->
                val ds = plusDays(t, i.toLong()); val d = q.wxDay(ds)
                if (d != null) Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    T("${parseD(ds).dayOfMonth}(${DAY[dowOf(ds)]})", 11.sp, dayCol(ds, P), true)
                    T(wxEmoji(d.code), 16.sp)
                    Row { T("${d.max.roundToInt()}", 12.sp, P.hol, true); T(" | ", 12.sp, P.sub); T("${d.min.roundToInt()}", 12.sp, P.sat, true) }
                    T("☂${d.pop}%", 10.5.sp, P.rain)
                }
            }
        }
    }
}

/** この先1週間: 一覧(きょうから7日) / 7列(月〜日・ウィジェットと同じ) を切り替え */
@Composable
fun WeekCard(q: Q, open: (Sheet) -> Unit) {
    val t = todayStr()
    CardBox {
        SecH("この先1週間") {
            listOf("list" to "一覧", "grid" to "7列").forEach { (k, n) ->
                Box(Modifier.padding(start = 4.dp).clip(CircleShape).background(if (q.s.weekStyle == k) P.tintI else P.card2)
                    .clickable { Repo.setSettings { it.copy(weekStyle = k) } }.padding(horizontal = 10.dp, vertical = 4.dp)) {
                    T(n, 12.sp, if (q.s.weekStyle == k) P.indigo else P.ink, true)
                }
            }
        }
        if (q.s.weekStyle == "grid") {
            val st = weekStart(t)
            T("${mdShort(st)}（月）〜 ${mdShort(plusDays(st, 6))}（日）", 12.sp, P.sub, modifier = Modifier.padding(bottom = 4.dp))
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                for (i in 0 until 7) {
                    val ds = plusDays(st, i.toLong()); val w = q.wxDay(ds)
                    val bento = q.lunchInfo(ds).any { it.st == "b" }
                    Column(Modifier.weight(1f).fillMaxHeight().defaultMinSize(minHeight = 150.dp).padding(horizontal = 1.dp)
                        .clip(RoundedCornerShape(10.dp)).background(if (ds == t) P.tintO else P.card2).clickable { open(Sheet.Day(ds)) }.padding(2.dp)) {
                        T("${DAY[dowOf(ds)]}${parseD(ds).dayOfMonth}" + (w?.let { wxEmoji(it.code) } ?: ""), 10.5.sp, dayCol(ds, P), true, center = true, modifier = Modifier.fillMaxWidth())
                        if (bento) T("🍱", 9.sp)
                        q.eventsOn(ds).forEach { e -> EvLabel(q, e, 9.5.sp, true) { open(Sheet.View(e.id)) } }
                    }
                }
            }
        } else for (i in 0 until 7) {
            val ds = plusDays(t, i.toLong()); val evs = q.eventsOn(ds); val w = q.wxDay(ds); val c = dayCol(ds, P)
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(Modifier.width(52.dp).clickable { open(Sheet.GoDay(ds)) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    T("${parseD(ds).dayOfMonth}", 18.sp, c, true); T(DAY[dowOf(ds)], 11.sp, c)
                    if (w != null) { T(wxEmoji(w.code), 16.sp); T("${w.pop}%", 10.sp, P.rain) }
                }
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    if (evs.isEmpty()) T("予定なし", 12.5.sp, P.sub)
                    evs.forEach { e ->
                        Row(Modifier.fillMaxWidth().clickable { open(Sheet.View(e.id)) }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(hexColor(q.color(e)))); Spacer(Modifier.width(6.dp))
                            T(if (e.allDay || e.start.isBlank()) "終日" else e.start, 11.5.sp, P.sub); Spacer(Modifier.width(6.dp))
                            T(e.title, 13.sp)
                        }
                    }
                    val b = q.lunchInfo(ds).filter { it.st == "b" }
                    if (b.isNotEmpty()) T("弁当: " + b.joinToString("・") { it.m.name }, 12.sp, P.rasp, true)
                    HOLI[ds]?.let { T(it, 12.sp, P.hol) }
                }
            }
        }
    }
}

/** きょう画面の編集: 長押しして上下にドラッグで並べ替え、－で非表示、＋で追加。この端末だけに保存 */
@Composable
fun HomeEditor(q: Q, done: () -> Unit) {
    val order = remember { mutableStateListOf<String>().apply { addAll(homeVisible(q.s)) } }
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val itemH = 58.dp
    val px = with(LocalDensity.current) { itemH.toPx() }
    CardBox(outline = P.rasp) {
        SecH("きょう画面の編集") { Btn("完了", P.rasp, small = true) { saveHome(order.toList()); done() } }
        T("≡ の行を長押しして上下に動かすと順番が変わります（▲▼でも動かせます）。－で表示しない、下の＋で追加。この端末だけに保存されます。", 12.sp, P.sub, modifier = Modifier.padding(bottom = 8.dp))
        order.toList().forEach { k ->
            key(k) {
                val dragging = dragKey == k
                Row(Modifier.fillMaxWidth().height(itemH).zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer { translationY = if (dragging) dragY else 0f; shadowElevation = if (dragging) 12f else 0f }
                    .padding(vertical = 3.dp).clip(RoundedCornerShape(14.dp)).background(if (dragging) P.tintO else P.card2)
                    .pointerInput(k) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { dragKey = k; dragY = 0f },
                            onDragEnd = { dragKey = null; dragY = 0f; saveHome(order.toList()) },
                            onDragCancel = { dragKey = null; dragY = 0f },
                        ) { ch, d ->
                            ch.consume()
                            dragY += d.y
                            val i = order.indexOf(k)
                            val target = (i + (dragY / px).roundToInt()).coerceIn(0, order.lastIndex)
                            if (target != i) { order.removeAt(i); order.add(target, k); dragY -= (target - i) * px }
                        }
                    }.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    T("≡", 20.sp, P.sub, true); Spacer(Modifier.width(12.dp))
                    T(HOME_SECTIONS[k] ?: k, 15.sp, bold = true, modifier = Modifier.weight(1f))
                    val i = order.indexOf(k)
                    T("▲", 14.sp, if (i > 0) P.ink else P.line, modifier = Modifier.clickable(enabled = i > 0) { order.removeAt(i); order.add(i - 1, k); saveHome(order.toList()) }.padding(8.dp))
                    T("▼", 14.sp, if (i < order.lastIndex) P.ink else P.line, modifier = Modifier.clickable(enabled = i < order.lastIndex) { order.removeAt(i); order.add(i + 1, k); saveHome(order.toList()) }.padding(8.dp))
                    Box(Modifier.size(30.dp).clip(CircleShape).background(P.tintR).clickable { order.remove(k); saveHome(order.toList()) }, contentAlignment = Alignment.Center) { T("－", 16.sp, P.rasp, true) }
                }
            }
        }
        val hidden = HOME_SECTIONS.keys.filter { it !in order }
        if (hidden.isNotEmpty()) {
            Spacer(Modifier.height(10.dp)); T("追加できる項目", 12.sp, P.sub, true)
            hidden.forEach { k ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(14.dp)).background(P.card2)
                    .clickable { order.add(k); saveHome(order.toList()) }.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    T(HOME_SECTIONS[k] ?: k, 15.sp, P.sub, modifier = Modifier.weight(1f))
                    Box(Modifier.size(30.dp).clip(CircleShape).background(P.tintT), contentAlignment = Alignment.Center) { T("＋", 16.sp, P.teal, true) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        T("この先1週間の表示", 12.sp, P.sub, true)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
            listOf("list" to "一覧（きょうから7日）", "grid" to "7列（月〜日・ウィジェットと同じ）").forEach { (k, n) ->
                Box(Modifier.clip(CircleShape).background(if (q.s.weekStyle == k) P.tintI else P.card2).clickable { Repo.setSettings { it.copy(weekStyle = k) } }.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    T(n, 12.sp, if (q.s.weekStyle == k) P.indigo else P.ink, true)
                }
            }
        }
        GhostBtn("最初の並びに戻す", Modifier.padding(top = 10.dp)) { order.clear(); order.addAll(HOME_SECTIONS.keys); saveHome(order.toList()) }
    }
}

@Composable
private fun DayBlock(q: Q, ds: String, open: (Sheet) -> Unit, label: String) {
    val evs = q.eventsOn(ds)
    if (evs.isEmpty()) T("${label}の予定はありません", 13.5.sp, P.sub)
    evs.forEach { e -> EventRow(q, e, ds) { open(Sheet.View(e.id)) } }
    if (evs.isEmpty()) q.dayAdvice(ds, evs).firstOrNull()?.let { AdviceLine(it, 0.dp) }
    LunchPills(q.lunchInfo(ds))
}

/** キャラ表示: HTML / GLB(3D) / 画像 / 標準キャラ。話している間は跳ねる */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CharStage(mid: String?, modifier: Modifier) {
    val ctx = LocalContext.current
    val speaking by Speaker.speaking.collectAsState()
    val ver by CharStore.version.collectAsState()
    val inf = rememberInfiniteTransition(label = "hop")
    val hop by inf.animateFloat(0f, -6f, infiniteRepeatable(tween(260), RepeatMode.Reverse), label = "hop")
    val html = remember(mid, ver) { mid?.let { CharStore.html(ctx, it) } }
    val img = remember(mid, ver) { mid?.let { File(ctx.filesDir, "char_img_$it.png").takeIf { f -> f.exists() }?.let { f -> BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() } } }
    var web by remember { mutableStateOf<WebView?>(null) }
    LaunchedEffect(speaking) { web?.evaluateJavascript("window.postMessage({type:'irodori-speak',speaking:$speaking},'*')", null) }
    Box(modifier.clip(RoundedCornerShape(22.dp)).background(P.tintO.copy(alpha = 0.6f)).offset(y = if (speaking) hop.dp else 0.dp), contentAlignment = Alignment.Center) {
        when {
            html != null -> AndroidView(factory = { c -> WebView(c).apply {
                settings.javaScriptEnabled = true; settings.domStorageEnabled = true; setBackgroundColor(0)
                loadDataWithBaseURL("https://irodori.local/", html, "text/html", "utf-8", null); web = this } },
                update = { }, modifier = Modifier.fillMaxSize())
            img != null -> Image(img, "キャラ", Modifier.fillMaxSize().padding(6.dp))
            else -> Image(painterResource(R.drawable.mascot), "キャラ", Modifier.fillMaxSize().padding(8.dp))
        }
    }
}

/** キャラのファイル置き場(端末ごと) */
object CharStore {
    val version = kotlinx.coroutines.flow.MutableStateFlow(0)
    fun html(ctx: android.content.Context, mid: String): String? {
        File(ctx.filesDir, "char_html_$mid.html").takeIf { it.exists() }?.let { return it.readText() }
        File(ctx.filesDir, "char_glb_$mid.glb").takeIf { it.exists() }?.let {
            val b64 = Base64.encodeToString(it.readBytes(), Base64.NO_WRAP)
            return """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<script type="module" src="https://unpkg.com/@google/model-viewer@3.5.0/dist/model-viewer.min.js"></script>
<style>html,body{margin:0;height:100%;background:transparent}model-viewer{width:100%;height:100%}</style></head>
<body><model-viewer id="m" src="data:model/gltf-binary;base64,$b64" auto-rotate camera-controls autoplay></model-viewer>
<script>window.addEventListener('message',e=>{if(e.data&&e.data.type==='irodori-speak'){const m=document.getElementById('m');e.data.speaking?m.play():m.pause()}})</script></body></html>"""
        }
        return null
    }
    fun clear(ctx: android.content.Context, mid: String) {
        listOf("char_html_$mid.html", "char_glb_$mid.glb", "char_img_$mid.png").forEach { File(ctx.filesDir, it).delete() }
        version.value++
    }
    fun label(ctx: android.content.Context, mid: String): String {
        val parts = mutableListOf<String>()
        if (File(ctx.filesDir, "char_html_$mid.html").exists()) parts += "HTML"
        if (File(ctx.filesDir, "char_glb_$mid.glb").exists()) parts += "3D(GLB)"
        if (File(ctx.filesDir, "char_img_$mid.png").exists()) parts += "画像(ウィジェット用)"
        return if (parts.isEmpty()) "標準キャラ" else parts.joinToString("・")
    }
}

/* ============================ リスト(買い物・ToDo・ストック) ============================ */
@Composable
fun ShopCard(q: Q, open: (Sheet) -> Unit) {
    val t = q.trip(); val rows = q.shopRows(t); val left = rows.filter { !it.on }
    CardBox(outline = if (t.isToday) P.orange else null) {
        SecH("お買い物 ${md(t.d)}" + if (t.isToday) "（きょう）" else "", "残り${left.size}品")
        if (t.isToday) ShopList(q)
        else {
            if (left.isEmpty()) T("今のところ買うものはありません", 13.5.sp, P.sub)
            left.take(3).forEach { r -> Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(P.orange)); Spacer(Modifier.width(6.dp)); T(r.name, 14.sp, bold = true); Spacer(Modifier.width(6.dp)); T(r.sub, 12.sp, P.sub) } }
            if (left.size > 3) T("ほか${left.size - 3}品", 12.sp, P.sub)
            Btn("チェックリストを開く", P.orange, Modifier.fillMaxWidth().padding(top = 8.dp)) { open(Sheet.Shop) }
        }
    }
}

@Composable
fun TodoCard(q: Q, full: Boolean) {
    val ctx = LocalContext.current
    var mine by remember { mutableStateOf(false) }
    val shared = q.todos(full); val own = q.todos(full, mine = true)
    val items = if (mine) own else shared
    var text by remember { mutableStateOf("") }
    var owner by remember { mutableStateOf(if (q.viewer != "all") q.viewer else "") }
    var due by remember { mutableStateOf("") }
    CardBox {
        SecH("ToDo")
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(P.card2).padding(3.dp)) {
            listOf(false to "みんなのToDo ${shared.count { !it.done }}", true to "自分だけ ${own.count { !it.done }}").forEach { (k, n) ->
                Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (mine == k) (if (k) P.tintI else P.tintR) else Color.Transparent)
                    .clickable { mine = k }.padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                    T(n, 13.sp, if (mine == k) (if (k) P.indigo else P.rasp) else P.sub, true)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        if (items.isEmpty()) T(if (mine) "自分だけのToDoはありません" else "ToDoはありません", 13.5.sp, P.sub, modifier = Modifier.padding(vertical = 6.dp))
        (if (full) items else items.take(6)).forEach { x -> CheckRow(x.done, x.text, q.todoSub(x), x.due.isNotBlank() && x.due < todayStr() && !x.done) { Repo.toggleTodo(x.id) } }
        if (!full && items.size > 6) T("ほか${items.size - 6}件（リストタブ）", 12.sp, P.sub)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            Field(text, { text = it }, if (mine) "自分だけのやること" else "みんなに見えるやること", Modifier.weight(1f)); Spacer(Modifier.width(6.dp))
            Btn("追加", if (mine) P.indigo else P.rasp, small = true) {
                if (text.isNotBlank()) {
                    val x = Todo(uid(), text.trim(), if (mine) "" else owner, due, createdAt = System.currentTimeMillis())
                    if (mine) Repo.putPrivateTodo(x) else Repo.putTodo(x)
                    text = ""; due = ""
                }
            }
        }
        if (!mine) MemberChips(q, setOf(owner), { owner = if (owner == it) "" else it }, withAll = true, allSelected = owner.isBlank(), onAll = { owner = "" })
        PickBox("期限（なくてもよい）", if (due.isBlank()) "" else md(due), Modifier.padding(top = 6.dp)) { pickDate(ctx, due) { due = it } }
        T(if (mine) "🔒 自分だけのToDoはこのスマホの中だけに保存され、家族には見えません（共有されません）。通知と自分のカレンダーには出ます。"
          else "家族みんなに見えるToDoです。だれの分かを選ぶと、その人の画面と通知にも出ます。", 12.sp, P.sub)
        if (full) T("チェックしたToDoは7日後に自動で消えます。", 12.sp, P.sub)
    }
}

@Composable
fun ListScreen(q: Q, open: (Sheet) -> Unit) {
    ScreenColumn {
        ShopCard(q, open)
        TodoCard(q, true)
        SecH("ストック（日数管理）")
        T("「1回買うと何日もつか」と「最後に買った日」から、次に買う日を出します。", 12.5.sp, P.sub)
        q.d.stocks.filter { q.stockVisible(it) }.sortedBy { q.remain(it) }.forEach { x ->
            val r = q.remain(x)
            val col = when { r <= x.lead -> P.rasp; r <= x.lead + 5 -> P.orange; else -> P.teal }
            CardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(q.member(x.owner), 24.dp); if (x.owner.isNotBlank()) Spacer(Modifier.width(8.dp))
                    T(x.name, 16.sp, bold = true, modifier = Modifier.weight(1f))
                    if (x.sample) Pill("例", P.tintO, P.orange)
                    Spacer(Modifier.width(6.dp)); GhostBtn("編集") { open(Sheet.StockEd(x)) }
                }
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(10.dp).clip(RoundedCornerShape(6.dp)).background(P.card2)) {
                    Box(Modifier.fillMaxWidth((r.toFloat() / x.days).coerceIn(0f, 1f)).height(10.dp).clip(RoundedCornerShape(6.dp)).background(col))
                }
                T((if (r > 0) "あと${r}日" else "${-r}日すぎています") + "　${md(q.buyBy(x))}までに買う", 13.5.sp, col, true)
                T("最後に買った日 ${md(x.lastBought)}・${x.days}日分・${x.lead}日前にお知らせ" + if (x.memo.isNotBlank()) "・${x.memo}" else "", 12.sp, P.sub)
                Btn("きょう買った", P.teal, Modifier.padding(top = 6.dp), small = true) { Repo.putStock(x.copy(lastBought = todayStr(), shopTrip = "")) }
            }
        }
        Btn("＋ ストックを追加", P.orange) { open(Sheet.StockEd(null)) }
    }
}

/* ============================ とりこみ ============================ */
private data class RvEvent(val sel: Boolean, val date: String, val start: String, val end: String, val title: String, val location: String, val note: String, val outdoor: Boolean)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImportScreen(q: Q, open: (Sheet) -> Unit) {
    var text by remember { mutableStateOf("") }
    val mem = remember { mutableStateListOf("k1") }
    var err by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("club") }
    val lunch = remember { mutableStateMapOf<String, String>() }
    val menus = remember { mutableStateMapOf<String, String>() }
    val unsure = remember { mutableStateListOf<String>() }
    var lunchYm by remember { mutableStateOf<LocalDate?>(null) }
    val evs = remember { mutableStateListOf<RvEvent>() }

    fun loadLunchMonth(first: LocalDate) {
        lunchYm = first; lunch.clear(); menus.clear(); unsure.clear()
        q.lunchOf(mem.firstOrNull() ?: "")?.let { l -> lunch.putAll(l.status); menus.putAll(l.menus) }
    }
    fun parse() {
        err = ""; evs.clear(); lunchYm = null
        try {
            val j = JSONObject(text.substring(text.indexOf('{'), text.lastIndexOf('}') + 1))
            j.optJSONArray("members")?.let { a ->
                val ids = (0 until a.length()).mapNotNull { i -> val n = a.optString(i); q.members.firstOrNull { it.name == n || it.id == n }?.id }
                if (ids.isNotEmpty()) { mem.clear(); mem.addAll(ids) }
            }
            if (mem.isEmpty()) { err = "だれの分か選んでください"; return }
            val type = j.optString("type").ifBlank { if (j.has("days")) "lunch" else "events" }
            if (type == "lunch") {
                val days = j.optJSONArray("days")
                val list = (0 until (days?.length() ?: 0)).mapNotNull { i -> days!!.optJSONObject(i)?.let { o -> if (isDate(o.optString("date"))) o.optString("date") to o.optString("menu") else null } }
                val now = LocalDate.now()
                var first = LocalDate.of(j.optInt("year", now.year), j.optInt("month", now.monthValue).coerceIn(1, 12), 1)
                if (list.isNotEmpty()) { val top = list.groupingBy { it.first.take(7) }.eachCount().maxByOrNull { it.value }!!.key; first = LocalDate.parse("$top-01") }
                val no = j.optJSONArray("noLunch")?.let { a -> (0 until a.length()).map { a.optString(it) }.toSet() } ?: emptySet()
                lunchYm = first; lunch.clear(); menus.clear(); unsure.clear()
                list.forEach { (ds, menu) -> menus[ds] = menu }
                val listed = list.map { it.first }.toSet()
                for (i in 0 until first.lengthOfMonth()) {
                    val ds = first.plusDays(i.toLong()).toString(); val w = dowOf(ds)
                    if (w == 0 || w == 6) continue
                    when { ds in no -> lunch[ds] = "b"; ds in listed -> lunch[ds] = "k"; HOLI.containsKey(ds) -> lunch[ds] = "off"; else -> { lunch[ds] = "b"; unsure += ds } }
                }
            } else {
                cat = j.optString("cat").takeIf { it in listOf("club", "lesson", "school", "plan") } ?: "club"
                val a = j.optJSONArray("events")
                for (i in 0 until (a?.length() ?: 0)) {
                    val o = a!!.optJSONObject(i) ?: continue
                    if (!isDate(o.optString("date"))) continue
                    evs += RvEvent(true, o.optString("date"), normTime(o.optString("start")), normTime(o.optString("end")), o.optString("title").ifBlank { "予定" },
                        o.optString("location"), o.optString("note"), o.optBoolean("outdoor"))
                }
                if (evs.isEmpty()) err = "予定が1件も見つかりませんでした"
            }
        } catch (e: Exception) { err = "JSONを読めませんでした。{ から } までを貼り付けてください（${e.message}）" }
    }

    ScreenColumn {
        CardBox {
            SecH("習い事・決まった予定", "一度登録すれば毎週自動")
            val ls = q.d.lessons.filter { !it.deleted }
            if (ls.isEmpty()) T("まだありません。下のボタンから追加してください。", 13.5.sp, P.sub)
            ls.forEach { l ->
                val m = q.member(l.member)
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(6.dp).height(44.dp).clip(RoundedCornerShape(4.dp)).background(hexColor(m?.color ?: "#999999"))); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        T(l.name + if (l.sample) "（例）" else "", 15.sp, bold = true)
                        T("${m?.name ?: ""}・毎週${l.days.joinToString("・") { DAY[it] }} ${l.start}–${l.end}" + if (l.location.isNotBlank()) "・${l.location}" else "", 12.sp, P.sub)
                        T((if (l.auto) "6週間先まで自動で入れる" else "手動で入れる") + if (l.skipHoliday) "・祝日は休み" else "", 12.sp, P.sub)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        GhostBtn("編集") { open(Sheet.LessonEd(l)) }
                        if (!l.auto) {
                            Btn("今月分", P.teal, small = true) { val f = LocalDate.now().withDayOfMonth(1); Repo.genLesson(l, maxOf(todayStr(), f.toString()), f.plusMonths(1).minusDays(1).toString()) }
                            Btn("来月分", P.teal, small = true) { val f = LocalDate.now().withDayOfMonth(1).plusMonths(1); Repo.genLesson(l, f.toString(), f.plusMonths(1).minusDays(1).toString()) }
                        }
                    }
                }
            }
            Btn("＋ 習い事を追加", P.orange, Modifier.padding(top = 6.dp)) { open(Sheet.LessonEd(null)) }
        }

        CardBox {
            SecH("献立表・予定表をまとめて入れる", "JSONを貼り付け")
            T("1. 献立表や部活の予定表の写真を、Claudeのチャットに送って「いろどり用のJSONにして」と頼む\n2. 返ってきたJSON（{ から } まで）をコピー\n3. 下に貼り付けて「読み込む」→ 中身を確認して保存", 13.sp)
            Spacer(Modifier.height(8.dp))
            T("だれの分？（複数OK：同じ学校のきょうだいはまとめて）", 12.sp, P.sub, true); Spacer(Modifier.height(4.dp))
            MemberChips(q, mem.toSet(), { id -> if (id in mem) mem.remove(id) else mem += id })
            Spacer(Modifier.height(8.dp))
            Field(text, { text = it }, "JSON", singleLine = false, minLines = 4, placeholder = "{\"type\":\"lunch\", ... }")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("読み込む", P.rasp) { parse() }
                GhostBtn("給食を手で入れる", small = false) { if (mem.isEmpty()) err = "だれの分か選んでください" else loadLunchMonth(LocalDate.now().withDayOfMonth(1)) }
            }
            if (err.isNotBlank()) T(err, 13.sp, P.rasp, true, Modifier.padding(top = 6.dp))
        }

        lunchYm?.let { first ->
            CardBox {
                SecH("${first.year}年${first.monthValue}月 給食カレンダー", mem.mapNotNull { q.member(it)?.name }.joinToString("・"))
                T("マスをタップで「給食 → お弁当 → なし(休み) → 未設定」と切り替わります。オレンジ枠は献立表に載っていなかった平日です（お弁当にしてあります）。", 12.sp, P.sub)
                Spacer(Modifier.height(8.dp))
                Row { DAY.forEach { T(it, 11.sp, P.sub, true, Modifier.weight(1f), center = true) } }
                val lead = dow(first); val n = first.lengthOfMonth(); val rows = (lead + n + 6) / 7
                for (r in 0 until rows) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        for (c in 0 until 7) {
                            val idx = r * 7 + c - lead
                            if (idx < 0 || idx >= n) { Spacer(Modifier.weight(1f)); continue }
                            val ds = first.plusDays(idx.toLong()).toString(); val we = c == 0 || c == 6
                            val st = lunch[ds]
                            val (bg, fg, lb) = when (st) { "k" -> Triple(P.tintT, P.teal, "給食"); "b" -> Triple(P.tintR, P.rasp, "お弁当"); "off" -> Triple(P.card2, P.sub, "なし"); else -> Triple(P.card2, P.sub, "—") }
                            Column(Modifier.weight(1f).height(60.dp).clip(RoundedCornerShape(10.dp)).background(if (we) Color.Transparent else bg)
                                .border(1.5.dp, if (ds in unsure && st == "b") P.orange else Color.Transparent, RoundedCornerShape(10.dp))
                                .clickable(enabled = !we) {
                                    val order = listOf(null, "k", "b", "off"); val nx = order[(order.indexOf(st) + 1) % 4]
                                    if (nx == null) lunch.remove(ds) else lunch[ds] = nx; unsure.remove(ds)
                                }.padding(3.dp)) {
                                T("${idx + 1}", 12.sp, dayCol(ds, P).copy(alpha = if (we) 0.4f else 1f), true)
                                if (!we) { T(lb, 10.sp, fg, true); T(menus[ds] ?: HOLI[ds] ?: "", 9.sp, P.sub, maxLines = 2) }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostBtn("‹ 前の月", small = false) { loadLunchMonth(first.minusMonths(1)) }
                    GhostBtn("次の月 ›", small = false) { loadLunchMonth(first.plusMonths(1)) }
                    Btn("保存", P.rasp, Modifier.weight(1f)) {
                        val prefix = first.toString().take(8)
                        mem.forEach { mid ->
                            val cur = q.lunchOf(mid) ?: Lunch(mid)
                            val st = cur.status.filterKeys { !it.startsWith(prefix) } + lunch.filterKeys { it.startsWith(prefix) }
                            val mn = cur.menus + menus.filter { it.key.startsWith(prefix) && it.value.isNotBlank() }
                            Repo.putLunch(cur.copy(status = st, menus = mn, sample = false, deleted = false))
                        }
                        lunchYm = null; text = ""
                    }
                }
            }
        }

        if (evs.isNotEmpty()) CardBox {
            SecH("読み取った予定（${evs.size}件）", "確認して登録")
            evs.forEachIndexed { i, e ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                    Checkbox(checked = e.sel, onCheckedChange = { evs[i] = e.copy(sel = it) })
                    Column(Modifier.weight(1f)) {
                        Field(e.title, { evs[i] = e.copy(title = it) }, "件名")
                        T("${md(e.date)}　${if (e.start.isBlank()) "終日" else e.start + (if (e.end.isNotBlank()) "–" + e.end else "")}" +
                            (if (e.location.isNotBlank()) "　${e.location}" else "") + (if (e.note.isNotBlank()) "\n${e.note}" else ""), 12.5.sp, P.sub)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostBtn("やめる", small = false) { evs.clear() }
                Btn("チェックした予定を登録", P.rasp, Modifier.weight(1f)) {
                    evs.filter { it.sel }.forEach { x ->
                        Repo.putEvent(Event(uid(), x.date, start = x.start, end = x.end, allDay = x.start.isBlank(), title = x.title, members = mem.toList(),
                            location = x.location, outdoor = x.outdoor, cat = cat, memo = x.note))
                    }
                    evs.clear(); text = ""
                }
            }
        }
    }
}
