package jp.irodori.family

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* ============ 色(添付の水彩ブロブ: 藍・橙・青緑・ラズベリーをくすませた配色) ============ */
data class Pal(
    val paper: Color, val card: Color, val card2: Color, val ink: Color, val sub: Color, val line: Color,
    val indigo: Color, val orange: Color, val teal: Color, val rasp: Color, val sage: Color,
    val tintI: Color, val tintO: Color, val tintT: Color, val tintR: Color, val hol: Color, val sat: Color, val rain: Color,
)
val LightPal = Pal(Color(0xFFF7F3EE), Color.White, Color(0xFFFBF8F4), Color(0xFF2B2A3B), Color(0xFF7B7689), Color(0xFFECE5DD),
    Color(0xFF34408E), Color(0xFFE98D4D), Color(0xFF2E84A3), Color(0xFFC8457A), Color(0xFF7DAA92),
    Color(0xFFE9EAF6), Color(0xFFFDEDE0), Color(0xFFE0F0F5), Color(0xFFF9E4EC), Color(0xFFD0536E), Color(0xFF3F78B5), Color(0xFF4C86C0))
val DarkPal = Pal(Color(0xFF1B1A24), Color(0xFF25242F), Color(0xFF2B2A37), Color(0xFFF2EEF7), Color(0xFFABA5BC), Color(0xFF363445),
    Color(0xFF8D97E0), Color(0xFFF2A26C), Color(0xFF5FB3CF), Color(0xFFE47AA3), Color(0xFF9CC6AF),
    Color(0xFF2C2E48), Color(0xFF3A2C24), Color(0xFF1F3540), Color(0xFF3C2531), Color(0xFFEE7D93), Color(0xFF7FAEE3), Color(0xFF7FB0E0))
val LocalPal = staticCompositionLocalOf { LightPal }
val P: Pal @Composable get() = LocalPal.current

@Composable
fun IrodoriTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val p = if (dark) DarkPal else LightPal
    val scheme = if (dark) darkColorScheme(primary = p.rasp, secondary = p.indigo, tertiary = p.teal, background = p.paper, surface = p.card, onSurface = p.ink, onBackground = p.ink)
                 else lightColorScheme(primary = p.rasp, secondary = p.indigo, tertiary = p.teal, background = p.paper, surface = p.card, onSurface = p.ink, onBackground = p.ink)
    CompositionLocalProvider(LocalPal provides p) { MaterialTheme(colorScheme = scheme, content = content) }
}

fun hexColor(h: String): Color = Color(parseColor(h))
fun onColor(c: Color): Color = if (0.299f * c.red + 0.587f * c.green + 0.114f * c.blue > 0.66f) Color(0xFF2B2A3B) else Color.White
fun dayCol(ds: String, p: Pal): Color = when { HOLI.containsKey(ds) || dowOf(ds) == 0 -> p.hol; dowOf(ds) == 6 -> p.sat; else -> p.ink }

/* ============ 部品 ============ */
@Composable
fun T(text: String, size: TextUnit = 14.sp, color: Color = P.ink, bold: Boolean = false, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE, strike: Boolean = false, center: Boolean = false) =
    Text(text, modifier = modifier, maxLines = maxLines, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        style = TextStyle(fontSize = size, color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        textDecoration = if (strike) TextDecoration.LineThrough else null, lineHeight = size * 1.3,
        textAlign = if (center) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start))

@Composable
fun CardBox(modifier: Modifier = Modifier, outline: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    var m = modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(P.card)
    if (outline != null) m = m.border(2.dp, outline, RoundedCornerShape(20.dp))
    Column(m.padding(16.dp), content = content)
}

@Composable
fun SecH(title: String, sub: String = "", trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        T(title, 15.sp, bold = true, modifier = Modifier.weight(1f))
        if (sub.isNotBlank()) T(sub, 12.sp, P.sub)
        trailing?.invoke(this)
    }
}

@Composable
fun Avatar(m: Member?, size: Dp = 26.dp) {
    if (m == null) return
    val c = hexColor(m.color)
    Box(Modifier.size(size).clip(RoundedCornerShape(46, 54, 52, 48))
        .background(c), contentAlignment = Alignment.Center) {
        T(m.name.take(1), (size.value * 0.46f).sp, Color.White, true)
    }
}

@Composable
fun Chip(selected: Boolean, color: Color, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Row(Modifier.clip(CircleShape).background(if (selected) color.copy(alpha = 0.16f) else P.card2)
        .border(1.5.dp, if (selected) color else P.line, CircleShape).clickable(onClick = onClick).padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), content = content)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemberChips(q: Q, selected: Set<String>, onToggle: (String) -> Unit, withAll: Boolean = false, allSelected: Boolean = false, onAll: () -> Unit = {}) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (withAll) Chip(allSelected, P.rasp, onAll) { Spacer(Modifier.width(6.dp)); T("全員", 13.sp, bold = true) }
        q.members.forEach { m -> Chip(m.id in selected, hexColor(m.color), { onToggle(m.id) }) { Avatar(m, 22.dp); T(m.name, 13.sp, bold = true) } }
    }
}

