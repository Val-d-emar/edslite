package com.sovworks.eds.android.locations

import android.content.Context
import android.net.Uri
import com.sovworks.eds.android.Logger.Companion.debug
import com.sovworks.eds.android.Logger.Companion.log
import com.sovworks.eds.android.errors.UserException
import com.sovworks.eds.android.errors.WrongPasswordOrBadContainerException
import com.sovworks.eds.android.helpers.ContainerOpeningProgressReporter
import com.sovworks.eds.android.locations.EDSLocationBase.ExternalSettings
import com.sovworks.eds.android.locations.EDSLocationBase.SharedData
import com.sovworks.eds.android.settings.UserSettings
import com.sovworks.eds.android.settings.UserSettingsCommon.getSettingsProtectionKey
import com.sovworks.eds.container.ContainerFormatInfo
import com.sovworks.eds.container.EdsContainer
import com.sovworks.eds.container.EdsContainerBase.close
import com.sovworks.eds.container.EdsContainerBase.getEncryptedFS
import com.sovworks.eds.container.EdsContainerBase.volumeLayout
import com.sovworks.eds.container.VolumeLayoutBase
import com.sovworks.eds.crypto.FileEncryptionEngine
import com.sovworks.eds.crypto.SecureBuffer
import com.sovworks.eds.crypto.SecureBuffer.Companion.eraseData
import com.sovworks.eds.crypto.SimpleCrypto.calcStringMD5
import com.sovworks.eds.exceptions.WrongFileFormatException
import com.sovworks.eds.fs.FileSystem
import com.sovworks.eds.locations.ContainerLocation
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.Location.ProtectionKeyProvider
import com.sovworks.eds.locations.LocationsManagerBase
import com.sovworks.eds.settings.Settings
import com.sovworks.eds.settings.SettingsCommon.InvalidSettingsPassword
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.Arrays

open class ContainerBasedLocation : EDSLocationBase, ContainerLocation {
    class ExternalSettings : com.sovworks.eds.android.locations.EDSLocationBase.ExternalSettings(),
        ContainerLocation.ExternalSettings {
        override fun setContainerFormatName(containerFormatName: String) {
            _containerFormatName = containerFormatName
        }

        override fun setEncEngineName(encEngineName: String) {
            _encEngineName = encEngineName
        }

        override fun setHashFuncName(hashFuncName: String) {
            _hashFuncName = hashFuncName
        }

        override fun getContainerFormatName(): String {
            return _containerFormatName!!
        }

        override fun getEncEngineName(): String {
            return _encEngineName!!
        }

        override fun getHashFuncName(): String {
            return _hashFuncName!!
        }

        @Throws(JSONException::class)
        override fun saveToJSONObject(jo: JSONObject) {
            super.saveToJSONObject(jo)
            jo.put(SETTINGS_CONTAINER_FORMAT, _containerFormatName)
            jo.put(SETTINGS_ENC_ENGINE, _encEngineName)
            jo.put(SETTINGS_HASH_FUNC, _hashFuncName)
        }

        @Throws(JSONException::class)
        override fun loadFromJSONOjbect(jo: JSONObject) {
            super.loadFromJSONOjbect(jo)
            _containerFormatName = jo.optString(SETTINGS_CONTAINER_FORMAT, null)
            _encEngineName = jo.optString(SETTINGS_ENC_ENGINE, null)
            _hashFuncName = jo.optString(SETTINGS_HASH_FUNC, null)
        }

        private var _containerFormatName: String? = null
        private var _hashFuncName: String? = null
        private var _encEngineName: String? = null

        companion object {
            private const val SETTINGS_CONTAINER_FORMAT = "container_format"
            private const val SETTINGS_ENC_ENGINE = "encryption_engine"
            private const val SETTINGS_HASH_FUNC = "hash_func"
        }
    }

    constructor(uri: Uri, lm: LocationsManagerBase, context: Context, settings: Settings?) : this(
        EDSLocationBase.Companion.getContainerLocationFromUri(uri, lm),
        null,
        context,
        settings
    ) {
        loadFromUri(uri)
    }

    constructor(sibling: ContainerBasedLocation?) : super(sibling)

    constructor(containerLocation: Location, context: Context) : this(
        containerLocation,
        null,
        context,
        UserSettings.getSettings(context)
    )

    constructor(
        containerLocation: Location,
        cont: EdsContainer?,
        context: Context,
        settings: Settings?
    ) : super(
        settings,
        SharedData(
            getLocationId(containerLocation),
            EDSLocationBase.Companion.createInternalSettings(),
            containerLocation,
            context
        )
    ) {
        sharedData.container = cont
    }

    override fun loadFromUri(uri: Uri) {
        super.loadFromUri(uri)
        _currentPathString = uri.path
    }

