package com.macrotap.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Step(var type: Int, var x: Float, var y: Float, var x2: Float, var y2: Float, var delay: Long)

data class Macro(
    var id: Long, var name: String, var shape: Int, var size: Int, var color: Int,
    var repeat: Int, var px: Int, var py: Int, var steps: MutableList<Step>,
    var opacity: Int = 90, var icon: Int = 0
)

object Store {
    private fun prefs(c: Context) = c.getSharedPreferences("macros", Context.MODE_PRIVATE)

    private fun parse(s: String): MutableList<Macro> {
        val arr = JSONArray(s)
        val out = mutableListOf<Macro>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val sa = o.getJSONArray("steps")
            val steps = mutableListOf<Step>()
            for (j in 0 until sa.length()) {
                val st = sa.getJSONObject(j)
                steps.add(
                    Step(
                        st.getInt("t"), st.getDouble("x").toFloat(), st.getDouble("y").toFloat(),
                        st.getDouble("x2").toFloat(), st.getDouble("y2").toFloat(), st.getLong("d")
                    )
                )
            }
            out.add(
                Macro(
                    o.getLong("id"), o.getString("name"), o.getInt("shape"), o.getInt("size"),
                    o.getInt("color"), o.getInt("repeat"), o.getInt("px"), o.getInt("py"), steps,
                    o.optInt("opacity", 90), o.optInt("icon", 0)
                )
            )
        }
        return out
    }

    fun raw(c: Context): String = prefs(c).getString("list", "[]") ?: "[]"

    fun setRaw(c: Context, s: String): Boolean {
        return try {
            parse(s)
            prefs(c).edit().putString("list", s).apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun all(c: Context): MutableList<Macro> = try {
        parse(raw(c))
    } catch (e: Exception) {
        mutableListOf()
    }

    private fun saveAll(c: Context, list: List<Macro>) {
        val arr = JSONArray()
        for (m in list) {
            val o = JSONObject()
            o.put("id", m.id); o.put("name", m.name); o.put("shape", m.shape)
            o.put("size", m.size); o.put("color", m.color); o.put("repeat", m.repeat)
            o.put("px", m.px); o.put("py", m.py)
            o.put("opacity", m.opacity); o.put("icon", m.icon)
            val sa = JSONArray()
            for (s in m.steps) {
                val so = JSONObject()
                so.put("t", s.type); so.put("x", s.x.toDouble()); so.put("y", s.y.toDouble())
                so.put("x2", s.x2.toDouble()); so.put("y2", s.y2.toDouble()); so.put("d", s.delay)
                sa.put(so)
            }
            o.put("steps", sa)
            arr.put(o)
        }
        prefs(c).edit().putString("list", arr.toString()).apply()
    }

    fun get(c: Context, id: Long): Macro? = all(c).firstOrNull { it.id == id }

    fun put(c: Context, m: Macro) {
        val l = all(c)
        val i = l.indexOfFirst { it.id == m.id }
        if (i >= 0) l[i] = m else l.add(m)
        saveAll(c, l)
    }

    fun delete(c: Context, id: Long) = saveAll(c, all(c).filter { it.id != id })

    fun move(c: Context, id: Long, x: Int, y: Int) {
        val m = get(c, id) ?: return
        m.px = x; m.py = y
        put(c, m)
    }
}
