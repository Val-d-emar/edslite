package com.sovworks.eds.android.filemanager.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Fragment
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.net.Uri
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.sovworks.eds.android.EdsApplication
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.R
import com.sovworks.eds.android.activities.VersionHistory
import com.sovworks.eds.android.dialogs.AskOverwriteDialog
import com.sovworks.eds.android.filemanager.FileManagerFragment
import com.sovworks.eds.android.filemanager.activities.FileManagerActivity
import com.sovworks.eds.android.filemanager.fragments.FileListDataFragment
import com.sovworks.eds.android.filemanager.fragments.FileListViewFragment
import com.sovworks.eds.android.filemanager.fragments.FilePropertiesFragment
import com.sovworks.eds.android.filemanager.fragments.PreviewFragment
import com.sovworks.eds.android.filemanager.fragments.PreviewFragment.Host
import com.sovworks.eds.android.filemanager.records.BrowserRecord
import com.sovworks.eds.android.filemanager.tasks.CheckStartPathTask
import com.sovworks.eds.android.fragments.TaskFragment.Result
import com.sovworks.eds.android.fragments.TaskFragment.TaskCallbacks
import com.sovworks.eds.android.helpers.AppInitHelper
import com.sovworks.eds.android.helpers.CachedPathInfo
import com.sovworks.eds.android.helpers.CompatHelper
import com.sovworks.eds.android.helpers.ProgressDialogTaskFragmentCallbacks
import com.sovworks.eds.android.helpers.Util
import com.sovworks.eds.android.navigdrawer.DrawerController
import com.sovworks.eds.android.service.FileOpsService
import com.sovworks.eds.android.settings.UserSettings
import com.sovworks.eds.fs.Path
import com.sovworks.eds.fs.util.SrcDstCollection
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.LocationsManager
import com.sovworks.eds.locations.Openable
import com.sovworks.eds.settings.GlobalConfig
import com.trello.rxlifecycle2.android.ActivityEvent
import com.trello.rxlifecycle2.android.ActivityEvent.RESUME
import com.trello.rxlifecycle2.components.RxActivity
import io.reactivex.functions.Action
import io.reactivex.functions.Consumer
import io.reactivex.functions.Predicate
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject
import java.util.NavigableSet
import java.util.TreeSet
import java.util.concurrent.CancellationException

@SuppressLint("CommitPrefEdits", "ApplySharedPref")
abstract class FileManagerActivityBase : RxActivity(), Host {
    val isSelectAction: Boolean
        get() {
            val action = intent.action
            return Intent.ACTION_PICK == action || Intent.ACTION_GET_CONTENT == action
        }

    val isSingleSelectionMode: Boolean
        get() = !intent.getBooleanExtra(
            EXTRA_ALLOW_MULTIPLE,
            false
        )

    fun allowFileSelect(): Boolean {
        return intent.getBooleanExtra(EXTRA_ALLOW_FILE_SELECT, true)
    }

    fun allowFolderSelect(): Boolean {
        return intent.getBooleanExtra(EXTRA_ALLOW_FOLDER_SELECT, true)
    }

    @JvmOverloads
    fun goTo(location: Location?, scrollPosition: Int = 0) {
        Logger.debug(TAG + ": goTo")
        closeIntegratedViewer()
        val f = fileListViewFragment
        f?.goTo(location, scrollPosition, true)
    }

    fun goTo(path: Path?) {
        val prevLocation = location
        if (prevLocation != null) {
            val newLocation = prevLocation.copy()
            newLocation.currentPath = path
            goTo(newLocation, 0)
        }
    }

    fun rereadCurrentLocation() {
        val f = fileListViewFragment
        f?.rereadCurrentLocation()
    }

    override fun getCurrentFiles(): NavigableSet<out CachedPathInfo> {
        val f = fileListDataFragment
        return if (f != null) f.fileList else TreeSet()
    }

    override fun getFilesListSync(): Any {
        val f = fileListDataFragment
        return if (f != null) f.filesListSync else Any()
    }

    override fun getLocation(): Location? {
        val f = fragmentManager.findFragmentByTag(FileListDataFragment.TAG) as FileListDataFragment
        return f?.location
    }

    val realLocation: Location?
        get() = getRealLocation(location)

    fun hasSelectedFiles(): Boolean {
        val f = fileListDataFragment
        return f != null && f.hasSelectedFiles()
    }

