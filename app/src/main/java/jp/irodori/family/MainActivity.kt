package jp.irodori.family

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    val speakReq = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(this)
        Speaker.init(this)
        Notifier.channel(this)
        Notifier.schedule(this)
        enableEdgeToEdge()
        handle(intent)
        setContent { IrodoriTheme { App(this) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        Repo.ensureLessons()
        lifecycleScope.launch { Wx.refresh(this@MainActivity, false) }
    }

    private fun handle(i: Intent?) { i?.getStringExtra("speak")?.let { speakReq.value = it } }
}

@Composable
fun App(act: MainActivity) {
    val d by Repo.data.collectAsState()
    val s by Repo.settings.collectAsState()
    val w by Repo.weather.collectAsState()
    val q = Q(d, s, w)
    var tab by rememberSaveable { mutableStateOf(if (s.startView == "home") "home" else "cal") }
    var mode by rememberSaveable { mutableStateOf(if (s.startView in listOf("month", "week", "day")) s.startView else "month") }
    var cursor by rememberSaveable { mutableStateOf(initCursor(mode)) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    val speakReq by act.speakReq.collectAsState()

    LaunchedEffect(speakReq) {
        speakReq?.let { tab = "home"; Speaker.speak(Repo.q().let { qq -> qq.talk(qq.talkMember(), it == "tomorrow") }); act.speakReq.value = null }
    }
    val open: (Sheet) -> Unit = { sh ->
        if (sh is Sheet.GoDay) { cursor = sh.ds; mode = "day"; tab = "cal"; sheet = null } else sheet = sh
    }

    Scaffold(
        containerColor = P.paper,
        topBar = {
            Row(Modifier.fillMaxWidth().background(P.paper).statusBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (tab == "cal") {
                    IconButton(onClick = { cursor = moveCursor(mode, cursor, -1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "前へ", tint = P.sub) }
                    val c = parseD(cursor)
                    Row(Modifier.clickable { cursor = initCursor(mode) }.padding(4.dp), verticalAlignment = Alignment.Bottom) {
                        T("${c.year}年", 17.sp, bold = true); T("${c.monthValue}", 24.sp, bold = true); T("月", 17.sp, bold = true)
                    }
                    IconButton(onClick = { cursor = moveCursor(mode, cursor, 1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "次へ", tint = P.sub) }
                } else {
                    androidx.compose.foundation.Image(painterResource(R.drawable.mascot), null, Modifier.size(30.dp))
                    Spacer(Modifier.width(6.dp))
                    T("いろどり", 19.sp, bold = true); Spacer(Modifier.width(8.dp))
                    T(mapOf("home" to "きょう", "imp" to "とりこみ", "list" to "リスト", "set" to "設定")[tab] ?: "", 13.sp, P.sub)
                }
                Spacer(Modifier.weight(1f))
                val v = q.member(q.viewer)
                Row(Modifier.clip(CircleShape).background(P.card).clickable { sheet = Sheet.Viewer }.padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (v != null) { Avatar(v, 28.dp); Spacer(Modifier.width(6.dp)); T(v.name, 13.sp, bold = true) }
                    else { q.members.take(3).forEach { Avatar(it, 24.dp) }; Spacer(Modifier.width(6.dp)); T("全員", 13.sp, bold = true) }
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = P.card) {
                listOf(Triple("cal", "カレンダー", Icons.Filled.DateRange), Triple("home", "きょう", Icons.Filled.Home), Triple("imp", "とりこみ", Icons.Filled.Create),
                    Triple("list", "リスト", Icons.Filled.ShoppingCart), Triple("set", "設定", Icons.Filled.Settings)).forEach { (k, n, ic) ->
                    NavigationBarItem(selected = tab == k, onClick = { tab = k }, icon = { Icon(ic, n) }, label = { Text(n, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = P.rasp, selectedTextColor = P.rasp, indicatorColor = P.tintR, unselectedIconColor = P.sub, unselectedTextColor = P.sub))
                }
            }
        },
        floatingActionButton = {
            if (tab == "cal" || tab == "home") FloatingActionButton(onClick = { sheet = Sheet.Edit(newEvent(q, if (tab == "cal") cursor else todayStr()), true) },
                containerColor = P.orange, contentColor = Color.White, shape = CircleShape) { Icon(Icons.Filled.Add, "予定を追加") }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                "cal" -> CalendarScreen(q, mode, { mode = it }, cursor, { cursor = it }, open)
                "home" -> TodayScreen(q, open)
                "imp" -> ImportScreen(q, open)
                "list" -> ListScreen(q, open)
                else -> SettingsScreen(q)
            }
        }
    }
    sheet?.let { sh -> SheetHost(q, sh, close = { sheet = null }, open = open) }
}
