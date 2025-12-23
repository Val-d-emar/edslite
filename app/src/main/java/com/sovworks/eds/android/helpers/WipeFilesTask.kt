package com.sovworks.eds.android.helpers

import com.sovworks.eds.fs.File
import com.sovworks.eds.fs.util.SrcDstCollection
import java.io.IOException
import java.util.Random

class WipeFilesTask
    (protected val _wipe: Boolean) {
    interface ITask {
        fun cancel(): Boolean
        fun progress(sizeInc: Int)
    }

    protected var _syncer: Any? = null


    protected val iTask: ITask
        get() = object : ITask {
            override fun cancel(): Boolean {
                return false
            }

            override fun progress(sizeInc: Int) {
            }
        }

    @Throws(Exception::class)
    protected fun doWork(vararg records: SrcDstCollection?) {
        wipeFilesRnd(iTask, _syncer, _wipe, *records)
    }

    companion object {
        @JvmOverloads
        @Throws(IOException::class)
        fun wipeFileRnd(file: File, task: ITask? = null) {
            val rg = Random()
            try {
                val buf = ByteArray(4 * 1024)
                val l = file.size
                val s = file.outputStream
                try {
                    var i: Long = 0
                    while (i < l) {
                        if (task != null && task.cancel()) return
                        rg.nextBytes(buf)
                        s.write(buf)
                        val tmp = l - i - buf.size
                        updStatus(task, if (tmp < 0) buf.size + tmp else buf.size.toLong())

                        i += buf.size.toLong()
                    }
                    s.flush()
                } finally {
                    s.close()
                }
            } finally {
                file.delete()
            }
        }

        @JvmStatic
        @Throws(IOException::class)
        fun wipeFilesRnd(
            task: ITask?,
            syncer: Any?,
            wipe: Boolean,
            vararg records: SrcDstCollection?
        ) {
            for (col in records) {
                if (col != null) {
                    for (rec in col) {
                        if (task != null && task.cancel()) return
                        val p = rec.srcLocation.currentPath
                        if (p.isFile) {
                            if (syncer != null) {
                                synchronized(syncer) {
                                    wipeFile(p.file, wipe, task)
                                }
                            } else wipeFile(p.file, wipe, task)
                        } else if (p.isDirectory) p.directory.delete()
                    }
                }
            }
        }

        @JvmStatic
        @Throws(IOException::class)
        fun wipeFile(file: File, wipe: Boolean, task: ITask?) {
            if (wipe) wipeFileRnd(file, task)
            else {
                file.delete()
                updStatus(task, 0)
            }
        }

        protected fun updStatus(task: ITask?, sizeInc: Long) {
            if (task == null) return
            task.progress(sizeInc.toInt())
        }
    }
}