    fun showProperties(currentFile: BrowserRecord?, allowInplace: Boolean) {
        if (!hasSelectedFiles() && currentFile == null) {
            Logger.debug(TAG + ": showProperties (hide)")
            if (fragmentManager.findFragmentByTag(FilePropertiesFragment.TAG) != null) hideSecondaryFragment()
        } else if (isWideScreenLayout || !allowInplace) {
            Logger.debug(TAG + ": showProperties")
            showPropertiesFragment(currentFile)
        }
    }

    fun showPhoto(currentFile: BrowserRecord?, allowInplace: Boolean) {
        Logger.debug(TAG + ": showPhoto")
        val contextPath = currentFile?.path
        if (!hasSelectedFiles() && contextPath == null) hideSecondaryFragment()
        else if (isWideScreenLayout || !allowInplace) showPreviewFragment(contextPath)
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        if (GlobalConfig.isTest()) TEST_INIT_OBSERVABLE!!.onNext(false)
        Util.setTheme(this)
        super.onCreate(savedInstanceState)
        Logger.debug("fm start intent: $intent")
        _settings = UserSettings.getSettings(this)
        if (_settings.isFlagSecureEnabled) CompatHelper.setWindowFlagSecure(this)
        isWideScreenLayout = UserSettings.isWideScreenLayout(_settings, this)
        setContentView(R.layout.main_activity)
        val f = fragmentManager.findFragmentById(R.id.fragment2)
        if (f != null) {
            var panel = findViewById<View>(R.id.fragment2)
            if (panel != null) panel.visibility = View.VISIBLE
            panel = findViewById(R.id.fragment1)
            if (panel != null) panel.visibility = View.GONE
        }
        LocalBroadcastManager.getInstance(applicationContext)
            .registerReceiver(_exitBroadcastReceiver, IntentFilter(EdsApplication.BROADCAST_EXIT))
        registerReceiver(
            _locationAddedOrRemovedReceiver,
            LocationsManager.getLocationAddedIntentFilter()
        )
        registerReceiver(
            _locationAddedOrRemovedReceiver,
            LocationsManager.getLocationRemovedIntentFilter()
        )
        registerReceiver(
            _locationChangedReceiver,
            IntentFilter(LocationsManager.BROADCAST_LOCATION_CHANGED)
        )
        registerReceiver(
            _locationAddedOrRemovedReceiver,
            IntentFilter(LocationsManager.BROADCAST_LOCATION_CHANGED)
        )

        drawerController.init(savedInstanceState)
        AppInitHelper.createObservable
        (this).compose
        (bindToLifecycle<Any>()).subscribe
        (Action {
            startAction(savedInstanceState)
            addFileListFragments()
        }, io.reactivex.functions.Consumer<kotlin.Throwable?> { err: Throwable? ->
            if (err !is CancellationException) Logger.showAndLog(
                applicationContext, err
            )
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        lifecycle().filter
        (Predicate<ActivityEvent> { event: ActivityEvent -> event == RESUME }).firstElement
        ().subscribe
        (Consumer { res: ActivityEvent? -> startAction(null) }, io.reactivex.functions.Consumer<kotlin.Throwable?> { err: Throwable? ->
            if (err !is CancellationException) Logger.log(err)
        })
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            val pf = fragmentManager.findFragmentByTag(PreviewFragment.TAG) as PreviewFragment
            pf?.updateImageViewFullScreen()
        }
    }

    override fun onToggleFullScreen() {
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        Logger.debug(TAG + ": dispatchKeyEvent")
        //Prevent selection clearing when back button is pressed while properties fragment is active
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            if (!isWideScreenLayout && hasSelectedFiles()) {
                val f = fragmentManager.findFragmentByTag(FilePropertiesFragment.TAG)
                if (f != null) {
                    hideSecondaryFragment()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onOptionsItemSelected(menuItem: MenuItem): Boolean {
        return drawerController.onOptionsItemSelected(menuItem) || super.onOptionsItemSelected(
            menuItem
        )
    }

    override fun onBackPressed() {
        Logger.debug(TAG + ": onBackPressed")

        if (drawerController.onBackPressed()) return

        var f = fragmentManager.findFragmentById(R.id.fragment2)
        if (f != null && (f as FileManagerFragment).onBackPressed()) return

        if (hideSecondaryFragment()) return

        f = fragmentManager.findFragmentById(R.id.fragment1)
        if (f != null && (f as FileManagerFragment).onBackPressed()) return

        super.onBackPressed()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        drawerController.onConfigurationChanged(newConfig)
    }

    val fileListDataFragment: FileListDataFragment?
        get() {
            val f =
                fragmentManager.findFragmentByTag(FileListDataFragment.TAG) as FileListDataFragment
            return if (f != null && f.isAdded) f else null
        }

    val fileListViewFragment: FileListViewFragment?
        get() {
            val f =
                fragmentManager.findFragmentByTag(FileListViewFragment.TAG) as FileListViewFragment
            return if (f != null && f.isAdded) f else null
        }


    val checkStartPathCallbacks: TaskCallbacks
        get() = object : ProgressDialogTaskFragmentCallbacks(this, R.string.loading) {
            override fun onCompleted(args: Bundle, result: Result) {
                try {
                    val locToOpen =
                        result.result as Location
                    if (locToOpen != null) {
                        intent = Intent(Intent.ACTION_MAIN, locToOpen.locationUri)
                        val df: FileListDataFragment = this.fileListDataFragment
                        if (df != null) df.loadLocation(null, true)
                    } else intent = Intent()
                } catch (e: Throwable) {
                    Logger.showAndLog(_context, e)
                    intent = Intent()
                }
            }
        }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        drawerController.onSaveInstanceState(outState)
    }

    override fun onPostCreate(state: Bundle?) {
        super.onPostCreate(state)
        drawerController.onPostCreate()
    }


    override fun onStart() {
        super.onStart()
        checkIfCurrentLocationIsStillOpen()
        drawerController.updateMenuItemViews()
        registerReceiver(
            _updatePathReceiver, IntentFilter(
                FileOpsService.BROADCAST_FILE_OPERATION_COMPLETED
            )
        )
        registerReceiver(_closeAllReceiver, IntentFilter(LocationsManager.BROADCAST_CLOSE_ALL))
        Logger.debug("FileManagerActivity has started")
    }

    override fun onStop() {
        unregisterReceiver(_closeAllReceiver)
        unregisterReceiver(_updatePathReceiver)
        super.onStop()
        Logger.debug("FileManagerActivity has stopped")
    }

    protected fun startAction(savedState: Bundle?) {
        var action = intent.action
        if (action == null) action = ""
        Logger.log("FileManagerActivity action is $action")
        try {
            when (action) {
                Intent.ACTION_VIEW -> actionView(savedState)
                ACTION_ASK_OVERWRITE -> actionAskOverwrite()
                Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE, Intent.ACTION_MAIN -> actionMain(
                    savedState
                )

                else -> actionMain(savedState)
            }
        } catch (e: Exception) {
            Logger.showAndLog(this, e)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        if (GlobalConfig.isTest()) TEST_INIT_OBSERVABLE!!.onNext(true)
    }

    override fun onDestroy() {
        unregisterReceiver(_locationAddedOrRemovedReceiver)
        unregisterReceiver(_locationChangedReceiver)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(_exitBroadcastReceiver)
        _settings = null
        super.onDestroy()
    }

    protected abstract fun createDrawerController(): DrawerController

    val drawerController: DrawerController = createDrawerController()
    var isWideScreenLayout: Boolean = false
        protected set
    protected var _settings: UserSettings? = null

    private val _updatePathReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            rereadCurrentLocation()
        }
    }

    private val _closeAllReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            checkIfCurrentLocationIsStillOpen()
            this.drawerController.updateMenuItemViews()
        }
    }


