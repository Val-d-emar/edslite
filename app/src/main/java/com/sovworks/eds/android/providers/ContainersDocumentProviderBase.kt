package com.sovworks.eds.android.providers

import android.annotation.TargetApi
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.util.Base64
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.filemanager.tasks.LoadPathInfoObservable.create
import com.sovworks.eds.android.helpers.CachedPathInfo
import com.sovworks.eds.android.providers.cursor.DocumentRootsCursor
import com.sovworks.eds.android.providers.cursor.FSCursor
import com.sovworks.eds.android.service.FileOpsService
import com.sovworks.eds.android.settings.SystemConfig
import com.sovworks.eds.fs.Directory
import com.sovworks.eds.fs.Path
import com.sovworks.eds.fs.util.StringPathUtil
import com.sovworks.eds.fs.util.Util
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.LocationsManager
import io.reactivex.Completable
import io.reactivex.CompletableEmitter
import io.reactivex.Single
import io.reactivex.SingleEmitter
import io.reactivex.functions.Function
import io.reactivex.schedulers.Schedulers
import java.io.FileNotFoundException
import java.nio.charset.Charset

@TargetApi(VERSION_CODES.KITKAT)
abstract class ContainersDocumentProviderBase : DocumentsProvider() {
    @Throws(FileNotFoundException::class)
    override fun queryRoots(projection: Array<String>?): Cursor {
        return DocumentRootsCursor(
            context,
            locationsManager,
            projection ?: ALL_ROOT_COLUMNS
        )
    }

    @Throws(FileNotFoundException::class)
    override fun queryDocument(documentId: String, projection: Array<String>?): Cursor {
        try {
            val loc = locationsManager.getLocation(getLocationUriFromDocumentId(documentId))
            return FSCursor(
                context,
                loc,
                projection ?: ALL_DOCUMENT_COLUMNS,
                null,
                null,
                false
            )
        } catch (e: Exception) {
            Logger.log(e)
            throw IllegalArgumentException("Wrong document uri", e)
        }
    }

    @Throws(FileNotFoundException::class)
    override fun getDocumentType(documentId: String): String {
        try {
            val loc = locationsManager.getLocation(getLocationUriFromDocumentId(documentId))
            return create(loc).map<String> { cpi: CachedPathInfo ->
                if (cpi.isFile) FileOpsService.getMimeTypeFromExtension(
                    context,
                    StringPathUtil(cpi.name).fileExtension
                ) else Document.MIME_TYPE_DIR
            }.subscribeOn(Schedulers.io()).blockingGet()
        } catch (e: Exception) {
            Logger.log(e)
            throw IllegalArgumentException("Wrong document uri", e)
        }
    }