    @Throws(Exception::class)
    override fun open() {
        if (isOpenOrMounted) return
        val cnt = edsContainer
        cnt.containerFormat = null
        cnt.setEncryptionEngineHint(null)
        cnt.setHashFuncHint(null)
        cnt.setNumKDFIterations(0)
        if (_openingProgressReporter != null) cnt.setProgressReporter(_openingProgressReporter as ContainerOpeningProgressReporter)
        val cfi = containerFormatInfo
        if (cfi != null) {
            cnt.containerFormat = cfi
            val vl = cfi.volumeLayout
            var name: String? = externalSettings.getEncEngineName()
            if (name != null && !name.isEmpty()) cnt.setEncryptionEngineHint(
                VolumeLayoutBase.findEncEngineByName(
                    vl!!.supportedEncryptionEngines,
                    name
                ) as FileEncryptionEngine?
            )

            name = externalSettings.getHashFuncName()
            if (name != null && !name.isEmpty()) cnt.setHashFuncHint(
                VolumeLayoutBase.findHashFunc(
                    vl!!.supportedHashFuncs, name
                )
            )
        }

        val numKDFIterations = selectedKDFIterations
        if (numKDFIterations > 0) cnt.setNumKDFIterations(numKDFIterations)

        val pass = finalPassword
        try {
            cnt.open(pass)
        } catch (e: WrongFileFormatException) {
            sharedData.container = null
            throw WrongPasswordOrBadContainerException(context)
        } catch (e: Exception) {
            sharedData.container = null
            throw e
        } finally {
            if (pass != null) Arrays.fill(pass, 0.toByte())
        }
    }

    override val locationUri: Uri
        get() = makeUri(URI_SCHEME).build()

    override val externalSettings: com.sovworks.eds.android.locations.EDSLocationBase.ExternalSettings?
        get() = super.getExternalSettings() as ExternalSettings?

    override fun hasCustomKDFIterations(): Boolean {
        val cfi = containerFormatInfo
        return cfi == null || cfi.hasCustomKDFIterationsSupport()
    }

    @Throws(IOException::class)
    override fun close(force: Boolean) {
        debug("Closing container at " + location.locationUri)
        super.close(force)
        if (isOpen) {
            try {
                sharedData.container.close()
            } catch (e: Throwable) {
                if (!force) throw IOException(e)
                else log(e)
            }
            sharedData.container = null
        }
        debug("Container has been closed")
    }

    override fun isOpen(): Boolean {
        return sharedData.container != null && sharedData.container.volumeLayout != null
    }

    override fun copy(): ContainerBasedLocation? {
        return ContainerBasedLocation(this)
    }

    @Synchronized
    @Throws(IOException::class)
    override fun getEdsContainer(): EdsContainer {
        var cnt: EdsContainer = sharedData.container
        if (cnt == null) {
            cnt = initEdsContainer()
            sharedData.container = cnt
        }
        return cnt
    }

    override fun getSupportedFormats(): List<ContainerFormatInfo> {
        return EdsContainer.getSupportedFormats()
    }

    protected class SharedData(
        id: String?,
        settings: InternalSettings,
        location: Location,
        context: Context
    ) :
        com.sovworks.eds.android.locations.EDSLocationBase.SharedData(
            id,
            settings,
            location,
            context
        ) {
        var container: EdsContainer? = null
    }

    override val sharedData: com.sovworks.eds.android.locations.EDSLocationBase.SharedData
        get() = super.getSharedData() as SharedData

    @Throws(IOException::class)
    protected fun initEdsContainer(): EdsContainer {
        return EdsContainer(location.currentPath)
    }

    protected open val containerFormatInfo: ContainerFormatInfo?
        get() {
            val name: String = externalSettings.getContainerFormatName()
            return if (name != null) EdsContainer.findFormatByName(name) else null
        }

    override fun getSelectedPassword(): ByteArray {
        var pass = super.getSelectedPassword()
        if (pass != null && pass.size > MAX_PASSWORD_LENGTH) {
            val tmp = pass
            pass = ByteArray(MAX_PASSWORD_LENGTH)
            System.arraycopy(tmp, 0, pass, 0, MAX_PASSWORD_LENGTH)
            eraseData(tmp)
        }
        return pass
    }

    override fun loadExternalSettings(): ExternalSettings? {
        val res = ExternalSettings()
        res.setProtectionKeyProvider(
            object : ProtectionKeyProvider {
                override val protectionKey: SecureBuffer?
                    get() {
                        return try {
                            UserSettings.getSettings(context).getSettingsProtectionKey()
                        } catch (invalidSettingsPassword: InvalidSettingsPassword) {
                            null
                        }
                    }
            })
        res.load(_globalSettings, id)
        return res
    }

    @Throws(IOException::class, UserException::class)
    override fun createBaseFS(readOnly: Boolean): FileSystem? {
        return sharedData.container.getEncryptedFS(readOnly)
    }

    companion object {
        const val URI_SCHEME: String = "eds-container"

        @Throws(Exception::class)
        fun getLocationId(lm: LocationsManagerBase, locationUri: Uri): String {
            val containerLocation: Location =
                EDSLocationBase.Companion.getContainerLocationFromUri(locationUri, lm)
            return getLocationId(containerLocation)
        }

        fun getLocationId(containerLocation: Location): String {
            return calcStringMD5(containerLocation.locationUri.toString())
        }

        const val MAX_PASSWORD_LENGTH: Int = 64
    }
}
