package com.sovworks.eds.android.providers

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.UriMatcher
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.net.Uri.Builder
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Base64
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.filemanager.tasks.LoadPathInfoObservable.create
import com.sovworks.eds.android.helpers.CachedPathInfo
import com.sovworks.eds.android.helpers.TempFilesMonitor.Companion.getMonitor
import com.sovworks.eds.android.helpers.TempFilesMonitor.Companion.getTmpLocation
import com.sovworks.eds.android.helpers.WipeFilesTask
import com.sovworks.eds.android.locations.PathsStore
import com.sovworks.eds.android.providers.cursor.FSCursor
import com.sovworks.eds.android.providers.cursor.FSCursorBase
import com.sovworks.eds.android.providers.cursor.SelectionChecker
import com.sovworks.eds.android.service.FileOpsService
import com.sovworks.eds.android.settings.SystemConfig
import com.sovworks.eds.android.settings.UserSettings
import com.sovworks.eds.fs.File
import com.sovworks.eds.fs.File.AccessMode.Read
import com.sovworks.eds.fs.File.AccessMode.Write
import com.sovworks.eds.fs.Path
import com.sovworks.eds.fs.util.PathUtil
import com.sovworks.eds.fs.util.SrcDstSingle
import com.sovworks.eds.fs.util.StringPathUtil
import com.sovworks.eds.fs.util.Util
import com.sovworks.eds.fs.util.Util.CancellableProgressInfo
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.LocationsManager
import com.sovworks.eds.settings.GlobalConfig
import io.reactivex.Completable
import io.reactivex.CompletableEmitter
import io.reactivex.Single
import io.reactivex.SingleEmitter
import io.reactivex.functions.Action
import io.reactivex.functions.Function
import io.reactivex.schedulers.Schedulers
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.Charset
import java.util.Arrays


abstract class MainContentProviderBase : ContentProvider() {
    internal class SelectionBuilder {
        fun addCondition(filterName: String?, arg: String) {
            if (_selectionBuilder.length > 0) _selectionBuilder.append(' ')
            _selectionBuilder.append(filterName)
            _selectionArgs.add(arg)
        }

        val selectionString: String
            get() = _selectionBuilder.toString()

        val selectionArgs: Array<String?>
            get() {
                val res =
                    arrayOfNulls<String>(_selectionArgs.size)
                return _selectionArgs.toArray(res)
            }

        private val _selectionBuilder = StringBuilder()
        private val _selectionArgs = ArrayList<String>()
    }

    override fun onCreate(): Boolean {
        com.sovworks.eds.settings.SystemConfig.setInstance(
            SystemConfig(
                context
            )
        )
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        when (_uriMatcher.match(uri)) {
            CONTENT_PATH_CODE, META_PATH_CODE -> {
                require(sortOrder == null) { "Sorting is not supported" }
                return queryMeta(uri, projection, selection, selectionArgs)
            }

            CURRENT_SELECTION_PATH_CODE -> {
                require(!(selection != null || selectionArgs != null)) { "Selection is not supported" }
                require(sortOrder == null) { "Sorting is not supported" }
                return querySelection(projection)
            }
        }
        throw IllegalArgumentException("Unsupported uri: $uri")
    }

    override fun getType(uri: Uri): String? {
        when (_uriMatcher.match(uri)) {
            META_PATH_CODE -> return getMetaMimeType(uri)
            CONTENT_PATH_CODE -> return getContentMimeType(uri)
            CURRENT_SELECTION_PATH_CODE -> return if (_currentSelection == null) null else MIME_TYPE_SELECTION
        }
        throw IllegalArgumentException("Unsupported uri: $uri")
    }

    override fun insert(uri: Uri, contentValues: ContentValues): Uri? {
        when (_uriMatcher.match(uri)) {
            CONTENT_PATH_CODE, META_PATH_CODE -> return insertMeta(uri, contentValues)
            CURRENT_SELECTION_PATH_CODE -> {
                _currentSelection = null
                setCurrentSelection(contentValues)
                return CURRENT_SELECTION_URI
            }
        }
        throw IllegalArgumentException("Unsupported uri: $uri")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        when (_uriMatcher.match(uri)) {
            CONTENT_PATH_CODE, META_PATH_CODE -> return deleteFromFS(uri, selection, selectionArgs)
            CURRENT_SELECTION_PATH_CODE -> {
                require(!(selection != null || selectionArgs != null)) { "Selection is not supported" }
                if (_currentSelection != null) {
                    _currentSelection = null
                    return 1
                }
                return 0
            }
        }
        throw IllegalArgumentException("Unsupported uri: $uri")
    }

