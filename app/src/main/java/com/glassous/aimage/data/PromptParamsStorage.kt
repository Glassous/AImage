package com.glassous.aimage.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object PromptParamsStorage {
    private const val PREF_NAME = "PromptParams"
    private const val KEY_ENABLED_PREFIX = "param_enabled_"
    private const val KEY_VALUE_PREFIX = "param_value_"
    private const val KEY_FAVORITES = "favorites"
    private const val KEY_PARAM_DEFS_JSON = "param_defs_json"

    data class ParamDefRecord(val name: String, val suggestions: List<String> = emptyList(), val builtin: Boolean = false)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun saveEnabled(ctx: Context, name: String, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_ENABLED_PREFIX + name, enabled).apply()
    }

    fun loadEnabled(ctx: Context, name: String): Boolean {
        return prefs(ctx).getBoolean(KEY_ENABLED_PREFIX + name, false)
    }

    fun saveValue(ctx: Context, name: String, value: String) {
        prefs(ctx).edit().putString(KEY_VALUE_PREFIX + name, value).apply()
    }

    fun loadValue(ctx: Context, name: String): String {
        return prefs(ctx).getString(KEY_VALUE_PREFIX + name, "").orEmpty()
    }

    fun enabledCount(ctx: Context): Int {
        val all = prefs(ctx).all
        var count = 0
        all.forEach { (key, value) ->
            if (key.startsWith(KEY_ENABLED_PREFIX) && value is Boolean && value) {
                val name = key.removePrefix(KEY_ENABLED_PREFIX)
                val v = prefs(ctx).getString(KEY_VALUE_PREFIX + name, "").orEmpty()
                if (v.isNotBlank()) count++
            }
        }
        return count
    }

    fun loadFavorites(ctx: Context): Set<String> {
        return prefs(ctx).getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
    }

    fun saveFavorites(ctx: Context, favs: Set<String>) {
        // Store a mutable copy to satisfy SharedPreferences contract
        prefs(ctx).edit().putStringSet(KEY_FAVORITES, favs.toMutableSet()).apply()
    }

    fun isFavorite(ctx: Context, name: String): Boolean {
        return loadFavorites(ctx).contains(name)
    }

    fun setFavorite(ctx: Context, name: String, favored: Boolean) {
        val set = loadFavorites(ctx).toMutableSet()
        if (favored) set.add(name) else set.remove(name)
        saveFavorites(ctx, set)
    }

    // ----- Param definitions persistence -----
    fun loadParamDefs(ctx: Context): List<ParamDefRecord> {
        val json = prefs(ctx).getString(KEY_PARAM_DEFS_JSON, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<ParamDefRecord>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val name = obj.optString("name")
                val sArr = obj.optJSONArray("suggestions") ?: JSONArray()
                val suggestions = mutableListOf<String>()
                for (j in 0 until sArr.length()) suggestions.add(sArr.optString(j))
                val builtin = obj.optBoolean("builtin", false)
                list.add(ParamDefRecord(name, suggestions, builtin))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveParamDefs(ctx: Context, defs: List<ParamDefRecord>) {
        val arr = JSONArray()
        defs.forEach { d ->
            val obj = JSONObject()
                .put("name", d.name)
                .put("suggestions", JSONArray(d.suggestions))
                .put("builtin", d.builtin)
            arr.put(obj)
        }
        prefs(ctx).edit().putString(KEY_PARAM_DEFS_JSON, arr.toString()).apply()
    }

    fun addParamDef(ctx: Context, def: ParamDefRecord) {
        val list = loadParamDefs(ctx).toMutableList()
        // Replace if exists by name
        val idx = list.indexOfFirst { it.name == def.name }
        if (idx >= 0) list[idx] = def else list.add(def)
        saveParamDefs(ctx, list)
    }

    fun updateParamDef(ctx: Context, oldName: String, newDef: ParamDefRecord) {
        val list = loadParamDefs(ctx).toMutableList()
        val idx = list.indexOfFirst { it.name == oldName }
        if (idx >= 0) list[idx] = newDef else list.add(newDef)
        saveParamDefs(ctx, list)
        if (oldName != newDef.name) renameParam(ctx, oldName, newDef.name)
    }

    fun deleteParamDef(ctx: Context, name: String) {
        val list = loadParamDefs(ctx).toMutableList()
        val idx = list.indexOfFirst { it.name == name }
        if (idx >= 0) list.removeAt(idx)
        saveParamDefs(ctx, list)
        // clean enabled/value and favorites
        val ed = prefs(ctx).edit()
        ed.remove(KEY_ENABLED_PREFIX + name)
        ed.remove(KEY_VALUE_PREFIX + name)
        ed.apply()
        val favs = loadFavorites(ctx).toMutableSet()
        favs.remove(name)
        saveFavorites(ctx, favs)
    }

    fun renameParam(ctx: Context, oldName: String, newName: String) {
        if (oldName == newName) return
        val p = prefs(ctx)
        val enabled = p.getBoolean(KEY_ENABLED_PREFIX + oldName, false)
        val value = p.getString(KEY_VALUE_PREFIX + oldName, null)
        val ed = p.edit()
        ed.remove(KEY_ENABLED_PREFIX + oldName)
        ed.remove(KEY_VALUE_PREFIX + oldName)
        ed.putBoolean(KEY_ENABLED_PREFIX + newName, enabled)
        if (value != null) ed.putString(KEY_VALUE_PREFIX + newName, value)
        ed.apply()
        val favs = loadFavorites(ctx).toMutableSet()
        if (favs.remove(oldName)) favs.add(newName)
        saveFavorites(ctx, favs)
    }
}