package com.sovworks.eds.android.filemanager.fragments

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipData.Item
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.ActionMode
import android.view.ActionMode.Callback
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.AdapterView.OnItemLongClickListener
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.R
import com.sovworks.eds.android.filemanager.FileListViewAdapter
import com.sovworks.eds.android.filemanager.FileManagerFragment
import com.sovworks.eds.android.filemanager.activities.FileManagerActivity
import com.sovworks.eds.android.filemanager.activities.FileManagerActivity.Companion.openFileManager
import com.sovworks.eds.android.filemanager.activities.FileManagerActivityBase.isSelectAction
import com.sovworks.eds.android.filemanager.activities.FileManagerActivityBase.isSingleSelectionMode
import com.sovworks.eds.android.filemanager.activities.FileManagerActivityBase.realLocation
import com.sovworks.eds.android.filemanager.dialogs.DeleteConfirmationDialog.Companion.showDialog
import com.sovworks.eds.android.filemanager.dialogs.NewFileDialog
import com.sovworks.eds.android.filemanager.dialogs.NewFileDialog.Receiver
import com.sovworks.eds.android.filemanager.dialogs.RenameFileDialog.Companion.showDialog
import com.sovworks.eds.android.filemanager.dialogs.SortDialog
import com.sovworks.eds.android.filemanager.dialogs.SortDialog.SortingReceiver
import com.sovworks.eds.android.filemanager.fragments.FileListDataFragment.HistoryItem
import com.sovworks.eds.android.filemanager.fragments.FileListDataFragment.LoadLocationInfo
import com.sovworks.eds.android.filemanager.fragments.FileListDataFragment.LoadLocationInfo.Stage.FinishedLoading
import com.sovworks.eds.android.filemanager.fragments.FileListDataFragment.LoadLocationInfo.Stage.Loading
import com.sovworks.eds.android.filemanager.fragments.FileListDataFragment.LoadLocationInfo.Stage.StartedLoading
import com.sovworks.eds.android.filemanager.records.BrowserRecord
import com.sovworks.eds.android.filemanager.records.ExecutableFileRecord
import com.sovworks.eds.android.filemanager.tasks.CopyToClipboardTask
import com.sovworks.eds.android.filemanager.tasks.CreateNewFile
import com.sovworks.eds.android.filemanager.tasks.OpenAsContainerTask
import com.sovworks.eds.android.filemanager.tasks.PrepareToSendTask
import com.sovworks.eds.android.filemanager.tasks.RenameFileTask
import com.sovworks.eds.android.fragments.TaskFragment.Result
import com.sovworks.eds.android.fragments.TaskFragment.TaskCallbacks
import com.sovworks.eds.android.fs.ContentResolverFs
import com.sovworks.eds.android.helpers.CachedPathInfo
import com.sovworks.eds.android.helpers.ExtendedFileInfoLoader
import com.sovworks.eds.android.helpers.ProgressDialogTaskFragmentCallbacks
import com.sovworks.eds.android.locations.ContentResolverLocation
import com.sovworks.eds.android.locations.DocumentTreeLocation
import com.sovworks.eds.android.locations.opener.fragments.LocationOpenerBaseFragment
import com.sovworks.eds.android.locations.opener.fragments.LocationOpenerBaseFragment.LocationOpenerResultReceiver
import com.sovworks.eds.android.providers.MainContentProvider
import com.sovworks.eds.android.service.FileOpsService
import com.sovworks.eds.android.settings.UserSettings
import com.sovworks.eds.android.settings.UserSettingsCommon
import com.sovworks.eds.fs.Path
import com.sovworks.eds.fs.util.SrcDstCollection
import com.sovworks.eds.fs.util.SrcDstGroup
import com.sovworks.eds.fs.util.SrcDstPlain
import com.sovworks.eds.fs.util.SrcDstRec
import com.sovworks.eds.fs.util.SrcDstSingle
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.LocationsManager
import com.sovworks.eds.settings.GlobalConfig
import com.trello.rxlifecycle2.android.FragmentEvent
import com.trello.rxlifecycle2.android.FragmentEvent.RESUME
import com.trello.rxlifecycle2.components.RxFragment
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Action
import io.reactivex.functions.Consumer
import io.reactivex.functions.Predicate
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject
import java.io.IOException
import java.util.concurrent.CancellationException

