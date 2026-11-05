package jp.irodori.family

import android.Manifest
import android.app.AlarmManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

private fun copy(ctx: Context, text: String) {
    ctx.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("irodori", text))
}
private fun readText(ctx: Context, uri: Uri): String? = runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } }.getOrNull()

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(q: Q) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val syncStatus by Sync.status.collectAsState()
    val charVer by CharStore.version.collectAsState()
    var msg by remember { mutableStateOf("") }
    var charTarget by remember { mutableStateOf("") }
    var freeFor by remember { mutableStateOf("") }

    val pickHtml = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) readText(ctx, u)?.let { File(ctx.filesDir, "char_html_$charTarget.html").writeText(it); File(ctx.filesDir, "char_glb_$charTarget.glb").delete(); CharStore.version.value++ }
    }
    val pickGlb = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) runCatching { ctx.contentResolver.openInputStream(u)?.use { i -> FileOutputStream(File(ctx.filesDir, "char_glb_$charTarget.glb")).use { i.copyTo(it) } }
            File(ctx.filesDir, "char_html_$charTarget.html").delete(); CharStore.version.value++ }
    }
    val pickImg = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) runCatching {
            val b = ctx.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it) } ?: return@runCatching
            val s = minOf(1f, 256f / maxOf(b.width, b.height))
            val sc = Bitmap.createScaledBitmap(b, (b.width * s).toInt().coerceAtLeast(1), (b.height * s).toInt().coerceAtLeast(1), true)
            FileOutputStream(File(ctx.filesDir, "char_img_$charTarget.png")).use { sc.compress(Bitmap.CompressFormat.PNG, 100, it) }
            CharStore.version.value++; Widgets.refresh(ctx)
        }
    }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> msg = if (ok) "通知を許可しました" else "通知が許可されませんでした" }
    var wxMsg by remember { mutableStateOf("") }
    var locDenied by remember { mutableStateOf(false) }
    fun refreshWx() = scope.launch {
        wxMsg = "現在地を確認中…"
        Wx.refresh(ctx, true)
        val e = Repo.wxError.value
        wxMsg = if (e.isBlank()) "✓ ${Repo.weather.value?.place ?: ""} の天気に更新しました（" + java.text.SimpleDateFormat("H:mm", java.util.Locale.JAPAN).format(java.util.Date()) + "）" else e
    }
    val locPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        locDenied = !ok
        if (ok) refreshWx() else wxMsg = "位置情報が許可されていません。前に「許可しない」を選んでいると、このボタンでは確認が出ません。下の「Androidの設定を開く」→「権限」→「位置情報」→「アプリの使用中のみ許可」にしてください。"
    }
    val gsJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> if (u != null) readText(ctx, u)?.let { t -> Repo.setSettings { it.copy(fbConfig = t) }; msg = "読み込みました。「接続する」を押してください" } }
    val exportDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { u ->
        if (u != null) runCatching { ctx.contentResolver.openOutputStream(u)?.use { it.write(Repo.exportJson().toByteArray()) }; msg = "書き出しました" }
    }
    val importDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) readText(ctx, u)?.let { t -> msg = runCatching { Repo.importAll(t); "読み込みました" }.getOrElse { "読み込めませんでした: ${it.message}" } }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (msg.isNotBlank()) T(msg, 13.sp, P.teal, true, Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(P.tintT).clickable { msg = "" }.padding(12.dp))

        CardBox {
            SecH("表示", "この端末に保存")
            T("この端末で見る人（娘さんの端末は本人にすると、その子の予定だけ）", 12.sp, P.sub, true); Spacer(Modifier.height(4.dp))
            MemberChips(q, setOf(q.viewer), { id -> Repo.setSettings { it.copy(viewer = id) } }, withAll = true, allSelected = q.viewer == "all", onAll = { Repo.setSettings { it.copy(viewer = "all") } })
            Spacer(Modifier.height(10.dp))
            T("予定の見た目", 12.sp, P.sub, true)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)) {
                listOf("solid" to "塗りつぶし・白文字", "tint" to "うす塗り・色の線").forEach { (k, n) ->
                    Box(Modifier.clip(CircleShape).background(if (q.s.evStyle == k) P.tintR else P.card2).clickable { Repo.setSettings { it.copy(evStyle = k) } }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        T(n, 13.sp, if (q.s.evStyle == k) P.rasp else P.ink, true)
                    }
                }
            }
            T("2人以上の予定は、塗りつぶしでは上下に色を分け、うす塗りでは左に色の線を並べます。", 11.5.sp, P.sub, modifier = Modifier.padding(bottom = 8.dp))
            T("開いたときの画面", 12.sp, P.sub, true)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                listOf("month" to "カレンダー（月）", "week" to "カレンダー（週）", "day" to "カレンダー（日）", "home" to "ホーム").forEach { (k, n) ->
                    Box(Modifier.clip(CircleShape).background(if (q.s.startView == k) P.tintR else P.card2).clickable { Repo.setSettings { it.copy(startView = k) } }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        T(n, 13.sp, if (q.s.startView == k) P.rasp else P.ink, true)
                    }
                }
            }
        }

        CardBox {
            SecH("家族メンバー", "名前と色（家族で共有）")
            q.members.forEach { m ->
                var name by remember(m.id, m.name) { mutableStateOf(m.name) }
                var armed by remember(m.id) { mutableStateOf(false) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(m, 30.dp); Spacer(Modifier.width(8.dp))
                    Field(name, { name = it }, "名前", Modifier.weight(1f))
                    if (name != m.name && name.isNotBlank()) { Spacer(Modifier.width(4.dp)); Btn("保存", small = true) { Repo.putMember(m.copy(name = name.trim())) } }
                    else if (q.members.size > 1) { Spacer(Modifier.width(4.dp)); GhostBtn(if (armed) "もう一度" else "削除") { if (!armed) armed = true else { Repo.putMember(m.copy(deleted = true)); if (q.viewer == m.id) Repo.setSettings { it.copy(viewer = "all") } } } }
                }
                FlowRow(Modifier.padding(start = 38.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    PALETTE.forEach { c -> Box(Modifier.size(26.dp).clip(CircleShape).background(hexColor(c)).border(2.5.dp, if (m.color.equals(c, true)) P.ink else Color.Transparent, CircleShape).clickable { Repo.putMember(m.copy(color = c)) }) }
                    Box(Modifier.size(26.dp).clip(CircleShape).background(P.card2).border(1.5.dp, P.line, CircleShape).clickable { freeFor = if (freeFor == m.id) "" else m.id }, contentAlignment = Alignment.Center) { T("🎨", 13.sp) }
                }
                if (freeFor == m.id) FreeColorPicker(m.color) { c -> Repo.putMember(m.copy(color = c)) }
                Spacer(Modifier.height(6.dp))
            }
            GhostBtn("＋ メンバーを追加") { Repo.putMember(Member(uid(), "あたらしい人", PALETTE[q.members.size % PALETTE.size])) }
        }

        CardBox {
            SecH("キャラクター", "この端末だけ")
            q.members.forEach { m ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    Avatar(m, 30.dp); Spacer(Modifier.width(8.dp))
                    Column { T(m.name, 14.sp, bold = true); T(remember(charVer) { CharStore.label(ctx, m.id) }, 12.sp, P.sub) }
                }
                FlowRow(Modifier.padding(start = 38.dp, top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GhostBtn("HTML") { charTarget = m.id; pickHtml.launch(arrayOf("text/html", "text/plain", "*/*")) }
                    GhostBtn("3D(GLB)") { charTarget = m.id; pickGlb.launch(arrayOf("*/*")) }
                    GhostBtn("ウィジェット用画像") { charTarget = m.id; pickImg.launch(arrayOf("image/*")) }
                    GhostBtn("標準に戻す") { CharStore.clear(ctx, m.id); Widgets.refresh(ctx) }
                }
            }
            Spacer(Modifier.height(10.dp))
            T("「全員」表示のときに話すキャラ", 12.sp, P.sub, true); Spacer(Modifier.height(4.dp))
            MemberChips(q, setOf(q.s.charMember), { id -> Repo.setSettings { it.copy(charMember = id) } })
            T("話す速さ ${"%.2f".format(q.s.voiceRate)}", 12.sp, P.sub, true, Modifier.padding(top = 8.dp))
            Slider(q.s.voiceRate, { v -> Repo.setSettings { it.copy(voiceRate = v) } }, valueRange = 0.6f..1.6f)
            T("声の高さ ${"%.2f".format(q.s.voicePitch)}", 12.sp, P.sub, true)
            Slider(q.s.voicePitch, { v -> Repo.setSettings { it.copy(voicePitch = v) } }, valueRange = 0.5f..2f)
            GhostBtn("声のテスト") { Speaker.speak(q.talk(q.talkMember(), false)) }
            T("ホーム画面のウィジェットには、HTML・3Dは表示できません（Androidの仕組み上、画像と文字だけ）。ウィジェットのキャラを変えたいときは「ウィジェット用画像」でPNGなどを選んでください。自作HTMLキャラは、話している間 {type:'irodori-speak', speaking:true} のメッセージを受け取れます。", 12.sp, P.sub)
        }

        FoldCard("マーク（絵）と日の色", "家族で共有") {
            var pickFor by remember { mutableStateOf<Int?>(null) }   // -1=お弁当, 0..=ルールの番号
            T("家族みんなの画面に同じ設定が反映されます（共有をつないでいる場合）。カレンダーとウィジェットの日付の横に出る絵です。予定名に言葉が入っていると、その絵が出ます（「|」で区切ると複数の言葉）。予定ごとに選ぶこともできます（予定の編集画面）。", 12.sp, P.sub)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                T("お弁当の日", 14.sp, bold = true, modifier = Modifier.weight(1f))
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(P.card2).clickable { pickFor = if (pickFor == -1) null else -1 }, contentAlignment = Alignment.Center) { T(q.fp.bentoIcon.ifBlank { "🍱" }, 22.sp) }
            }
            if (pickFor == -1) EmojiPicker(q.fp.bentoIcon, allowNone = false) { e -> Repo.putFamilyPrefs(q.fp.copy(bentoIcon = e)); pickFor = null }
            T("お弁当の日の背景色", 12.sp, P.sub, true, Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(P.card2).border(2.dp, if (q.fp.bentoBg.isBlank()) P.ink else P.line, CircleShape)
                    .clickable { Repo.putFamilyPrefs(q.fp.copy(bentoBg = "")) }, contentAlignment = Alignment.Center) { T("なし", 9.sp, P.sub, true) }
                DAY_COLORS.forEach { c ->
                    Box(Modifier.size(30.dp).clip(CircleShape).background(hexColor(c)).border(2.5.dp, if (q.fp.bentoBg == c) P.ink else Color.Transparent, CircleShape)
                        .clickable { Repo.putFamilyPrefs(q.fp.copy(bentoBg = c)) })
                }
            }
            T("大事な日の色は、カレンダーで日付を2回タップ →「この日の色」で付けられます（家族で共有）。", 12.sp, P.sub)
            Spacer(Modifier.height(10.dp))
            T("言葉 → 絵", 12.sp, P.sub, true)
            q.fp.marks.forEachIndexed { i, r ->
                var word by remember(i, r.word) { mutableStateOf(r.word) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(P.card2).clickable { pickFor = if (pickFor == i) null else i }, contentAlignment = Alignment.Center) { T(r.icon.ifBlank { "？" }, 22.sp) }
                    Spacer(Modifier.width(8.dp))
                    Field(word, { word = it }, "言葉", Modifier.weight(1f))
                    if (word != r.word) { Spacer(Modifier.width(4.dp)); Btn("保存", small = true) { Repo.putFamilyPrefs(q.fp.copy(marks = q.fp.marks.toMutableList().also { it[i] = r.copy(word = word.trim()) })) } }
                    else { Spacer(Modifier.width(4.dp)); T("－", 18.sp, P.rasp, true, Modifier.clickable { Repo.putFamilyPrefs(q.fp.copy(marks = q.fp.marks.filterIndexed { k, _ -> k != i })) }.padding(8.dp)) }
                }
                if (pickFor == i) EmojiPicker(r.icon, allowNone = false) { e -> Repo.putFamilyPrefs(q.fp.copy(marks = q.fp.marks.toMutableList().also { it[i] = r.copy(icon = e) })); pickFor = null }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                GhostBtn("＋ ルールを追加") { Repo.putFamilyPrefs(q.fp.copy(marks = q.fp.marks + MarkRule("", "⭐"))) }
                GhostBtn("最初の設定に戻す") { Repo.putFamilyPrefs(q.fp.copy(marks = DEFAULT_MARKS)) }
            }
        }

        CardBox {
            SecH("スマホ通知", "朝はきょう・夜はあした")
            Toggle("通知を出す", q.s.notifyOn) { v -> Repo.setSettings { it.copy(notifyOn = v) }; Notifier.schedule(ctx) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PickBox("きょうの予定（朝）", q.s.notifyToday, Modifier.weight(1f)) { pickTime(ctx, q.s.notifyToday) { t -> Repo.setSettings { it.copy(notifyToday = t) }; Notifier.schedule(ctx) } }
                PickBox("あしたの予定（夜）", q.s.notifyTomorrow, Modifier.weight(1f)) { pickTime(ctx, q.s.notifyTomorrow) { t -> Repo.setSettings { it.copy(notifyTomorrow = t) }; Notifier.schedule(ctx) } }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (Build.VERSION.SDK_INT >= 33 && !Notifier.canPost(ctx)) Btn("通知を許可する", P.teal, small = true) { notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS) }
                if (Build.VERSION.SDK_INT >= 31 && ctx.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == false)
                    Btn("時刻ぴったりを許可", P.teal, small = true) { runCatching { ctx.startActivity(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))) } }
                GhostBtn("いま通知を試す") { Notifier.fire(ctx, "today") }
            }
            T(if (Notifier.canPost(ctx)) "通知は許可されています" else "まだ通知が許可されていません", 12.sp, if (Notifier.canPost(ctx)) P.teal else P.rasp)
            Spacer(Modifier.height(8.dp))
            T("🔔 予定ごとのお知らせ（1時間前など）を受け取る範囲", 12.sp, P.sub, true)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                listOf("mine" to "自分に関係する予定だけ", "all" to "全員の予定", "off" to "受け取らない").forEach { (k, n) ->
                    Box(Modifier.clip(CircleShape).background(if (q.s.remindScope == k) P.tintO else P.card2).clickable { Repo.setSettings { it.copy(remindScope = k) }; Reminders.schedule(ctx) }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        T(n, 13.sp, if (q.s.remindScope == k) P.orange else P.ink, true)
                    }
                }
            }
            T("「自分」は上の「この端末で見る人」です（全員にしている端末は全員分が届きます）。お知らせの時間は、予定の追加・編集画面の「🔔 この予定のお知らせ」で選びます。習い事に付けると毎回付きます。", 12.sp, P.sub)
            T("届かない端末は、Androidの設定 → アプリ → カゾスケ → バッテリー を「制限なし」にしてください。", 12.sp, P.sub)
        }

        FoldCard("天気", q.w?.place ?: "未取得", startOpen = q.w == null) {
            var city by remember { mutableStateOf("") }
            var res by remember { mutableStateOf<List<Wx.Place>>(emptyList()) }
            Toggle("現在地の天気を使う", q.s.useGps) { v ->
                Repo.setSettings { it.copy(useGps = v) }
                if (v) locPerm.launch(Manifest.permission.ACCESS_COARSE_LOCATION) else scope.launch { Wx.refresh(ctx, true) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Field(city, { city = it }, "地名で探す（例: 厚木）", Modifier.weight(1f)); Spacer(Modifier.width(6.dp))
                Btn("探す", small = true) { scope.launch { res = Wx.search(city) } }
            }
            res.forEach { p ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(P.card2).clickable {
                    Repo.setSettings { it.copy(locName = p.name, locLat = p.lat, locLon = p.lon, useGps = false) }; res = emptyList()
                    scope.launch { Wx.refresh(ctx, true) }
                }.padding(10.dp)) { T(p.name, 14.sp, bold = true); Spacer(Modifier.width(8.dp)); T(p.area, 12.sp, P.sub) }
                Spacer(Modifier.height(4.dp))
            }
            if (q.s.locName.isNotBlank()) T("固定の地点: ${q.s.locName}（現在地が取れない時に使用）", 12.sp, P.sub)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GhostBtn("今すぐ更新") { refreshWx() }
                if (q.s.useGps) GhostBtn("現在地で更新") {
                    if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) refreshWx()
                    else locPerm.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            }
            if (wxMsg.isNotBlank()) T(wxMsg, 12.5.sp, if (wxMsg.startsWith("✓")) P.teal else if (wxMsg.endsWith("…")) P.sub else P.rasp, true, Modifier.padding(top = 6.dp))
            if (locDenied) GhostBtn("Androidの設定を開く", Modifier.padding(top = 4.dp)) {
                runCatching { ctx.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))) }
            }
            T("位置情報: " + if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) "許可されています" else "まだ許可されていません", 12.sp, P.sub)
            T("天気データ: Open-Meteo（無料・キー不要）。アドバイスは降水確率・風速・気温によるルール判定です。", 12.sp, P.sub)
        }

        FoldCard("家族で共有（Firebase）", syncStatus, startOpen = !syncStatus.startsWith("つながって")) {
            var cfg by remember(q.s.fbConfig) { mutableStateOf(q.s.fbConfig) }
            var fam by remember(q.s.familyId) { mutableStateOf(q.s.familyId) }
            T(syncStatus, 13.sp, if (syncStatus.startsWith("つながって")) P.teal else if (syncStatus.startsWith("エラー")) P.rasp else P.sub, true)
            Spacer(Modifier.height(6.dp))
            Field(cfg, { cfg = it }, "Firebaseの設定（google-services.json の中身）", singleLine = false, minLines = 3)
            GhostBtn("google-services.json をファイルから読む") { gsJson.launch(arrayOf("application/json", "*/*")) }
            Spacer(Modifier.height(8.dp))
            Field(fam, { fam = it }, "家族ID（家族全員で同じものを入れる）")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                GhostBtn("家族IDを新しく作る") { fam = randomId(20) }
                GhostBtn("家族IDをコピー") { copy(ctx, fam); msg = "家族IDをコピーしました" }
                Btn("接続する", small = true) { Repo.setSettings { it.copy(fbConfig = cfg, familyId = fam.trim(), syncCursor = 0) }; Sync.start(ctx, force = true) }
            }
            Spacer(Modifier.height(8.dp))
            T("""最初の準備（1回だけ・パソコン推奨）
1. console.firebase.google.com を開き「プロジェクトを作成」（名前は irodori など。アナリティクスはオフでよい）
2. 構築 → Authentication → 始める → ログイン方法 →「匿名」を有効にして保存
3. 構築 → Firestore Database → データベースを作成 → 場所 asia-northeast1 (Tokyo) → 本番環境モード
4. Firestore の「ルール」タブに下の文を貼って「公開」
5. 歯車 → プロジェクトの設定 →「アプリを追加」→ Android → パッケージ名に jp.irodori.family → 登録 → google-services.json をダウンロード（その後の手順は「次へ」で飛ばしてOK）
6. そのファイルをスマホに送り、上の「google-services.json をファイルから読む」で選ぶ →「家族IDを新しく作る」→「接続する」
7. 家族のスマホでも同じファイルと同じ家族IDで「接続する」（家族IDはLINEなどで送る）""", 12.5.sp)
            T("""rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /families/{familyId}/items/{itemId} {
      allow read, write: if request.auth != null;
    }
  }
}""", 11.5.sp, P.ink, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(10.dp)).background(P.card2).clickable {
                copy(ctx, "rules_version = '2';\nservice cloud.firestore {\n  match /databases/{database}/documents {\n    match /families/{familyId}/items/{itemId} {\n      allow read, write: if request.auth != null;\n    }\n  }\n}"); msg = "ルールをコピーしました"
            }.padding(10.dp))
            T("（タップでコピー）家族IDを知っている人は読み書きできます。家族IDは長いランダム文字のまま使い、家族以外に教えないでください。Web版いろどりと同じFirebase・同じ家族IDなら、Web版とも共有できます。", 12.sp, P.sub)
        }

        FoldCard("データ") {
            var armed by remember { mutableStateOf(false) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                GhostBtn("書き出し(JSON)") { exportDoc.launch("irodori-${todayStr()}.json") }
                GhostBtn("読み込み(Web版の書き出しもOK)") { importDoc.launch(arrayOf("application/json", "*/*")) }
                Btn(if (armed) "もう一度でサンプル削除" else "サンプルを消す", P.tintR, small = true) { if (!armed) armed = true else { Repo.deleteSamples(); armed = false; msg = "サンプルを消しました" } }
            }
            T("書き出しにはキャラクターは含まれません。", 12.sp, P.sub, modifier = Modifier.padding(top = 6.dp))
        }

        FoldCard("ウィジェットの置き方") {
            T("ホーム画面の何もないところを長押し →「ウィジェット」→「カゾスケ」を探す。\n・カゾスケ 1か月＋天気：1か月の予定と、3時間おき・7日間の天気\n・カゾスケ 週＋天気：1週間 ⇄ 3週間（上の札をタップで切り替え）と天気\n・カゾスケ 1か月：1か月の予定だけ\n・カゾスケ キャラ：横2マス。タップでその場でお話し\n・カゾスケ 買い物・ToDo：お買い物・みんなのToDo・自分のToDo（ウィジェット上でチェックできます）\n予定が多い日は、自動で短い札（略称）に切り替わり、入りきらない数は日付の横に「+3」のように出ます。", 13.sp)
            T("バージョン ${runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: ""}", 11.sp, P.sub, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** 色を自由に作る(色合い・あざやかさ・明るさ) */
@Composable
fun FreeColorPicker(cur: String, onPick: (String) -> Unit) {
    val hsv = remember(cur) { FloatArray(3).also { android.graphics.Color.colorToHSV(parseColor(cur), it) } }
    var h by remember(cur) { mutableStateOf(hsv[0]) }
    var sat by remember(cur) { mutableStateOf(hsv[1]) }
    var v by remember(cur) { mutableStateOf(hsv[2]) }
    val c = Color.hsv(h, sat, v)
    val hex = "#%06X".format(android.graphics.Color.HSVToColor(floatArrayOf(h, sat, v)) and 0xFFFFFF)
    Column(Modifier.fillMaxWidth().padding(start = 38.dp, bottom = 8.dp).clip(RoundedCornerShape(14.dp)).background(P.card2).padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(c)); Spacer(Modifier.width(10.dp))
            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(c).padding(horizontal = 8.dp, vertical = 3.dp)) { WhiteText("予定の見本", 13.sp, true) }
            Spacer(Modifier.weight(1f)); T(hex, 12.sp, P.sub)
        }
        T("色合い", 11.sp, P.sub); Slider(h, { h = it }, valueRange = 0f..359f)
        T("あざやかさ", 11.sp, P.sub); Slider(sat, { sat = it }, valueRange = 0.15f..1f)
        T("明るさ", 11.sp, P.sub); Slider(v, { v = it }, valueRange = 0.35f..1f)
        Btn("この色にする", hexColor(hex), Modifier.fillMaxWidth()) { onPick(hex) }
    }
}
