package com.sovworks.eds.android.locations

import android.net.Uri
import com.sovworks.eds.fs.Path
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.LocationsManager
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class PathsStore
    (private val _lm: LocationsManager) {
    fun isPathStoreData(data: String): Boolean {
        val jo: JSONObject
        try {
            jo = JSONObject(data)
            if (jo.has("location")) return true
        } catch (ignored: JSONException) {
        }

        try {
            val uri = Uri.parse(data)
            return uri.path != null
        } catch (e: Exception) {
            return false
        }
    }

    fun load(data: String): Boolean {
        pathsStore.clear()
        paramsStore = JSONObject()
        val jo: JSONObject
        try {
            jo = JSONObject(data)
            if (jo.has("location")) {
                try {
                    loadFromJO(jo)
                } catch (e: Exception) {
                    return false
                }
                return true
            }
        } catch (ignored: JSONException) {
        }

        try {
            val uri = Uri.parse(data)
            loadFromUri(uri)
        } catch (e: Exception) {
            return false
        }
        return true
    }

    override fun toString(): String {
        if (location == null) return super.toString()
        if (pathsStore.isEmpty() && paramsStore.length() == 0) return location!!.locationUri.toString()
        val jo = JSONObject()
        try {
            jo.put("location", location!!.locationUri.toString())
            if (!pathsStore.isEmpty()) {
                val ja = JSONArray()
                for (p in pathsStore) ja.put(p.pathString)
                jo.put("paths", ja)
            }
            if (paramsStore.length() > 0) jo.put("params", paramsStore)
        } catch (e: JSONException) {
            return "error"
        }
        return jo.toString()
    }

    var location: Location? = null
    val pathsStore: ArrayList<Path> = ArrayList()
    var paramsStore: JSONObject = JSONObject()
        private set

    @Throws(Exception::class)
    private fun loadFromJO(jo: JSONObject) {
        val uriString = jo.getString("location")
        val uri = Uri.parse(uriString)
        location = _lm.getLocation(uri)
        if (jo.has("paths")) {
            val ja = jo.getJSONArray("paths")
            for (i in 0..<ja.length()) pathsStore.add(location.fS.getPath(ja.getString(i)))
        }
        if (jo.has("params")) paramsStore = jo.getJSONObject("params")
    }

    @Throws(Exception::class)
    private fun loadFromUri(uri: Uri) {
        location = _lm.getLocation(uri)
        pathsStore.add(location.currentPath)
    }
}
