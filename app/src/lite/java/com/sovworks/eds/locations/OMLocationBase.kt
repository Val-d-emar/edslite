package com.sovworks.eds.locations

import android.net.Uri
import com.sovworks.eds.android.helpers.ProgressReporter
import com.sovworks.eds.android.providers.MainContentProvider
import com.sovworks.eds.android.providers.MainContentProviderBase.Companion.getContentUriFromLocation
import com.sovworks.eds.crypto.SecureBuffer
import com.sovworks.eds.fs.Path
import com.sovworks.eds.fs.std.StdFs.Companion.stdFs
import com.sovworks.eds.locations.LocationBase.ExternalSettings
import com.sovworks.eds.locations.LocationBase.SharedData
import com.sovworks.eds.settings.Settings
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

abstract class OMLocationBase : LocationBase, OMLocation, Cloneable {
    open class ExternalSettings : com.sovworks.eds.locations.LocationBase.ExternalSettings(),
        OMLocation.ExternalSettings {
        override var password: ByteArray?
            get() = if (_pass == null) null else decodeAndDecrypt(_pass!!)
            set(password) {
                _pass = if (password == null) null else encryptAndEncode(password)
            }

        override fun hasPassword(): Boolean {
            return _pass != null && _pass!!.length > 0
        }

        @Throws(JSONException::class)
        override fun saveToJSONObject(jo: JSONObject) {
            super.saveToJSONObject(jo)
            if (_pass != null) jo.put(SETTINGS_PASS, _pass)
            if (customKDFIterations >= 0) storeProtectedField(
                jo, SETTINGS_CUSTOM_KDF_ITERATIONS, customKDFIterations.toString()
            )
            else jo.remove(SETTINGS_CUSTOM_KDF_ITERATIONS)
        }

        @Throws(JSONException::class)
        override fun loadFromJSONOjbect(jo: JSONObject) {
            super.loadFromJSONOjbect(jo)
            _pass = jo.optString(SETTINGS_PASS, null)
            val iters = loadProtectedString(jo, SETTINGS_CUSTOM_KDF_ITERATIONS)
            if (iters != null) customKDFIterations = iters.toInt()
            else customKDFIterations = -1
        }

        private var _pass: String? = null
        override var customKDFIterations: Int = 0
            set(value) {
                field = value
            }

        companion object {
            private const val SETTINGS_PASS = "pass"
            private const val SETTINGS_CUSTOM_KDF_ITERATIONS = "custom_kdf_iterations"
        }
    }

    protected constructor(sibling: OMLocationBase) : super(sibling)

    protected constructor(settings: Settings?, sharedData: SharedData) : super(settings, sharedData)

    @Synchronized
    @Throws(IOException::class)
    override fun close(force: Boolean) {
        closeFileSystem(force)
        val p = getPassword()
        if (p != null) {
            p.close()
            sharedData.password = null
        }
    }

    @Synchronized
    override fun setPassword(password: SecureBuffer) {
        val p = getPassword()
        if (p != null && p !== password) p.close()

        sharedData.password = password
    }

    override fun setNumKDFIterations(num: Int) {
        sharedData.numKDFIterations = num
    }

    override fun hasPassword(): Boolean {
        return false
    }

    override fun hasCustomKDFIterations(): Boolean {
        return false
    }

    override fun requirePassword(): Boolean {
        return hasPassword() && !externalSettings!!.hasPassword()
    }

    override fun requireCustomKDFIterations(): Boolean {
        return hasCustomKDFIterations() && externalSettings.getCustomKDFIterations() < 0
    }

    override val isOpenOrMounted: Boolean
        get() = isOpen

    override val externalSettings: OMLocation.ExternalSettings?
        get() = super.externalSettings as ExternalSettings?

    override fun setOpeningProgressReporter(pr: ProgressReporter?) {
        _openingProgressReporter = pr
    }

    override val isReadOnly: Boolean
        get() = sharedData.isReadOnly

    override fun setOpenReadOnly(readOnly: Boolean) {
        sharedData.isReadOnly = readOnly
    }

    protected var _openingProgressReporter: ProgressReporter? = null

    open class SharedData protected constructor(id: String) :
        com.sovworks.eds.locations.LocationBase.SharedData(id) {
        var password: SecureBuffer? = null
        var numKDFIterations: Int = 0
        var isReadOnly: Boolean = false
    }

    override val sharedData: SharedData
        get() = super.sharedData as SharedData

    protected fun getPassword(): SecureBuffer {
        return sharedData.password!!
    }

    protected fun getNumKDFIterations(): Int {
        return sharedData.numKDFIterations
    }

    override fun getDeviceAccessibleUri(path: Path): Uri? {
        return if (!_globalSettings.dontUseContentProvider())
            MainContentProvider.getContentUriFromLocation(this, path)
        else
            null
    }

    @Throws(IOException::class)
    override fun loadPaths(paths: Collection<String>): ArrayList<Path>? {
        val res = ArrayList<Path>()
        for (path in paths) res.add(stdFs.getPath(path))
        return res
    }

    protected open val selectedPassword: ByteArray
        get() {
            val p = getPassword()
            if (p != null) {
                val pb = p.dataArray
                if (pb != null && pb.size > 0) return pb
            }
            var res = externalSettings.getPassword()
            if (res == null) res = ByteArray(0)
            return res
        }

    protected val selectedKDFIterations: Int
        get() {
            val n = getNumKDFIterations()
            return if (n == 0) externalSettings.getCustomKDFIterations() else n
        }

    @get:Throws(IOException::class)
    protected val finalPassword: ByteArray
        get() = selectedPassword
}
