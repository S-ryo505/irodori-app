package jp.irodori.family

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import kotlin.coroutines.resume

/* ======================= 家族共有(Firebase Firestore) ======================= */
object Sync {
    val status = MutableStateFlow("未接続")
    private var col: CollectionReference? = null
    private var reg: ListenerRegistration? = null
    @Volatile private var on = false

    /** google-services.json の中身、または Web版の firebaseConfig のどちらでも読む */
    fun parseConfig(t: String): FirebaseOptions? {
        runCatching {
            val j = JSONObject(t.substring(t.indexOf('{'), t.lastIndexOf('}') + 1))
            if (j.has("project_info")) {
                val pid = j.getJSONObject("project_info").getString("project_id")
                val clients = j.getJSONArray("client")
                var c = clients.getJSONObject(0)
                for (i in 0 until clients.length()) {
                    val x = clients.getJSONObject(i)
                    if (x.optJSONObject("client_info")?.optJSONObject("android_client_info")?.optString("package_name") == "jp.irodori.family") c = x
                }
                val appId = c.getJSONObject("client_info").getString("mobilesdk_app_id")
                val key = c.getJSONArray("api_key").getJSONObject(0).getString("current_key")
                return FirebaseOptions.Builder().setProjectId(pid).setApplicationId(appId).setApiKey(key).build()
            }
        }
        fun f(k: String) = Regex(k + "\"?\\s*:\\s*[\"']([^\"']+)[\"']").find(t)?.groupValues?.get(1)
        val key = f("apiKey"); val app = f("appId"); val pid = f("projectId")
        return if (key != null && app != null && pid != null) FirebaseOptions.Builder().setApiKey(key).setApplicationId(app).setProjectId(pid).build() else null
    }

    fun start(ctx: Context) {
        val s = Repo.settings.value
        reg?.remove(); reg = null; on = false; col = null
        if (s.fbConfig.isBlank() || s.familyId.isBlank()) { status.value = "未接続"; return }
        val opts = parseConfig(s.fbConfig) ?: run { status.value = "設定を読めません。貼り付けた内容を確認してください"; return }
        status.value = "接続中…"
        Repo.scope.launch(Dispatchers.Main) {
            try {
                var app = FirebaseApp.getApps(ctx).firstOrNull { it.name == "irodori" }
                if (app != null && (app.options.apiKey != opts.apiKey || app.options.projectId != opts.projectId)) { app.delete(); app = null }
                if (app == null) app = FirebaseApp.initializeApp(ctx, opts, "irodori")
                FirebaseAuth.getInstance(app).signInAnonymously().await()
                val c = FirebaseFirestore.getInstance(app).collection("families").document(s.familyId).collection("items")
                col = c
                var first = true
                reg = c.addSnapshotListener { snap, err ->
                    if (err != null) { status.value = "エラー: ${err.message}"; return@addSnapshotListener }
                    if (snap == null) return@addSnapshotListener
                    var changed = false
                    val remote = HashMap<String, Long>()
                    for (dc in snap.documentChanges) {
                        val doc = dc.document
                        remote[doc.id] = doc.getLong("updatedAt") ?: 0L
                        val kind = doc.getString("kind") ?: continue
                        val js = doc.getString("json") ?: continue
                        if (Repo.applyRemote(kind, js)) changed = true
                    }
                    if (first) {
                        first = false; on = true
                        if (!snap.isEmpty) Repo.dropLocalSamples(remote.keys)
                        Repo.entities().forEach { e ->
                            val r = remote["${e.kind}_${e.id}"]
                            if (r == null || e.ts > r) push(e.kind, e.id, e.json, e.ts)
                        }
                        status.value = "つながっています"; changed = true
                    }
                    if (changed) Repo.saveData()
                }
            } catch (e: Exception) {
                status.value = "エラー: ${e.message}"; on = false
            }
        }
    }

    fun push(kind: String, id: String, json: String, ts: Long) {
        if (!on) return
        val c = col ?: return
        runCatching {
            c.document("${kind}_$id").set(mapOf("kind" to kind, "json" to json, "updatedAt" to ts))
                .addOnFailureListener { status.value = "書き込みエラー: ${it.message}" }
        }
    }
}

