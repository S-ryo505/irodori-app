package jp.irodori.family

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate

/** データの保存・変更の窓口。端末内のJSONファイルに保存し、変更はFirebaseへも送る */
object Repo {
    lateinit var ctx: Context
        private set
    private var inited = false
    private val lock = Any()

    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true; isLenient = true }
    val data = MutableStateFlow(AppData())
    val settings = MutableStateFlow(Settings())
    val weather = MutableStateFlow<Weather?>(null)
    /** 自分だけのToDo(この端末だけ・Firebaseに送らない) */
    val privateTodos = MutableStateFlow<List<Todo>>(emptyList())
    val wxError = MutableStateFlow("")
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun q() = Q(data.value, settings.value, weather.value, privateTodos.value)

    fun init(c: Context) {
        synchronized(lock) {
            if (inited) return
            ctx = c.applicationContext
            inited = true
            load()
        }
        if (!settings.value.seeded) {
            if (data.value.events.isEmpty()) Samples.seed()
            setSettings { it.copy(seeded = true) }
        }
        if (data.value.fprefs.isEmpty()) {
            val st = settings.value
            data.value = data.value.copy(fprefs = listOf(FamilyPrefs(bentoIcon = st.bentoIcon, bentoBg = st.bentoBg, marks = upgradeMarks(st.marks), updatedAt = 1)))
            saveData()
        }
        ensureLessons()
        BgSync.schedule(ctx)
        BgSync.registerUnlock(ctx)
    }

    private fun file(n: String) = File(ctx.filesDir, n)
    private fun load() {
        runCatching { data.value = json.decodeFromString(AppData.serializer(), file("data.json").readText()) }
        runCatching { settings.value = json.decodeFromString(Settings.serializer(), file("settings.json").readText()) }
        runCatching { weather.value = json.decodeFromString(Weather.serializer(), file("weather.json").readText()) }
        runCatching { privateTodos.value = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(Todo.serializer()), file("private_todos.json").readText()) }
    }

    private var saveJob: Job? = null
    /** 少し待ってからまとめて保存し、ウィジェットも更新 */
    fun saveData() {
        saveJob?.cancel()
        saveJob = scope.launch { delay(200); flush(); Widgets.refresh(ctx); Reminders.schedule(ctx) }
    }
    fun flush() {
        val txt = json.encodeToString(AppData.serializer(), data.value)
        synchronized(lock) { file("data.json").writeText(txt) }
    }
    fun setSettings(refreshWidgets: Boolean = true, f: (Settings) -> Settings) {
        settings.value = f(settings.value)
        val txt = json.encodeToString(Settings.serializer(), settings.value)
        synchronized(lock) { file("settings.json").writeText(txt) }
        if (refreshWidgets) Widgets.refresh(ctx)
    }
    fun setWeather(w: Weather) {
        weather.value = w
        val txt = json.encodeToString(Weather.serializer(), w)
        synchronized(lock) { file("weather.json").writeText(txt) }
        Widgets.refresh(ctx)
    }

    private fun now() = System.currentTimeMillis()
    private fun <T> commit(kind: String, x: T, ser: KSerializer<T>, id: String, ts: Long, apply: (AppData) -> AppData) {
        data.value = apply(data.value)
        Sync.push(kind, id, json.encodeToString(ser, x), ts)
        saveData()
    }

    fun putEvent(e: Event) { val x = e.copy(updatedAt = now()); commit("events", x, Event.serializer(), x.id, x.updatedAt) { it.copy(events = it.events.upsert(x) { v -> v.id }) } }
    fun putLesson(e: Lesson) { val x = e.copy(updatedAt = now()); commit("lessons", x, Lesson.serializer(), x.id, x.updatedAt) { it.copy(lessons = it.lessons.upsert(x) { v -> v.id }) } }
    fun putMember(e: Member) { val x = e.copy(updatedAt = now()); commit("members", x, Member.serializer(), x.id, x.updatedAt) { it.copy(members = it.members.upsert(x) { v -> v.id }) } }
    fun putStock(e: Stock) { val x = e.copy(updatedAt = now()); commit("stocks", x, Stock.serializer(), x.id, x.updatedAt) { it.copy(stocks = it.stocks.upsert(x) { v -> v.id }) } }
    fun putLunch(e: Lunch) { val x = e.copy(updatedAt = now()); commit("lunch", x, Lunch.serializer(), x.id, x.updatedAt) { it.copy(lunch = it.lunch.upsert(x) { v -> v.id }) } }
    fun putTodo(e: Todo) { val x = e.copy(updatedAt = now()); commit("todos", x, Todo.serializer(), x.id, x.updatedAt) { it.copy(todos = it.todos.upsert(x) { v -> v.id }) } }
    fun putShop(e: ShopItem) { val x = e.copy(updatedAt = now()); commit("shop", x, ShopItem.serializer(), x.id, x.updatedAt) { it.copy(shop = it.shop.upsert(x) { v -> v.id }) } }

    fun putFamilyPrefs(e: FamilyPrefs) { val x = e.copy(updatedAt = now()); commit("fprefs", x, FamilyPrefs.serializer(), x.id, x.updatedAt) { it.copy(fprefs = it.fprefs.upsert(x) { v -> v.id }) } }
    fun putCList(e: CList) { val x = e.copy(updatedAt = now()); commit("clists", x, CList.serializer(), x.id, x.updatedAt) { it.copy(clists = it.clists.upsert(x) { v -> v.id }) } }
    fun putDay(e: DayMark) { val x = e.copy(updatedAt = now()); commit("days", x, DayMark.serializer(), x.id, x.updatedAt) { it.copy(days = it.days.upsert(x) { v -> v.id }) } }
    fun putWish(e: Wish) { val x = e.copy(updatedAt = now()); commit("wishes", x, Wish.serializer(), x.id, x.updatedAt) { it.copy(wishes = it.wishes.upsert(x) { v -> v.id }) } }
    fun deleteEvent(e: Event, auto: Boolean = false) = putEvent(e.copy(deleted = true, autoRemoved = auto, images = emptyList()))

    // ---------- チェック操作(アプリとウィジェット共通) ----------
    fun toggleShop(kind: String, id: String) {
        val t = q().trip()
        if (kind == "stock") {
            val x = data.value.stocks.firstOrNull { it.id == id } ?: return
            putStock(if (x.shopTrip == t.d) x.copy(lastBought = x.prevBought.ifBlank { x.lastBought }, shopTrip = "")
                     else x.copy(prevBought = x.lastBought, lastBought = todayStr(), shopTrip = t.d))
        } else {
            val x = data.value.shop.firstOrNull { it.id == id } ?: return
            putShop(x.copy(done = !x.done, trip = if (!x.done) t.d else ""))
        }
    }
    fun putPrivateTodo(t: Todo) {
        privateTodos.value = privateTodos.value.upsert(t.copy(updatedAt = now())) { it.id }
        val txt = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(Todo.serializer()), privateTodos.value)
        synchronized(lock) { file("private_todos.json").writeText(txt) }
        Widgets.refresh(ctx)
    }
    fun toggleTodo(id: String) {
        privateTodos.value.firstOrNull { it.id == id }?.let { x -> putPrivateTodo(x.copy(done = !x.done, doneAt = if (!x.done) now() else 0)); return }
        val x = data.value.todos.firstOrNull { it.id == id } ?: return
        putTodo(x.copy(done = !x.done, doneAt = if (!x.done) now() else 0))
    }

    // ---------- 習い事テンプレートから予定を作る(IDを決めておき、複数端末でも重複しない) ----------
    fun genLesson(l: Lesson, from: String, to: String): Int {
        if (l.deleted) return 0
        var n = 0
        var d = parseD(from)
        val end = parseD(to)
        while (!d.isAfter(end)) {
            val ds = d.toString()
            d = d.plusDays(1)
            if (dowOf(ds) !in l.days) continue
            if (l.skipHoliday && HOLI.containsKey(ds)) continue
            val id = l.id + "_" + ds
            val ex = data.value.events.firstOrNull { it.id == id }
            if (ex != null && !(ex.deleted && ex.autoRemoved)) continue
            putEvent(Event(id = id, date = ds, start = l.start, end = l.end, title = l.name, members = l.who(), location = l.location,
                outdoor = l.outdoor, cat = "lesson", seriesId = l.id, sample = l.sample, remind = l.remind, short = l.short, comments = ex?.comments ?: emptyList()))
            n++
        }
        return n
    }
    fun ensureLessons() {
        val f = todayStr(); val t = plusDays(f, 42)
        data.value.lessons.filter { !it.deleted && it.auto }.forEach { genLesson(it, f, t) }
    }
    /** テンプレートを変えたら、きょう以降で個別に編集していない分を合わせる */
    fun syncSeries(l: Lesson) {
        val t = todayStr()
        data.value.events.filter { it.seriesId == l.id && it.date >= t && !it.edited && !it.deleted }.forEach { e ->
            if (l.deleted || dowOf(e.date) !in l.days || (l.skipHoliday && HOLI.containsKey(e.date))) deleteEvent(e, auto = true)
            else putEvent(e.copy(title = l.name, start = l.start, end = l.end, location = l.location, outdoor = l.outdoor, members = l.who(), remind = l.remind, short = l.short))
        }
        if (!l.deleted && l.auto) genLesson(l, t, plusDays(t, 42))
    }

    // ---------- 共有(Firebase)との受け渡し ----------
    fun applyRemote(kind: String, js: String): Boolean = runCatching {
        fun <T> merge(list: List<T>, x: T, id: (T) -> String, ts: (T) -> Long): List<T>? {
            val cur = list.firstOrNull { id(it) == id(x) }
            return if (cur == null || ts(x) > ts(cur)) list.upsert(x, id) else null
        }
        val d = data.value
        val nd: AppData? = when (kind) {
            "events" -> json.decodeFromString(Event.serializer(), js).let { x -> merge(d.events, x, { it.id }, { it.updatedAt })?.let { d.copy(events = it) } }
            "lessons" -> json.decodeFromString(Lesson.serializer(), js).let { x -> merge(d.lessons, x, { it.id }, { it.updatedAt })?.let { d.copy(lessons = it) } }
            "members" -> json.decodeFromString(Member.serializer(), js).let { x -> merge(d.members, x, { it.id }, { it.updatedAt })?.let { d.copy(members = it) } }
            "stocks" -> json.decodeFromString(Stock.serializer(), js).let { x -> merge(d.stocks, x, { it.id }, { it.updatedAt })?.let { d.copy(stocks = it) } }
            "lunch" -> json.decodeFromString(Lunch.serializer(), js).let { x -> merge(d.lunch, x, { it.id }, { it.updatedAt })?.let { d.copy(lunch = it) } }
            "todos" -> json.decodeFromString(Todo.serializer(), js).let { x -> merge(d.todos, x, { it.id }, { it.updatedAt })?.let { d.copy(todos = it) } }
            "shop" -> json.decodeFromString(ShopItem.serializer(), js).let { x -> merge(d.shop, x, { it.id }, { it.updatedAt })?.let { d.copy(shop = it) } }
            "fprefs" -> json.decodeFromString(FamilyPrefs.serializer(), js).let { x -> merge(d.fprefs, x, { it.id }, { it.updatedAt })?.let { d.copy(fprefs = it) } }
            "clists" -> json.decodeFromString(CList.serializer(), js).let { x -> merge(d.clists, x, { it.id }, { it.updatedAt })?.let { d.copy(clists = it) } }
            "days" -> json.decodeFromString(DayMark.serializer(), js).let { x -> merge(d.days, x, { it.id }, { it.updatedAt })?.let { d.copy(days = it) } }
            "wishes" -> json.decodeFromString(Wish.serializer(), js).let { x -> merge(d.wishes, x, { it.id }, { it.updatedAt })?.let { d.copy(wishes = it) } }
            else -> null
        }
        if (nd != null) { data.value = nd; true } else false
    }.getOrDefault(false)

    data class Ent(val kind: String, val id: String, val json: String, val ts: Long, val sample: Boolean)
    fun entities(): List<Ent> {
        val d = data.value; val o = mutableListOf<Ent>()
        d.events.forEach { o += Ent("events", it.id, json.encodeToString(Event.serializer(), it), it.updatedAt, it.sample) }
        d.lessons.forEach { o += Ent("lessons", it.id, json.encodeToString(Lesson.serializer(), it), it.updatedAt, it.sample) }
        d.members.forEach { o += Ent("members", it.id, json.encodeToString(Member.serializer(), it), it.updatedAt, false) }
        d.stocks.forEach { o += Ent("stocks", it.id, json.encodeToString(Stock.serializer(), it), it.updatedAt, it.sample) }
        d.lunch.forEach { o += Ent("lunch", it.id, json.encodeToString(Lunch.serializer(), it), it.updatedAt, it.sample) }
        d.todos.forEach { o += Ent("todos", it.id, json.encodeToString(Todo.serializer(), it), it.updatedAt, it.sample) }
        d.shop.forEach { o += Ent("shop", it.id, json.encodeToString(ShopItem.serializer(), it), it.updatedAt, it.sample) }
        d.fprefs.forEach { o += Ent("fprefs", it.id, json.encodeToString(FamilyPrefs.serializer(), it), it.updatedAt, false) }
        d.clists.forEach { o += Ent("clists", it.id, json.encodeToString(CList.serializer(), it), it.updatedAt, false) }
        d.days.forEach { o += Ent("days", it.id, json.encodeToString(DayMark.serializer(), it), it.updatedAt, false) }
        d.wishes.forEach { o += Ent("wishes", it.id, json.encodeToString(Wish.serializer(), it), it.updatedAt, false) }
        return o
    }
    /** 家族の共有データが既にあるとき、この端末のサンプルは捨てる */
    fun dropLocalSamples(remoteIds: Set<String>) {
        val d = data.value
        data.value = d.copy(
            events = d.events.filter { !(it.sample && "events_${it.id}" !in remoteIds) },
            lessons = d.lessons.filter { !(it.sample && "lessons_${it.id}" !in remoteIds) },
            stocks = d.stocks.filter { !(it.sample && "stocks_${it.id}" !in remoteIds) },
            lunch = d.lunch.filter { !(it.sample && "lunch_${it.id}" !in remoteIds) },
            todos = d.todos.filter { !(it.sample && "todos_${it.id}" !in remoteIds) },
            shop = d.shop.filter { !(it.sample && "shop_${it.id}" !in remoteIds) },
        )
    }

    // ---------- まとめ操作 ----------
    fun deleteSamples() {
        val d = data.value
        d.events.filter { it.sample && !it.deleted }.forEach { deleteEvent(it) }
        d.lessons.filter { it.sample && !it.deleted }.forEach { putLesson(it.copy(deleted = true)) }
        d.stocks.filter { it.sample && !it.deleted }.forEach { putStock(it.copy(deleted = true)) }
        d.lunch.filter { it.sample && !it.deleted }.forEach { putLunch(it.copy(deleted = true)) }
        d.todos.filter { it.sample && !it.deleted }.forEach { putTodo(it.copy(deleted = true)) }
        d.shop.filter { it.sample && !it.deleted }.forEach { putShop(it.copy(deleted = true)) }
    }
    fun exportJson(): String = json.encodeToString(AppData.serializer(), data.value)
    /** Web版・アプリ版の「書き出し」JSONを読み込む */
    fun importAll(text: String) {
        val d = json.decodeFromString(AppData.serializer(), text)
        data.value = d
        entities().forEach { Sync.push(it.kind, it.id, it.json, it.ts) }
        saveData()
    }
}

