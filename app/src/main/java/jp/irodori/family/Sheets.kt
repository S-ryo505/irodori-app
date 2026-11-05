package jp.irodori.family

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.io.ByteArrayOutputStream

sealed class Sheet {
    data class Day(val ds: String) : Sheet()
    data class GoDay(val ds: String) : Sheet()
    data class View(val id: String) : Sheet()
    data class Edit(val ev: Event, val isNew: Boolean) : Sheet()
    data class LessonEd(val l: Lesson?) : Sheet()
    data class StockEd(val s: Stock?) : Sheet()
    data object Shop : Sheet()
    data object Viewer : Sheet()
    data class WishEd(val w: Wish, val isNew: Boolean) : Sheet()
    data class GoTab(val tab: String) : Sheet()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetHost(q: Q, sheet: Sheet, close: () -> Unit, open: (Sheet) -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = close, sheetState = state, containerColor = P.card) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 18.dp).padding(bottom = 28.dp)) {
            when (sheet) {
                is Sheet.Day -> DaySheet(q, sheet.ds, open)
                is Sheet.View -> EventView(q, sheet.id, close, open)
                is Sheet.Edit -> EventEditor(q, sheet.ev, sheet.isNew, close)
                is Sheet.LessonEd -> LessonEditor(q, sheet.l, close)
                is Sheet.StockEd -> StockEditor(q, sheet.s, close)
                Sheet.Shop -> ShopSheet(q)
                Sheet.Viewer -> ViewerSheet(q, close)
                is Sheet.WishEd -> WishEditor(q, sheet.w, sheet.isNew, close)
                is Sheet.GoTab -> {}
                is Sheet.GoDay -> {}
            }
        }
    }
}

/* ---------- 日付・時刻の入力(タップでカレンダー/時計が出る) ---------- */
fun pickDate(ctx: Context, cur: String, set: (String) -> Unit) {
    val d = if (isDate(cur)) parseD(cur) else parseD(todayStr())
    DatePickerDialog(ctx, { _, y, m, dd -> set("%04d-%02d-%02d".format(y, m + 1, dd)) }, d.year, d.monthValue - 1, d.dayOfMonth).show()
}
fun pickTime(ctx: Context, cur: String, set: (String) -> Unit) {
    val m = toMin(cur) ?: (9 * 60)
    TimePickerDialog(ctx, { _, h, mi -> set("%02d:%02d".format(h, mi)) }, m / 60, m % 60, true).show()
}
@Composable
fun PickBox(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.padding(bottom = 8.dp)) {
        T(label, 12.sp, P.sub, true)
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(P.card2).border(1.5.dp, P.line, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp)) { T(value.ifBlank { "タップして選ぶ" }, 15.sp, if (value.isBlank()) P.sub else P.ink) }
    }
}
@Composable
fun Toggle(label: String, on: Boolean, set: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { set(!on) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        T(label, 14.sp, bold = true, modifier = Modifier.weight(1f)); Switch(checked = on, onCheckedChange = set)
    }
}
@Composable
fun Title(text: String) { T(text, 18.sp, bold = true, modifier = Modifier.padding(bottom = 10.dp)) }

/** 写真を縮小して data URL に(Web版と共通・共有の容量対策で 1024px / JPEG) */
fun compressUri(ctx: Context, uri: Uri, max: Int = 1024, quality: Int = 70): String? = runCatching {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
    var sample = 1
    while (maxOf(o.outWidth, o.outHeight) / sample > max * 2) sample *= 2
    val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
    val s = minOf(1f, max.toFloat() / maxOf(bmp.width, bmp.height))
    val sc = Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt().coerceAtLeast(1), (bmp.height * s).toInt().coerceAtLeast(1), true)
    val bo = ByteArrayOutputStream(); sc.compress(Bitmap.CompressFormat.JPEG, quality, bo)
    "data:image/jpeg;base64," + Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP)
}.getOrNull()