@Composable
fun Btn(text: String, color: Color = P.indigo, modifier: Modifier = Modifier, small: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier, shape = RoundedCornerShape(14.dp),
        contentPadding = if (small) androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp) else ButtonDefaults.ContentPadding,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = onColor(color))) { T(text, if (small) 13.sp else 14.sp, onColor(color), true) }
}
@Composable
fun GhostBtn(text: String, modifier: Modifier = Modifier, small: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(14.dp),
        contentPadding = if (small) androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp) else ButtonDefaults.ContentPadding,
        colors = ButtonDefaults.buttonColors(containerColor = P.card2, contentColor = P.ink)) { T(text, 13.sp, P.ink, true) }
}

@Composable
fun Field(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier.fillMaxWidth(), singleLine: Boolean = true, minLines: Int = 1, placeholder: String = "") {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, modifier = modifier.padding(bottom = 8.dp),
        singleLine = singleLine, minLines = minLines, placeholder = { if (placeholder.isNotBlank()) Text(placeholder) },
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = P.teal, unfocusedBorderColor = P.line, focusedContainerColor = P.card2, unfocusedContainerColor = P.card2))
}

@Composable
fun Pill(text: String, bg: Color, fg: Color, m: Member? = null) {
    Row(Modifier.clip(CircleShape).background(bg).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        if (m != null) Avatar(m, 18.dp)
        T(text, 12.sp, fg, true)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LunchPills(li: List<LunchInfo>) {
    val b = li.filter { it.st == "b" }
    if (b.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
        b.forEach { x -> Pill("お弁当", P.tintR, P.rasp, x.m) }
    }
}

@Composable
fun AdviceLine(a: Advice, indent: Dp = 58.dp) {
    val (bg, fg) = when (a.lv) { 3 -> P.tintR to P.rasp; 2 -> P.tintO to P.orange; else -> P.tintT to P.teal }
    Row(Modifier.padding(start = indent, top = 2.dp, bottom = 2.dp).clip(RoundedCornerShape(12.dp)).background(bg).padding(horizontal = 10.dp, vertical = 6.dp)) {
        T("☂ " + a.text, 12.5.sp, fg, a.lv >= 3)
    }
}

/** 1件の予定(時刻・色の帯・件名・人) */
@Composable
fun EventRow(q: Q, e: Event, day: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 6.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(46.dp), horizontalAlignment = Alignment.End) {
            if (e.allDay || e.start.isBlank()) T("終日", 13.sp, bold = true)
            else { T(e.start, 13.5.sp, bold = true); if (e.end.isNotBlank()) T(e.end, 12.sp, P.sub) }
        }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.width(4.dp).height(34.dp).clip(RoundedCornerShape(4.dp)).background(Brush.verticalGradient(q.colors(e).map { hexColor(it) }.let { if (it.size == 1) it + it else it })))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T(e.title, 14.5.sp, bold = true, modifier = Modifier.weight(1f, fill = false))
                if (e.sample) { Spacer(Modifier.width(4.dp)); Pill("例", P.tintO, P.orange) }
                if (e.comments.isNotEmpty()) { Spacer(Modifier.width(4.dp)); Pill("💬${e.comments.size}", P.tintT, P.teal) }
            }
            if (e.location.isNotBlank()) T(e.location, 12.sp, P.sub)
        }
        Row { e.members.mapNotNull { q.member(it) }.forEach { Avatar(it, 22.dp) } }
    }
    q.advice(e, day).take(2).forEach { AdviceLine(it) }
}

@Composable
fun CheckRow(on: Boolean, text: String, sub: String, late: Boolean = false, onDelete: (() -> Unit)? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp, horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp).clip(RoundedCornerShape(8.dp)).background(if (on) P.teal else Color.Transparent)
            .border(2.dp, if (on) P.teal else P.sub.copy(alpha = 0.5f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            if (on) T("✓", 14.sp, Color.White, true)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            T(text, 14.5.sp, if (on) P.sub else P.ink, true, strike = on)
            if (sub.isNotBlank()) T(sub, 12.sp, if (late) P.rasp else P.sub, late)
        }
        // 終わったものは自分で消せる
        if (on && onDelete != null) Box(Modifier.clip(RoundedCornerShape(10.dp)).background(P.tintR).clickable(onClick = onDelete).padding(horizontal = 10.dp, vertical = 6.dp)) { T("削除", 12.sp, P.rasp, true) }
    }
}

fun deleteTodo(q: Q, x: Todo) { if (q.pt.any { it.id == x.id }) Repo.putPrivateTodo(x.copy(deleted = true)) else Repo.putTodo(x.copy(deleted = true)) }

/** 予定に付いた写真(data:image/jpeg;base64,... 形式。Web版と共通) */
@Composable
fun dataImage(src: String): ImageBitmap? = remember(src) {
    runCatching {
        val b = Base64.decode(src.substringAfter(","), Base64.DEFAULT)
        BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap()
    }.getOrNull()
}

/** 予定の帯(メンバーの色を左に縦線で並べる。複数人なら複数本) */
fun Modifier.stripes(colors: List<Color>, tint: Color): Modifier = this.background(tint).drawBehind {
    colors.forEachIndexed { i, c -> drawRect(c, topLeft = Offset(i * 3.dp.toPx(), 0f), size = Size(3.dp.toPx(), size.height)) }
}
