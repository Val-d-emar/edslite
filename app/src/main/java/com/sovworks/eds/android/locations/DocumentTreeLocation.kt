package com.sovworks.eds.android.locations

import android.annotation.TargetApi
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.net.Uri.Builder
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.fs.DocumentTreeFS
import com.sovworks.eds.android.fs.DocumentTreeFS.DocumentPath
import com.sovworks.eds.android.settings.UserSettings
import com.sovworks.eds.fs.Path
import com.sovworks.eds.locations.LocationBase
import com.sovworks.eds.locations.LocationBase.SharedData
import java.io.IOException

@TargetApi(VERSION_CODES.LOLLIPOP)
class DocumentTreeLocation : LocationBase {
    constructor(context: Context?, treeUri: Uri) : super(
        UserSettings.getSettings(context), SharedData(
            getId(treeUri),
            context,
            treeUri
        )
    )

    constructor(sibling: DocumentTreeLocation) : super(sibling)

    override fun loadFromUri(uri: Uri) {
        super.loadFromUri(uri)
        _currentPathString =
            uri.buildUpon().scheme(ContentResolver.SCHEME_CONTENT).build().toString()
    }

    override fun getTitle(): String {
        try {
            return fs.rootPath.directory.name
        } catch (e: IOException) {
            Logger.log(e)
            return treeUri.toString()
        }
    }

    @Synchronized
    @Throws(IOException::class)
    override fun getFS(): DocumentTreeFS {
        if (sharedData.fs == null) {
            sharedData.fs = DocumentTreeFS(context!!, treeUri)
        }

        return sharedData.fs as DocumentTreeFS
    }

    @Throws(IOException::class)
    override fun getCurrentPath(): DocumentPath {
        return super.getCurrentPath() as DocumentPath
    }

    override fun getLocationUri(): Uri {
        try {
            val path = currentPath
            val ub: Builder = path.getPathUri().buildUpon()
            ub.scheme(URI_SCHEME)
            return ub.build()
        } catch (err: IOException) {
            throw IllegalStateException("Wrong path", err)
        }
    }

    override fun copy(): DocumentTreeLocation {
        return DocumentTreeLocation(this)
    }

    override fun isReadOnly(): Boolean {
        return false
    }

    override fun isEncrypted(): Boolean {
        return false
    }

    override fun getDeviceAccessibleUri(path: Path): Uri? {
        return try {
            (path as DocumentPath).documentUri
        } catch (e: IOException) {
            null
        }
    }

    override fun loadExternalSettings(): ExternalSettings {
        val res = ExternalSettings()
        res.load(_globalSettings, id)
        return res
    }

    override fun getSharedData(): SharedData {
        return super.getSharedData() as SharedData
    }

    protected val treeUri: Uri
        get() = sharedData.treeUri

    protected val context: Context?
        get() = sharedData.context

    protected class SharedData(id: String?, val context: Context?, val treeUri: Uri) :
        LocationBase.SharedData(id)


    companion object {
        const val URI_SCHEME: String = "doc-tree"

        @JvmStatic
        fun getLocationId(locationUri: Uri): String {
            return getId(getDocumentUri(locationUri))
        }

        fun getId(treeUri: Uri): String {
            return URI_SCHEME + treeUri
        }

        fun getDocumentUri(locationUri: Uri): Uri {
            return DocumentsContract.buildTreeDocumentUri(
                locationUri.authority,
                DocumentsContract.getTreeDocumentId(locationUri)
            )
        }

        @JvmStatic
        fun isDocumentTreeUri(context: Context, uri: Uri?): Boolean {
            return try {
                VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP && DocumentsContract.getTreeDocumentId(
                    uri
                ) != null && DocumentFile.isDocumentUri(context, uri)
            } catch (e: IllegalArgumentException) {
                false
            }
        }


        @JvmStatic
        @Throws(IOException::class)
        fun fromLocationUri(context: Context?, locationUri: Uri): DocumentTreeLocation {
            val loc = DocumentTreeLocation(
                context,
                getDocumentUri(locationUri)
            )
            loc.loadFromUri(locationUri)
            return loc
        }
    }
}