/* ---------- 日の一覧 ---------- */
@Composable
fun DaySheet(q: Q, ds: String, open: (Sheet) -> Unit) {
    Title(md(ds) + (HOLI[ds]?.let { "  $it" } ?: ""))
    DayColorPicker(q, ds)
    q.wxDay(ds)?.let { T("${wxEmoji(it.code)} ${WXL[wxKind(it.code)]}　${it.max.toInt()}° / ${it.min.toInt()}°　降水確率${it.pop}%", 13.sp, P.sub) }
    LunchPills(q.lunchInfo(ds))
    Spacer(Modifier.height(6.dp))
    val evs = q.eventsOn(ds)
    if (evs.isEmpty()) T("予定はありません", 13.5.sp, P.sub)
    evs.forEach { e -> EventRow(q, e, ds) { open(Sheet.View(e.id)) } }
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GhostBtn("時間で見る", Modifier.weight(1f), small = false) { open(Sheet.GoDay(ds)) }
        Btn("＋ 予定を追加", P.orange, Modifier.weight(1f)) { open(Sheet.Edit(newEvent(q, ds), true)) }
    }
}

/* ---------- 予定の詳細(コメント・写真) ---------- */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EventView(q: Q, id: String, close: () -> Unit, open: (Sheet) -> Unit) {
    val e = q.d.events.firstOrNull { it.id == id } ?: run { T("この予定は削除されました", 14.sp, P.sub); return }
    var zoom by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf("") }
    var by by remember { mutableStateOf(if (q.viewer != "all") q.viewer else q.members.firstOrNull()?.id ?: "") }
    var armed by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(14.dp).clip(RoundedCornerShape(5.dp)).background(hexColor(q.color(e)))); Spacer(Modifier.width(8.dp))
        T(e.title, 18.sp, bold = true)
    }
    T(md(e.date) + (if (e.endDate.isNotBlank() && e.endDate != e.date) " 〜 " + md(e.endDate) else "") + "　" + q.timeTxt(e), 13.5.sp, P.sub, modifier = Modifier.padding(top = 4.dp))
    if (e.location.isNotBlank()) T("📍 " + e.location, 13.5.sp, P.sub)
    FlowRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val ms = e.members.mapNotNull { q.member(it) }
        if (ms.isEmpty()) Pill("家族みんな", P.tintI, P.indigo) else ms.forEach { Pill(it.name, hexColor(it.color).copy(alpha = 0.16f), P.ink, it) }
        Pill(CATS[e.cat] ?: "予定", P.tintT, P.teal)
        if (isOutdoor(e)) Pill("屋外", P.tintO, P.orange)
        if (e.seriesId.isNotBlank()) Pill("くり返し", P.tintI, P.indigo)
        e.remind.sorted().forEach { Pill("🔔" + remindLabel(it), P.tintO, P.orange) }
    }
    q.advice(e).forEach { AdviceLine(it, 0.dp) }
    if (e.memo.isNotBlank()) T(e.memo, 14.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(14.dp)).background(P.card2).padding(12.dp))
    if (e.images.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
        e.images.forEach { src -> dataImage(src)?.let { Image(it, "写真", Modifier.size(84.dp).clip(RoundedCornerShape(12.dp)).clickable { zoom = src }, contentScale = ContentScale.Crop) } }
    }
    Spacer(Modifier.height(6.dp)); T("コメント", 12.sp, P.sub, true)
    if (e.comments.isEmpty()) T("まだありません", 13.sp, P.sub)
    e.comments.forEach { c ->
        Row(Modifier.padding(vertical = 4.dp)) {
            Avatar(q.member(c.by), 26.dp); Spacer(Modifier.width(8.dp))
            Column(Modifier.clip(RoundedCornerShape(4.dp, 14.dp, 14.dp, 14.dp)).background(P.card2).padding(horizontal = 11.dp, vertical = 7.dp)) {
                T((q.member(c.by)?.name ?: "") + "　" + java.text.SimpleDateFormat("M/d HH:mm", java.util.Locale.JAPAN).format(java.util.Date(c.at)), 11.sp, P.sub)
                T(c.text, 14.sp)
            }
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 6.dp)) {
        q.members.forEach { m -> Box(Modifier.clip(CircleShape).border(2.dp, if (by == m.id) hexColor(m.color) else Color.Transparent, CircleShape).clickable { by = m.id }.padding(2.dp)) { Avatar(m, 24.dp) } }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Field(text, { text = it }, "コメントを書く", Modifier.weight(1f))
        Spacer(Modifier.width(6.dp))
        Btn("送信", small = true) {
            if (text.isNotBlank()) { Repo.putEvent(e.copy(comments = e.comments + Comment(uid(), by, text.trim(), System.currentTimeMillis()))); text = "" }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Btn(if (armed) "もう一度押すと削除" else "削除", P.tintR, Modifier.weight(1f)) { if (!armed) armed = true else { Repo.deleteEvent(e); close() } }
        Btn("編集", P.indigo, Modifier.weight(1f)) { open(Sheet.Edit(e, false)) }
    }
    zoom?.let { z -> Dialog(onDismissRequest = { zoom = null }) { dataImage(z)?.let { Image(it, "写真", Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { zoom = null }) } } }
}

