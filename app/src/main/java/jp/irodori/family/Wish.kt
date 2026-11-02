package jp.irodori.family

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

private fun kindColor(k: String, p: Pal): Pair<Color, Color> = when (k) {
    "go" -> p.tintT to p.teal
    "eat" -> p.tintO to p.orange
    "want" -> p.tintI to p.indigo
    else -> p.tintR to p.rasp
}
fun domainOf(url: String): String = runCatching { java.net.URI(url).host?.removePrefix("www.") ?: url }.getOrDefault(url)
fun firstUrl(text: String): String = Regex("https?://\\S+").find(text)?.value ?: ""

/** やりたいことから予定を作る(タイトル・メモ・URL・写真・行きたい人を引き継ぐ) */
fun wishToEvent(q: Q, w: Wish): Event = Event(
    id = uid(), date = todayStr(), allDay = true, title = w.title,
    memo = listOf(w.memo, w.url).filter { it.isNotBlank() }.joinToString("\n"),
    images = w.images.take(4), members = (listOf(w.by) + w.likes).filter { q.member(it) != null }.distinct(),
    cat = if (w.kind == "go" || w.kind == "eat") "out" else "plan",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WishScreen(q: Q, open: (Sheet) -> Unit) {
    var kind by remember { mutableStateOf("") }
    var who by remember { mutableStateOf("") }
    var showDone by remember { mutableStateOf(false) }
    val all = q.d.wishes.filter { !it.deleted && (kind.isBlank() || it.kind == kind) && (who.isBlank() || it.by == who || who in it.likes) }
        .sortedByDescending { it.createdAt }
    val doing = all.filter { !it.done }; val done = all.filter { it.done }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CardBox {
            SecH("やりたい・行きたいリスト", "${doing.size}件")
            T("見つけたお店や行きたい場所、やってみたいことを家族で集めるリストです。ほかのアプリ（Chrome・Instagram・写真など）の「共有」から「カゾスケ」を選ぶと、URLや画像をそのまま追加できます。", 12.5.sp, P.sub)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                (listOf("" to "全部") + WISH_KINDS.toList()).forEach { (k, n) ->
                    val (bg, fg) = if (k.isBlank()) P.tintI to P.indigo else kindColor(k, P)
                    Box(Modifier.clip(CircleShape).background(if (kind == k) bg else P.card2).clickable { kind = k }.padding(horizontal = 12.dp, vertical = 6.dp)) { T(n, 13.sp, if (kind == k) fg else P.ink, true) }
                }
            }
            Spacer(Modifier.height(6.dp))
            MemberChips(q, setOf(who), { who = if (who == it) "" else it }, withAll = true, allSelected = who.isBlank(), onAll = { who = "" })
            Btn("＋ 追加する", P.rasp, Modifier.fillMaxWidth().padding(top = 10.dp)) {
                open(Sheet.WishEd(Wish(uid(), kind = kind.ifBlank { "do" }, by = if (q.viewer != "all") q.viewer else "", createdAt = System.currentTimeMillis()), true))
            }
        }
        if (doing.isEmpty()) T("まだありません。「＋ 追加する」から入れてみてください。", 13.5.sp, P.sub)
        doing.forEach { WishCard(q, it, open) }
        if (done.isNotEmpty()) {
            GhostBtn(if (showDone) "済み（${done.size}件）を隠す" else "済み（${done.size}件）を見る") { showDone = !showDone }
            if (showDone) done.forEach { WishCard(q, it, open) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WishCard(q: Q, w: Wish, open: (Sheet) -> Unit) {
    val uri = LocalUriHandler.current
    var zoom by remember { mutableStateOf<String?>(null) }
    val (bg, fg) = kindColor(w.kind, P)
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill(WISH_KINDS[w.kind] ?: "やりたい", bg, fg)
            Spacer(Modifier.width(8.dp))
            T(w.title, 16.sp, if (w.done) P.sub else P.ink, true, Modifier.weight(1f), strike = w.done)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            q.member(w.by)?.let { Avatar(it, 20.dp); Spacer(Modifier.width(4.dp)); T("${it.name}が追加", 12.sp, P.sub); Spacer(Modifier.width(8.dp)) }
            T(java.text.SimpleDateFormat("M/d", java.util.Locale.JAPAN).format(java.util.Date(w.createdAt)), 12.sp, P.sub)
        }
        if (w.url.isNotBlank()) Row(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).background(P.card2)
            .clickable { runCatching { uri.openUri(w.url) } }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            T("🔗", 14.sp); Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) { T(domainOf(w.url), 13.sp, P.teal, true, maxLines = 1); T(w.url, 11.sp, P.sub, maxLines = 1) }
            T("開く", 12.sp, P.teal, true)
        }
        if (w.memo.isNotBlank()) T(w.memo, 14.sp, modifier = Modifier.padding(top = 8.dp))
        if (w.images.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            w.images.forEach { src -> dataImage(src)?.let { Image(it, "写真", Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)).clickable { zoom = src }, contentScale = ContentScale.Crop) } }
        }
        // わたしも！(タップした人の顔に色がつく)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
            T("わたしも！", 12.sp, P.sub, true); Spacer(Modifier.width(6.dp))
            q.members.forEach { m ->
                val on = m.id in w.likes || m.id == w.by
                Box(Modifier.padding(end = 3.dp).clip(CircleShape).border(2.dp, if (on) P.rasp else Color.Transparent, CircleShape)
                    .clickable(enabled = m.id != w.by) { Repo.putWish(w.copy(likes = if (m.id in w.likes) w.likes - m.id else w.likes + m.id)) }
                    .padding(2.dp)) { Box(Modifier.background(if (on) Color.Transparent else P.card.copy(alpha = 0.55f))) { Avatar(m, 24.dp) } }
            }
            val n = (w.likes + w.by).filter { it.isNotBlank() }.distinct().size
            if (n > 0) T("♥$n", 13.sp, P.rasp, true)
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("📅 予定にする", P.orange, Modifier.weight(1.4f), small = true) { open(Sheet.Edit(wishToEvent(q, w), true)) }
            GhostBtn("編集", Modifier.weight(1f)) { open(Sheet.WishEd(w, false)) }
            GhostBtn(if (w.done) "戻す" else "済み", Modifier.weight(1f)) { Repo.putWish(w.copy(done = !w.done, doneAt = if (!w.done) System.currentTimeMillis() else 0)) }
        }
    }
    zoom?.let { z -> Dialog(onDismissRequest = { zoom = null }) { dataImage(z)?.let { Image(it, "写真", Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { zoom = null }) } } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WishEditor(q: Q, w: Wish, isNew: Boolean, close: () -> Unit) {
    val ctx = LocalContext.current
    var title by remember { mutableStateOf(w.title) }
    var kind by remember { mutableStateOf(w.kind) }
    var by by remember { mutableStateOf(w.by) }
    var url by remember { mutableStateOf(w.url) }
    var memo by remember { mutableStateOf(w.memo) }
    val imgs = remember { mutableStateListOf<String>().apply { addAll(w.images) } }
    var err by remember { mutableStateOf("") }
    var armed by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(4)) { uris ->
        uris.forEach { u -> if (imgs.size < 4) compressUri(ctx, u, 800, 65)?.let { imgs += it } }
    }
    Title(if (isNew) "やりたいことを追加" else "やりたいことを編集")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 8.dp)) {
        WISH_KINDS.forEach { (k, n) ->
            val (bg, fg) = kindColor(k, P)
            Box(Modifier.clip(CircleShape).background(if (kind == k) bg else P.card2).clickable { kind = k }.padding(horizontal = 14.dp, vertical = 7.dp)) { T(n, 14.sp, if (kind == k) fg else P.ink, true) }
        }
    }
    Field(title, { title = it }, "なにを？どこへ？", placeholder = "例: 〇〇水族館、パンケーキのお店")
    T("だれのやりたい？", 12.sp, P.sub, true); Spacer(Modifier.height(4.dp))
    MemberChips(q, setOf(by), { by = it })
    Spacer(Modifier.height(10.dp))
    Field(url, { url = it.trim() }, "URL（お店・場所・SNSのリンク）", placeholder = "https://...")
    Field(memo, { memo = it }, "メッセージ・メモ", singleLine = false, minLines = 3, placeholder = "例: 夏休みに行きたい！ 予約がいるみたい")
    T("画像（4枚まで・スクショもOK）", 12.sp, P.sub, true)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
        imgs.forEachIndexed { i, src ->
            Box {
                dataImage(src)?.let { Image(it, "画像", Modifier.size(80.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop) }
                Box(Modifier.align(Alignment.TopEnd).size(24.dp).clip(CircleShape).background(P.ink).clickable { imgs.removeAt(i) }, contentAlignment = Alignment.Center) { T("×", 13.sp, P.card, true) }
            }
        }
        if (imgs.size < 4) GhostBtn("＋ 画像") { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    }
    if (err.isNotBlank()) T(err, 13.sp, P.rasp, true)
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!isNew) Btn(if (armed) "もう一度で削除" else "削除", P.tintR, Modifier.weight(1f)) { if (!armed) armed = true else { Repo.putWish(w.copy(deleted = true, images = emptyList())); close() } }
        else GhostBtn("やめる", Modifier.weight(1f), small = false) { close() }
        Btn("保存", P.rasp, Modifier.weight(2f)) {
            val t = title.trim().ifBlank { if (url.isNotBlank()) domainOf(url) else "" }
            if (t.isBlank()) { err = "なにを・どこへ を入れてください"; return@Btn }
            Repo.putWish(w.copy(title = t, kind = kind, by = by, url = url, memo = memo.trim(), images = imgs.toList(), createdAt = if (w.createdAt == 0L) System.currentTimeMillis() else w.createdAt))
            close()
        }
    }
}

/** きょう画面用: 新しい順に3件 */
@Composable
fun WishHomeCard(q: Q, open: (Sheet) -> Unit) {
    val list = q.d.wishes.filter { !it.deleted && !it.done }.sortedByDescending { it.createdAt }
    CardBox {
        SecH("やりたい・行きたい", "${list.size}件")
        if (list.isEmpty()) T("まだありません", 13.5.sp, P.sub)
        list.take(3).forEach { w ->
            val (bg, fg) = kindColor(w.kind, P)
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill(WISH_KINDS[w.kind] ?: "", bg, fg); Spacer(Modifier.width(8.dp))
                T(w.title, 14.sp, bold = true, modifier = Modifier.weight(1f), maxLines = 1)
                Avatar(q.member(w.by), 20.dp)
            }
        }
        Btn("リストを開く", P.rasp, Modifier.fillMaxWidth().padding(top = 8.dp)) { open(Sheet.GoTab("wish")) }
    }
}
