package com.sovworks.eds.android.views

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ScaleGestureDetector.SimpleOnScaleGestureListener
import android.widget.ImageView
import android.widget.ImageView.ScaleType.MATRIX
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

open class GestureImageView(context: Context, attr: AttributeSet?) :
    ImageView(context, attr) {
    interface OptimImageRequiredListener {
        fun onOptimImageRequired(srcImageRect: Rect?)
    }


    interface NavigListener {
        fun onNext()
        fun onPrev()
    }

    fun setNavigListener(listener: NavigListener?) {
        _navigListener = listener
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        // Let the ScaleGestureDetector inspect all events.
        _scaleDetector.onTouchEvent(ev)
        _flingDetector.onTouchEvent(ev)

        val action = ev.action
        when (action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                val x = ev.x
                val y = ev.y

                _lastTouchX = x
                _lastTouchY = y
                _activePointerId = ev.getPointerId(0)
            }

            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = ev.findPointerIndex(_activePointerId)
                val x = ev.getX(pointerIndex)
                val y = ev.getY(pointerIndex)


                // Only move if the ScaleGestureDetector isn't processing a gesture.
                if (!_scaleDetector.isInProgress) {
                    if (drawable == null) break

                    val dx = x - _lastTouchX
                    val dy = y - _lastTouchY
                    _posX += dx
                    _posY += dy
                    moveAndScale()
                }

                _lastTouchX = x
                _lastTouchY = y
            }

            MotionEvent.ACTION_UP -> {
                _activePointerId = INVALID_POINTER_ID
                onTouchUp()
            }

            MotionEvent.ACTION_CANCEL -> {
                _activePointerId = INVALID_POINTER_ID
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex =
                    (ev.action and MotionEvent.ACTION_POINTER_INDEX_MASK) shr MotionEvent.ACTION_POINTER_INDEX_SHIFT
                val pointerId = ev.getPointerId(pointerIndex)
                if (pointerId == _activePointerId) {
                    // This was our active pointer going up. Choose a new
                    // active pointer and adjust accordingly.
                    val newPointerIndex = if (pointerIndex == 0) 1 else 0
                    _lastTouchX = ev.getX(newPointerIndex)
                    _lastTouchY = ev.getY(newPointerIndex)
                    _activePointerId = ev.getPointerId(newPointerIndex)
                }
            }
        }

        return true
    }

    fun setOptimImage(b: Bitmap, sampleSize: Int) {
        if (_optimImage != null) _optimImage!!.recycle()
        val sfX = sampleSize.toFloat() / _originalSampleSize.toFloat() * _scaleFactorX
        val sfy = sampleSize.toFloat() / _originalSampleSize.toFloat() * _scaleFactorY
        _optimImage = b
        _optimImageMatrix.reset()
        _optimImageMatrix.postRotate(_rotation.toFloat())
        _optimImageMatrix.postScale(sfX, sfy, 0f, 0f)
        val imageRect = RectF(0f, 0f, b.width.toFloat(), b.height.toFloat())
        val delta = PointF()
        validate(imageRect, _optimImageMatrix, delta)
        _optimImageMatrix.postTranslate(delta.x, delta.y)
        setImageBitmap(b)
        val m = Matrix(_optimImageMatrix)
        imageMatrix = m
    }

    fun clearImage() {
        setImage(null, 0)
    }

    fun setImage(bm: Bitmap?, sampleSize: Int) {
        setImage(bm, sampleSize, 0, false, false)
    }

    fun setImage(bm: Bitmap?, sampleSize: Int, rotation: Int, flixpX: Boolean, flipY: Boolean) {
        _inited = false
        if (_optimImage != null) {
            _optimImage!!.recycle()
            _optimImage = null
        }

        if (originalImage != null) originalImage!!.recycle()
        originalImage = bm
        _originalSampleSize = sampleSize
        _rotation = rotation
        _flipX = flixpX
        _flipY = flipY
        setImageBitmap(bm)
        if (bm != null) {
            _imageRect[0f, 0f, bm.width.toFloat()] = bm.height.toFloat()
            calcInitParams()
        }
    }


    fun rotateLeft() {
        _rotation -= 90
        while (_rotation < 0) _rotation += 360
        moveAndScale()
        startOptimImageLoad()
    }

    fun rotateRight() {
        _rotation += 90
        while (_rotation > 360) _rotation -= 360
        moveAndScale()
        startOptimImageLoad()
    }

    fun zoomIn() {
        var sci = findClosestScaleIndex()
        if (++sci < SCALE_FACTORS.size) {
            _scaleFactorX = SCALE_FACTORS[sci] * (if (_flipX) -1 else 1)
            _scaleFactorY = SCALE_FACTORS[sci] * (if (_flipY) -1 else 1)
            moveAndScale()
            startOptimImageLoad()
        }
    }

    fun zoomOut() {
        var sci = findClosestScaleIndex()
        if (--sci >= 0) {
            _scaleFactorX = SCALE_FACTORS[sci] * (if (_flipX) -1 else 1)
            _scaleFactorY = SCALE_FACTORS[sci] * (if (_flipY) -1 else 1)
            moveAndScale()
            startOptimImageLoad()
        }
    }

    fun rotate(angle: Int) {
        _rotation = angle
        moveAndScale()
        startOptimImageLoad()
    }

    fun setOnLoadOptimImageListener(listener: OptimImageRequiredListener?) {
        _onLoadOptimImageListener = listener
    }

    fun setOnSizeChangedListener(listener: Runnable?) {
        _onSizeChangedListener = listener
    }

    fun setAutoZoom(`val`: Boolean) {
        _autoZoom = `val`
        calcInitParams()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewRect[0f, 0f, w.toFloat()] = h.toFloat()
        if (!_inited) calcInitParams()
        if (_onSizeChangedListener != null) _onSizeChangedListener!!.run()
    }


    protected open fun onTouchUp() {
        startOptimImageLoad()
    }

    private var _navigListener: NavigListener? = null
    private val _imageRect = RectF()
    val viewRect: RectF = RectF()
    private val _scaleDetector: ScaleGestureDetector
    private val _flingDetector: GestureDetector
    val originalImageMatrix: Matrix = Matrix()
    private val _optimImageMatrix = Matrix()
    private var _scaleFactorX = 0f
    private var _scaleFactorY = 0f
    private var _posX = 0f
    private var _posY = 0f
    private var _lastTouchX = 0f
    private var _lastTouchY = 0f
    private var _scaleX = 0f
    private var _scaleY = 0f
    private var _activePointerId = INVALID_POINTER_ID
    private var _allowNavig = false
    private var _autoZoom = false
    private var _flipX = false
    private var _flipY = false
    private var _inited = false
    private var _rotation = 0
    var originalImage: Bitmap? = null
        private set
    private var _optimImage: Bitmap? = null
    private var _originalSampleSize = 0
    private var _onSizeChangedListener: Runnable? = null


    private var _onLoadOptimImageListener: OptimImageRequiredListener? = null

    init {
        _scaleDetector = ScaleGestureDetector(context, object : SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                val m = Matrix()
                originalImageMatrix.invert(m)
                val points = floatArrayOf(detector.focusX, detector.focusY)
                m.mapPoints(points)
                _scaleX = points[0]
                _scaleY = points[1]
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                var sf = (abs(_scaleFactorX.toDouble()) * detector.scaleFactor).toFloat()
                // Don't let the object get too small or too large.
                sf = max(MIN_SCALE.toDouble(), min(sf.toDouble(), MAX_SCALE.toDouble())).toFloat()

                _scaleFactorX = sf
                _scaleFactorY = sf
                moveAndScale()
                return true
            }
        })
        _flingDetector = GestureDetector(context, object : SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?, e2: MotionEvent, velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (_navigListener != null && _allowNavig)  //&& Math.abs(velocityX) > MIN_NAVIG_VELOCITY)
                {
                    if (velocityX < 0) _navigListener!!.onNext()
                    else _navigListener!!.onPrev()

                    return true
                }
                return false
            }
        })
        scaleType = MATRIX
    }

    private fun showOriginalImage() {
        super.setImageBitmap(originalImage)
        if (_optimImage != null) {
            _optimImage!!.recycle()
            _optimImage = null
        }
    }

    private fun findClosestScaleIndex(): Int {
        var idx = 0
        var delta = 100f
        for (i in SCALE_FACTORS.indices) {
            val c = abs((SCALE_FACTORS[i] - abs(_scaleFactorX.toDouble())).toDouble()).toFloat()
            if (c < delta) {
                delta = c
                idx = i
            }
        }
        return idx
    }

    private fun centerImage() {
        validate()
    }

    private fun moveAndScale() {
        showOriginalImage()
        applyTrans()
        validate()
        val m = Matrix(originalImageMatrix)
        imageMatrix = m
        val rf = RectF(_imageRect)
        originalImageMatrix.mapRect(rf)
        _allowNavig =
            rf.width() <= viewRect.width() //_imageRect.width()*_scaleFactor<=_viewRect.width();			
    }

    private fun startOptimImageLoad() {
        if (_onLoadOptimImageListener == null) return

        if (_originalSampleSize > 1) {
            var rf = RectF(_imageRect)
            originalImageMatrix.mapRect(rf)
            if (rf.width() > viewRect.width() || rf.height() > viewRect.height()) {
                val m = Matrix()

                //m.postScale(_scaleFactor, _scaleFactor,_scaleX,_scaleY);		
                //m.postTranslate(_posX, _posY);
                if (originalImageMatrix.invert(m)) {
                    m.postScale(_originalSampleSize.toFloat(), _originalSampleSize.toFloat())
                    rf = RectF(viewRect)
                    m.mapRect(rf)
                    val r = Rect()
                    rf.round(r)
                    _onLoadOptimImageListener!!.onOptimImageRequired(r)
                }
            }
        }
    }

    private fun calcInitParams() {
        if (_imageRect.width() == 0f || _imageRect.height() == 0f || viewRect.width() == 0f || viewRect.height() == 0f) return

        _scaleY = 0f
        _scaleX = _scaleY
        _posY = 0f
        _posX = _posY
        _scaleFactorY = 1f
        _scaleFactorX = _scaleFactorY
        val imageRect = RectF(_imageRect)
        if (_rotation != 0) {
            applyTrans()
            originalImageMatrix.mapRect(imageRect)
        }
        val scaleFactor =
            if (imageRect.width() <= viewRect.width() && imageRect.height() <= viewRect.height() && !_autoZoom) 1f
            else min(
                (viewRect.height() / imageRect.height()).toDouble(),
                (viewRect.width() / imageRect.width()).toDouble()
            ).toFloat()


        _scaleFactorX = if (_flipX) -scaleFactor else scaleFactor
        _scaleFactorY = if (_flipY) -scaleFactor else scaleFactor
        originalImageMatrix.reset()
        originalImageMatrix.postRotate(_rotation.toFloat())
        originalImageMatrix.postScale(_scaleFactorX, _scaleFactorY)
        validate()
        val m = Matrix(originalImageMatrix)
        imageMatrix = m
        _inited = true
    }

    private fun applyTrans() {
        originalImageMatrix.reset()
        originalImageMatrix.postScale(_scaleFactorX, _scaleFactorY, _scaleX, _scaleY)
        originalImageMatrix.postRotate(_rotation.toFloat())
        originalImageMatrix.postTranslate(_posX, _posY)
    }

    private fun validate() {
        val delta = PointF()
        validate(_imageRect, originalImageMatrix, delta)

        if (delta.x != 0f || delta.y != 0f) {
            _posX += delta.x
            _posY += delta.y
            applyTrans()
        }
    }

    private fun validate(curImageRect: RectF, curImageMatrix: Matrix, outDelta: PointF) {
        var deltaX = 0f
        var deltaY = 0f
        val imageRect = RectF(curImageRect)
        curImageMatrix.mapRect(imageRect)

        if (imageRect.height() <= viewRect.height()) deltaY =
            (viewRect.height() - imageRect.height()) / 2 - imageRect.top
        else if (imageRect.top > 0) deltaY = -imageRect.top
        else if (imageRect.bottom < viewRect.height()) deltaY = viewRect.height() - imageRect.bottom


        if (imageRect.width() <= viewRect.width()) deltaX =
            (viewRect.width() - imageRect.width()) / 2 - imageRect.left
        else if (imageRect.left > 0) deltaX = -imageRect.left
        else if (imageRect.right < viewRect.width()) deltaX = viewRect.width() - imageRect.right

        outDelta.offset(deltaX, deltaY)
    }

    companion object {
        //private static final float MIN_NAVIG_VELOCITY = 300;
        private const val MIN_SCALE = 0.1f
        private const val MAX_SCALE = 10f

        private const val INVALID_POINTER_ID = -1

        private val SCALE_FACTORS = floatArrayOf(0.1f, 0.2f, 0.4f, 0.8f, 1f, 2f, 4f, 8f, 10f)
    }
}