/* ---------- 予定の追加・編集 ---------- */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EventEditor(q: Q, ev: Event, isNew: Boolean, close: () -> Unit) {
    val ctx = LocalContext.current
    var title by remember { mutableStateOf(ev.title) }
    var short by remember { mutableStateOf(ev.short) }
    var date by remember { mutableStateOf(ev.date) }
    var allDay by remember { mutableStateOf(ev.allDay) }
    var start by remember { mutableStateOf(ev.start) }
    var end by remember { mutableStateOf(ev.end) }
    var endDate by remember { mutableStateOf(ev.endDate) }
    var loc by remember { mutableStateOf(ev.location) }
    var cat by remember { mutableStateOf(ev.cat) }
    var outdoor by remember { mutableStateOf(ev.outdoor) }
    var memo by remember { mutableStateOf(ev.memo) }
    val mem = remember { mutableStateListOf<String>().apply { addAll(ev.members) } }
    val imgs = remember { mutableStateListOf<String>().apply { addAll(ev.images) } }
    var rep by remember { mutableStateOf(0) }
    val rem = remember { mutableStateListOf<Int>().apply { addAll(ev.remind) } }
    var icon by remember { mutableStateOf(ev.icon) }
    var showIcons by remember { mutableStateOf(false) }
    var until by remember { mutableStateOf(plusDays(ev.date, 56)) }
    var err by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(4)) { uris ->
        uris.forEach { u -> if (imgs.size < 4) compressUri(ctx, u)?.let { imgs += it } }
    }

    Title(if (isNew) "予定を追加" else "予定を編集")
    val lessons = q.d.lessons.filter { !it.deleted }
    if (isNew && lessons.isNotEmpty()) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(P.tintO).padding(10.dp)) {
            T("習い事から1タップ", 11.sp, P.sub, true)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                lessons.forEach { l ->
                    Box(Modifier.clip(CircleShape).background(P.card).clickable {
                        title = l.name; start = l.start; end = l.end; loc = l.location; outdoor = l.outdoor; cat = "lesson"; allDay = false
                        mem.clear(); mem += l.who()
                    }.padding(horizontal = 12.dp, vertical = 6.dp)) { T(l.name, 13.sp, bold = true) }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
    Field(title, { title = it }, "タイトル", placeholder = "例: 歯医者、運動会、部活")
    Field(short, { short = it.take(3) }, "略称（ウィジェットの短い札用・2文字くらい。なくてもよい）", placeholder = "例: 歯医")
    T("だれの予定（だれも選ばないと家族みんな）", 12.sp, P.sub, true); Spacer(Modifier.height(4.dp))
    MemberChips(q, mem.toSet(), { id -> if (id in mem) mem.remove(id) else mem += id })
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        PickBox("日付", md(date), Modifier.weight(1f)) { pickDate(ctx, date) { date = it } }
        Column(Modifier.weight(1f)) { Toggle("終日", allDay) { allDay = it } }
    }
    if (!allDay) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PickBox("開始", start, Modifier.weight(1f)) { pickTime(ctx, start) { start = it; if (end.isBlank() || end <= it) end = "%02d:%s".format(minOf(23, it.take(2).toInt() + 1), it.drop(3)) } }
        PickBox("終了", end, Modifier.weight(1f)) { pickTime(ctx, end.ifBlank { start }) { end = it } }
    } else PickBox("終了日（何日か続くとき）", if (endDate.isBlank()) "" else md(endDate)) { pickDate(ctx, endDate.ifBlank { date }) { endDate = it } }
    Field(loc, { loc = it }, "場所")
    T("種類", 12.sp, P.sub, true)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        CATS.forEach { (k, n) -> Box(Modifier.clip(CircleShape).background(if (cat == k) P.tintR else P.card2).clickable { cat = k }.padding(horizontal = 12.dp, vertical = 6.dp)) { T(n, 13.sp, if (cat == k) P.rasp else P.ink, true) } }
    }
    Toggle("屋外の予定（雨のアドバイスを強めに出す）", outdoor) { outdoor = it }
    Field(memo, { memo = it }, "メモ（持ち物、集合場所など）", singleLine = false, minLines = 2)
    T("写真（4枚まで）", 12.sp, P.sub, true)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
        imgs.forEachIndexed { i, src ->
            Box { dataImage(src)?.let { Image(it, "写真", Modifier.size(80.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop) }
                Box(Modifier.align(Alignment.TopEnd).size(24.dp).clip(CircleShape).background(P.ink).clickable { imgs.removeAt(i) }, contentAlignment = Alignment.Center) { T("×", 13.sp, P.card, true) } }
        }
        if (imgs.size < 4) GhostBtn("＋ 写真") { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    }
    RemindChips(rem)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
        T("カレンダーのマーク", 12.sp, P.sub, true, Modifier.weight(1f))
        T(icon.ifBlank { q.iconOf(ev.copy(title = title, icon = "")).ifBlank { "なし" } + "（自動）" }, 16.sp)
        Spacer(Modifier.width(8.dp)); GhostBtn(if (showIcons) "閉じる" else "選ぶ") { showIcons = !showIcons }
    }
    if (showIcons) EmojiPicker(icon) { icon = it; showIcons = false }
    if (isNew) {
        T("くり返し", 12.sp, P.sub, true)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            listOf(0 to "なし", 7 to "毎週", 14 to "隔週").forEach { (k, n) -> Box(Modifier.clip(CircleShape).background(if (rep == k) P.tintR else P.card2).clickable { rep = k }.padding(horizontal = 12.dp, vertical = 6.dp)) { T(n, 13.sp, if (rep == k) P.rasp else P.ink, true) } }
        }
        if (rep > 0) PickBox("いつまで", md(until)) { pickDate(ctx, until) { until = it } }
    }
    if (err.isNotBlank()) T(err, 13.sp, P.rasp, true)
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GhostBtn("やめる", Modifier.weight(1f), small = false) { close() }
        Btn("保存", P.rasp, Modifier.weight(2f)) {
            if (title.isBlank()) { err = "タイトルを入れてください"; return@Btn }
            val ad = allDay || start.isBlank()
            val base = ev.copy(title = title.trim(), date = date, allDay = ad, start = if (ad) "" else start, end = if (ad) "" else end,
                endDate = if (ad && endDate > date) endDate else "", location = loc.trim(), cat = cat, outdoor = outdoor, memo = memo.trim(),
                members = mem.toList(), images = imgs.toList(), remind = rem.sorted(), icon = icon, short = short.trim(), edited = ev.seriesId.isNotBlank() || ev.edited)
            if (isNew && rep > 0) {
                val sid = uid(); var d = date; var n = 0
                while (d <= until && n < 60) { Repo.putEvent(base.copy(id = uid(), date = d, seriesId = sid, edited = false)); d = plusDays(d, rep.toLong()); n++ }
            } else Repo.putEvent(base)
            close()
        }
    }
}

