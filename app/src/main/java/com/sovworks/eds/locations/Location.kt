package com.sovworks.eds.locations

import android.content.Intent
import android.net.Uri
import com.sovworks.eds.crypto.SecureBuffer
import com.sovworks.eds.fs.FileSystem
import com.sovworks.eds.fs.Path
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

interface Location : Cloneable {
    interface ProtectionKeyProvider {
        val protectionKey: SecureBuffer?
    }

    interface ExternalSettings {
        fun setProtectionKeyProvider(p: ProtectionKeyProvider?)

        fun getTitle(): String

        fun setTitle(title: String?)

        var isVisibleToUser: Boolean

        @Throws(JSONException::class)
        fun saveToJSONObject(jo: JSONObject)

        @Throws(JSONException::class)
        fun loadFromJSONOjbect(jo: JSONObject)

        fun useExtFileManager(): Boolean

        fun setUseExtFileManager(value: Boolean)
    }

    class DefaultExternalSettings : ExternalSettings {
        override fun setProtectionKeyProvider(p: ProtectionKeyProvider?) {}

        override fun getTitle(): String {
            return ""
        }

        override fun setTitle(title: String?) {}

        @Throws(JSONException::class)
        override fun saveToJSONObject(jo: JSONObject) {
        }

        @Throws(JSONException::class)
        override fun loadFromJSONOjbect(jo: JSONObject) {
        }

        override fun useExtFileManager(): Boolean {
            return false
        }

        override fun setUseExtFileManager(value: Boolean) {}

        override var isVisibleToUser: Boolean = false
            set(value) {
                field = value
            }
    }

    val title: String?

    val id: String

    @get:Throws(IOException::class)
    val fS: FileSystem?

    @JvmField
    @get:Throws(IOException::class)
    var currentPath: Path?

    @JvmField
    val locationUri: Uri

    fun loadFromUri(uri: Uri)

    fun copy(): Location?

    // void initFileSystem() throws Exception;
    @Throws(IOException::class)
    fun closeFileSystem(force: Boolean)

    @JvmField
    val isFileSystemOpen: Boolean

    val isReadOnly: Boolean

    val isEncrypted: Boolean

    val isDirectlyAccessible: Boolean

    fun getDeviceAccessibleUri(path: Path): Uri?

    val externalSettings: ExternalSettings?

    fun saveExternalSettings()

    val externalFileManagerLaunchIntent: Intent?
}