    private val _locationChangedReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (isFinishing) return

            try {
                val locUri = intent.getParcelableExtra<Uri>(LocationsManager.PARAM_LOCATION_URI)
                if (locUri != null) {
                    val changedLocation = LocationsManager.getLocationsManager(
                        applicationContext
                    ).getLocation(locUri)
                    if (changedLocation != null) {
                        val loc: Location = this.realLocation
                        if (loc != null && changedLocation.id == loc.id) checkIfCurrentLocationIsStillOpen()

                        val f: FileListDataFragment = this.fileListDataFragment
                        if (f != null && !LocationsManager.isOpen(changedLocation)) f.removeLocationFromHistory(
                            changedLocation
                        )
                    }
                }
            } catch (e: Exception) {
                Logger.showAndLog(context, e)
                finish()
            }
        }
    }

    private val _locationAddedOrRemovedReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (isFinishing) return
            if (LocationsManager.BROADCAST_LOCATION_REMOVED == intent.action) {
                try {
                    val locUri = intent.getParcelableExtra<Uri>(LocationsManager.PARAM_LOCATION_URI)
                    if (locUri != null) {
                        val changedLocation = LocationsManager.getLocationsManager(
                            applicationContext
                        ).getLocation(locUri)
                        if (changedLocation != null) {
                            val f: FileListDataFragment = this.fileListDataFragment
                            if (f != null) f.removeLocationFromHistory(changedLocation)
                        }
                    }
                } catch (e: Exception) {
                    Logger.showAndLog(context, e)
                }
            }
            this.drawerController.reloadItems()
        }
    }

    private val _exitBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            finish()
        }
    }

    @Throws(Exception::class)
    protected fun actionMain(savedState: Bundle?) {
        if (savedState == null) {
            if (intent.data == null) drawerController.showContainers()
            showPromoDialogIfNeeded()
        }
    }

    private fun actionView(savedState: Bundle?) {
        if (savedState == null) {
            val dataUri = intent.data
            if (dataUri != null) {
                val mime = intent.type
                if (!FOLDER_MIME_TYPE.equals(mime, ignoreCase = true)) {
                    fragmentManager.beginTransaction
                    ().add
                    (
                            CheckStartPathTask.newInstance(dataUri, false),
                    CheckStartPathTask.TAG
                    ).commit
                    ()
                    intent = Intent()
                }
            }
        }
    }

    private fun actionAskOverwrite() {
        AskOverwriteDialog.showDialog(
            fragmentManager,
            intent.extras
        )
        intent = Intent()
    }

    protected fun addFileListFragments() {
        val fm = fragmentManager
        if (fm.findFragmentByTag(FileListDataFragment.TAG) == null) {
            val trans = fragmentManager.beginTransaction()
            trans.add(FileListDataFragment.newInstance(), FileListDataFragment.TAG)
            trans.add(R.id.fragment1, FileListViewFragment.newInstance(), FileListViewFragment.TAG)
            trans.commit()
        }
    }

    protected fun showSecondaryFragment(f: Fragment?, tag: String?) {
        val trans = fragmentManager.beginTransaction()
        trans.replace(R.id.fragment2, f, tag)
        var panel = findViewById<View>(R.id.fragment2)
        if (panel != null) panel.visibility = View.VISIBLE
        if (!isWideScreenLayout) {
            panel = findViewById(R.id.fragment1)
            if (panel != null) panel.visibility = View.GONE
        }
        trans.disallowAddToBackStack()
        trans.commit()
    }

    protected fun hideSecondaryFragment(): Boolean {
        Logger.debug(TAG + ": hideSecondaryFragment")
        val fm = fragmentManager
        val f = fm.findFragmentById(R.id.fragment2)
        if (f != null) {
            val trans = fm.beginTransaction()
            trans.remove(f)
            trans.commit()
            var panel = findViewById<View>(R.id.fragment1)
            if (panel != null) panel.visibility = View.VISIBLE
            if (!isWideScreenLayout) {
                panel = findViewById(R.id.fragment2)
                if (panel != null) panel.visibility = View.GONE
            }
            invalidateOptionsMenu()
            return true
        }
        return false
    }

    protected fun checkIfCurrentLocationIsStillOpen() {
        val loc = realLocation
        if (!isFinishing &&
            loc is Openable && !LocationsManager.isOpen(loc) &&
            (intent.data == null || intent.data != loc.locationUri)
        ) {
            //closeIntegratedViewer();
            goTo(getStartLocation(this))
        }
    }

    protected open fun showPromoDialogIfNeeded() {
        if (!GlobalConfig.isDebug()) startActivity(Intent(this, VersionHistory::class.java))
    }

    private fun showPropertiesFragment(currentFile: BrowserRecord?) {
        val f = FilePropertiesFragment.newInstance(currentFile?.path)
        showSecondaryFragment(f, FilePropertiesFragment.TAG)
    }

    private fun showPreviewFragment(currentImage: Path?) {
        val f = PreviewFragment.newInstance(currentImage)
        showSecondaryFragment(f, PreviewFragment.TAG)
    }

    private fun closeIntegratedViewer() {
        Logger.debug(TAG + ": closeIntegratedViewer")
        hideSecondaryFragment()
    }

    companion object {
        const val TAG: String = "FileManagerActivity"
        const val ACTION_ASK_OVERWRITE: String = "com.sovworks.eds.android.ACTION_ASK_OVERWRITE"

        var TEST_INIT_OBSERVABLE: Subject<Boolean>? = null

        init {
            if (GlobalConfig.isTest()) TEST_INIT_OBSERVABLE = BehaviorSubject.createDefault(false)
        }

        @JvmStatic
        fun getStartLocation(context: Context?): Location {
            return LocationsManager.getLocationsManager(context, true).defaultDeviceLocation
        }

        @JvmStatic
        fun getOverwriteRequestIntent(
            context: Context?,
            move: Boolean,
            records: SrcDstCollection?
        ): Intent {
            val i = Intent(context, FileManagerActivity::class.java)
            i.setAction(ACTION_ASK_OVERWRITE)
            i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            //i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            i.putExtra(AskOverwriteDialog.ARG_MOVE, move)
            i.putExtra(AskOverwriteDialog.ARG_PATHS, records)
            return i
        }

        @JvmStatic
        fun getSelectPathIntent(
            context: Context?,
            startPath: Uri?,
            allowMultiSelect: Boolean,
            allowFileSelect: Boolean,
            allowDirSelect: Boolean,
            allowCreateNew: Boolean,
            allowBrowseDevice: Boolean,
            allowBrowseContainer: Boolean
        ): Intent {
            var startPath = startPath
            val intent = Intent(context, FileManagerActivity::class.java)
            intent.setAction(Intent.ACTION_PICK)
            if (startPath == null) startPath = getStartLocation(context).locationUri

            intent.setData(startPath)

            intent.putExtra(
                EXTRA_ALLOW_MULTIPLE, allowMultiSelect
            )
            intent.putExtra(
                EXTRA_ALLOW_FILE_SELECT, allowFileSelect
            )
            intent.putExtra(
                EXTRA_ALLOW_FOLDER_SELECT, allowDirSelect
            )
            intent.putExtra(
                EXTRA_ALLOW_CREATE_NEW_FILE, allowCreateNew
            )
            intent.putExtra(
                EXTRA_ALLOW_CREATE_NEW_FOLDER, allowCreateNew
            )

            intent.putExtra(
                EXTRA_ALLOW_BROWSE_DEVICE, allowBrowseDevice
            )
            intent.putExtra(
                EXTRA_ALLOW_BROWSE_CONTAINERS, allowBrowseContainer
            )
            return intent
        }

        @JvmOverloads
        fun selectPath(
            context: Activity?,
            f: Fragment,
            requestCode: Int,
            allowMultiSelect: Boolean,
            allowFileSelect: Boolean,
            allowDirSelect: Boolean,
            allowCreateNew: Boolean,
            allowBrowseDevice: Boolean = true,
            allowBrowseContainer: Boolean = true
        ) {
            val i = getSelectPathIntent(
                context,
                null,
                allowMultiSelect,
                allowFileSelect,
                allowDirSelect,
                allowCreateNew,
                allowBrowseDevice,
                allowBrowseContainer
            )
            f.startActivityForResult(i, requestCode)
        }

        @SuppressLint("InlinedApi")
        val EXTRA_ALLOW_MULTIPLE: String =
            if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN_MR2) Intent.EXTRA_ALLOW_MULTIPLE else "com.sovworks.eds.android.ALLOW_MULTIPLE"
        const val EXTRA_ALLOW_FILE_SELECT: String = "com.sovworks.eds.android.ALLOW_FILE_SELECT"
        const val EXTRA_ALLOW_FOLDER_SELECT: String = "com.sovworks.eds.android.ALLOW_FOLDER_SELECT"
        const val EXTRA_ALLOW_CREATE_NEW_FILE: String =
            "com.sovworks.eds.android.ALLOW_CREATE_NEW_FILE"
        const val EXTRA_ALLOW_CREATE_NEW_FOLDER: String =
            "com.sovworks.eds.android.ALLOW_CREATE_NEW_FOLDER"

        const val EXTRA_ALLOW_BROWSE_CONTAINERS: String =
            "com.sovworks.eds.android.ALLOW_BROWSE_CONTAINERS"
        const val EXTRA_ALLOW_BROWSE_DEVICE: String = "com.sovworks.eds.android.ALLOW_BROWSE_DEVICE"
        const val EXTRA_ALLOW_BROWSE_DOCUMENT_PROVIDERS: String =
            "com.sovworks.eds.android.ALLOW_BROWSE_DOCUMENT_PROVIDERS"

        const val EXTRA_ALLOW_SELECT_FROM_CONTENT_PROVIDERS: String =
            "com.sovworks.eds.android.ALLOW_SELECT_FROM_CONTENT_PROVIDERS"
        const val EXTRA_ALLOW_SELECT_ROOT_FOLDER: String =
            "com.sovworks.eds.android.ALLOW_SELECT_ROOT_FOLDER"

        fun getRealLocation(loc: Location?): Location? {
            return loc
        }

        protected const val FOLDER_MIME_TYPE: String = "resource/folder"
    }
}