/* ---------- 習い事テンプレート ---------- */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LessonEditor(q: Q, l0: Lesson?, close: () -> Unit) {
    val ctx = LocalContext.current
    val l = l0 ?: Lesson(id = uid(), member = if (q.viewer != "all") q.viewer else q.members.firstOrNull()?.id ?: "")
    var name by remember { mutableStateOf(l.name) }
    var short by remember { mutableStateOf(l.short) }
    val who = remember { mutableStateListOf<String>().apply { addAll(l.who()) } }
    val days = remember { mutableStateListOf<Int>().apply { addAll(l.days) } }
    var start by remember { mutableStateOf(l.start) }
    var end by remember { mutableStateOf(l.end) }
    var loc by remember { mutableStateOf(l.location) }
    var outdoor by remember { mutableStateOf(l.outdoor) }
    var auto by remember { mutableStateOf(l.auto) }
    var hol by remember { mutableStateOf(l.skipHoliday) }
    val rem = remember { mutableStateListOf<Int>().apply { addAll(l.remind) } }
    var err by remember { mutableStateOf("") }
    var armed by remember { mutableStateOf(false) }
    Title(if (l0 == null) "習い事を追加" else "習い事を編集")
    Field(name, { name = it }, "名前", placeholder = "例: ピアノ、スイミング、塾")
    Field(short, { short = it.take(3) }, "略称（ウィジェットの短い札用・2文字くらい）", placeholder = "例: チア")
    T("だれ（複数OK：姉妹で同じ習い事など）", 12.sp, P.sub, true); Spacer(Modifier.height(4.dp))
    MemberChips(q, who.toSet(), { id -> if (id in who) who.remove(id) else who += id })
    Spacer(Modifier.height(10.dp))
    T("曜日（複数OK）", 12.sp, P.sub, true)
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(vertical = 6.dp)) {
        DAY.forEachIndexed { i, dn ->
            Box(Modifier.size(38.dp).clip(CircleShape).background(if (i in days) P.rasp else P.card2).clickable { if (i in days) days.remove(i) else days += i }, contentAlignment = Alignment.Center) {
                T(dn, 14.sp, if (i in days) Color.White else P.ink, true)
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PickBox("開始", start, Modifier.weight(1f)) { pickTime(ctx, start) { start = it } }
        PickBox("終了", end, Modifier.weight(1f)) { pickTime(ctx, end) { end = it } }
    }
    Field(loc, { loc = it }, "場所")
    Toggle("屋外（雨のアドバイスを強めに出す）", outdoor) { outdoor = it }
    Toggle("毎週自動で入れる（6週間先まで）", auto) { auto = it }
    Toggle("祝日は入れない", hol) { hol = it }
    RemindChips(rem)
    T("時間や曜日を変えると、きょう以降の分（個別に編集していないもの）がまとめて変わります。1回だけ休みの日は、その日の予定を削除してください。", 12.sp, P.sub)
    if (err.isNotBlank()) T(err, 13.sp, P.rasp, true)
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (l0 != null) Btn(if (armed) "もう一度で削除" else "削除", P.tintR, Modifier.weight(1f)) {
            if (!armed) armed = true else { val x = l0.copy(deleted = true); Repo.putLesson(x); Repo.syncSeries(x); close() }
        }
        Btn("保存", P.rasp, Modifier.weight(2f)) {
            if (name.isBlank()) { err = "名前を入れてください"; return@Btn }
            if (days.isEmpty()) { err = "曜日を選んでください"; return@Btn }
            if (who.isEmpty()) { err = "だれの習い事か選んでください（複数OK）"; return@Btn }
            val x = l.copy(name = name.trim(), short = short.trim(), member = who.first(), members = who.toList(), days = days.sorted(), start = start, end = end, location = loc.trim(), outdoor = outdoor, auto = auto, skipHoliday = hol, remind = rem.sorted(), sample = false)
            Repo.putLesson(x); Repo.syncSeries(x); close()
        }
    }
}

