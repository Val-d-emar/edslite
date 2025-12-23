package com.sovworks.eds.android.filemanager

import com.sovworks.eds.fs.Directory
import com.sovworks.eds.fs.util.Util.writeToFile
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

class DirectorySettings {
    constructor()

    constructor(storedSettings: String) : this(JSONObject(storedSettings))

    constructor(settings: JSONObject) {
        _hiddenFilesList = getArrayParam(settings, PARAM_HIDDEN_FILES)
    }

    var hiddenFilesMasks: ArrayList<String?>?
        get() = _hiddenFilesList
        set(masks) {
            _hiddenFilesList = ArrayList()
            _hiddenFilesList!!.addAll(masks!!)
        }

    fun saveToString(): String {
        try {
            val jo = JSONObject()
            if (_hiddenFilesList != null) jo.put(PARAM_HIDDEN_FILES, JSONArray(_hiddenFilesList))
            return jo.toString()
        } catch (e: JSONException) {
            return ""
        }
    }

    @Throws(IOException::class)
    fun saveToDir(dir: Directory) {
        writeToFile(dir, FILE_NAME, saveToString())
    }

    private var _hiddenFilesList: ArrayList<String?>? = null

    companion object {
        const val FILE_NAME: String = ".eds"

        const val PARAM_HIDDEN_FILES: String = "hidden_files_masks"

        private fun getArrayParam(o: JSONObject, name: String): ArrayList<String?> {
            val result = ArrayList<String?>()
            val entries = getParam(o, name, null as JSONArray?)
            if (entries != null) {
                for (i in 0..<entries.length()) {
                    val entry = entries.optString(i, null)
                    if (entry != null) result.add(entry)
                }
            }
            return result
        }

        private fun getParam(o: JSONObject, name: String, defaultValue: JSONArray?): JSONArray? {
            return try {
                o.getJSONArray(name)
            } catch (e: JSONException) {
                defaultValue
            }
        }

        private fun getParam(o: JSONObject, name: String, defaultValue: String): String {
            return try {
                o.getString(name)
            } catch (e: JSONException) {
                defaultValue
            }
        }
    }
}
