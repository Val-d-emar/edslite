package com.sovworks.eds.android.filemanager.fragments

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapFactory.Options
import android.graphics.Rect
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ViewSwitcher
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.R
import com.sovworks.eds.android.filemanager.FileManagerFragment
import com.sovworks.eds.android.filemanager.tasks.LoadPathInfoObservable
import com.sovworks.eds.android.filemanager.tasks.LoadedImage
import com.sovworks.eds.android.helpers.CachedPathInfo
import com.sovworks.eds.android.service.FileOpsService
import com.sovworks.eds.android.settings.UserSettings
import com.sovworks.eds.android.settings.UserSettingsCommon
import com.sovworks.eds.android.views.GestureImageView.NavigListener
import com.sovworks.eds.android.views.GestureImageView.OptimImageRequiredListener
import com.sovworks.eds.android.views.GestureImageViewWithFullScreenMode
import com.sovworks.eds.exceptions.ApplicationException
import com.sovworks.eds.fs.Path
import com.sovworks.eds.fs.util.StringPathUtil
import com.sovworks.eds.locations.Location
import com.sovworks.eds.settings.GlobalConfig
import com.trello.rxlifecycle2.components.RxFragment
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Action
import io.reactivex.functions.Consumer
import io.reactivex.functions.Predicate
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import java.io.IOException
import java.util.NavigableSet
import java.util.concurrent.CancellationException
import kotlin.math.max
import kotlin.math.pow

class PreviewFragment : RxFragment(), FileManagerFragment {
    interface Host {
        val currentFiles: NavigableSet<out CachedPathInfo>
        val location: Location?
        val filesListSync: Any?
        fun onToggleFullScreen()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        try {
            initParams(
                if (savedInstanceState != null && savedInstanceState.containsKey(
                        STATE_CURRENT_PATH
                    )
                )
                    savedInstanceState
                else
                    arguments
            )
            loadImagePaths()
        } catch (e: IOException) {
            Logger.showAndLog(activity, e)
        } catch (e: ApplicationException) {
            Logger.showAndLog(activity, e)
        }
        setHasOptionsMenu(true)
    }

    override fun onStart() {
        super.onStart()
        Logger.debug(TAG + " fragment started")
        loadImageWhenReady()
    }

    override fun onStop() {
        super.onStop()
        Logger.debug(TAG + " fragment stopped")
    }

    override fun onResume() {
        super.onResume()
        Logger.debug(TAG + " fragment resumed")
    }

    override fun onPause() {
        super.onPause()
        Logger.debug(TAG + " fragment paused")
    }

    override fun onBackPressed(): Boolean {
        return false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle
    ): View? {
        val view = inflater.inflate(R.layout.preview_fragment, container, false)
        _mainImageView = view.findViewById(R.id.imageView)
        _mainImageView.setAutoZoom(UserSettings.getSettings(activity).isImageViewerAutoZoomEnabled)
        _mainImageView.setNavigListener(object : NavigListener {
            override fun onPrev() {
                try {
                    moveLeft()
                } catch (e: IOException) {
                    Logger.showAndLog(activity, e)
                } catch (e: ApplicationException) {
                    Logger.showAndLog(activity, e)
                }
            }

            override fun onNext() {
                try {
                    moveRight()
                } catch (e: IOException) {
                    Logger.showAndLog(activity, e)
                } catch (e: ApplicationException) {
                    Logger.showAndLog(activity, e)
                }
            }
        })
        _viewSwitcher = view.findViewById(R.id.viewSwitcher)
        _mainImageView.setOnLoadOptimImageListener(OptimImageRequiredListener { srcImageRect: Rect? ->
            if (_isOptimSupported) loadImage(srcImageRect)
        })
        _mainImageView.setOnSizeChangedListener(Runnable {
            _mainImageView.getViewRect().round(_viewRect)
            _imageViewPrepared.onNext(_viewRect.width() > 0 && _viewRect.height() > 0)
        })


        if (UserSettings.getSettings(activity).isImageViewerFullScreenModeEnabled) _mainImageView.setFullscreenMode(
            true
        )
        return view
    }