/* ---------- ストック ---------- */
@Composable
fun StockEditor(q: Q, s0: Stock?, close: () -> Unit) {
    val ctx = LocalContext.current
    val s = s0 ?: Stock(id = uid(), owner = if (q.viewer != "all") q.viewer else "", lastBought = todayStr())
    var name by remember { mutableStateOf(s.name) }
    var days by remember { mutableStateOf(s.days.toString()) }
    var lead by remember { mutableStateOf(s.lead.toString()) }
    var last by remember { mutableStateOf(s.lastBought.ifBlank { todayStr() }) }
    var owner by remember { mutableStateOf(s.owner) }
    var memo by remember { mutableStateOf(s.memo) }
    var armed by remember { mutableStateOf(false) }
    Title(if (s0 == null) "ストックを追加" else "ストックを編集")
    Field(name, { name = it }, "名前", placeholder = "例: poro、コンタクト、洗剤")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Field(days, { days = it.filter { c -> c.isDigit() } }, "1回買うと何日もつ", Modifier.weight(1f))
        Field(lead, { lead = it.filter { c -> c.isDigit() } }, "何日前に知らせる", Modifier.weight(1f))
    }
    PickBox("最後に買った日（使い始めた日）", md(last)) { pickDate(ctx, last) { last = it } }
    T("だれのもの（自分だけ表示にしたいとき）", 12.sp, P.sub, true); Spacer(Modifier.height(4.dp))
    MemberChips(q, setOf(owner), { owner = if (owner == it) "" else it }, withAll = true, allSelected = owner.isBlank(), onAll = { owner = "" })
    Spacer(Modifier.height(8.dp))
    Field(memo, { memo = it }, "メモ")
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (s0 != null) Btn(if (armed) "もう一度で削除" else "削除", P.tintR, Modifier.weight(1f)) { if (!armed) armed = true else { Repo.putStock(s0.copy(deleted = true)); close() } }
        Btn("保存", P.rasp, Modifier.weight(2f)) {
            if (name.isBlank()) return@Btn
            Repo.putStock(s.copy(name = name.trim(), days = days.toIntOrNull()?.coerceAtLeast(1) ?: 30, lead = lead.toIntOrNull() ?: 0, lastBought = last, owner = owner, memo = memo.trim(), sample = false))
            close()
        }
    }
}

