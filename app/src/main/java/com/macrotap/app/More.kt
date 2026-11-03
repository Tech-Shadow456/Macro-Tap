package com.macrotap.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.text.InputType
import android.widget.*

object More {
    private fun msg(c: Context, s: String) = Toast.makeText(c, s, Toast.LENGTH_LONG).show()

    fun tab(a: MainActivity, col: LinearLayout) {
        with(a) {
            text(col, "More", 28f, C.TEXT, true)
            text(col, "Profiles, looks, safety and backup", 14f, C.SUB)
        }
        proCard(a, col)
        profilesCard(a, col)
        lookCard(a, col)
        safetyCard(a, col)
        layoutsCard(a, col)
        backupCard(a, col)
        with(a) {
            text(col, "Drivex Play collects nothing and has no internet permission.", 12f, C.SUB, false, 16)
        }
    }

    // ---------- Pro ----------
    private fun proCard(a: MainActivity, col: LinearLayout) {
        with(a) {
            val c = card(col)
            val on = License.isPro(a)
            text(c, if (on) "Pro unlocked ✔" else "Drivex Play Pro", 18f, if (on) C.ACCENT else C.TEXT, true)
            text(c, "Your phone ID", 12f, C.SUB, false, 8)
            text(c, License.deviceId(a), 22f, C.ACCENT, true)
            button(c, "Copy ID", false) {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("id", License.deviceId(a)))
                msg(a, "ID copied. Send it to the developer after paying.")
            }
            if (!on) {
                val key = EditText(a)
                key.hint = "Paste license key"
                key.setTextColor(C.TEXT)
                key.setHintTextColor(C.SUB)
                c.addView(key, llp(top = 8))
                button(c, "Activate Pro") {
                    if (License.activate(a, key.text.toString())) {
                        msg(a, "Pro unlocked!")
                        Themes.apply(a)
                        recreate()
                    } else {
                        msg(a, "That key does not match this phone")
                    }
                }
                text(
                    c,
                    "Pro adds more themes, animated wallpapers, icon styles, unlimited game profiles and layouts. One key works on one phone.",
                    12f, C.SUB, false, 8
                )
            }
        }
    }

    // ---------- Game profiles ----------
    private fun profilesCard(a: MainActivity, col: LinearLayout) {
        with(a) {
            val c = card(col)
            text(c, "Game profiles", 18f, C.TEXT, true)
            text(
                c,
                "Open a game and its settings apply automatically. When you leave, everything goes back. Needs Accessibility on.",
                12f, C.SUB, false, 4
            )
            switchRow(c, "Auto profiles", "", Profiles.enabled(a)) { Profiles.setEnabled(a, it) }
            val list = Profiles.all(a)
            if (list.isEmpty()) text(c, "No games added yet.", 13f, C.SUB, false, 8)
            for (p in list) {
                text(c, p.label, 16f, C.TEXT, true, 12)
                val r = row(c)
                button(r, "Edit", true, true) { editProfile(a, p, false) }
                button(r, "Delete", false, true) {
                    Profiles.delete(a, p.pkg)
                    render()
                }
            }
            button(c, "+ Add game") {
                if (list.isNotEmpty() && !License.isPro(a)) {
                    msg(a, "Free plan: 1 game profile. Pro allows unlimited.")
                } else {
                    pickGame(a)
                }
            }
        }
    }

    private fun pickGame(a: MainActivity) {
        val pm = a.packageManager
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(q, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
            .filter { it.first != a.packageName }
            .sortedBy { it.second.lowercase() }
        AlertDialog.Builder(a)
            .setTitle("Pick a game")
            .setItems(apps.map { it.second }.toTypedArray()) { _, w ->
                val p = Profile(apps[w].first, apps[w].second)
                Profiles.put(a, p)
                editProfile(a, p, true)
            }
            .show()
    }

    private fun editProfile(a: MainActivity, p: Profile, isNew: Boolean) {
        val box = LinearLayout(a)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(40, 20, 40, 0)

        fun sw(t: String, v: Boolean): Switch {
            val s = Switch(a)
            s.text = t
            s.isChecked = v
            box.addView(s)
            return s
        }

        val sNotif = sw("Block notifications", p.notif)
        val sCalls = sw("Block calls", p.calls)
        val sHud = sw("Show live monitor", p.hud)
        val sBoost = sw("Boost (free RAM) when the game opens", p.boost)

        val bLabel = TextView(a)
        bLabel.text = "Brightness: " + (if (p.brightness < 5) "don't change" else "${p.brightness}%")
        bLabel.setPadding(0, 24, 0, 0)
        box.addView(bLabel)
        val bBar = SeekBar(a)
        bBar.max = 100
        bBar.progress = p.brightness
        bBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, v: Int, fromUser: Boolean) {
                bLabel.text = "Brightness: " + (if (v < 5) "don't change" else "$v%")
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        box.addView(bBar)

        val br = EditText(a)
        br.inputType = InputType.TYPE_CLASS_NUMBER
        br.hint = "Break reminder every N minutes (0 = off)"
        br.setText(p.breakMin.toString())
        box.addView(br)

        val hdr = TextView(a)
        hdr.text = "Show these macro buttons"
        hdr.setPadding(0, 24, 0, 8)
        box.addView(hdr)
        val checks = Store.all(a).map { m ->
            val cb = CheckBox(a)
            cb.text = m.name
            cb.isChecked = p.macroIds.contains(m.id)
            box.addView(cb)
            Pair(m.id, cb)
        }

        val sv = ScrollView(a)
        sv.addView(box)
        AlertDialog.Builder(a)
            .setTitle(p.label)
            .setView(sv)
            .setPositiveButton("Save") { _, _ ->
                p.notif = sNotif.isChecked
                p.calls = sCalls.isChecked
                p.hud = sHud.isChecked
                p.boost = sBoost.isChecked
                p.brightness = if (bBar.progress < 5) 0 else bBar.progress
                p.breakMin = br.text.toString().toIntOrNull() ?: 0
                p.macroIds = checks.filter { it.second.isChecked }.map { it.first }.toMutableList()
                Profiles.put(a, p)
                a.render()
            }
            .setNegativeButton(if (isNew) "Remove" else "Cancel") { _, _ ->
                if (isNew) Profiles.delete(a, p.pkg)
                a.render()
            }
            .show()
    }

    // ---------- Themes and wallpapers ----------
    private fun lookCard(a: MainActivity, col: LinearLayout) {
        with(a) {
            val c = card(col)
            val isPro = License.isPro(a)
            text(c, "Themes", 18f, C.TEXT, true)
            val cur = Themes.index(a)
            for ((i, t) in Themes.list.withIndex()) {
                val locked = t.pro && !isPro
                button(c, (if (i == cur) "● " else "") + t.name + (if (locked) "  🔒" else ""), i == cur) {
                    if (locked) {
                        msg(a, "Pro theme. Unlock Pro at the top of this tab.")
                    } else {
                        Themes.setIndex(a, i)
                        Themes.apply(a)
                        recreate()
                    }
                }
            }
            text(c, "Animated wallpaper", 16f, C.TEXT, true, 16)
            val names = listOf("Off", "Floating glow", "Neon grid", "Starfield")
            val cs = Themes.backdropStyle(a)
            for ((i, n) in names.withIndex()) {
                val locked = i > 1 && !isPro
                button(c, (if (i == cs) "● " else "") + n + (if (locked) "  🔒" else ""), i == cs) {
                    if (locked) {
                        msg(a, "Pro wallpaper. Unlock Pro at the top of this tab.")
                    } else {
                        Themes.setBackdrop(a, i)
                        recreate()
                    }
                }
            }
        }
    }

    // ---------- Safety ----------
    private fun safetyCard(a: MainActivity, col: LinearLayout) {
        with(a) {
            val c = card(col)
            text(c, "Safety", 18f, C.TEXT, true)
            button(c, "🛑 Panic stop (end all macros)") {
                MacroAccessibilityService.instance?.panic()
                msg(a, "All macros stopped")
            }
            switchRow(c, "Heat alert", "Buzz and warn when the phone gets hot", Alerts.enabled(a)) {
                Alerts.setEnabled(a, it)
            }
            slider(c, "Alert at", 38, 50, Alerts.limit(a), { "$it °C" }) { Alerts.setLimit(a, it) }
            text(
                c,
                "The heat alert runs while the floating monitor is on (Game Mode turns it on). The Game Panel and the macro notification also have a Stop button.",
                12f, C.SUB, false, 8
            )
        }
    }

    // ---------- Layouts ----------
    private fun layoutsCard(a: MainActivity, col: LinearLayout) {
        with(a) {
            val c = card(col)
            text(c, "Button layouts", 18f, C.TEXT, true)
            text(c, "Save where your macro buttons sit and how big they are, then restore it any time.", 12f, C.SUB, false, 4)
            val list = Layouts.all(a)
            for (l in list) {
                text(c, l.name, 16f, C.TEXT, true, 12)
                val r = row(c)
                button(r, "Load", true, true) {
                    Layouts.apply(a, l)
                    msg(a, "Layout loaded")
                    render()
                }
                button(r, "Delete", false, true) {
                    Layouts.delete(a, l.name)
                    render()
                }
            }
            button(c, "+ Save current layout") {
                if (list.isNotEmpty() && !License.isPro(a)) {
                    msg(a, "Free plan: 1 layout. Pro allows unlimited.")
                } else {
                    Layouts.save(a, "Layout ${list.size + 1}")
                    msg(a, "Layout saved")
                    render()
                }
            }
        }
    }

    // ---------- Backup ----------
    private fun backupCard(a: MainActivity, col: LinearLayout) {
        with(a) {
            val c = card(col)
            text(c, "Backup", 18f, C.TEXT, true)
            text(c, "Save your macros, profiles, layouts and look to a file. Restore after reinstalling.", 12f, C.SUB, false, 4)
            button(c, "Export backup") {
                val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
                i.addCategory(Intent.CATEGORY_OPENABLE)
                i.type = "application/json"
                i.putExtra(Intent.EXTRA_TITLE, "drivexplay-backup.json")
                startActivityForResult(i, 51)
            }
            button(c, "Import backup", false) {
                val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
                i.addCategory(Intent.CATEGORY_OPENABLE)
                i.type = "*/*"
                startActivityForResult(i, 52)
            }
        }
    }

    fun onResult(a: MainActivity, req: Int, res: Int, data: Intent?) {
        if (req != 51 && req != 52) return
        if (res != Activity.RESULT_OK) return
        val uri = data?.data ?: return
        if (req == 51) {
            try {
                a.contentResolver.openOutputStream(uri)!!.use { it.write(Backup.export(a).toByteArray()) }
                msg(a, "Backup saved")
            } catch (e: Exception) {
                msg(a, "Backup failed: ${e.message}")
            }
        } else {
            try {
                val s = a.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
                if (Backup.import(a, s)) {
                    msg(a, "Backup restored")
                    Themes.apply(a)
                    a.recreate()
                } else {
                    msg(a, "That is not a valid Drivex Play backup")
                }
            } catch (e: Exception) {
                msg(a, "Restore failed: ${e.message}")
            }
        }
    }
}