/* ======================= 天気(Open-Meteo・無料/キー不要) ======================= */
object Wx {
    fun http(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000; c.readTimeout = 15000
        c.setRequestProperty("User-Agent", "irodori-family-app")
        try {
            if (c.responseCode !in 200..299) throw Exception("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().readText()
        } finally { c.disconnect() }
    }

    @SuppressLint("MissingPermission")
    private suspend fun location(ctx: Context): Location? {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
        val last = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }.maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < 3 * 3600_000L) return last
        if (Build.VERSION.SDK_INT >= 30 && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            val cur = withTimeoutOrNull(10_000) {
                suspendCancellableCoroutine<Location?> { cont ->
                    lm.getCurrentLocation(LocationManager.NETWORK_PROVIDER, null, ctx.mainExecutor) { loc -> if (cont.isActive) cont.resume(loc) }
                }
            }
            if (cur != null) return cur
        }
        return last
    }

    @Suppress("DEPRECATION")
    private fun placeName(ctx: Context, lat: Double, lon: Double): String = runCatching {
        val a = Geocoder(ctx, Locale.JAPAN).getFromLocation(lat, lon, 1)?.firstOrNull()
        a?.locality ?: a?.subAdminArea ?: a?.adminArea ?: "現在地"
    }.getOrDefault("現在地")

    suspend fun refresh(ctx: Context, force: Boolean) {
        val cur = Repo.weather.value
        if (!force && cur != null && System.currentTimeMillis() - cur.at < 30 * 60_000L) return
        withContext(Dispatchers.IO) {
            try {
                val s = Repo.settings.value
                var lat = 0.0; var lon = 0.0; var place = ""; var ok = false
                if (s.useGps) location(ctx)?.let { lat = Math.round(it.latitude * 1000) / 1000.0; lon = Math.round(it.longitude * 1000) / 1000.0; place = placeName(ctx, lat, lon); ok = true }
                if (!ok && s.locName.isNotBlank()) { lat = s.locLat; lon = s.locLon; place = s.locName; ok = true }
                if (!ok) { Repo.wxError.value = "位置情報が使えません。設定 → 天気 で位置情報を許可するか、地名を選んでください"; return@withContext }
                val txt = http("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code" +
                    "&hourly=temperature_2m,precipitation_probability,weather_code,wind_speed_10m" +
                    "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,wind_speed_10m_max" +
                    "&timezone=Asia%2FTokyo&forecast_days=8&wind_speed_unit=ms")
                val j = JSONObject(txt)
                val dl = j.getJSONObject("daily"); val hr = j.getJSONObject("hourly"); val cu = j.optJSONObject("current")
                fun JSONArray.str() = List(length()) { optString(it) }
                fun JSONArray.dbl() = List(length()) { optDouble(it, 0.0) }
                fun JSONArray.int() = List(length()) { optInt(it, 0) }
                Repo.setWeather(Weather(
                    at = System.currentTimeMillis(), place = place,
                    curTemp = cu?.optDouble("temperature_2m")?.takeIf { !it.isNaN() },
                    curCode = cu?.let { if (it.has("weather_code")) it.optInt("weather_code") else null },
                    dTime = dl.getJSONArray("time").str(), dCode = dl.getJSONArray("weather_code").int(),
                    dMax = dl.getJSONArray("temperature_2m_max").dbl(), dMin = dl.getJSONArray("temperature_2m_min").dbl(),
                    dPop = dl.getJSONArray("precipitation_probability_max").int(), dWind = dl.getJSONArray("wind_speed_10m_max").dbl(),
                    hTime = hr.getJSONArray("time").str(), hTemp = hr.getJSONArray("temperature_2m").dbl(),
                    hPop = hr.getJSONArray("precipitation_probability").int(), hCode = hr.getJSONArray("weather_code").int(),
                    hWind = hr.getJSONArray("wind_speed_10m").dbl(),
                ))
                Repo.wxError.value = ""
            } catch (e: Exception) {
                Repo.wxError.value = "天気を取得できませんでした（${e.message}）"
            }
        }
    }

    data class Place(val name: String, val area: String, val lat: Double, val lon: Double)
    suspend fun search(q: String): List<Place> = withContext(Dispatchers.IO) {
        runCatching {
            val j = JSONObject(http("https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(q, "UTF-8")}&count=6&language=ja"))
            val a = j.optJSONArray("results") ?: return@runCatching emptyList<Place>()
            List(a.length()) { i -> val o = a.getJSONObject(i); Place(o.optString("name"), o.optString("admin1"), o.optDouble("latitude"), o.optDouble("longitude")) }
        }.getOrDefault(emptyList())
    }
}

