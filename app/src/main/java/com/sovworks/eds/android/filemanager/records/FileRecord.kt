package com.sovworks.eds.android.filemanager.records

import android.content.Context
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.text.format.DateFormat
import android.text.format.Formatter
import android.util.TypedValue
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.R
import com.sovworks.eds.android.helpers.ExtendedFileInfoLoader.ExtendedFileInfo
import com.sovworks.eds.android.helpers.Util
import com.sovworks.eds.android.service.FileOpsService
import com.sovworks.eds.android.settings.UserSettings
import com.sovworks.eds.fs.Path
import com.sovworks.eds.fs.util.StringPathUtil
import com.sovworks.eds.settings.GlobalConfig
import java.io.IOException

open class FileRecord(context: Context?) : FsBrowserRecord(context) {
    class ExtFileInfo : ExtendedFileInfo {
        var mainIcon: Drawable? = null

        override fun attach(record: BrowserRecord) {
            val fr = record as FileRecord
            _records.add(fr)
            fr._mainIcon = mainIcon
            fr._needLoadExtInfo = false
            FsBrowserRecord.Companion.updateRowView(fr.hostFragment, fr)
        }

        override fun detach(record: BrowserRecord) {
            _records.remove(record)
        }

        override fun clear() {
            for (r in _records) {
                val fr = r as FileRecord
                val rvi: RowViewInfo =
                    FsBrowserRecord.Companion.getCurrentRowViewInfo(fr.hostFragment, fr)
                if (rvi != null) {
                    val iv = rvi.view!!.findViewById<ImageView>(android.R.id.icon)
                    iv.setImageDrawable(null)
                    iv.setImageBitmap(null)
                    FsBrowserRecord.Companion.updateRowView(rvi)
                }
            }

            //if(mainIcon instanceof BitmapDrawable)
            //{
            //   Bitmap b = ((BitmapDrawable) mainIcon).getBitmap();
            //    if(b!=null)
            //        b.recycle();
            //}
            mainIcon = null
        }

        private val _records: MutableList<BrowserRecord> = ArrayList()
    }

    @Throws(IOException::class)
    override fun init(path: Path) {
        super.init(path)
        updateFileInfoString()
    }

    override fun allowSelect(): Boolean {
        return _host!!.allowFileSelect()
    }

    override fun updateView(view: View, position: Int) {
        super.updateView(view, position)
        val tv = view.findViewById<TextView>(android.R.id.text2)
        if (_infoString != null) {
            tv.visibility = View.VISIBLE
            tv.text = _infoString
        } else tv.visibility = View.INVISIBLE

        val iv = view.findViewById<ImageView>(android.R.id.icon)
        if (_mainIcon != null) {
            //iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setImageDrawable(_mainIcon)
            if (_animateIcon) {
                iv.startAnimation(AnimationUtils.loadAnimation(_context, R.anim.restore))
                _animateIcon = false
            }
        }
        //DisplayMetrics dm = _mainActivity.getResources().getDisplayMetrics();
        //_iconWidth = iv.getMeasuredWidth();
        //if(_iconWidth == 0)
        //    _iconWidth = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, Preferences.FB_PREVIEW_WIDTH, dm);
        //_iconHeight = iv.getMeasuredHeight();
        //if(_iconHeight == 0)
        //    _iconHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, Preferences.FB_PREVIEW_HEIGHT, dm);
    }

    override fun loadExtendedInfo(): ExtendedFileInfo {
        val res = ExtFileInfo()
        initExtFileInfo(res)
        return res
    }

    override fun needLoadExtendedInfo(): Boolean {
        return _needLoadExtInfo
    }

    protected var _needLoadExtInfo: Boolean

    override val defaultIcon: Drawable?
        get() = getFileIcon(_host)

    protected fun updateFileInfoString() {
        if (path != null) {
            try {
                _infoString = formatInfoString(_context!!)
            } catch (ignored: IOException) {
            }
        } else _infoString = null
    }

