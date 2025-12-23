package com.sovworks.eds.android.filemanager.fragments

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.filemanager.DirectorySettings
import com.sovworks.eds.android.filemanager.FileListViewAdapter
import com.sovworks.eds.android.filemanager.activities.FileManagerActivity
import com.sovworks.eds.android.filemanager.activities.FileManagerActivityBase.Companion.getStartLocation
import com.sovworks.eds.android.filemanager.comparators.FileNamesComparator
import com.sovworks.eds.android.filemanager.comparators.FileNamesNumericComparator
import com.sovworks.eds.android.filemanager.comparators.FileSizesComparator
import com.sovworks.eds.android.filemanager.comparators.ModDateComparator
import com.sovworks.eds.android.filemanager.fragments.FileListDataFragment.LoadLocationInfo.Stage.FinishedLoading
import com.sovworks.eds.android.filemanager.fragments.FileListDataFragment.LoadLocationInfo.Stage.Loading
import com.sovworks.eds.android.filemanager.fragments.FileListDataFragment.LoadLocationInfo.Stage.StartedLoading
import com.sovworks.eds.android.filemanager.records.BrowserRecord
import com.sovworks.eds.android.filemanager.tasks.CreateNewFile
import com.sovworks.eds.android.filemanager.tasks.LoadDirSettingsObservable
import com.sovworks.eds.android.filemanager.tasks.ReadDir
import com.sovworks.eds.android.helpers.CachedPathInfo
import com.sovworks.eds.android.helpers.CachedPathInfoBase
import com.sovworks.eds.android.helpers.ExtendedFileInfoLoader
import com.sovworks.eds.android.locations.activities.OpenLocationsActivity
import com.sovworks.eds.android.settings.UserSettings
import com.sovworks.eds.fs.Path
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.LocationsManager
import com.sovworks.eds.settings.GlobalConfig
import com.sovworks.eds.settings.Settings
import com.sovworks.eds.settings.SettingsCommon
import com.trello.rxlifecycle2.android.FragmentEvent
import com.trello.rxlifecycle2.android.FragmentEvent.DESTROY
import com.trello.rxlifecycle2.android.FragmentEvent.RESUME
import com.trello.rxlifecycle2.components.RxFragment
import io.reactivex.Observable
import io.reactivex.ObservableSource
import io.reactivex.Single
import io.reactivex.SingleEmitter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Action
import io.reactivex.functions.Consumer
import io.reactivex.functions.Function
import io.reactivex.functions.Predicate
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import java.util.NavigableSet
import java.util.Stack
import java.util.TreeSet
import java.util.concurrent.CancellationException