/* ---------- お買い物チェックリスト ---------- */
@Composable
fun ShopList(q: Q) {
    val t = q.trip()
    var add by remember { mutableStateOf("") }
    val rows = q.shopRows(t)
    if (rows.isEmpty()) T("今回買うものはありません", 13.5.sp, P.sub)
    rows.forEach { r ->
        val del: (() -> Unit)? = if (r.kind == "shop") ({ q.d.shop.firstOrNull { it.id == r.id }?.let { Repo.putShop(it.copy(deleted = true)) } }) else null
        CheckRow(r.on, r.name, r.sub, onDelete = del) { Repo.toggleShop(r.kind, r.id) }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Field(add, { add = it }, "ほかに買うもの（例: 牛乳）", Modifier.weight(1f)); Spacer(Modifier.width(6.dp))
        Btn("追加", small = true) { if (add.isNotBlank()) { Repo.putShop(ShopItem(uid(), add.trim(), by = if (q.viewer != "all") q.viewer else "")); add = "" } }
    }
}
@Composable
fun ShopSheet(q: Q) {
    val t = q.trip()
    Title("お買い物 ${md(t.d)}")
    T("次の買い物（${md(t.n)}）までになくなるものと、追加したものです。チェックすると「買った日」が記録されます。", 12.5.sp, P.sub)
    Spacer(Modifier.height(6.dp))
    ShopList(q)
    T("お買い物の曜日（この端末）", 12.sp, P.sub, true)
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(vertical = 6.dp)) {
        DAY.forEachIndexed { i, dn ->
            val on = i in q.s.shopDays
            Box(Modifier.size(38.dp).clip(CircleShape).background(if (on) P.rasp else P.card2).clickable {
                Repo.setSettings { st -> val l = st.shopDays.toMutableList(); if (on && l.size > 1) l.remove(i) else if (!on) l += i; st.copy(shopDays = l.sorted()) }
            }, contentAlignment = Alignment.Center) { T(dn, 14.sp, if (on) Color.White else P.ink, true) }
        }
    }
}