    override fun update(
        uri: Uri,
        values: ContentValues,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        when (_uriMatcher.match(uri)) {
            CONTENT_PATH_CODE, META_PATH_CODE -> return updateFS(
                uri,
                values,
                selection,
                selectionArgs
            )

            CURRENT_SELECTION_PATH_CODE -> {
                require(!(selection != null || selectionArgs != null)) { "Selection is not supported" }
                setCurrentSelection(values)
                return 1
            }
        }
        throw IllegalArgumentException("Unsupported uri: $uri")
    }

    override fun getStreamTypes(uri: Uri, mimeTypeFilter: String): Array<String>? {
        when (_uriMatcher.match(uri)) {
            CONTENT_PATH_CODE, META_PATH_CODE -> return getContentMimeType(uri, mimeTypeFilter)
            CURRENT_SELECTION_PATH_CODE -> return null
        }
        throw IllegalArgumentException("Unsupported uri: $uri")
    }

    @Throws(FileNotFoundException::class)
    override fun openTypedAssetFile(
        uri: Uri,
        mimeTypeFilter: String,
        opts: Bundle?
    ): AssetFileDescriptor? {
        val loc = getLocationFromProviderUri(uri)
        // Checks to see if the MIME type filter matches a supported MIME type.
        val mimeTypes = getContentMimeType(loc, mimeTypeFilter)
        // If the MIME type is supported
        if (mimeTypes != null) return getAssetFileDescriptor(loc, "r", opts ?: Bundle())

        // If the MIME type is not supported, return a read-only handle to the file.
        return super.openTypedAssetFile(uri, mimeTypeFilter, opts)
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        when (_uriMatcher.match(uri)) {
            CONTENT_PATH_CODE, META_PATH_CODE -> {
                val loc = getLocationFromProviderUri(uri)
                return getAssetFileDescriptor(loc, mode, Bundle())
            }
        }
        throw IllegalArgumentException("Unsupported uri: $uri")
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        when (_uriMatcher.match(uri)) {
            CONTENT_PATH_CODE, META_PATH_CODE -> {
                val loc = getLocationFromProviderUri(uri)
                return getParcelFileDescriptor(loc, mode, Bundle())
            }
        }
        throw IllegalArgumentException("Unsupported uri: $uri")
    }

    private var _currentSelection: PathsStore? = null

    protected fun getAssetFileDescriptor(
        loc: Location,
        accessMode: String,
        opts: Bundle
    ): AssetFileDescriptor {
        val pfd = getParcelFileDescriptor(loc, accessMode, opts)
        return AssetFileDescriptor(
            pfd,
            opts.getLong(OPTION_OFFSET, 0),
            opts.getLong(OPTION_NUM_BYTES, AssetFileDescriptor.UNKNOWN_LENGTH)
        )
    }

    protected fun getParcelFileDescriptor(
        loc: Location,
        accessMode: String,
        opts: Bundle?
    ): ParcelFileDescriptor {
        return Single.create { s: SingleEmitter<ParcelFileDescriptor?> ->
            s.onSuccess(
                getParcelFileDescriptor(
                    this,
                    loc,
                    accessMode,
                    opts
                )
            )
        }.subscribeOn
        (Schedulers.io()).blockingGet()
    }

    protected fun updateFS(
        uri: Uri,
        values: ContentValues,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        val loc = getLocationFromProviderUri(uri)
        return if (create(loc).filter
                (SelectionChecker(loc, selection, selectionArgs)).map<Boolean>
                (Function<CachedPathInfo, Boolean> { cpi: CachedPathInfo ->
                if (cpi.isFile) {
                    cpi.path!!.file.rename(values.getAsString(FSCursorBase.Companion.COLUMN_NAME))
                    return@map true.toInt()
                } else if (cpi.isDirectory) {
                    cpi.path!!.directory.rename(values.getAsString(FSCursorBase.Companion.COLUMN_NAME))
                    return@map true.toInt()
                }
                false
            }).subscribeOn(Schedulers.io()).blockingGet()
        ) 1 else 0
    }