    @Throws(FileNotFoundException::class)
    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<String>?,
        sortOrder: String
    ): Cursor {
        try {
            val loc = locationsManager.getLocation(getLocationUriFromDocumentId(parentDocumentId))
            return FSCursor(
                context,
                loc,
                projection ?: ALL_DOCUMENT_COLUMNS,
                null,
                null,
                true
            )
        } catch (e: Exception) {
            Logger.log(e)
            throw IllegalArgumentException("Wrong folder uri", e)
        }
    }

    @Throws(FileNotFoundException::class)
    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        try {
            val loc = locationsManager.getLocation(getLocationUriFromDocumentId(documentId))
            return Single.create { s: SingleEmitter<ParcelFileDescriptor?> ->
                s.onSuccess(
                    MainContentProvider.getParcelFileDescriptor(
                        this,
                        loc,
                        mode,
                        Bundle()
                    )
                )
            }.subscribeOn
            (Schedulers.io()).blockingGet
            ()
        } catch (e: Exception) {
            Logger.log(e)
            throw IllegalArgumentException("Wrong document uri", e)
        }
    }

    override fun onCreate(): Boolean {
        com.sovworks.eds.settings.SystemConfig.setInstance(
            SystemConfig(
                context
            )
        )
        return true
    }

    @Throws(FileNotFoundException::class)
    override fun copyDocument(sourceDocumentId: String, targetParentDocumentId: String): String {
        try {
            return Single.create<String?> { em: SingleEmitter<String?> ->
                val srcPath: Path = locationsManager.getLocation
                (getLocationUriFromDocumentId(
                    sourceDocumentId
                )).getCurrentPath
                ()
                val dstLocation =
                    locationsManager.getLocation
                (getLocationUriFromDocumentId(
                    targetParentDocumentId
                ))
                val dest: Directory = dstLocation.getCurrentPath
                ().getDirectory
                ()
                val res = dstLocation.copy()
                if (srcPath.isDirectory) res.currentPath =
                    dest.createDirectory(srcPath.directory.name).path
                else if (srcPath.isFile) res.currentPath =
                    Util.copyFile(srcPath.file, dest).path
                val context = context
                context?.contentResolver?.notifyChange(
                    getUriFromLocation(res),
                    null
                )
                em.onSuccess(getDocumentIdFromLocation(res))
            }.subscribeOn
            (Schedulers.io()).blockingGet
            ()
        } catch (e: Exception) {
            Logger.log(e)
            throw IllegalArgumentException("Copy failed", e)
        }
    }

    @Throws(FileNotFoundException::class)
    override fun moveDocument(
        sourceDocumentId: String,
        sourceParentDocumentId: String,
        targetParentDocumentId: String
    ): String {
        try {
            return Single.create { em: SingleEmitter<String?> ->
                val srcLocation =
                    locationsManager.getLocation
                (getLocationUriFromDocumentId(
                    sourceDocumentId
                ))
                val srcPath = srcLocation.getCurrentPath
                ()
                val dstLocation =
                    locationsManager.getLocation
                (getLocationUriFromDocumentId(
                    targetParentDocumentId
                ))
                val dest: Directory = dstLocation.getCurrentPath
                ().getDirectory
                ()
                val res = dstLocation.copy()
                if (srcPath.isDirectory) {
                    val name = srcPath.directory.name
                    srcPath.directory.moveTo(dest)
                    res.currentPath = dest.path.combine(name)
                } else if (srcPath.isFile) {
                    val name = srcPath.file.name
                    srcPath.file.moveTo(dest)
                    res.currentPath = dest.path.combine(name)
                }
                val context = context
                if (context != null) {
                    context.contentResolver.notifyChange(
                        getUriFromLocation(
                            srcLocation
                        ), null
                    )
                    context.contentResolver.notifyChange(
                        getUriFromLocation(
                            res
                        ), null
                    )
                }
                em.onSuccess(getDocumentIdFromLocation(res))
            }.subscribeOn
            (Schedulers.io()).blockingGet
            ()
        } catch (e: Exception) {
            Logger.log(e)
            throw IllegalArgumentException("Move failed", e)
        }
    }

    @Throws(FileNotFoundException::class)
    override fun deleteDocument(documentId: String) {
        try {
            Completable.create { em: CompletableEmitter ->
                val loc = locationsManager.getLocation
                (getLocationUriFromDocumentId(documentId))
                val srcPath = loc.currentPath
                if (srcPath.isFile) srcPath.file.delete()
                else if (srcPath.isDirectory) srcPath.directory.delete()
                val context = context
                context?.contentResolver?.notifyChange(
                    getUriFromLocation(loc),
                    null
                )
                em.onComplete()
            }.subscribeOn
            (Schedulers.io()).blockingAwait
            ()
        } catch (e: Exception) {
            Logger.log(e)
            throw IllegalArgumentException("Delete failed", e)
        }
    }

    @Throws(FileNotFoundException::class)
    override fun renameDocument(documentId: String, displayName: String): String {
        try {
            return Single.create { em: SingleEmitter<String?> ->
                val loc = locationsManager.getLocation(
                    getLocationUriFromDocumentId(documentId)
                ).copy()
                val srcPath = loc.currentPath
                if (srcPath.isDirectory) srcPath.directory.rename(displayName)
                else if (srcPath.isFile) srcPath.file.rename(displayName)
                val context = context
                context?.contentResolver?.notifyChange(
                    getUriFromLocation(loc),
                    null
                )
                loc.currentPath = srcPath
                context?.contentResolver?.notifyChange(
                    getUriFromLocation(loc),
                    null
                )
                em.onSuccess(getDocumentIdFromLocation(loc))
            }.subscribeOn
            (Schedulers.io()).blockingGet
            ()
        } catch (e: Exception) {
            Logger.log(e)
            throw IllegalArgumentException("Rename failed", e)
        }
    }

    @Throws(FileNotFoundException::class)
    override fun createDocument(
        parentDocumentId: String,
        mimeType: String,
        displayName: String
    ): String {
        try {
            return Single.create<String?> { em: SingleEmitter<String?> ->
                val res = locationsManager.getLocation
                (getLocationUriFromDocumentId(
                    parentDocumentId
                )).copy()
                val dest = res.currentPath.directory
                if (Document.MIME_TYPE_DIR == mimeType) res.currentPath =
                    dest.createDirectory(displayName).path
                else res.currentPath = dest.createFile(displayName).path
                val context = context
                context?.contentResolver?.notifyChange(
                    getUriFromLocation(res),
                    null
                )
                em.onSuccess(getDocumentIdFromLocation(res))
            }.subscribeOn
            (Schedulers.io()).blockingGet
            ()
        } catch (e: Exception) {
            Logger.log(e)
            throw IllegalArgumentException("Create failed", e)
        }
    }

    @TargetApi(VERSION_CODES.LOLLIPOP)
    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        try {
            return Single.create<Boolean?> { em: SingleEmitter<Boolean?> ->
                val parentPath: Path = locationsManager.getLocation
                (getLocationUriFromDocumentId(
                    parentDocumentId
                )).getCurrentPath()
                var testPath: Path? = locationsManager.getLocation
                (getLocationUriFromDocumentId(documentId)).getCurrentPath()
                var maxParents = 0
                while (testPath != null && (testPath != parentPath) && maxParents++ < 1000) testPath =
                    testPath.parentPath
                em.onSuccess(testPath != null && maxParents < 1000)
            }.subscribeOn
            (Schedulers.io()).blockingGet
            ()
        } catch (e: Exception) {
            Logger.log(e)
            throw IllegalArgumentException("isChildDocument failed", e)
        }
    }

    protected val locationsManager: LocationsManager
        get() = LocationsManager.getLocationsManager(context, true)

    companion object {
        @JvmStatic
        @TargetApi(VERSION_CODES.LOLLIPOP)
        fun getUriFromLocation(location: Location): Uri {
            return DocumentsContract.buildTreeDocumentUri(
                ContainersDocumentProvider.AUTHORITY,
                getDocumentIdFromLocation(location)
            )
        }

        @JvmStatic
        fun notifyOpenedLocationsListChanged(context: Context) {
            context.contentResolver.notifyChange(
                DocumentsContract.buildRootsUri(ContainersDocumentProvider.AUTHORITY),
                null,
                false
            )
        }

        fun getDocumentIdFromLocationUri(uri: Uri): String {
            return Base64.encodeToString(
                uri.toString().toByteArray(),
                Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE
            )
        }

        fun getDocumentIdFromLocation(location: Location): String {
            return getDocumentIdFromLocationUri(location.locationUri)
        }

        fun getLocationUriFromDocumentId(documentId: String?): Uri {
            val locationUriBytes =
                Base64.decode(documentId, Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE)
            return Uri.parse(String(locationUriBytes, Charset.defaultCharset()))
        }

        private val ALL_ROOT_COLUMNS = arrayOf(
            Root.COLUMN_AVAILABLE_BYTES,
            Root.COLUMN_DOCUMENT_ID,
            Root.COLUMN_FLAGS,
            Root.COLUMN_ICON,
            Root.COLUMN_MIME_TYPES,
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_SUMMARY,
            Root.COLUMN_TITLE
        )

        private val ALL_DOCUMENT_COLUMNS = arrayOf(
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_FLAGS,
            Document.COLUMN_ICON,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_SUMMARY
        )
    }
}