    override fun onDestroyView() {
        _mainImageView!!.clearImage()
        _mainImageView = null
        _viewSwitcher = null
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (_currentImagePath != null) outState.putString(
            STATE_CURRENT_PATH,
            _currentImagePath!!.pathString
        )
    }

    override fun onCreateOptionsMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.image_viewer_menu, menu)
        super.onCreateOptionsMenu(menu, menuInflater)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        try {
            var mi = menu.findItem(R.id.prevMenuItem)
            mi.setEnabled(prevImagePath != null)
            mi = menu.findItem(R.id.nextMenuItem)
            mi.setEnabled(nextImagePath != null)
            mi = menu.findItem(R.id.fullScreenModeMenuItem)
            mi.setChecked(_isFullScreen)
        } catch (e: IOException) {
            Logger.showAndLog(activity, e)
        } catch (e: ApplicationException) {
            Logger.showAndLog(activity, e)
        }
    }

    override fun onOptionsItemSelected(menuItem: MenuItem): Boolean {
        try {
            when (menuItem.itemId) {
                R.id.prevMenuItem -> {
                    moveLeft()
                    return true
                }

                R.id.nextMenuItem -> {
                    moveRight()
                    return true
                }

                R.id.zoomInMenuItem -> {
                    _mainImageView!!.zoomIn()
                    return true
                }

                R.id.zoomOutMenuItem -> {
                    _mainImageView!!.zoomOut()
                    return true
                }

                R.id.rotateLeftMenuItem -> {
                    _mainImageView!!.rotateLeft()
                    return true
                }

                R.id.rotateRightMenuItem -> {
                    _mainImageView!!.rotateRight()
                    return true
                }

                R.id.toggleAutoZoom -> {
                    toggleAutoZoom()
                    return true
                }

                R.id.fullScreenModeMenuItem -> {
                    toggleFullScreen()
                    return true
                }
            }
        } catch (e: Exception) {
            Logger.showAndLog(activity, e)
        }
        return super.onOptionsItemSelected(menuItem)
    }

    fun updateImageViewFullScreen() {
        if (_mainImageView != null && _isFullScreen) _mainImageView!!.setFullscreenMode(true)
    }

    private var _mainImageView: GestureImageViewWithFullScreenMode? = null
    private var _viewSwitcher: ViewSwitcher? = null
    private var _currentImagePath: Path? = null
    private var prevImagePath: Path? = null

    @get:Throws(IOException::class, ApplicationException::class)
    private var nextImagePath: Path? = null
    private val _viewRect = Rect()
    private var _isFullScreen = false
    private var _isOptimSupported = false
    private val _imageViewPrepared: Subject<Boolean> = BehaviorSubject.create()

    private fun loadImagePaths() {
        nextImagePath = null
        prevImagePath = nextImagePath
        activity.invalidateOptionsMenu()
        if (_currentImagePath != null) {
            var loc = previewFragmentHost.location
            if (loc != null) {
                loc = loc.copy()
                loc.currentPath = _currentImagePath
                LoadPathInfoObservable.create(loc).subscribeOn
                (Schedulers.io()).observeOn
                (AndroidSchedulers.mainThread()).compose<CachedPathInfo>
                (bindToLifecycle<CachedPathInfo>()).subscribe
                (
                        Consumer { rec: CachedPathInfo ->
                            setNeibImagePaths(rec)
                            activity.invalidateOptionsMenu()
                        },
                Consumer { err: Throwable? ->
                    if (err !is CancellationException) Logger.showAndLog(
                        activity, err
                    )
                }
                )
            }
        }
    }


    @SuppressLint("ApplySharedPref")
    private fun toggleFullScreen() {
        _isFullScreen = !_isFullScreen
        UserSettings.getSettings(activity).sharedPreferences.edit()
            .putBoolean(UserSettingsCommon.IMAGE_VIEWER_FULL_SCREEN_ENABLED, _isFullScreen).commit()

        if (VERSION.SDK_INT >= VERSION_CODES.KITKAT) {
            if (_mainImageView != null) _mainImageView!!.setFullscreenMode(_isFullScreen)
        }
        previewFragmentHost.onToggleFullScreen()
        activity.invalidateOptionsMenu()
    }

    @Throws(IOException::class, ApplicationException::class)
    private fun initParams(args: Bundle) {
        val act =
            previewFragmentHost ?: return
        val loc = act.location
        try {
            _currentImagePath = if (args.containsKey(STATE_CURRENT_PATH)) loc!!.fs.getPath(
                args.getString(
                    STATE_CURRENT_PATH
                )
            ) else null
        } catch (e: IOException) {
            Logger.showAndLog(activity, e)
        }
        if (_currentImagePath == null) _currentImagePath = firstImagePath
    }

    private val firstImagePath: Path?
        get() {
            synchronized(previewFragmentHost.filesListSync!!) {
                val h =
                    previewFragmentHost
                val files = h.currentFiles as NavigableSet<CachedPathInfo>
                return if (files.isEmpty()) null else files.first().path
            }
        }

    private val previewFragmentHost: Host
        get() = activity as Host

    private fun showLoading() {
        if (_viewSwitcher!!.currentView === _mainImageView) _viewSwitcher!!.showNext()
    }

    private fun showImage() {
        if (_viewSwitcher!!.currentView !== _mainImageView) _viewSwitcher!!.showPrevious()
        activity.invalidateOptionsMenu()
    }

    @SuppressLint("ApplySharedPref")
    private fun toggleAutoZoom() {
        val settings = UserSettings.getSettings(activity)
        val `val` = !settings.isImageViewerAutoZoomEnabled
        settings.sharedPreferences.edit()
            .putBoolean(UserSettingsCommon.IMAGE_VIEWER_AUTO_ZOOM_ENABLED, `val`).commit()
        _mainImageView!!.setAutoZoom(`val`)
    }

    @Throws(IOException::class, ApplicationException::class)
    private fun moveLeft() {
        if (prevImagePath != null) {
            _currentImagePath = prevImagePath
            loadImageWhenReady()
        }
        loadImagePaths()
    }

    @Throws(IOException::class, ApplicationException::class)
    private fun moveRight() {
        if (nextImagePath != null) {
            _currentImagePath = nextImagePath
            loadImageWhenReady()
        }
        loadImagePaths()
    }

    @Throws(IOException::class, ApplicationException::class)
    private fun setNeibImagePaths(curImageFileInfo: CachedPathInfo) {
        synchronized(previewFragmentHost.filesListSync!!) {
            val h =
                previewFragmentHost
            val files = h.currentFiles as NavigableSet<CachedPathInfo>
            if (files.isEmpty()) return
            val ctx = activity.applicationContext
            var cur: CachedPathInfo? = curImageFileInfo
            while (cur != null) {
                cur = files.higher(cur)
                if (cur != null && cur.isFile) {
                    val mime = FileOpsService.getMimeTypeFromExtension(
                        ctx,
                        StringPathUtil(cur.name).fileExtension
                    )
                    if (mime.startsWith("image/")) {
                        nextImagePath = cur.path
                        break
                    }
                }
            }
            cur = curImageFileInfo
            while (cur != null) {
                cur = files.lower(cur)
                if (cur != null && cur.isFile) {
                    val mime = FileOpsService.getMimeTypeFromExtension(
                        ctx,
                        StringPathUtil(cur.name).fileExtension
                    )
                    if (mime.startsWith("image/")) {
                        prevImagePath = cur.path
                        break
                    }
                }
            }
        }
    }

    private fun loadImageWhenReady() {
        _imageViewPrepared.filter
        (Predicate<Boolean> { res: Boolean? -> res!! }).firstElement
        ().compose<Boolean>
        (bindToLifecycle<Boolean>()).subscribe
        (Consumer { res: Boolean? -> loadImage(null) }, io.reactivex.functions.Consumer<kotlin.Throwable?> { err: Throwable? ->
            if (err !is CancellationException) Logger.log(err)
        })
    }

    private fun loadImage(regionRect: Rect?) {
        if (_currentImagePath == null) return
        Logger.debug(TAG + ": loading image")
        if (regionRect == null) showLoading()
        var loadImageTaskObservable = LoadedImage.createObservable(
            activity.applicationContext,
            _currentImagePath,
            _viewRect,
            regionRect
        ).subscribeOn
        (Schedulers.io()).observeOn
        (AndroidSchedulers.mainThread()).compose<LoadedImage>
        (bindToLifecycle<LoadedImage>())
        if (GlobalConfig.isTest()) {
            loadImageTaskObservable = loadImageTaskObservable.doOnSubscribe
            (Consumer<Disposable> { sub: Disposable? ->
                TEST_LOAD_IMAGE_TASK_OBSERVABLE!!.onNext(
                    true
                )
            }).doFinally
            (Action { TEST_LOAD_IMAGE_TASK_OBSERVABLE!!.onNext(false) })
        }

        loadImageTaskObservable.subscribe(
            { res: LoadedImage ->
                if (regionRect == null) {
                    _mainImageView!!.setImage(
                        res.imageData,
                        res.sampleSize,
                        res.rotation,
                        res.flipX,
                        res.flipY
                    )
                    _isOptimSupported = res.isOptimSupported
                    showImage()
                } else _mainImageView!!.setOptimImage(res.imageData, res.sampleSize)
            },
            { err: Throwable? ->
                if (err !is CancellationException) Logger.showAndLog(
                    activity, err
                )
            })
    }

    companion object {
        const val TAG: String = "PreviewFragment"

        const val STATE_CURRENT_PATH: String = "com.sovworks.eds.android.CURRENT_PATH"

        fun newInstance(currentImagePath: Path?): PreviewFragment {
            val b = Bundle()
            if (currentImagePath != null) b.putString(
                STATE_CURRENT_PATH,
                currentImagePath.pathString
            )
            val pf = PreviewFragment()
            pf.arguments = b
            return pf
        }

        fun newInstance(currentImagePathString: String?): PreviewFragment {
            val b = Bundle()
            b.putString(STATE_CURRENT_PATH, currentImagePathString)
            val pf = PreviewFragment()
            pf.arguments = b
            return pf
        }

        @JvmStatic
		@Throws(IOException::class)
        fun loadDownsampledImage(path: Path, sampleSize: Int): Bitmap? {
            val options = Options()
            options.inSampleSize = sampleSize
            val data = path.file.inputStream
            try {
                return BitmapFactory.decodeStream(data, null, options)
            } finally {
                data.close()
            }
        }

        @JvmStatic
		@Throws(IOException::class)
        fun loadImageParams(path: Path): Options {
            val options = Options()
            options.inJustDecodeBounds = true
            val data = path.file.inputStream
            try {
                BitmapFactory.decodeStream(data, null, options)
            } finally {
                data.close()
            }
            return options
        }

        @JvmStatic
		fun calcSampleSize(viewRect: Rect, regionRect: Rect): Int {
            var inSampleSize = 1

            if (regionRect.height() > viewRect.height() || regionRect.width() > viewRect.width()) {
                inSampleSize = max(
                    Math.round(regionRect.height().toFloat() / viewRect.height().toFloat())
                        .toDouble(),
                    Math.round(regionRect.width().toFloat() / viewRect.width().toFloat()).toDouble()
                ).toInt()
                if (inSampleSize > 1) {
                    for (i in 1..9) {
                        if (2.0.pow(i.toDouble()) > inSampleSize) {
                            inSampleSize = 2.0.pow((i - 1).toDouble()).toInt()
                            break
                        }
                    }
                }
            }
            return inSampleSize
        }

        var TEST_LOAD_IMAGE_TASK_OBSERVABLE: Subject<Boolean>? = null

        init {
            if (GlobalConfig.isTest()) TEST_LOAD_IMAGE_TASK_OBSERVABLE = PublishSubject.create()
        }
    }
}
