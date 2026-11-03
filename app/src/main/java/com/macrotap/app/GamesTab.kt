package com.macrotap.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.text.InputType
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

data class PinnedApp(val pkg: String, val label: String, val vault: Boolean)

object PinnedApps {
    private fun prefs(c: Context) = c.getSharedPreferences("pinned", Context.MODE_PRIVATE)

    fun all(c: Context): MutableList<PinnedApp> {
        val arr = JSONArray(prefs(c).getString("list", "[]"))
        val out = mutableListOf<PinnedApp>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(PinnedApp(o.getString("pkg"), o.getString("label"), o.optBoolean("vault", false)))
        }
        return out
    }

    private fun save(c: Context, list: List<PinnedApp>) {
        val arr = JSONArray()
        for (p in list) {
            val o = JSONObject()
            o.put("pkg", p.pkg); o.put("label", p.label); o.put("vault", p.vault)
            arr.put(o)
        }
        prefs(c).edit().putString("list", arr.toString()).apply()
    }

    fun add(c: Context, p: PinnedApp) {
        val l = all(c).filter { it.pkg != p.pkg }.toMutableList()
        l.add(p)
        save(c, l)
    }

    fun remove(c: Context, pkg: String) = save(c, all(c).filter { it.pkg != pkg })

    private fun hash(pin: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
        return d.joinToString("") { "%02x".format(it) }
    }

    fun hasPin(c: Context): Boolean = prefs(c).getString("pin", null) != null
    fun setPin(c: Context, pin: String) = prefs(c).edit().putString("pin", hash(pin)).apply()
    fun checkPin(c: Context, pin: String): Boolean = prefs(c).getString("pin", "") == hash(pin)

    var unlockedThisSession = false
}

object GamesScreen {
    private fun msg(c: Context, s: String) = Toast.makeText(c, s, Toast.LENGTH_LONG).show()

    fun tab(a: MainActivity, col: LinearLayout) {
        with(a) {
            text(col, "Games", 28f, C.TEXT, true)
            text(col, "Launch your games straight from Drivex Play", 14f, C.SUB)
        }
        publicCard(a, col)
        vaultCard(a, col)
    }

    private fun launch(a: Activity, pkg: String) {
        val i = a.packageManager.getLaunchIntentForPackage(pkg)
        if (i != null) a.startActivity(i) else msg(a, "That app could not be opened")
    }

    private fun pickApp(a: MainActivity, vault: Boolean) {
        val pm = a.packageManager
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(q, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
            .filter { it.first != a.packageName }
            .sortedBy { it.second.lowercase() }
        AlertDialog.Builder(a)
            .setTitle(if (vault) "Add to Vault" else "Pin a game")
            .setItems(apps.map { it.second }.toTypedArray()) { _, w ->
                PinnedApps.add(a, PinnedApp(apps[w].first, apps[w].second, vault))
                a.render()
            }
            .show()
    }

    private fun publicCard(a: MainActivity, col: LinearLayout) {
        with(a) {
            val c = card(col)
            text(c, "Pinned games", 18f, C.TEXT, true)
            val list = PinnedApps.all(a).filter { !it.vault }
            if (list.isEmpty()) text(c, "No games pinned yet.", 13f, C.SUB, false, 8)
            for (p in list) {
                val r = row(c)
                button(r, p.label, true, true) { launch(a, p.pkg) }
                button(r, "✕", false, true) { PinnedApps.remove(a, p.pkg); render() }
            }
            button(c, "+ Pin a game") {
                val count = PinnedApps.all(a).size
                if (count >= 5 && !License.isPro(a)) {
                    msg(a, "Free plan: 5 pinned apps total. Pro allows unlimited.")
                } else {
                    pickApp(a, false)
                }
            }
            text(
                c,
                "Opening a pinned game also turns on its game profile automatically, if you made one in the More tab.",
                12f, C.SUB, false, 8
            )
        }
    }

    private fun vaultCard(a: MainActivity, col: LinearLayout) {
        with(a) {
            val c = card(col)
            text(c, "Vault", 18f, C.TEXT, true)
            text(
                c,
                "A private, PIN-locked list inside Drivex Play. This does not hide the app's icon from your home screen or phone Settings — it only gives you a discreet, locked shortcut here.",
                12f, C.SUB, false, 4
            )
            if (!PinnedApps.unlockedThisSession) {
                button(c, "Unlock Vault") { unlock(a) }
                return
            }
            val list = PinnedApps.all(a).filter { it.vault }
            if (list.isEmpty()) text(c, "Vault is empty.", 13f, C.SUB, false, 8)
            for (p in list) {
                val r = row(c)
                button(r, p.label, true, true) { launch(a, p.pkg) }
                button(r, "✕", false, true) { PinnedApps.remove(a, p.pkg); render() }
            }
            button(c, "+ Add to Vault") {
                val count = PinnedApps.all(a).size
                if (count >= 5 && !License.isPro(a)) {
                    msg(a, "Free plan: 5 pinned apps total. Pro allows unlimited.")
                } else {
                    pickApp(a, true)
                }
            }
            button(c, "Lock Vault", false) {
                PinnedApps.unlockedThisSession = false
                render()
            }
        }
    }

    private fun unlock(a: MainActivity) {
        val e = EditText(a)
        e.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        e.hint = if (PinnedApps.hasPin(a)) "Enter your PIN" else "Set a 4-digit PIN"
        AlertDialog.Builder(a)
            .setTitle("Vault")
            .setView(e)
            .setPositiveButton("OK") { _, _ ->
                val v = e.text.toString()
                if (v.length < 4) {
                    msg(a, "Use at least 4 digits")
                    return@setPositiveButton
                }
                if (!PinnedApps.hasPin(a)) {
                    PinnedApps.setPin(a, v)
                    PinnedApps.unlockedThisSession = true
                    msg(a, "PIN set. Vault unlocked.")
                    a.render()
                } else if (PinnedApps.checkPin(a, v)) {
                    PinnedApps.unlockedThisSession = true
                    a.render()
                } else {
                    msg(a, "Wrong PIN")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