    @Throws(IOException::class)
    protected fun formatInfoString(context: Context): String {
        val sb = StringBuilder()
        appendSizeInfo(context, sb)
        appendModDataInfo(context, sb)
        return sb.toString()
    }

    protected fun appendSizeInfo(context: Context, sb: StringBuilder) {
        sb.append(
            String.format(
                "%s: %s",
                context.getText(R.string.size),
                Formatter.formatFileSize(context, size)
            )
        )
    }

    protected fun appendModDataInfo(context: Context, sb: StringBuilder) {
        val md = modificationDate
        if (md != null) {
            val df = DateFormat.getDateFormat(context)
            val tf = DateFormat.getTimeFormat(context)
            sb.append(
                String.format(
                    " %s: %s %s",
                    context.getText(R.string.last_modified),
                    df.format(md),
                    tf.format(md)
                )
            )
        }
    }

    protected fun initExtFileInfo(info: ExtFileInfo) {
        if (_loadPreviews) info.mainIcon = loadMainIcon()
    }

    protected fun loadMainIcon(): Drawable? {
        val mime = FileOpsService.getMimeTypeFromExtension(
            _context, StringPathUtil(
                name
            ).fileExtension
        )
        var res: Drawable? = null
        try {
            res = if (mime.startsWith("image/")) getImagePreview(path) else getDefaultAppIcon(mime)
            _animateIcon = true
        } catch (e: IOException) {
            Logger.log(e)
        }
        return res
    }

    @Throws(IOException::class)
    protected fun getImagePreview(path: Path): Drawable? {
        val bitmap = Util.loadDownsampledImage(path, _iconWidth, _iconHeight)
        return if (bitmap != null) BitmapDrawable(_context!!.resources, bitmap) else null
    }

    private var _iconWidth = 40
    private var _iconHeight = 40
    private var _infoString: String? = null
    private var _mainIcon: Drawable? = null

    private var _animateIcon = false
    private val _loadPreviews: Boolean

    init {
        _needLoadExtInfo = UserSettings.getSettings(context).showPreviews()
        _loadPreviews = _needLoadExtInfo
        val dm = _context!!.resources.displayMetrics
        _iconWidth = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            GlobalConfig.FB_PREVIEW_WIDTH.toFloat(),
            dm
        ).toInt()
        _iconHeight = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            GlobalConfig.FB_PREVIEW_HEIGHT.toFloat(),
            dm
        ).toInt()
    }

    private fun getDefaultAppIcon(mime: String): Drawable? {
        if (mime == "*/*") return null
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setType(mime)

        val pacMan = _context!!.packageManager
        try {
            val matches = pacMan.queryIntentActivities(intent, 0)
            for (match in matches) {
                val icon = match.loadIcon(pacMan)
                if (icon != null) return icon //drawableToBitmap(icon);
            }
        } catch (ignored: NullPointerException) {
            //bug?
            //java.lang.NullPointerException: Attempt to invoke virtual method 'java.lang.String android.net.Uri.getHost()' on a null object reference
            //at android.os.Parcel.readException(Parcel.java:1552)
        }
        return null
    } /*
    private Bitmap drawableToBitmap(Drawable d)
    {
        if (d instanceof BitmapDrawable)
            return ((BitmapDrawable)d).getBitmap();

        Bitmap bitmap = Bitmap.createBitmap(d.getIntrinsicWidth(), d.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        d.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        d.draw(canvas);
        return bitmap;
    }
    */

    companion object {
        private var _fileIcon: Drawable? = null

        @Synchronized
        private fun getFileIcon(context: Context?): Drawable? {
            if (_fileIcon == null && context != null) {
                val typedValue = TypedValue()
                context.theme.resolveAttribute(R.attr.fileIcon, typedValue, true)
                _fileIcon = context.resources.getDrawable(typedValue.resourceId)
            }
            return _fileIcon
        }
    }
}