/** 初回だけ入れるサンプル(設定 → データ → サンプルを消す で削除) */
object Samples {
    fun seed() {
        val t = LocalDate.now()
        fun d(n: Long) = t.plusDays(n).toString()
        fun nextDow(w: Int): String { for (i in 0..6) { val x = t.plusDays(i.toLong()); if (dow(x) == w) return x.toString() }; return d(5) }
        val ev = listOf(
            Event(uid(), d(0), start = "16:00", end = "18:00", title = "バレー部 練習", members = listOf("k1"), location = "学校 体育館", cat = "club", sample = true, updatedAt = 1),
            Event(uid(), d(0), start = "18:30", end = "19:30", title = "夕ごはん当番", members = listOf("papa"), memo = "カレーの予定", sample = true, updatedAt = 1),
            Event(uid(), d(1), start = "10:00", end = "11:00", title = "歯医者", members = listOf("mama"), location = "駅前デンタル", cat = "hosp", sample = true, updatedAt = 1),
            Event(uid(), d(1), start = "15:30", end = "17:00", title = "公園で遊ぶ", members = listOf("k3"), location = "中央公園", outdoor = true, cat = "out", sample = true, updatedAt = 1,
                comments = listOf(Comment(uid(), "mama", "帽子を持っていってね", System.currentTimeMillis()))),
            Event(uid(), d(2), allDay = true, title = "授業参観", members = listOf("k2", "mama"), location = "小学校", cat = "school", sample = true, updatedAt = 1),
            Event(uid(), nextDow(6), start = "09:00", end = "15:00", title = "家族でピクニック", location = "河川敷", outdoor = true, cat = "out", sample = true, updatedAt = 1),
        )
        val ls = listOf(
            Lesson("smpPiano", "ピアノ", "k2", days = listOf(3), start = "16:30", end = "17:15", location = "ピアノ教室", sample = true, updatedAt = 1),
            Lesson("smpCheer", "チアダンス", "k2", members = listOf("k2", "k3"), days = listOf(6), start = "10:00", end = "11:30", location = "市民体育館", skipHoliday = false, sample = true, updatedAt = 1),
        )
        val st = mutableMapOf<String, String>(); val menus = mutableMapOf<String, String>()
        val first = t.withDayOfMonth(1); var bdone = false
        val names = listOf("カレーライス", "鮭の塩焼き", "ソフト麺", "ちくわ磯辺揚げ", "肉じゃが")
        for (i in 0 until t.lengthOfMonth()) {
            val x = first.plusDays(i.toLong()); val ds = x.toString(); val w = dow(x)
            if (w == 0 || w == 6) continue
            if (HOLI.containsKey(ds)) { st[ds] = "off"; continue }
            if (!bdone && x.isAfter(t) && w == 5) { st[ds] = "b"; bdone = true } else { st[ds] = "k"; menus[ds] = names[i % 5] }
        }
        Repo.data.value = Repo.data.value.copy(
            events = ev, lessons = ls,
            lunch = listOf(Lunch("k1", st, menus, true, 1), Lunch("k2", st, menus, true, 1)),
            stocks = listOf(Stock(uid(), "poro", days = 30, lastBought = d(-24), lead = 5, memo = "メモの「poro」をサンプルで登録", sample = true, updatedAt = 1)),
            todos = listOf(Todo(uid(), "上履きを洗う", "k2", d(4), createdAt = System.currentTimeMillis(), sample = true, updatedAt = 1)),
            shop = listOf(ShopItem(uid(), "牛乳", by = "mama", sample = true, updatedAt = 1)),
        )
        Repo.saveData()
    }
}