    protected fun deleteFromFS(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        val loc = getLocationFromProviderUri(uri)
        return if (create(loc).filter
                (SelectionChecker(loc, selection, selectionArgs)).map<Boolean>
                (Function<CachedPathInfo, Boolean> { cpi: CachedPathInfo ->
                if (cpi.isFile) {
                    cpi.path!!.file.delete()
                    return@map true.toInt()
                } else if (cpi.isDirectory) {
                    cpi.path!!.directory.delete()
                    return@map true.toInt()
                }
                false
            }).subscribeOn(Schedulers.io()).blockingGet()
        ) 1 else 0
    }

    protected fun insertMeta(uri: Uri, contentValues: ContentValues): Uri? {
        return Single.create<Uri?> { em: SingleEmitter<Uri?> ->
            val loc = getLocationFromProviderUri(uri)
            val basePath = loc.currentPath
            require(basePath.isDirectory) { "Wrong parent folder: $basePath" }
            val name = contentValues.getAsString(FSCursorBase.Companion.COLUMN_NAME)
            val res =
                if (contentValues.getAsBoolean(FSCursorBase.Companion.COLUMN_IS_FOLDER)) basePath.directory.createDirectory(
                    name
                ) else basePath.directory.createFile(name)
            loc.currentPath = res.path
            em.onSuccess(loc.locationUri)
        }.subscribeOn(Schedulers.io()).blockingGet()
    }

    protected fun setCurrentSelection(contentValues: ContentValues) {
        val lm = LocationsManager.getLocationsManager(context, true)
        var selection = _currentSelection
        if (selection == null) selection = PathsStore(lm)
        if (contentValues.containsKey(COLUMN_LOCATION)) try {
            selection.location = lm.getLocation(
                Uri.parse(
                    contentValues.getAsString(
                        COLUMN_LOCATION
                    )
                )
            )
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed getting location from uri")
        }
        if (contentValues.containsKey(FSCursorBase.Companion.COLUMN_PATH)) {
            requireNotNull(selection.location) { "Location is not set" }
            try {
                selection.pathsStore.add(
                    selection.location!!.fs.getPath(
                        contentValues.getAsString(
                            FSCursorBase.Companion.COLUMN_PATH
                        )
                    )
                )
            } catch (e: Exception) {
                throw IllegalArgumentException("Failed getting location from uri")
            }
        }
        _currentSelection = selection
    }