class FileListDataFragment : RxFragment() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        retainInstance = true
        location = fallbackLocation
        _locationsManager = LocationsManager.getLocationsManager(activity)
        fileList = TreeSet(initSorter())
        loadLocation(state, true)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        //TODO remove dependency
        synchronized(filesListSync) {
            if (fileList != null) for (br in fileList!!) br.setHostActivity(
                activity as FileManagerActivity
            )
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        if (requestCode == REQUEST_CODE_OPEN_LOCATION) {
            if (resultCode != Activity.RESULT_OK) activity.intent = Intent()
            lifecycle()
                .filter { event: FragmentEvent -> event == RESUME }
                .firstElement()
                .subscribe(
                    { loadLocation(null, false) },
                    { err -> if (err !is CancellationException) Logger.log(err) }
                )
        } else super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onDetach() {
        super.onDetach()
        //TODO remove dependency
        synchronized(filesListSync) {
            if (fileList != null) for (br in fileList!!) br.setHostActivity(null)
        }
    }

    override fun onDestroy() {
        cancelReadDirTask()
        navigHistory.clear()
        synchronized(filesListSync) {
            if (fileList != null) {
                fileList!!.clear()
                fileList = null
            }
        }
        _locationsManager = null
        super.onDestroy()
    }

    override fun onSaveInstanceState(state: Bundle) {
        super.onSaveInstanceState(state)
        if (location != null) {
            val selectedPaths = selectedPaths
            LocationsManager.storePathsInBundle(state, location, selectedPaths)
        }
        state.putParcelableArrayList(
            STATE_NAVIG_HISTORY, ArrayList(
                navigHistory
            )
        )
    }

    val selectedFiles: ArrayList<BrowserRecord>
        get() {
            val res = ArrayList<BrowserRecord>()
            synchronized(filesListSync) {
                if (fileList != null) for (rec in fileList!!) {
                    if (rec.isSelected) res.add(rec)
                }
            }
            return res
        }

    fun hasSelectedFiles(): Boolean {
        synchronized(filesListSync) {
            if (fileList != null) for (rec in fileList!!) {
                if (rec.isSelected) return true
            }
        }
        return false
    }

    fun findLoadedFileByPath(path: Path): BrowserRecord? {
        synchronized(filesListSync) {
            if (fileList != null) for (f in fileList!!) if (path == f.path) return f
        }
        return null
    }

    val selectedPaths: ArrayList<Path>
        get() = FileListViewFragment.getPathsFromRecords(selectedFiles)

    fun copyToAdapter(adapter: FileListViewAdapter) {
        synchronized(filesListSync) {
            adapter.clear()
            if (fileList != null) adapter.addAll(fileList!!)
        }
    }

    val locationLoadingObservable: Observable<LoadLocationInfo>
        get() = _locationLoading

    val loadRecordObservable: Observable<BrowserRecord?>
        get() = _recordLoadedSubject

    class LoadLocationInfo : Cloneable {
        internal enum class Stage {
            StartedLoading,
            Loading,
            FinishedLoading
        }

        var stage: Stage? = null
        var folder: CachedPathInfo? = null
        var file: BrowserRecord? = null
        var folderSettings: DirectorySettings? = null
        var location: Location? = null

        public override fun clone(): LoadLocationInfo {
            return try {
                super.clone() as LoadLocationInfo
            } catch (ignore: CloneNotSupportedException) {
                null
            }
        }
    }

    @Synchronized
    fun readLocation(location: Location?, selectedFiles: Collection<Path>?) {
        Logger.debug(TAG + " readCurrentLocation")
        cancelReadDirTask()
        clearCurrentFiles()
        this.location = location
        if (this.location == null) return

        val activity = activity as FileManagerActivity ?: return
        val context = activity.applicationContext
        val showRootFolder = activity.intent.getBooleanExtra
        (
                FileManagerActivity.EXTRA_ALLOW_SELECT_ROOT_FOLDER,
        activity.isSelectAction && activity.allowFolderSelect()
        )
        val startInfo = LoadLocationInfo()
        startInfo.stage = StartedLoading
        startInfo.location = location
        Logger.debug(TAG + ": _locationLoading.onNext started loading")
        _locationLoading.onNext(startInfo)
        var observable: Observable<BrowserRecord?> = LoadDirSettingsObservable.create
        (location).toSingle
        (DirectorySettings()).onErrorReturn
        (Function { err: Throwable? ->
            Logger.log(err)
            DirectorySettings()
        }).map<LoadLocationInfo>
        (Function<DirectorySettings, LoadLocationInfo> { dirSettings: DirectorySettings? ->
            val loadLocationInfo = LoadLocationInfo()
            loadLocationInfo.stage = Loading
            loadLocationInfo.folderSettings = dirSettings
            if (location!!.currentPath.isFile) {
                loadLocationInfo.file = ReadDir.getBrowserRecordFromFsRecord(
                    context,
                    location,
                    location.currentPath,
                    dirSettings
                )
                val parentLocation = location.copy()
                parentLocation.currentPath = location.currentPath.parentPath
                loadLocationInfo.location = parentLocation
            } else loadLocationInfo.location = location
            val cpi: CachedPathInfo = CachedPathInfoBase()
            cpi.init(loadLocationInfo.location!!.currentPath)
            loadLocationInfo.folder = cpi
            loadLocationInfo
        }).observeOn
        (AndroidSchedulers.mainThread()).doOnSuccess
        (Consumer<LoadLocationInfo> { loadLocationInfo: LoadLocationInfo ->
            directorySettings = loadLocationInfo.folderSettings
            Logger.debug(TAG + ": _locationLoading.onNext loading")
            _locationLoading.onNext(loadLocationInfo)
        }).observeOn
        (Schedulers.io()).flatMapObservable<BrowserRecord>
        (Function<LoadLocationInfo, ObservableSource<out BrowserRecord>> { loadLocationInfo: LoadLocationInfo ->
            ReadDir.createObservable(
                context,
                loadLocationInfo.location,
                selectedFiles,
                loadLocationInfo.folderSettings,
                showRootFolder

            )
        })
        if (TEST_READING_OBSERVABLE != null) {
            observable = observable.doOnSubscribe
            (Consumer<Disposable> { res: Disposable? -> TEST_READING_OBSERVABLE!!.onNext(true) }).doFinally
            (Action { TEST_READING_OBSERVABLE!!.onNext(false) })
        }

        _readLocationObserver = observable.compose<BrowserRecord>
        (bindUntilEvent<BrowserRecord>(DESTROY)).subscribeOn
        (Schedulers.io()).observeOn
        (AndroidSchedulers.mainThread()).doFinally
        (Action { sendFinishedLoading(location) }).subscribe
        (Consumer { loadedRecord: BrowserRecord ->
            addRecordToList(loadedRecord)
            _recordLoadedSubject.onNext(loadedRecord)
        },
        Consumer { err: Throwable? ->
            if (err !is CancellationException) Logger.log(err)
        }
        )
    }

    private fun sendFinishedLoading(location: Location?) {
        Logger.debug(TAG + ": _locationLoading.onNext isLoading = false")
        val loadLocationInfo = LoadLocationInfo()
        loadLocationInfo.location = location
        loadLocationInfo.stage = FinishedLoading
        _locationLoading.onNext(loadLocationInfo)
    }

    fun makeNewFile(name: String?, type: Int): Single<BrowserRecord?> {
        return Single.create<BrowserRecord?> { emitter: SingleEmitter<BrowserRecord?> ->
            CreateNewFile.createObservable(
                activity.applicationContext,
                location,
                name,
                type,
                false
            ).compose(bindToLifecycle()).subscribeOn
            (Schedulers.io()).observeOn
            (AndroidSchedulers.mainThread()).subscribe
            (Consumer { rec: BrowserRecord ->
                addRecordToList(rec)
                if (!emitter.isDisposed) emitter.onSuccess(rec)
            },
            Consumer { err: Throwable? ->
                Logger.showAndLog(
                    activity, err
                )
            })
        }
    }

    fun createOrFindFile(name: String?, type: Int): Single<BrowserRecord?> {
        return Single.create<BrowserRecord?> { emitter: SingleEmitter<BrowserRecord?> ->
            CreateNewFile.createObservable(
                activity.applicationContext,
                location,
                name,
                type,
                true
            ).compose(bindToLifecycle()).subscribeOn
            (Schedulers.io()).observeOn
            (AndroidSchedulers.mainThread()).subscribe
            (Consumer { rec: BrowserRecord ->
                if (findLoadedFileByPath(rec.path) == null) addRecordToList(rec)
                if (!emitter.isDisposed) emitter.onSuccess(rec)
            },
            Consumer { err: Throwable? ->
                Logger.showAndLog(
                    activity, err
                )
            })
        }
    }

    private fun addRecordToList(rec: BrowserRecord) {
        val fm = activity as FileManagerActivity
        rec.setHostActivity(fm)
        synchronized(filesListSync) {
            if (fileList != null) fileList!!.add(rec)
        }
    }

    fun reSortFiles() {
        var loadInfo = LoadLocationInfo()
        loadInfo.stage = StartedLoading
        loadInfo.location = location
        Logger.debug(TAG + ": _locationLoading.onNext started loading (sorting)")
        _locationLoading.onNext(loadInfo)
        synchronized(filesListSync) {
            val n = TreeSet(initSorter())
            if (fileList != null) n.addAll(fileList!!)
            fileList = n
        }
        loadInfo = loadInfo.clone()
        loadInfo.stage = FinishedLoading
        Logger.debug(TAG + ": _locationLoading.onNext finished loading (sorting)")
        _locationLoading.onNext(loadInfo)
    }

    fun removeLocationFromHistory(loc: Location) {
        val id = loc.id
        if (id != null) {
            val cur: List<HistoryItem> = ArrayList(navigHistory)
            for (hi in cur) if (id == hi.locationId) navigHistory.remove(hi)
        }
    }

    class HistoryItem : Parcelable {
        override fun describeContents(): Int {
            return 0
        }

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeParcelable(locationUri, flags)
            parcel.writeInt(scrollPosition)
            parcel.writeString(locationId)
        }

        internal constructor()

        @JvmField
        var locationUri: Uri? = null
        @JvmField
        var scrollPosition: Int = 0
        @JvmField
        var locationId: String? = null

        internal constructor(p: Parcel) {
            locationUri = p.readParcelable(ClassLoader.getSystemClassLoader())
            scrollPosition = p.readInt()
            locationId = p.readString()
        }

        companion object {
            val CREATOR: Creator<HistoryItem> = object : Creator<HistoryItem?> {
                override fun createFromParcel(`in`: Parcel): HistoryItem? {
                    return HistoryItem(`in`)
                }

                override fun newArray(size: Int): Array<HistoryItem?> {
                    return arrayOfNulls(size)
                }
            }
        }
    }

    private var _locationsManager: LocationsManager? = null
    var location: Location? = null
        private set
    var directorySettings: DirectorySettings? = null
        private set
    var fileList: NavigableSet<BrowserRecord>? = null
        private set
    val filesListSync: Any = Any()
    val navigHistory: Stack<HistoryItem> = Stack()
    private val _locationLoading: Subject<LoadLocationInfo> = BehaviorSubject.create()
    private val _recordLoadedSubject: Subject<BrowserRecord?> = PublishSubject.create()
    private var _readLocationObserver: Disposable? = null

    @Synchronized
    private fun cancelReadDirTask() {
        if (_readLocationObserver != null) {
            _readLocationObserver!!.dispose()
            _readLocationObserver = null
        }
    }

    private fun restoreNavigHistory(state: Bundle) {
        if (state.containsKey(STATE_NAVIG_HISTORY)) {
            val l = state.getParcelableArrayList<HistoryItem>(STATE_NAVIG_HISTORY)
            if (l != null) navigHistory.addAll(l)
        }
    }

    fun loadLocation(savedState: Bundle?, autoOpen: Boolean) {
        val uri = getLocationUri(activity.intent, savedState)
        var loc: Location? = null
        try {
            loc = initLocationFromUri(uri)
        } catch (e: Exception) {
            Logger.showAndLog(activity, e)
        }
        if (loc == null) loc = fallbackLocation

        if (autoOpen && !LocationsManager.isOpen(loc)) {
            val i = Intent(activity, OpenLocationsActivity::class.java)
            LocationsManager.storeLocationsInIntent(i, listOf<Location>(loc))
            startActivityForResult(i, REQUEST_CODE_OPEN_LOCATION)
        } else if (savedState == null) {
            resetIntent()
            readLocation(loc, null)
        } else restoreState(savedState)
    }

    private fun resetIntent() {
        val i = activity.intent
        if (i.action == null || Intent.ACTION_MAIN == i.action) {
            i.setData(null)
            activity.intent = i
        }
    }

    private fun clearCurrentFiles() {
        synchronized(filesListSync) {
            if (location != null) {
                val loader = ExtendedFileInfoLoader.getInstance()
                for (br in fileList!!) loader.detachRecord(location!!.id, br)
            }
            fileList!!.clear()
        }
        directorySettings = null
        location = null
    }

    private fun restoreState(state: Bundle) {
        restoreNavigHistory(state)
        val selectedFiles = ArrayList<Path>()
        val loc = _locationsManager!!.getFromBundle(state, selectedFiles)
        if (loc != null) readLocation(loc, selectedFiles)
    }

    @Throws(Exception::class)
    private fun initLocationFromUri(locationUri: Uri?): Location? {
        return if (locationUri != null)
            _locationsManager!!.getLocation(locationUri)
        else
            null
    }

    private val fallbackLocation: Location
        get() = FileManagerActivity.getStartLocation(activity)

    private fun initSorter(): Comparator<BrowserRecord>? {
        return getComparator(
            UserSettings.getSettings(
                activity
            )
        )
    }

    companion object {
        fun newInstance(): FileListDataFragment {
            return FileListDataFragment()
        }

        var TEST_READING_OBSERVABLE: Subject<Boolean>? = null

        init {
            if (GlobalConfig.isTest()) TEST_READING_OBSERVABLE =
                BehaviorSubject.createDefault(false)
        }

        fun <T : CachedPathInfo?> getComparator(settings: Settings): Comparator<T>? {
            return when (settings.filesSortMode) {
                SettingsCommon.FB_SORT_FILENAME_ASC -> FileNamesComparator(true)
                SettingsCommon.FB_SORT_FILENAME_DESC -> FileNamesComparator(false)
                SettingsCommon.FB_SORT_FILENAME_NUM_ASC -> FileNamesNumericComparator(true)
                SettingsCommon.FB_SORT_FILENAME_NUM_DESC -> FileNamesNumericComparator(false)
                SettingsCommon.FB_SORT_SIZE_ASC -> FileSizesComparator(true)
                SettingsCommon.FB_SORT_SIZE_DESC -> FileSizesComparator(false)
                SettingsCommon.FB_SORT_DATE_ASC -> ModDateComparator(true)
                SettingsCommon.FB_SORT_DATE_DESC -> ModDateComparator(false)
                else -> null
            }
        }

        fun getLocationUri(intent: Intent, state: Bundle?): Uri? {
            val locUri = if (state != null) state.getParcelable(LocationsManager.PARAM_LOCATION_URI)
            else intent.data
            return locUri
        }

        const val TAG: String =
            "com.sovworks.eds.android.filemanager.fragments.FileListDataFragment"

        private const val STATE_NAVIG_HISTORY = "com.sovworks.eds.android.PATH_HISTORY"

        private const val REQUEST_CODE_OPEN_LOCATION = 1
    }
}