/* ---------- 見る人 ---------- */
@Composable
fun ViewerSheet(q: Q, close: () -> Unit) {
    Title("だれの予定を見る？")
    @Composable fun Opt(sel: Boolean, label: String, m: Member?, onClick: () -> Unit) {
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(14.dp)).background(if (sel) P.tintR else P.card2).clickable(onClick = onClick).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically) { if (m != null) { Avatar(m, 30.dp); Spacer(Modifier.width(10.dp)) }; T(label, 15.sp, if (sel) P.rasp else P.ink, true) }
    }
    Opt(q.viewer == "all", "全員の予定", null) { Repo.setSettings { it.copy(viewer = "all") }; close() }
    q.members.forEach { m -> Opt(q.viewer == m.id, "${m.name}の予定だけ", m) { Repo.setSettings { it.copy(viewer = m.id) }; close() } }
    T("この端末に保存されます。「家族みんな」の予定は誰を選んでも表示されます。", 12.sp, P.sub, modifier = Modifier.padding(top = 6.dp))
}

/** 個別のお知らせ(複数選べる) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RemindChips(rem: MutableList<Int>) {
    T("🔔 この予定のお知らせ（複数OK）", 12.sp, P.sub, true, Modifier.padding(top = 4.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
        REMIND_OPTS.forEach { (m, n) ->
            val on = m in rem
            Box(Modifier.clip(CircleShape).background(if (on) P.tintO else P.card2).border(1.5.dp, if (on) P.orange else P.line, CircleShape)
                .clickable { if (on) rem.remove(m) else rem.add(m) }.padding(horizontal = 12.dp, vertical = 6.dp)) { T(n, 13.sp, if (on) P.orange else P.ink, true) }
        }
    }
    T("終日の予定は当日8時が基準です。お知らせは、その予定に関係する人のスマホに出ます（設定 → スマホ通知 で変更可）。", 11.5.sp, P.sub, modifier = Modifier.padding(bottom = 8.dp))
}

/** 大事な日の背景色と、ひとこと(家族で共有) */
@Composable
fun DayColorPicker(q: Q, ds: String) {
    val cur = q.d.days.firstOrNull { it.id == ds && !it.deleted }
    var label by remember(ds, cur?.label) { mutableStateOf(cur?.label ?: "") }
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(14.dp)).background(P.card2).padding(10.dp)) {
        T("この日の色（大事な日に）", 12.sp, P.sub, true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(P.card).border(2.dp, if (cur == null || cur.color.isBlank()) P.ink else P.line, CircleShape)
                .clickable { Repo.putDay(DayMark(ds, "", label)) }, contentAlignment = Alignment.Center) { T("なし", 9.sp, P.sub, true) }
            DAY_COLORS.forEach { c ->
                Box(Modifier.size(30.dp).clip(CircleShape).background(hexColor(c)).border(2.5.dp, if (cur?.color == c) P.ink else Color.Transparent, CircleShape)
                    .clickable { Repo.putDay(DayMark(ds, c, label)) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Field(label, { label = it }, "ひとこと（例: 参観日、給料日）", Modifier.weight(1f)); Spacer(Modifier.width(6.dp))
            Btn("保存", small = true) { Repo.putDay(DayMark(ds, cur?.color ?: DAY_COLORS[0], label.trim())) }
        }
    }
}

/** 絵文字を選ぶ(マーク用) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmojiPicker(cur: String, allowNone: Boolean = true, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (allowNone) Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(if (cur.isBlank()) P.tintR else P.card2).clickable { onPick("") }, contentAlignment = Alignment.Center) { T("自動", 10.sp, P.sub, true) }
        EMOJI_CHOICES.forEach { e ->
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(if (cur == e) P.tintR else P.card2).clickable { onPick(e) }, contentAlignment = Alignment.Center) { T(e, 20.sp) }
        }
    }
}
