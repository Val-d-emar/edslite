package com.sovworks.eds.android.filemanager.tasks

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.PowerManager
import androidx.exifinterface.media.ExifInterface
import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.filemanager.fragments.PreviewFragment.Companion.calcSampleSize
import com.sovworks.eds.android.filemanager.fragments.PreviewFragment.Companion.loadDownsampledImage
import com.sovworks.eds.android.filemanager.fragments.PreviewFragment.Companion.loadImageParams
import com.sovworks.eds.android.helpers.CompatHelper
import com.sovworks.eds.fs.Path
import com.sovworks.eds.settings.GlobalConfig
import io.reactivex.Single
import io.reactivex.SingleEmitter
import java.io.IOException

class LoadedImage
private constructor(private val _imagePath: Path, private val _viewRect: Rect, regionRect: Rect) {
    var regionRect: Rect?
        private set
    var isOptimSupported: Boolean = false
        private set
    var imageData: Bitmap? = null
        private set
    var flipX: Boolean = false
        private set
    var flipY: Boolean = false
        private set
    var sampleSize: Int = 0
        private set
    var rotation: Int = 0
        private set

    init {
        this.regionRect = regionRect
    }

    @Throws(IOException::class)
    private fun loadImage() {
        val params = loadImageParams(_imagePath)
        val loadFull: Boolean
        val isJpg = "image/jpeg".equals(params.outMimeType, ignoreCase = true)
        if (regionRect == null) {
            regionRect = Rect(0, 0, params.outWidth, params.outHeight)
            isOptimSupported = isJpg || "image/png".equals(params.outMimeType, ignoreCase = true)
            loadFull = true
        } else {
            if (regionRect!!.top < 0) regionRect!!.top = 0
            if (regionRect!!.left < 0) regionRect!!.left = 0
            if (regionRect!!.width() > params.outWidth) regionRect!!.right -= (regionRect!!.width() - params.outWidth)
            if (regionRect!!.height() > params.outHeight) regionRect!!.bottom -= (regionRect!!.height() - params.outHeight)
            loadFull = false
        }
        sampleSize = calcSampleSize(_viewRect, regionRect!!)
        var i = 0
        while (i < 5) {
            try {
                if (loadFull) imageData = loadDownsampledImage(
                    _imagePath,
                    sampleSize
                )
                else imageData = CompatHelper.loadBitmapRegion(
                    _imagePath,
                    sampleSize,
                    regionRect
                )

                flipY = false
                flipX = flipY
                rotation = 0
                if (isJpg) loadInitOrientation(_imagePath)
                return
            } catch (e: OutOfMemoryError) {
                System.gc()
            }
            try {
                Thread.sleep(3000)
            } catch (ignored: InterruptedException) {
            }
            i++
            sampleSize *= 2
        }
        throw OutOfMemoryError()
    }

    private fun loadInitOrientation(imagePath: Path) {
        try {
            val s = imagePath.file.inputStream
            try {
                val m = ImageMetadataReader.readMetadata(s)

                for (directory in m.directories) if (directory.containsTag(ExifSubIFDDirectory.TAG_ORIENTATION)) {
                    val orientation = directory.getInt(ExifSubIFDDirectory.TAG_ORIENTATION)
                    when (orientation) {
                        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> flipX = true
                        ExifInterface.ORIENTATION_ROTATE_180 -> rotation = 180
                        ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                            rotation = 180
                            flipX = true
                        }

                        ExifInterface.ORIENTATION_TRANSPOSE -> {
                            rotation = 90
                            flipX = true
                        }

                        ExifInterface.ORIENTATION_ROTATE_90 -> rotation = 90
                        ExifInterface.ORIENTATION_TRANSVERSE -> {
                            rotation = -90
                            flipX = true
                        }

                        ExifInterface.ORIENTATION_ROTATE_270 -> rotation = -90
                    }
                    break
                }
            } finally {
                s.close()
            }
        } catch (e: Exception) {
            if (GlobalConfig.isDebug()) Logger.log(e)
        }
    }

    companion object {
        fun createObservable(
            context: Context,
            imagePath: Path,
            viewRect: Rect,
            regionRect: Rect
        ): Single<LoadedImage?> {
            return Single.create { emitter: SingleEmitter<LoadedImage?> ->
                val pm =
                    context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
                val wl = pm?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "LoadImageTask"
                )
                wl?.acquire(10000)
                try {
                    val loadedImage = LoadedImage(imagePath, viewRect, regionRect)
                    loadedImage.loadImage()
                    emitter.onSuccess(loadedImage)
                } finally {
                    wl?.release()
                }
            }
        }
    }
}