abstract class FileListViewFragmentBase : RxFragment(),
    SortingReceiver, FileManagerFragment, LocationOpenerResultReceiver, Receiver {
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        Logger.debug(TAG + " onActivityCreated")
        super.onActivityCreated(savedInstanceState)
        setHasOptionsMenu(true)
        _locationsManager = LocationsManager.getLocationsManager(activity)
        initListView()
        if (savedInstanceState != null) _scrollPosition = savedInstanceState.getInt(
            ARG_SCROLL_POSITION, 0
        )
    }

    override fun onStart() {
        Logger.debug(TAG + " onStart")
        super.onStart()
        val lv = listView
        val adapter = FileListViewAdapter(activity)
        lv.adapter = adapter
        _isReadingLocation = true
        _locationLoadingObserver = fileListDataFragment.getLocationLoadingObservable
        ().observeOn
        (AndroidSchedulers.mainThread()).compose<LoadLocationInfo>
        (bindToLifecycle<LoadLocationInfo>()).subscribe
        (Consumer { loadInfo: LoadLocationInfo ->
            when (loadInfo.stage) {
                StartedLoading -> setStartedLoading(loadInfo)
                Loading -> {
                    if (!_isReadingLocation) setStartedLoading(loadInfo)
                    setLocationLoading(loadInfo)
                }

                FinishedLoading -> if (_isReadingLocation) setLocationNotLoading()
            }
        }, { err ->
            if (err !is CancellationException) Logger.log(err)
        })
    }

    override fun onStop() {
        Logger.debug(TAG + " onStop")

        listView.adapter = null
        if (_locationLoadingObserver != null) {
            _locationLoadingObserver!!.dispose()
            _locationLoadingObserver = null
        }
        _isReadingLocation = false
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(ARG_SCROLL_POSITION, listView.firstVisiblePosition)
    }

    override fun onCreateOptionsMenu(menu: Menu, menuInflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, menuInflater)
        menuInflater.inflate(R.menu.file_list_menu, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        val isReading = _isReadingLocation
        val isSendAction = isSendAction
        val hasInClipboard = hasSelectionInClipboard()
        val isSelectAction = isSelectAction
        Logger.debug(
            String.format(
                "onPrepareOptionsMenu: isReading=%b isSendAction=%b hasInClipboard=%b isSelectAction=%b",
                isReading, isSendAction, hasInClipboard, isSelectAction
            )
        )

        menu.findItem(R.id.progressbar).setVisible(isReading)
        menu.findItem(R.id.copy)
            .setVisible(!isReading && !isSelectAction && (isSendAction || hasInClipboard))
        menu.findItem(R.id.move).setVisible(!isReading && !isSelectAction && hasInClipboard)

        menu.findItem(R.id.new_file).setVisible(
            !isReading && !isSendAction && allowCreateNewFile()
                    && (!isSelectAction || fileManagerActivity.allowFileSelect())
        )
        menu.findItem(R.id.new_dir).setVisible(
            !isReading
                    && allowCreateNewFolder()
        )
        menu.findItem(R.id.select_all)
            .setVisible((!isSelectAction || !isSingleSelectionMode) && !selectableFiles.isEmpty())
    }

    override fun onOptionsItemSelected(menuItem: MenuItem): Boolean {
        val mhi = MenuHandlerInfo()
        mhi.menuItemId = menuItem.itemId
        val res = handleMenu(mhi)
        if (res && mhi.clearSelection) {
            clearSelectedFlag()
            onSelectionChanged()
        }
        return res || super.onOptionsItemSelected(menuItem)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_SELECT_FROM_CONTENT_PROVIDER) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                returnSelectionFromContentProvider(data)
                if (VERSION.SDK_INT >= VERSION_CODES.KITKAT && data.data != null) {
                    activity.contentResolver.takePersistableUriPermission(
                        data.data!!,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                }
            }
        } else super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onResume() {
        super.onResume()
        if (haveSelectedFiles()) {
            startSelectionMode()
            if (_actionMode != null)  //sometimes it is null
                _actionMode!!.invalidate()
        }
        _cleanSelectionOnModeFinish = true
        ExtendedFileInfoLoader.getInstance().resumeViewUpdate()
    }

    override fun onPause() {
        super.onPause()
        ExtendedFileInfoLoader.getInstance().pauseViewUpdate()
        _cleanSelectionOnModeFinish = false
        if (isInSelectionMode) stopSelectionMode()
    }

    override fun onDestroyView() {
        _selectedFileEditText = null
        _currentPathTextView = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        _locationsManager = null
        super.onDestroy()
    }

    @SuppressLint("CommitPrefEdits", "ApplySharedPref")
    override fun applySort(sortMode: Int) {
        val e = UserSettings.getSettings(activity)
            .sharedPreferences
            .edit()
        if (sortMode == 0) e.remove(UserSettingsCommon.FILE_BROWSER_SORT_MODE)
        else e.putInt(UserSettings.FILE_BROWSER_SORT_MODE, sortMode)
        e.commit()
        fileListDataFragment.reSortFiles()
        updateSelectionMode()
    }

    fun renameFile(path: String?, newName: String?) {
        try {
            val loc = realLocation
            val curPath = loc!!.fs.getPath(path)
            val srcLoc = loc.copy()
            srcLoc.currentPath = curPath
            fragmentManager.beginTransaction
            ().add
            (
                    RenameFileTask.newInstance(srcLoc, newName),
            RenameFileTask.TAG
            ).commit
            ()
        } catch (e: IOException) {
            Logger.showAndLog(activity, e)
        }
    }

    override fun makeNewFile(name: String?, type: Int) {
        fileListDataFragment.makeNewFile(name, type).compose<BrowserRecord>
        (bindToLifecycle<BrowserRecord>()).subscribe
        (Consumer { res: BrowserRecord ->
            val adapter = adapter
            if (adapter != null) adapter.add(res)
            newRecordCreated(res)
        }, { err ->
            if (err !is CancellationException) Logger.log(err)
        })
    }

    fun deleteFiles(loc: Location, paths: List<Path?>?, wipe: Boolean) {
        if (wipe) {
            val targets = SrcDstRec.fromPathsNoDest(loc, true, paths)
            FileOpsService.wipeFiles(activity, targets)
        } else {
            val targets = if (isLocSupportsRecFolderDelete(loc)) SrcDstPlain.fromPaths(
                loc,
                null,
                paths
            ) else SrcDstRec.fromPathsNoDest(loc, true, paths)
            FileOpsService.deleteFiles(activity, targets)
        }
        Toast.makeText(activity, R.string.file_operation_started, Toast.LENGTH_SHORT).show()
    }

    fun selectFile(file: BrowserRecord) {
        if (isSelectAction && isSingleSelectionMode) clearSelectedFlag()
        file.isSelected = true
        if (_actionMode == null) startSelectionMode()
        file.updateView()
        onSelectionChanged()
    }

    fun unselectFile(file: BrowserRecord) {
        file.isSelected = false
        if (!haveSelectedFiles() && !isSelectAction) stopSelectionMode()
        else file.updateView()
        onSelectionChanged()
    }

    val openAsContainerTaskCallbacks: TaskCallbacks
        get() = object : ProgressDialogTaskFragmentCallbacks(activity, R.string.loading) {
            override fun onCompleted(args: Bundle, result: Result) {
                try {
                    val locToOpen = result.result as Location
                    if (locToOpen != null) lifecycle().filter
                    (Predicate<FragmentEvent> { event: FragmentEvent -> event == RESUME }).firstElement
                    ().subscribe
                    (Consumer { res: FragmentEvent? -> openLocation(locToOpen) }, { err ->
                        if (err !is CancellationException) Logger.log(err)
                    })
                } catch (e: Throwable) {
                    Logger.showAndLog(activity, e)
                }
            }
        }

    protected var _selectedFileEditText: EditText? = null
    protected var _currentPathTextView: TextView? = null
    protected var _locationsManager: LocationsManager? = null
    protected var _actionMode: ActionMode? = null
    private var _listView: ListView? = null
    private var _locationLoadingObserver: Disposable? = null
    private var _loadingRecordObserver: Disposable? = null
    private var _scrollPosition = 0

    protected var _isReadingLocation: Boolean = false
    protected var _changingSelectedFileText: Boolean = false
    protected var _cleanSelectionOnModeFinish: Boolean = false

    val isInSelectionMode: Boolean
        get() = _actionMode != null

    fun updateOptionsMenu() {
        //getFileManagerActivity().updateOptionsMenu();
        //if(_optionsMenu!=null)
        //    onPrepareOptionsMenu(_optionsMenu);
        fileManagerActivity.invalidateOptionsMenu()
    }

    private fun isLocSupportsRecFolderDelete(loc: Location): Boolean {
        return loc is DocumentTreeLocation
    }

    override fun onBackPressed(): Boolean {
        return goToPrevLocation()
    }

    private fun goToPrevLocation(): Boolean {
        val hs = fileListDataFragment.navigHistory
        while (!hs.isEmpty()) {
            val hi = hs.pop()
            val uri = hi!!.locationUri
            try {
                val loc = _locationsManager!!.getLocation(uri)
                if (loc != null && LocationsManager.isOpen(loc)) {
                    readLocation(fileListDataFragment, loc, hi.scrollPosition)
                    return true
                }
            } catch (e: Exception) {
                Logger.log(e)
            }
        }
        return false
    }

    fun goTo(location: Location?, scrollPosition: Int, addToHistory: Boolean) {
        val prevLocation = if (addToHistory) this.location else null
        val prevScrollPosition = listView.lastVisiblePosition
        val df = fileListDataFragment
        readLocation(df, location, scrollPosition)
        if (prevLocation != null) {
            val uri = prevLocation.locationUri
            val nh = df.navigHistory
            if (nh.empty() || nh.lastElement()!!.locationUri != uri) {
                val hi = HistoryItem()
                hi.locationUri = uri
                hi.scrollPosition = prevScrollPosition
                hi.locationId = prevLocation.id
                nh.push(hi)
            }
        }
    }

    //call from main thread
    fun rereadCurrentLocation() {
        Logger.debug(TAG + "rereadCurrentLocation")
        val scrollPosition = listView.lastVisiblePosition
        goTo(location, scrollPosition, false)
    }

    private fun setStartedLoading(loadInfo: LoadLocationInfo) {
        if (TEST_READING_OBSERVABLE != null) TEST_READING_OBSERVABLE!!.onNext(true)
        Logger.debug(TAG + ": Started loading " + loadInfo.location!!.locationUri)
        _isReadingLocation = true
        val adapter = adapter
        adapter.clear()
        adapter.setCurrentLocationId(loadInfo.location!!.id)
        _currentPathTextView!!.text = ""
        _selectedFileEditText!!.visibility = View.GONE
        if (_actionMode != null) _actionMode!!.invalidate()
        else updateOptionsMenu()
    }

    private fun setLocationLoading(loadInfo: LoadLocationInfo) {
        Logger.debug(TAG + ": Loading " + loadInfo.location!!.locationUri)
        val adapter = adapter
        adapter.clear()
        if (_loadingRecordObserver != null && !_loadingRecordObserver!!.isDisposed) {
            if (GlobalConfig.isDebug()) throw RuntimeException("Loading record observer was not disposed!")
            else _loadingRecordObserver!!.dispose()
        }
        _loadingRecordObserver = fileListDataFragment.loadRecordObservable.compose<BrowserRecord>
        (bindToLifecycle<BrowserRecord>()).buffer
        (200, java.util.concurrent.TimeUnit.MILLISECONDS).filter
        (Predicate<List<BrowserRecord?>> { records: List<BrowserRecord?> -> !records.isEmpty() }).compose<List<BrowserRecord>>
        (bindToLifecycle<List<BrowserRecord>>()).observeOn
        (AndroidSchedulers.mainThread()).subscribe
        (Consumer { collection: List<BrowserRecord?> -> adapter.addAll(collection) }, { err ->
            if (err !is CancellationException) Logger.log(err)
        })
        if (loadInfo.folder != null) updateCurrentFolderLabel(loadInfo.folder!!)
        showFileIfNeeded(loadInfo.file)
    }

    private fun setLocationNotLoading() {
        Logger.debug(TAG + ": Finished loading")
        if (_loadingRecordObserver != null) _loadingRecordObserver!!.dispose()
        val adapter = adapter
        fileListDataFragment.copyToAdapter(adapter)
        _isReadingLocation = false
        _selectedFileEditText!!.visibility =
            if (showSelectedFilenameEditText()) View.VISIBLE else View.GONE
        if (_actionMode != null) _actionMode!!.invalidate()
        else updateSelectionMode()
        if (_scrollPosition > 0) {
            val sp = _scrollPosition
            _scrollPosition = 0
            Completable.timer
            (50, java.util.concurrent.TimeUnit.MILLISECONDS, Schedulers.computation()).observeOn
            (AndroidSchedulers.mainThread()).compose
            (bindToLifecycle<Any>()).subscribe
            (Action { scrollList(sp) }, { err -> })
        }
        if (TEST_READING_OBSERVABLE != null) TEST_READING_OBSERVABLE!!.onNext(false)
    }

    private fun readLocation(df: FileListDataFragment, loc: Location?, scrollPosition: Int) {
        readLocationAndScroll(df, loc, scrollPosition)
    }

    private fun updateCurrentFolderLabel(currentFolder: CachedPathInfo) {
        _currentPathTextView!!.text = currentFolder.pathDesc
    }

    private fun showFileIfNeeded(file: BrowserRecord?) {
        if (file != null) {
            val activity = fileManagerActivity
            if (activity != null) {
                file.setHostActivity(activity)
                try {
                    Logger.debug(TAG + ": Opening file " + file.pathDesc)
                    file.open()
                } catch (e: Exception) {
                    Logger.showAndLog(activity, e)
                }
            }
        }
    }

    private fun readLocationAndScroll(
        df: FileListDataFragment,
        loc: Location?,
        scrollPosition: Int
    ) {
        _scrollPosition = scrollPosition
        df.readLocation(loc, null)
    }

    private fun scrollList(scrollPosition: Int) {
        if (scrollPosition > 0) {
            val lv = listView
            if (lv.firstVisiblePosition == 0) {
                val num = lv.count
                var sp = scrollPosition
                if (scrollPosition >= num) sp = num - 1
                if (sp >= 0)  //lv.setSelection(sp);
                    lv.smoothScrollToPosition(sp)
            }
        }
    }

    private fun updateSelectionMode() {
        if (haveSelectedFiles()) startSelectionMode()
        else {
            fileManagerActivity.showProperties(null, true)
            updateOptionsMenu()
        }
    }

    override fun onTargetLocationOpened(openerArgs: Bundle, location: Location) {
        openFileManager((activity as FileManagerActivity), location, 0)
    }

    override fun onTargetLocationNotOpened(openerArgs: Bundle) {
    }

    protected val adapter: FileListViewAdapter
        get() = listView.adapter as FileListViewAdapter

    val listView: ListView
        get() = if (_listView == null) ListView(activity) else _listView!!

    protected fun initListView() {
        val lv = listView
        lv.emptyView = view!!.findViewById(android.R.id.empty)
        lv.choiceMode = ListView.CHOICE_MODE_NONE
        lv.itemsCanFocus = true

        lv.onItemLongClickListener =
            OnItemLongClickListener { adapterView: AdapterView<*>, view: View?, pos: Int, itemId: Long ->
                val rec = adapterView.getItemAtPosition(pos) as BrowserRecord
                if (rec != null && rec.allowSelect()) selectFile(rec)
                true
            }
        lv.onItemClickListener =
            OnItemClickListener { adapterView: AdapterView<*>, view: View?, pos: Int, l: Long ->
                val rec = adapterView.getItemAtPosition(pos) as BrowserRecord
                if (rec != null) {
                    if (rec.isSelected) {
                        if (!isSelectAction || !isSingleSelectionMode) unselectFile(rec)
                    } else if (rec.allowSelect() && (_actionMode != null || (isSelectAction && rec.isFile))) selectFile(
                        rec
                    )
                    else onFileClicked(rec)
                }
            }
    }

    protected fun onFileClicked(file: BrowserRecord) {
        try {
            if (fileManagerActivity.isWideScreenLayout) file.openInplace()
            else file.open()
        } catch (e: Exception) {
            Logger.showAndLog(activity, e)
        }
    }

    protected val fileListDataFragment: FileListDataFragment
        get() = fragmentManager.findFragmentByTag(FileListDataFragment.Companion.TAG) as FileListDataFragment

    protected val selectedFiles: ArrayList<BrowserRecord>
        get() {
            val selectedRecordsList =
                ArrayList<BrowserRecord>()
            val lv = listView
            val count = lv.count
            for (i in 0..<count) {
                val file = lv.getItemAtPosition(i) as BrowserRecord
                if (file.isSelected) selectedRecordsList.add(file)
            }
            return selectedRecordsList
        }

    protected val selectableFiles: Collection<BrowserRecord>
        get() {
            val selectableFilesList =
                ArrayList<BrowserRecord>()
            val lv = listView
            val count = lv.count
            for (i in 0..<count) {
                val file = lv.getItemAtPosition(i) as BrowserRecord
                if (file != null && file.allowSelect()) selectableFilesList.add(file)
            }
            return selectableFilesList
        }

    protected val selectedPaths: ArrayList<Path>
        get() = getPathsFromRecords(selectedFiles)

    protected fun haveSelectedFiles(): Boolean {
        val lv = listView
        val count = lv.count
        for (i in 0..<count) {
            val file = lv.getItemAtPosition(i) as BrowserRecord
            if (file.isSelected) return true
        }
        return false
    }

    protected fun startSelectionMode() {
        _actionMode = listView.startActionMode(object : Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                if (this.isSendAction || _isReadingLocation) return false
                mode.menuInflater.inflate(R.menu.file_list_context_menu, menu)
                (this.listView.getAdapter() as FileListViewAdapter).notifyDataSetChanged()
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                val selectedFiles: ArrayList<BrowserRecord> = this.selectedFiles
                val hasSelectedFiles = (this.isSelectAction &&
                        this.isSingleSelectionMode &&
                        (allowCreateNewFile() || allowCreateNewFolder()) && !_selectedFileEditText!!.text.toString()
                    .isEmpty()
                        ) || !selectedFiles.isEmpty()

                val isSelectAction: Boolean = this.isSelectAction
                menu.findItem(R.id.select).setVisible(
                    isSelectAction &&
                            hasSelectedFiles &&
                            (!showSelectedFilenameEditText() || allowSelectedFileName())
                )
                menu.findItem(R.id.rename).setVisible(!isSelectAction && selectedFiles.size == 1)
                menu.findItem(R.id.open_as_container).setVisible(
                    !isSelectAction && selectedFiles.size == 1 &&
                            selectedFiles[0] is ExecutableFileRecord
                )
                menu.findItem(R.id.copy_to_temp)
                    .setVisible(!isSelectAction && this.location.isEncrypted())
                menu.findItem(R.id.choose_for_operation).setVisible(!isSelectAction)
                menu.findItem(R.id.delete).setVisible(!isSelectAction)
                val loc: Location = this.realLocation
                menu.findItem(R.id.wipe).setVisible(
                    !isSelectAction && loc != null && !loc.isEncrypted && !loc.isReadOnly
                )
                menu.findItem(R.id.properties).setVisible(!selectedFiles.isEmpty())
                menu.findItem(R.id.send).setVisible(!isSelectAction)
                return true
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                val mhi = MenuHandlerInfo()
                mhi.menuItemId = item.itemId
                val res = handleMenu(mhi)
                if (res && mhi.clearSelection) mode.finish()

                return res
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                if (_cleanSelectionOnModeFinish) {
                    clearSelectedFlag()
                    _actionMode = null
                    onSelectionChanged()
                } else _actionMode = null
            }
        })
    }

    protected fun stopSelectionMode() {
        if (_actionMode != null) _actionMode!!.finish()
    }

    protected fun clearSelectedFlag() {
        val lv = listView
        val count = lv.count
        for (i in 0..<count) {
            val file = lv.getItemAtPosition(i) as BrowserRecord
            if (file.isSelected) file.isSelected = false
        }
        (lv.adapter as FileListViewAdapter).notifyDataSetChanged()
    }

    protected fun handleMenu(mhi: MenuHandlerInfo): Boolean {
        when (mhi.menuItemId) {
            R.id.select -> {
                returnSelectedFiles()
                mhi.clearSelection = true
                return true
            }

            R.id.new_file -> {
                showNewFileDialog(false)
                return true
            }

            R.id.new_dir -> {
                showNewFileDialog(true)
                return true
            }

            R.id.rename -> {
                showRenameDialog()
                mhi.clearSelection = true
                return true
            }

            R.id.open_as_container -> {
                openFileAsContainer()
                mhi.clearSelection = true
                return true
            }

            R.id.choose_for_operation -> {
                chooseFilesForOperation()
                mhi.clearSelection = true
                return true
            }

            R.id.delete -> {
                confirmDelete(false)
                mhi.clearSelection = true
                return true
            }

            R.id.wipe -> {
                confirmDelete(true)
                mhi.clearSelection = true
                return true
            }

            R.id.copy -> {
                if (isSendAction) pasteSentFiles()
                else pasteFiles(false)
                return true
            }

            R.id.move -> {
                pasteFiles(true)
                return true
            }

            R.id.properties -> {
                showProperties()
                return true
            }

            R.id.copy_to_temp -> {
                copyToTemp()
                mhi.clearSelection = true
                return true
            }

            R.id.sort -> {
                changeSortMode()
                return true
            }

            R.id.select_all -> {
                selectAllFiles()
                return true
            }

            R.id.send -> {
                sendFiles()
                mhi.clearSelection = true
                return true
            }

            else -> return false
        }
    }

    protected fun showNewFileDialog(isDir: Boolean) {
        NewFileDialog.showDialog(
            fragmentManager,
            if (isDir) CreateNewFile.FILE_TYPE_FOLDER else CreateNewFile.FILE_TYPE_FILE,
            tag
        )
    }

    //full version compat
    protected fun newRecordCreated(rec: BrowserRecord?) {
    }

    protected val fileManagerActivity: FileManagerActivity
        get() = activity as FileManagerActivity

    protected val location: Location?
        get() = fileManagerActivity.location

    protected val realLocation: Location?
        get() = fileManagerActivity.realLocation

    protected val isSelectAction: Boolean
        get() = fileManagerActivity.isSelectAction

    protected val isSingleSelectionMode: Boolean
        get() = fileManagerActivity.isSingleSelectionMode

    protected fun allowCreateNewFile(): Boolean {
        return activity.intent.getBooleanExtra(
            FileManagerActivity.EXTRA_ALLOW_CREATE_NEW_FILE,
            true
        )
    }

    protected fun allowCreateNewFolder(): Boolean {
        return activity.intent.getBooleanExtra(
            FileManagerActivity.EXTRA_ALLOW_CREATE_NEW_FOLDER,
            true
        )
    }

    protected val isSendAction: Boolean
        get() {
            val action = activity.intent.action
            return Intent.ACTION_SEND == action || Intent.ACTION_SEND_MULTIPLE == action
        }

    /*
   private void initContentValuesFromPath(ContentValues values, Path path)
   {
       values.put(MainContentProvider.COLUMN_LOCATION, getRealLocation().getLocationUri().toString());
       values.put(MainContentProvider.COLUMN_PATH, path.getPathString());
   }*/
    protected fun chooseFilesForOperation() {
        /*ContentResolver cr = getActivity().getContentResolver();
        cr.delete(MainContentProvider.getCurrentSelectionUri(), null, null);
        ArrayList<Path> recs = getSelectedPaths();
        ContentValues cv = new ContentValues();
        for(Path path: recs)
        {
            initContentValuesFromPath(cv, path);
            cr.update(MainContentProvider.getCurrentSelectionUri(), cv, null, null);
        }
        ClipboardManager clipboard = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newUri(cr, recs.size() + " files are in clipboard", MainContentProvider.getCurrentSelectionUri()));
        getActivity().invalidateOptionsMenu();*/
        fragmentManager.beginTransaction
        ().add
        (
                CopyToClipboardTask.newInstance(
                    realLocation,
                    selectedPaths
                ),
        CopyToClipboardTask.TAG
        ).commit()
    }

    protected fun allowSelectedFileName(): Boolean {
        return true
        //The following check doesn't work with locations in which a path can point only to an existing file.
        //So if the filename is not valid, we'll return a error from "createObservable new file" task.
        /*try
        {
            String filename = _selectedFileEditText.getText().toString();
            if(filename.length() == 0)
                return false;
            Location loc = getFileManagerActivity().getTargetLocation();
            if (loc != null)
            {
                //check if the filename is valid
                loc.getCurrentPath().combine(filename);
                return true;
            }
        }
        catch (IOException ignored)
        {

        }
        return false;*/
    }

    protected fun confirmDelete(wipe: Boolean) {
        val b = Bundle()
        LocationsManager.storePathsInBundle(b, realLocation, selectedPaths)
        b.putBoolean(ARG_WIPE_FILES, wipe)
        showDialog(fragmentManager, b)
    }

    protected fun onSelectionChanged() {
        val sr = selectedFiles
        if (showSelectedFilenameEditText()) {
            val name = if (sr.isEmpty()) ""
            else sr[0].name

            _changingSelectedFileText = true
            try {
                _selectedFileEditText!!.setText(name)
            } finally {
                _changingSelectedFileText = false
            }
        }
        if (_actionMode != null) _actionMode!!.invalidate()
        updateOptionsMenu()
        fileManagerActivity.showProperties(null, true)
    }

    protected fun returnSelectionFromContentProvider(data: Intent?) {
        activity.setResult(Activity.RESULT_OK, data)
        activity.finish()
    }

    protected fun returnSelectedFiles() {
        val selectedRecs: List<BrowserRecord> = selectedFiles
        if (showSelectedFilenameEditText()) {
            val fn = _selectedFileEditText!!.text.toString()
            if (fn.length > 0) {
                if (selectedRecs.isEmpty() || fn != selectedRecs[0].name) {
                    fileListDataFragment.createOrFindFile(
                        fn,
                        if (allowCreateNewFile()) CreateNewFile.FILE_TYPE_FILE else CreateNewFile.FILE_TYPE_FOLDER
                    ).compose<BrowserRecord>
                    (bindToLifecycle<BrowserRecord>()).subscribe
                    (Consumer { rec: BrowserRecord ->
                        val adapter = adapter
                        if (adapter != null) adapter.add(rec)
                        val act = fileManagerActivity
                        if (act != null) {
                            val loc = act.realLocation!!.copy()
                            loc.currentPath = rec.path
                            val i = Intent()
                            LocationsManager.storePathsInIntent(
                                i,
                                loc,
                                listOf(rec.path)
                            )
                            act.setResult(Activity.RESULT_OK, i)
                            act.finish()
                        }
                    }, { err ->
                        if (err !is CancellationException) Logger.log(err)
                    })
                    return
                }
            }
        }
        val paths: List<Path> = getPathsFromRecords(selectedRecs)
        if (paths.size > 0) {
            activity.setResult(Activity.RESULT_OK, getSelectResult(paths))
            activity.finish()
        }
    }

    protected fun getSelectResult(paths: List<Path>): Intent {
        val loc = realLocation
        val intent = Intent()
        if (!isSingleSelectionMode) intent.setData(loc!!.locationUri)
        else if (paths.size > 0) {
            val res = loc!!.copy()
            res.currentPath = paths[0]
            intent.setData(res.locationUri)
        }
        val b = Bundle()
        LocationsManager.storePathsInBundle(b, loc, paths)
        intent.putExtras(b)
        return intent
    }

    protected fun showRenameDialog() {
        val br = selectedFiles[0]
        val name = br.name
        showDialog(fragmentManager, br.path.pathString, name)
    }

    protected fun sendFiles() {
        fragmentManager.beginTransaction
        ().add
        (
                PrepareToSendTask.newInstance(
                    realLocation,
                    selectedPaths
                ),
        PrepareToSendTask.TAG
        ).commit
        ()
    }

    private fun pasteSentFiles() {
        try {
            val recs = getSrcDsts(
                ContentResolverLocation(activity),
                false,
                ContentResolverFs.fromSendIntent(
                    activity.intent,
                    activity.contentResolver
                )
            )
            FileOpsService.copyFiles(activity, recs, false)
            Toast.makeText(activity, R.string.file_operation_started, Toast.LENGTH_SHORT).show()
            activity.finish()
        } catch (e: IOException) {
            Logger.showAndLog(activity, e)
        }
    }

    private fun hasSelectionInClipboard(): Boolean {
        return MainContentProvider.hasSelectionInClipboard(activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
    }

    private fun pasteFiles(move: Boolean) {
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (clipboard != null) {
            val clip = clipboard.primaryClip
            if (clip != null) {
                try {
                    val recs = getSrcDstsFromClip(
                        _locationsManager!!,
                        clip,
                        realLocation!!,
                        move
                    )
                    if (recs != null) {
                        if (move) FileOpsService.moveFiles(activity, recs, false)
                        else FileOpsService.copyFiles(activity, recs, false)
                        Toast.makeText(
                            activity,
                            R.string.file_operation_started,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    //cr.delete(MainContentProvider.getCurrentSelectionUri(), null, null);
                    clipboard.setPrimaryClip(ClipData.newPlainText("Empty", ""))
                    updateOptionsMenu()
                } catch (e: Exception) {
                    Logger.showAndLog(activity, e)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle
    ): View? {
        Logger.debug(TAG + " onCreateView")
        val view = inflater.inflate(R.layout.file_list_view_fragment, container, false)
        _selectedFileEditText = view.findViewById(R.id.selected_file_edit_text)
        _listView = view.findViewById(android.R.id.list)
        if (showSelectedFilenameEditText()) {
            _selectedFileEditText.setVisibility(View.VISIBLE)
            _selectedFileEditText.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(arg0: Editable) {
                    if (arg0 == null || _changingSelectedFileText) return
                    val s = arg0.toString()
                    if (s.isEmpty()) {
                        if (this.isInSelectionMode) stopSelectionMode()
                    } else {
                        if (this.isInSelectionMode) {
                            clearSelectedFlag()
                            _actionMode!!.invalidate()
                        } else startSelectionMode()
                    }
                }

                override fun beforeTextChanged(
                    arg0: CharSequence,
                    arg1: Int,
                    arg2: Int,
                    arg3: Int
                ) {
                }

                override fun onTextChanged(arg0: CharSequence, arg1: Int, arg2: Int, arg3: Int) {
                }
            })
        } else _selectedFileEditText.setVisibility(View.GONE)
        _currentPathTextView = view.findViewById(R.id.current_path_text)
        return view
    }

    @Throws(IOException::class)
    private fun getSrcDsts(
        srcLocation: Location,
        isDirLast: Boolean,
        paths: Collection<Path>
    ): SrcDstCollection {
        return SrcDstRec.fromPaths(srcLocation, realLocation, isDirLast, paths)
    }

    private fun showProperties() {
        fileManagerActivity.showProperties(null, false)
    }

    private fun copyToTemp() {
        val filesToCopy = selectedPaths
        if (filesToCopy.size > 0) FileOpsService.prepareTempFile(
            activity,
            realLocation, filesToCopy
        )
    }

    private fun changeSortMode() {
        val mode = UserSettings.getSettings(activity).filesSortMode
        SortDialog.showDialog(fragmentManager, mode, tag)
    }

    private fun openFileAsContainer() {
        val br = selectedFiles[0]
        var loc = realLocation ?: return
        loc = loc.copy()
        loc.currentPath = br.path
        fragmentManager.beginTransaction
        ().add
        (OpenAsContainerTask.newInstance(loc, false), OpenAsContainerTask.TAG).commit
        ()
    }

    private fun selectAllFiles() {
        val lv = listView
        var lr: BrowserRecord? = null
        for (i in 0..<lv.count) {
            val rec = lv.getItemAtPosition(i) as BrowserRecord
            if (rec.allowSelect()) {
                rec.isSelected = true
                lr = rec
            }
        }
        if (lr != null) {
            val adapter = lv.adapter as FileListViewAdapter
            adapter.notifyDataSetInvalidated()
            startSelectionMode()
            onSelectionChanged()
        }
    }

    private fun showSelectedFilenameEditText(): Boolean {
        return isSelectAction
                && isSingleSelectionMode
                && !realLocation!!.isReadOnly && (allowCreateNewFile()
                || allowCreateNewFolder()
                )
    }

    private fun openLocation(locToOpen: Location) {
        val fm = fragmentManager
        val openerTag = LocationOpenerBaseFragment.getOpenerTag(locToOpen)
        if (fm.findFragmentByTag(openerTag) == null) {
            val opener = LocationOpenerBaseFragment.getDefaultOpenerForLocation(locToOpen)
            val openerArgs = Bundle()
            LocationsManager.storePathsInBundle(openerArgs, locToOpen, null)
            openerArgs.putString(LocationOpenerBaseFragment.PARAM_RECEIVER_FRAGMENT_TAG, tag)
            opener.arguments = openerArgs
            fm.beginTransaction().add(opener, openerTag).commit()
        }
    }

    class MenuHandlerInfo {
        var menuItemId: Int = 0
        var clearSelection: Boolean = false
    }

    companion object {
        const val TAG: String =
            "com.sovworks.eds.android.filemanager.fragments.FileListViewFragment"

        const val REQUEST_CODE_SELECT_FROM_CONTENT_PROVIDER: Int = Activity.RESULT_FIRST_USER
        const val ARG_SCROLL_POSITION: String = "com.sovworks.eds.android.SCROLL_POSITION"

        fun getPathsFromRecords(records: List<BrowserRecord>): ArrayList<Path> {
            val res = ArrayList<Path>()
            for (rec in records) res.add(rec.path)
            return res
        }

        @Throws(Exception::class)
        private fun addSrcDstsFromClipItem(
            lm: LocationsManager,
            item: Item,
            dstLocation: Location,
            cols: MutableCollection<SrcDstCollection>,
            move: Boolean
        ) {
            val uri = item.uri
            if (!MainContentProvider.isClipboardUri(uri)) return
            val srcLoc = lm.getLocation(MainContentProvider.getLocationUriFromProviderUri(uri))
            if (move && srcLoc.fs === dstLocation.fs) cols.add(SrcDstSingle(srcLoc, dstLocation))
            else {
                val sdr = SrcDstRec(SrcDstSingle(srcLoc, dstLocation))
                sdr.setIsDirLast(false) //move);
                cols.add(sdr)
            }
            /*Cursor cur = cr.query(uri, null, null, null, null);
        if(cur!=null)
        {
            try
            {
                int ci = cur.getColumnIndex(MainContentProvider.COLUMN_LOCATION);
                while (cur.moveToNext())
                {
                    Location srcLoc = lm.getTargetLocation(Uri.parse(cur.getString(ci)));
                    SrcDstRec sdr = new SrcDstRec(srcLoc, dstLocation);
                    sdr.setIsDirLast(isDirLast);
                    cols.add(sdr);
                }
            }
            finally
            {
                cur.close();
            }
        }*/
        }

        @Throws(Exception::class)
        private fun getSrcDstsFromClip(
            lm: LocationsManager,
            clip: ClipData,
            dstLocation: Location,
            move: Boolean
        ): SrcDstCollection? {
            val cols = ArrayList<SrcDstCollection>()
            for (i in 0..<clip.itemCount) addSrcDstsFromClipItem(
                lm,
                clip.getItemAt(i),
                dstLocation,
                cols,
                move
            )

            return if (cols.isEmpty()) null else SrcDstGroup(cols)
        }

        const val ARG_WIPE_FILES: String = "com.sovworks.eds.android.WIPE_FILES"

        var TEST_READING_OBSERVABLE: Subject<Boolean>? = null

        init {
            if (GlobalConfig.isTest()) TEST_READING_OBSERVABLE =
                BehaviorSubject.createDefault(false)
        }
    }
}