    protected fun queryMeta(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Cursor {
        val loc = getLocationFromProviderUri(uri)
        return FSCursor(
            context,
            loc,
            projection ?: ALL_META_COLUMNS,
            selection,
            selectionArgs,
            true
        )
    }

    protected fun querySelection(projection: Array<String>?): Cursor {
        var projection = projection
        checkProjection(projection, ALL_SELECTION_COLUMNS)
        if (projection == null) projection = ALL_SELECTION_COLUMNS
        val selection = _currentSelection
        val res = MatrixCursor(projection)
        if (selection == null) return res
        val loc = selection.location
        for (p in selection.pathsStore) {
            val copy = loc!!.copy()
            copy.currentPath = p
            res.addRow(listOf(copy.locationUri.toString()))
        }
        return res
    }

    protected fun getContentMimeType(uri: Uri, requestedMime: String?): Array<String> {
        val loc = getLocationFromProviderUri(uri)
        return getContentMimeType(loc, requestedMime)
    }

    protected fun getContentMimeType(uri: Uri): String? {
        val loc = getLocationFromProviderUri(uri)
        return getContentMimeType(loc)
    }

    protected fun getContentMimeType(loc: Location): String? {
        try {
            val cpi: CachedPathInfo = create
            (loc).subscribeOn
            (Schedulers.io()).blockingGet
            ()
            return if (cpi == null) null else if (cpi.isFile) FileOpsService.getMimeTypeFromExtension(
                context, loc.currentPath
            ) else null
        } catch (e: IOException) {
            Logger.log(e)
            return null
        }
    }

    protected fun getContentMimeType(loc: Location, requestedMime: String?): Array<String> {
        val cd = ClipDescription(null, arrayOf(getContentMimeType(loc)))
        return cd.filterMimeTypes(requestedMime)
    }


    protected fun getMetaMimeType(uri: Uri): String? {
        val loc = getLocationFromProviderUri(uri)
        val cpi = create(loc).blockingGet()
        return if (cpi == null) null else if (cpi.isFile) MIME_TYPE_FILE_META else if (cpi.isDirectory) MIME_TYPE_FOLDER_META else null
    }

    protected fun checkProjection(projection: Array<String>?, columns: Array<String>) {
        if (projection != null) {
            for (col in projection) require(
                Arrays.asList(*columns).contains(col)
            ) { "Wrong projection column: $col" }
        }
    }


    protected fun getLocationFromProviderUri(providerUri: Uri): Location {
        return getLocationFromProviderUri(context, providerUri)
    }

    companion object {
        const val COLUMN_LOCATION: String = "location"

        const val MIME_TYPE_FILE_META: String = "vnd.android.cursor.item/file"
        const val MIME_TYPE_FOLDER_META: String = "vnd.android.cursor.dir/folder"
        const val MIME_TYPE_SELECTION: String = "vnd.android.cursor.dir/selection"

        const val OPTION_OFFSET: String = "offset"
        const val OPTION_NUM_BYTES: String = "num_bytes"

        @JvmStatic
        fun hasSelectionInClipboard(clipboard: ClipboardManager): Boolean {
            if (!clipboard.hasPrimaryClip()) {
                Logger.debug("hasSelectionInClipboard: clipboard doesn't have a primary clip")
                return false
            }
            val clip = clipboard.primaryClip
            if (clip != null) {
                if (GlobalConfig.isDebug()) Logger.debug(
                    String.format(
                        "hasSelectionInClipboard: clip = %s",
                        clip
                    )
                )
                return clip.itemCount > 0 && MainContentProvider.isClipboardUri(clip.getItemAt(0).uri)
            }

            Logger.debug("hasSelectionInClipboard: clip = null")
            return false
        }

        fun isClipboardUri(providerUri: Uri?): Boolean {
            return providerUri != null &&
                    MainContentProvider.AUTHORITY == providerUri.host && providerUri.pathSegments.size >= 2
        }

        fun getLocationFromProviderUri(context: Context?, providerUri: Uri): Location {
            try {
                return LocationsManager.getLocationsManager(context, true).getLocation
                (getLocationUriFromProviderUri(providerUri))
            } catch (e: Exception) {
                throw IllegalArgumentException("Wrong location uri", e)
            }
        }

        fun getLocationUriFromProviderUri(providerUri: Uri): Uri? {
            val path = providerUri.path ?: return null
            val parts = StringPathUtil(path).components
            if (parts.size < 2) return null
            val encodedLocationUri = parts[1]
            val locationUriBytes = Base64.decode(
                encodedLocationUri,
                Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE
            )
            return Uri.parse(String(locationUriBytes, Charset.defaultCharset()))
        }

        fun getParcelFileDescriptor(
            cp: ContentProvider,
            loc: Location,
            accessMode: String,
            opts: Bundle?
        ): ParcelFileDescriptor {
            try {
                val am = Util.getAccessModeFromString(accessMode)
                if (!loc.currentPath.isFile && am == Read) throw FileNotFoundException()
                val f = loc.currentPath.file
                val fd = f.getFileDescriptor(am)
                if (fd != null) return fd
                if (am == Read || am == Write) {
                    val s = UserSettings.getSettings(cp.context)
                    if (!s.forceTempFiles()) {
                        return if (am == Read)  //    return writeToPipe(f, opts == null ? new Bundle() : opts);
                        {
                            //String mime = FileOpsService.getMimeTypeFromExtension(cp.getContext(), loc.getCurrentPath());
                            //return cp.openPipeHelper(srcUri, mime, opts, f, new PipeWriter());
                            writeToPipe(f, opts!!)
                        } else readFromPipe(
                            f,
                            opts ?: Bundle()
                        )
                    }
                }

                var parentPath = loc.currentPath
                try {
                    parentPath = parentPath.parentPath
                } catch (ignored: IOException) {
                }
                val mode = Util.getParcelFileDescriptorModeFromAccessMode(am)
                val tmpLocation = copyFileToTmpLocation(loc, parentPath, f, mode, cp)
                val u = tmpLocation.getDeviceAccessibleUri(tmpLocation.currentPath)
                if (u != null && ContentResolver.SCHEME_FILE.equals(u.scheme, ignoreCase = true)) {
                    val jf = java.io.File(u.path)
                    return if (mode == ParcelFileDescriptor.MODE_READ_ONLY || VERSION.SDK_INT < VERSION_CODES.KITKAT) ParcelFileDescriptor.open(
                        jf,
                        mode
                    ) else ParcelFileDescriptor.open(
                        jf, mode, Handler(Looper.getMainLooper())
                    ) { e: IOException? ->
                        if (e != null) Logger.showAndLog(cp.context, e)
                        else FileOpsService.saveChangedFile(
                            cp.context,
                            SrcDstSingle(tmpLocation, loc)
                        )
                    }
                }
                return tmpLocation.currentPath.file.getFileDescriptor(am)
            } catch (e: IOException) {
                Logger.log(e)
                throw RuntimeException(e)
            }
        }

        @Throws(IOException::class)
        private fun copyFileToTmpLocation(
            srcLoc: Location,
            parentPath: Path,
            srcFile: File,
            mode: Int,
            cp: ContentProvider
        ): Location {
            if (!srcLoc.currentPath.exists() && (mode and ParcelFileDescriptor.MODE_CREATE) == 0) throw IOException(
                "File doesn't exist"
            )

            val tmpLocation = getTmpLocation(
                srcLoc,
                parentPath,
                cp.context,
                UserSettings.getSettings(cp.context).workDir,
                mode != ParcelFileDescriptor.MODE_READ_ONLY
            )
            var dst: File? = null
            val dstFilePath = PathUtil.buildPath(tmpLocation.currentPath, srcFile.name)
            if ((mode and ParcelFileDescriptor.MODE_TRUNCATE) == 0) {
                if (dstFilePath != null && dstFilePath.isFile) {
                    val dstLocation = tmpLocation.copy()
                    dstLocation.currentPath = dstFilePath
                    if (!getMonitor(cp.context!!).isUpdateRequired(srcLoc, dstLocation)) dst =
                        dstFilePath.file
                }
                if (dst == null) dst =
                    Util.copyFile(srcFile, tmpLocation.currentPath.directory, srcFile.name)
            } else {
                if (dstFilePath != null && dstFilePath.isFile) WipeFilesTask.wipeFileRnd(dstFilePath.file)
                dst = tmpLocation.currentPath.directory.createFile(srcFile.name)
            }
            tmpLocation.currentPath = dst!!.path
            val srcFolderLocation = srcLoc.copy()
            srcFolderLocation.currentPath = parentPath
            val u = tmpLocation.getDeviceAccessibleUri(dst.path)
            if (u == null || !ContentResolver.SCHEME_FILE.equals(
                    u.scheme,
                    ignoreCase = true
                )
            ) getMonitor(
                cp.context!!
            ).addFileToMonitor(
                srcLoc,
                srcFolderLocation,
                tmpLocation,
                mode == ParcelFileDescriptor.MODE_READ_ONLY
            )

            return tmpLocation
        }


        @Throws(IOException::class)
        private fun readFromPipe(targetFile: File, opts: Bundle): ParcelFileDescriptor {
            val pfds = ParcelFileDescriptor.createPipe()
            Completable.create { s: CompletableEmitter ->
                val fin = FileInputStream(pfds[0].fileDescriptor)
                try {
                    val pi = CancellableProgressInfo()
                    s.setCancellable(pi)
                    Util.copyFileFromInputStream(
                        fin,
                        targetFile,
                        opts.getLong(OPTION_OFFSET, 0),
                        opts.getLong(OPTION_NUM_BYTES, -1),
                        pi
                    )
                } finally {
                    fin.close()
                }
                pfds[0].close()
                s.onComplete()
            }.subscribeOn
            (Schedulers.io()).subscribe
            (Action {}, io.reactivex.functions.Consumer<kotlin.Throwable?> { e: Throwable? ->
                Logger.log(
                    e
                )
            })
            return pfds[1]
        }

        @Throws(IOException::class)
        private fun writeToPipe(srcFile: File, opts: Bundle): ParcelFileDescriptor {
            val pfds = ParcelFileDescriptor.createPipe()
            Completable.create { s: CompletableEmitter ->
                val fout = FileOutputStream(pfds[1].fileDescriptor)
                try {
                    val pi = CancellableProgressInfo()
                    s.setCancellable(pi)
                    Util.copyFileToOutputStream(
                        fout,
                        srcFile,
                        opts.getLong(OPTION_OFFSET, 0),
                        opts.getLong(OPTION_NUM_BYTES, -1),
                        pi
                    )
                } finally {
                    fout.close()
                }
                pfds[1].close()
                s.onComplete()
            }.subscribeOn
            (Schedulers.newThread()).subscribe
            (Action {}, io.reactivex.functions.Consumer<kotlin.Throwable?> { e: Throwable? ->
                Logger.log(
                    e
                )
            })
            return pfds[0]
        }


        /*
    public static Uri getCurrentSelectionUri()
    {
        return CURRENT_SELECTION_URI;
    }
    */
        @JvmStatic
        fun getContentUriFromLocation(loc: Location, path: Path?): Uri {
            val copy = loc.copy()
            copy.currentPath = path
            return getContentUriFromLocation(copy)
        }

        fun getContentUriFromLocation(loc: Location): Uri {
            return getContentUriFromLocationUri(loc.locationUri)
            /*try
        {
            if (loc.getCurrentPath().isFile())
            {
                Uri.Builder ub = uri.buildUpon();
                ub.appendPath(loc.getCurrentPath().getFile().getName());
                uri = ub.build();
            }
        }
        catch (IOException e)
        {
            Logger.log(e);
        }
        return uri;*/
        }

        fun getContentUriFromLocationUri(locationUri: Uri): Uri {
            val ub = CONTENT_URI.buildUpon()
            appendLocationUriToProviderUri(ub, locationUri)
            return ub.build()
        }

        fun getMetaUriFromLocationUri(locationUri: Uri): Uri {
            val ub = META_URI.buildUpon()
            appendLocationUriToProviderUri(ub, locationUri)
            return ub.build()
        }

        fun appendLocationUriToProviderUri(ub: Builder, locationUri: Uri) {
            ub.appendPath(
                Base64.encodeToString(
                    locationUri.toString().toByteArray(),
                    Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE
                )
            )
        }

        protected val ALL_SELECTION_COLUMNS: Array<String> = arrayOf(COLUMN_LOCATION)

        protected const val META_PATH: String = "fs"
        protected const val META_PATH_CODE: Int = 10
        protected val META_URI: Uri = Uri.parse(
            ("content://"
                    + MainContentProvider.AUTHORITY + "/" + META_PATH)
        )
        protected const val CONTENT_PATH: String = "content"
        protected const val CONTENT_PATH_CODE: Int = 20
        protected val CONTENT_URI: Uri = Uri.parse(
            ("content://"
                    + MainContentProvider.AUTHORITY + "/" + CONTENT_PATH)
        )
        protected const val CURRENT_SELECTION_PATH: String = "selection"
        protected const val CURRENT_SELECTION_PATH_CODE: Int = 30
        protected val CURRENT_SELECTION_URI: Uri = Uri.parse(
            ("content://"
                    + MainContentProvider.AUTHORITY + "/" + CURRENT_SELECTION_PATH)
        )


        private val _uriMatcher = UriMatcher(UriMatcher.NO_MATCH)

        init {
            _uriMatcher.addURI(MainContentProvider.AUTHORITY, META_PATH + "/*", META_PATH_CODE)
            _uriMatcher.addURI(
                MainContentProvider.AUTHORITY,
                CONTENT_PATH + "/*",
                CONTENT_PATH_CODE
            )
            _uriMatcher.addURI(
                MainContentProvider.AUTHORITY,
                CURRENT_SELECTION_PATH,
                CURRENT_SELECTION_PATH_CODE
            )
        }

        private val ALL_META_COLUMNS = arrayOf<String>(
            FSCursorBase.Companion.COLUMN_ID,
            FSCursorBase.Companion.COLUMN_NAME,
            FSCursorBase.Companion.COLUMN_TITLE,
            FSCursorBase.Companion.COLUMN_SIZE,
            FSCursorBase.Companion.COLUMN_LAST_MODIFIED,
            FSCursorBase.Companion.COLUMN_IS_FOLDER,
            FSCursorBase.Companion.COLUMN_PATH
        )

        val emptyMetaCursor: Cursor = MatrixCursor(ALL_META_COLUMNS, 0)
    }
}
