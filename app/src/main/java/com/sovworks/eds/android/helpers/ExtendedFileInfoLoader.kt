package com.sovworks.eds.android.helpers

import android.os.Handler
import android.os.Looper
import android.util.LruCache
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.filemanager.records.BrowserRecord
import com.sovworks.eds.android.helpers.ExtendedFileInfoLoader
import com.sovworks.eds.fs.Path
import com.sovworks.eds.locations.Location
import java.io.Closeable
import java.io.IOException
import java.util.AbstractQueue
import java.util.Arrays

class ExtendedFileInfoLoader : Closeable {
    interface ExtendedFileInfo {
        fun attach(record: BrowserRecord?)
        fun detach(record: BrowserRecord?)
        fun clear()
    }

    //Call from main thread
    fun requestExtendedInfo(locationId: String, rec: BrowserRecord) {
        val ii = InfoCache(locationId, rec)
        val data = _loadedInfo[ii.pathKey]
        if (data != null) data.attach(rec)
        else {
            synchronized(_loadingQueue) {
                enqueueRequest(ii)
            }
        }
    }

    //Call from main thread
    fun detachRecord(locationId: String, rec: BrowserRecord) {
        val ii = InfoCache(locationId, rec)
        val data = _loadedInfo[ii.pathKey]
        data?.detach(rec)
        synchronized(_loadingQueue) {
            _loadingQueue.discard(rec)
        }
    }

    fun pauseViewUpdate() {
        _pause = true
    }

    fun resumeViewUpdate() {
        _pause = false
    }

    fun discardCache(loc: Location, path: Path) {
        val key = getPathKey(loc.id, path)
        _loadedInfo.remove(key)
    }

    override fun close() {
        _pause = true
        _stop = true
        synchronized(_loadingQueue) {
            (_loadingQueue as Object).notify()
        }
        _loadedInfo.evictAll()
        try {
            _loadingTask.join(5000)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    private inner class LoadingTask : Thread() {
        override fun run() {
            var nextTarget: InfoCache? = null
            while (!_stop) {
                try {
                    synchronized(_loadingQueue) {
                        if (nextTarget == null) (_loadingQueue as Object).wait()
                        nextTarget = _loadingQueue.last //_loadingQueue.poll();
                    }
                    if (nextTarget != null && !nextTarget!!.discard) processExtInfo(nextTarget!!)
                } catch (e: Exception) {
                    Logger.log(e)
                }
            }
        }

        @Throws(IOException::class)
        fun processExtInfo(ii: InfoCache) {
            val data = ii.record.loadExtendedInfo()
            if (data != null) {
                _loadedInfo.put(ii.pathKey, data)
                _updateViewHandler.post {
                    if (!ii.discard) {
                        data.attach(ii.record)
                        if (!_pause) ii.record.updateView()
                    }
                }
            }
        }
    }

    //private final Map<String,IconInfo> _loadedInfo = new HashMap<String,IconInfo>(Preferences.MAX_CACHED_ICONS);
    private val _loadedInfo: LruCache<String, ExtendedFileInfo>
    private val _loadingQueue: FileInfoLoadQueue
    private val _updateViewHandler = Handler(Looper.getMainLooper())
    private var _stop = false
    private var _pause = true
    private val _loadingTask: LoadingTask

    init {
        _loadedInfo = object : LruCache<String?, ExtendedFileInfo?>(
            FB_NUM_CACHED_EXTENDED_INFO
        ) {
            /*@Override
                       protected int sizeOf(String key, ExtendedFileInfo ii)
                       {
                           return 16 + (ii.icon == null ? 0 : ii.icon.getWidth()*ii.icon.getHeight()*4);
                       }*/
            override fun entryRemoved(
                evicted: Boolean,
                key: String?,
                oldValue: ExtendedFileInfo?,
                newValue: ExtendedFileInfo?
            ) {
                if (oldValue != null) {
                    _updateViewHandler.post { oldValue.clear() }
                }
                super.entryRemoved(evicted, key, oldValue, newValue)
            }
        }
        _loadingQueue = FileInfoLoadQueue(FB_EXTENDED_INFO_QUEUE_SIZE)
        _loadingTask = LoadingTask()
        _loadingTask.start()
    }

    private fun removeOldestInfo() {
        _loadingQueue.poll()
    }

    private fun enqueueRequest(ii: InfoCache) {
        if (_loadingQueue.size == _loadingQueue.capacity) removeOldestInfo()
        _loadingQueue.add(ii)
        (_loadingQueue as Object).notify()
    }

    companion object {
        private const val FB_EXTENDED_INFO_QUEUE_SIZE = 40
        private const val FB_NUM_CACHED_EXTENDED_INFO = 100

        fun getPathKey(locationId: String, path: Path): String {
            return String.format("%s/%s", locationId, path.pathString)
        }

        @JvmStatic
        @get:Synchronized
        val instance: ExtendedFileInfoLoader
            get() {
                if (_instance == null) _instance =
                    ExtendedFileInfoLoader()
                return _instance!!
            }

        @JvmStatic
        @Synchronized
        fun closeInstance() {
            if (_instance != null) {
                _instance!!.close()
                _instance = null
            }
        }

        private var _instance: ExtendedFileInfoLoader? = null
    }
}

internal class InfoCache
    (val locationId: String, val record: BrowserRecord) {
    val pathKey: String
        get() = ExtendedFileInfoLoader.getPathKey(locationId, record.path)

    var discard: Boolean = false
}

internal class FileInfoLoadQueue(capacity: Int) : AbstractQueue<InfoCache?>() {
    val capacity: Int
        get() = _buf.size

    fun discard(rec: BrowserRecord) {
        for (i in 0..<_usedSlots) {
            val tmp = _buf[(_headPosition + i) % _buf.size]
            if (tmp.record === rec) tmp.discard = true
        }
    }

    override fun offer(e: InfoCache): Boolean {
        if (e == null) throw RuntimeException("Argument cannot be null")

        if (_usedSlots < _buf.size) {
            _buf[(_headPosition + _usedSlots++) % _buf.size] = e
            return true
        }
        return false
    }

    override fun peek(): InfoCache? {
        return if (_usedSlots > 0) _buf[_headPosition] else null
    }

    override fun poll(): InfoCache? {
        if (_usedSlots == 0) return null

        val tmp = _buf[_headPosition]
        _buf[_headPosition] = null
        _headPosition = ++_headPosition % _buf.size
        _usedSlots--
        return tmp
    }

    val last: InfoCache?
        get() {
            if (_usedSlots == 0) return null

            val pos = (_headPosition + _usedSlots - 1) % _buf.size
            val tmp = _buf[pos]
            _buf[pos] = null
            _usedSlots--
            return tmp
        }

    override fun clear() {
        _usedSlots = 0
        _headPosition = _usedSlots
        Arrays.fill(_buf, null)
    }

    override fun iterator(): MutableIterator<InfoCache?> {
        return object : MutableIterator<InfoCache?> {
            override fun remove() {
                throw UnsupportedOperationException()
            }

            override fun next(): InfoCache {
                if (!hasNext()) throw NoSuchElementException()

                return _buf[(_headPosition + _proc++) % _buf.size]
            }

            override fun hasNext(): Boolean {
                return _proc < _usedSlots
            }

            private var _proc = 0
        }
    }

    override fun size(): Int {
        return _usedSlots
    }

    private val _buf: Array<InfoCache>
    private var _usedSlots = 0
    private var _headPosition = 0

    init {
        _buf = arrayOfNulls(capacity)
    }
}
