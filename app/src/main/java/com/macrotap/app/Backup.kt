package com.macrotap.app

import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject

class LayoutItem(val id: Long, val x: Int, val y: Int, val size: Int)
class Layout(val name: String, val items: List<LayoutItem>)

object Layouts {
    private fun prefs(c: Context) = c.getSharedPreferences("layouts", Context.MODE_PRIVATE)

    fun raw(c: Context): String = prefs(c).getString("list", "[]") ?: "[]"

    private fun parse(s: String): MutableList<Layout> {
        val arr = JSONArray(s)
        val out = mutableListOf<Layout>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val ia = o.getJSONArray("items")
            val items = mutableListOf<LayoutItem>()
            for (j in 0 until ia.length()) {
                val x = ia.getJSONObject(j)
                items.add(LayoutItem(x.getLong("id"), x.getInt("x"), x.getInt("y"), x.getInt("size")))
            }
            out.add(Layout(o.getString("name"), items))
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

    fun all(c: Context): MutableList<Layout> = try {
        parse(raw(c))
    } catch (e: Exception) {
        mutableListOf()
    }

    private fun write(c: Context, list: List<Layout>) {
        val arr = JSONArray()
        for (l in list) {
            val o = JSONObject()
            o.put("name", l.name)
            val ia = JSONArray()
            for (it in l.items) {
                val x = JSONObject()
                x.put("id", it.id); x.put("x", it.x); x.put("y", it.y); x.put("size", it.size)
                ia.put(x)
            }
            o.put("items", ia)
            arr.put(o)
        }
        prefs(c).edit().putString("list", arr.toString()).apply()
    }

    fun save(c: Context, name: String) {
        val items = Store.all(c).map { LayoutItem(it.id, it.px, it.py, it.size) }
        val l = all(c).filter { it.name != name }.toMutableList()
        l.add(Layout(name, items))
        write(c, l)
    }

    fun delete(c: Context, name: String) = write(c, all(c).filter { it.name != name })

    fun apply(c: Context, l: Layout) {
        for (it in l.items) {
            val m = Store.get(c, it.id) ?: continue
            m.px = it.x; m.py = it.y; m.size = it.size
            Store.put(c, m)
            if (FloatingService.shown.contains(m.id)) {
                try {
                    c.startForegroundService(
                        Intent(c, FloatingService::class.java)
                            .setAction(FloatingService.SHOW).putExtra("id", m.id)
                    )
                } catch (e: Exception) {
                }
            }
        }
    }
}

object Backup {
    fun export(c: Context): String {
        val o = JSONObject()
        o.put("app", "DrivexPlay")
        o.put("v", 1)
        o.put("macros", JSONArray(Store.raw(c)))
        o.put("profiles", JSONArray(Profiles.raw(c)))
        o.put("layouts", JSONArray(Layouts.raw(c)))
        o.put("theme", Themes.index(c))
        o.put("backdrop", Themes.backdropRaw(c))
        return o.toString(2)
    }

    fun import(c: Context, s: String): Boolean {
        return try {
            val o = JSONObject(s)
            if (o.optString("app") != "DrivexPlay") return false
            val ok = Store.setRaw(c, o.getJSONArray("macros").toString()) &&
                Profiles.setRaw(c, o.getJSONArray("profiles").toString()) &&
                Layouts.setRaw(c, o.getJSONArray("layouts").toString())
            if (ok) {
                Themes.setIndex(c, o.optInt("theme", 0))
                Themes.setBackdrop(c, o.optInt("backdrop", 1))
            }
            ok
        } catch (e: Exception) {
            false
        }
    }
}
