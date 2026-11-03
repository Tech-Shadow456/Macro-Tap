package com.macrotap.app

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

data class Profile(
    var pkg: String, var label: String,
    var notif: Boolean = true, var calls: Boolean = true, var hud: Boolean = true,
    var boost: Boolean = true, var brightness: Int = 0, var breakMin: Int = 0,
    var macroIds: MutableList<Long> = mutableListOf()
)

object Profiles {
    private fun prefs(c: Context) = c.getSharedPreferences("profiles", Context.MODE_PRIVATE)

    fun raw(c: Context): String = prefs(c).getString("list", "[]") ?: "[]"
    fun enabled(c: Context): Boolean = prefs(c).getBoolean("auto", true)
    fun setEnabled(c: Context, v: Boolean) = prefs(c).edit().putBoolean("auto", v).apply()

    private fun parse(s: String): MutableList<Profile> {
        val arr = JSONArray(s)
        val out = mutableListOf<Profile>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val ids = mutableListOf<Long>()
            val ia = o.optJSONArray("macros")
            if (ia != null) for (j in 0 until ia.length()) ids.add(ia.getLong(j))
            out.add(
                Profile(
                    o.getString("pkg"), o.getString("label"), o.optBoolean("notif", true),
                    o.optBoolean("calls", true), o.optBoolean("hud", true), o.optBoolean("boost", true),
                    o.optInt("brightness", 0), o.optInt("breakMin", 0), ids
                )
            )
        }
        return out
    }

    fun setRaw(c: Context, s: String): Boolean {
        return try {
            parse(s)
            prefs(c).edit().putString("list", s).apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun all(c: Context): MutableList<Profile> = try {
        parse(raw(c))
    } catch (e: Exception) {
        mutableListOf()
    }

    private fun saveAll(c: Context, list: List<Profile>) {
        val arr = JSONArray()
        for (p in list) {
            val o = JSONObject()
            o.put("pkg", p.pkg); o.put("label", p.label); o.put("notif", p.notif)
            o.put("calls", p.calls); o.put("hud", p.hud); o.put("boost", p.boost)
            o.put("brightness", p.brightness); o.put("breakMin", p.breakMin)
            val ia = JSONArray()
            for (id in p.macroIds) ia.put(id)
            o.put("macros", ia)
            arr.put(o)
        }
        prefs(c).edit().putString("list", arr.toString()).apply()
    }

    fun put(c: Context, p: Profile) {
        val l = all(c)
        val i = l.indexOfFirst { it.pkg == p.pkg }
        if (i >= 0) l[i] = p else l.add(p)
        saveAll(c, l)
    }

    fun delete(c: Context, pkg: String) = saveAll(c, all(c).filter { it.pkg != pkg })

    // ---------- automatic switching ----------

    private val h = Handler(Looper.getMainLooper())
    private var ctx: Context? = null
    private var active: Profile? = null
    private var startTime = 0L
    private var wasNotif = false
    private var wasCalls = false
    private var wasHud = false
    private var wasAuto = false
    private var wasBright = -1
    private var wasShown: Set<Long> = emptySet()

    private val leave = Runnable { ctx?.let { deactivate(it) } }

    private val remind = object : Runnable {
        override fun run() {
            val c = ctx ?: return
            val p = active ?: return
            if (p.breakMin <= 0) return
            val mins = (System.currentTimeMillis() - startTime) / 60000L
            Toast.makeText(c, "You have played $mins min. Take a short break!", Toast.LENGTH_LONG).show()
            Alerts.buzz(c)
            h.postDelayed(this, p.breakMin * 60_000L)
        }
    }

    fun onForeground(c: Context, pkg: String) {
        val app = c.applicationContext
        ctx = app
        if (!enabled(app)) return
        if (pkg == app.packageName || pkg == "com.android.systemui" ||
            pkg.contains("inputmethod") || pkg.contains("keyboard")
        ) return
        if (pkg == active?.pkg) {
            h.removeCallbacks(leave)
            return
        }
        val p = all(app).firstOrNull { it.pkg == pkg }
        if (p != null) {
            h.removeCallbacks(leave)
            if (active != null) deactivate(app)
            activate(app, p)
        } else if (active != null) {
            h.removeCallbacks(leave)
            h.postDelayed(leave, 4000)
        }
    }

    private fun startSvc(c: Context, cls: Class<*>) {
        try {
            c.startForegroundService(Intent(c, cls))
        } catch (e: Exception) {
        }
    }

    private fun floating(c: Context, action: String, id: Long) {
        if (!Settings.canDrawOverlays(c)) return
        try {
            c.startForegroundService(
                Intent(c, FloatingService::class.java).setAction(action).putExtra("id", id)
            )
        } catch (e: Exception) {
        }
    }

    private fun activate(c: Context, p: Profile) {
        active = p
        startTime = System.currentTimeMillis()
        wasNotif = Game.notifBlocked(c)
        wasCalls = Game.callsBlocked(c)
        wasHud = HudService.running
        wasAuto = Game.isAuto(c)
        wasShown = HashSet(FloatingService.shown)
        wasBright = if (Settings.System.canWrite(c)) Game.getBrightness(c) else -1

        if (p.notif) Game.setNotifBlocked(c, true)
        if (p.calls && Game.hasCallRole(c)) Game.setCallsBlocked(c, true)
        if (p.hud && Settings.canDrawOverlays(c)) startSvc(c, HudService::class.java)
        if (p.brightness > 0 && wasBright >= 0) Game.setBrightness(c, p.brightness * 255 / 100)
        if (p.boost) Boost.run(c) { }
        for (id in p.macroIds) floating(c, FloatingService.SHOW, id)
        if (p.breakMin > 0) h.postDelayed(remind, p.breakMin * 60_000L)
        Toast.makeText(c, "Game profile on: ${p.label}", Toast.LENGTH_SHORT).show()
    }

    private fun deactivate(c: Context) {
        val p = active ?: return
        active = null
        h.removeCallbacks(remind)
        Game.setNotifBlocked(c, wasNotif)
        Game.setCallsBlocked(c, wasCalls)
        if (!wasHud) {
            try {
                c.stopService(Intent(c, HudService::class.java))
            } catch (e: Exception) {
            }
        }
        if (wasBright >= 0 && p.brightness > 0) {
            Game.setBrightness(c, wasBright)
            if (wasAuto) Game.setAuto(c, true)
        }
        for (id in p.macroIds) {
            if (!wasShown.contains(id) && FloatingService.shown.contains(id)) {
                floating(c, FloatingService.HIDE, id)
            }
        }
        Toast.makeText(c, "Game profile off", Toast.LENGTH_SHORT).show()
    }
}