/* ======================= 通知(朝:きょう / 夜:あした) ======================= */
object Notifier {
    private const val CH = "daily"
    fun channel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "きょう・あしたの予定", NotificationManager.IMPORTANCE_DEFAULT))
    }
    fun canPost(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun show(ctx: Context, title: String, body: String, id: Int) {
        channel(ctx)
        if (!canPost(ctx)) return
        val pi = PendingIntent.getActivity(ctx, id, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CH)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(0xFFE98D4D.toInt())
            .setContentTitle(title)
            .setContentText(body.lineSequence().firstOrNull() ?: "")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(id, n)
    }

    private fun nextAt(hm: String): Long {
        val p = hm.split(":"); val h = p.getOrNull(0)?.toIntOrNull() ?: 7; val m = p.getOrNull(1)?.toIntOrNull() ?: 0
        val now = LocalDateTime.now()
        var t = now.withHour(h.coerceIn(0, 23)).withMinute(m.coerceIn(0, 59)).withSecond(0).withNano(0)
        if (!t.isAfter(now)) t = t.plusDays(1)
        return t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    /** 次の朝・夜の通知を予約(届いたら次の日の分を予約し直す) */
    fun schedule(ctx: Context) {
        val s = Repo.settings.value
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        for ((code, kind, hm) in listOf(Triple(1, "today", s.notifyToday), Triple(2, "tomorrow", s.notifyTomorrow))) {
            val pi = PendingIntent.getBroadcast(ctx, code, Intent(ctx, AlarmReceiver::class.java).putExtra("kind", kind),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            am.cancel(pi)
            if (!s.notifyOn || hm.isBlank()) continue
            val at = nextAt(hm)
            val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
            try {
                if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } catch (e: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }
    }

    fun fire(ctx: Context, kind: String) {
        val q = Repo.q()
        val tomorrow = kind == "tomorrow"
        val day = if (tomorrow) plusDays(todayStr(), 1) else todayStr()
        show(ctx, if (tomorrow) "あしたの予定 ${md(day)}" else "きょうの予定 ${md(day)}", q.summary(day), if (tomorrow) 102 else 101)
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Repo.init(ctx)
        val pending = goAsync()
        Repo.scope.launch {
            try {
                Wx.refresh(ctx, false)
                Notifier.fire(ctx, intent.getStringExtra("kind") ?: "today")
                Notifier.schedule(ctx)
                Widgets.refreshNow(ctx)
            } finally { pending.finish() }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Repo.init(ctx)
        Notifier.schedule(ctx)
    }
}

/* ======================= 読み上げ(端末の日本語音声) ======================= */
object Speaker : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null
    val speaking = MutableStateFlow(false)
    val lastText = MutableStateFlow("")
    var onSpeak: ((Boolean) -> Unit)? = null

    fun init(ctx: Context) { if (tts == null) tts = TextToSpeech(ctx.applicationContext, this) }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        tts?.setLanguage(Locale.JAPANESE)
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { speaking.value = true; onSpeak?.invoke(true) }
            override fun onDone(utteranceId: String?) { speaking.value = false; onSpeak?.invoke(false) }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { speaking.value = false; onSpeak?.invoke(false) }
        })
        ready = true
        pending?.let { speak(it) }; pending = null
    }

    fun speak(text: String) {
        lastText.value = text
        val t = tts ?: return
        if (!ready) { pending = text; return }
        val s = Repo.settings.value
        t.setSpeechRate(s.voiceRate); t.setPitch(s.voicePitch)
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "irodori")
    }
    fun stop() { tts?.stop(); speaking.value = false }
}
