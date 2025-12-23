package com.sovworks.eds.fs.exfat

import android.annotation.SuppressLint
import com.sovworks.eds.android.Logger.Companion.debug
import com.sovworks.eds.android.Logger.Companion.log
import com.sovworks.eds.android.settings.SystemConfig
import com.sovworks.eds.fs.FileSystem
import com.sovworks.eds.fs.Path
import com.sovworks.eds.fs.RandomAccessIO
import com.sovworks.eds.fs.exfat.ExFat.ModuleState.Absent
import com.sovworks.eds.fs.exfat.ExFat.ModuleState.Incompatible
import com.sovworks.eds.fs.exfat.ExFat.ModuleState.Installed
import com.sovworks.eds.fs.exfat.ExFat.ModuleState.Unknown
import com.sovworks.eds.fs.util.FileStat
import com.sovworks.eds.fs.util.Util
import com.sovworks.eds.settings.GlobalConfig
import java.io.File
import java.io.IOException

class ExFat(
    @field:Suppress("unused") private val _exfatImageFile: RandomAccessIO,
    readOnly: Boolean
) : FileSystem {
    @Throws(IOException::class)
    override fun getRootPath(): Path {
        return ExFatPath(this, "/")
    }

    @Throws(IOException::class)
    override fun getPath(pathString: String): Path {
        return ExFatPath(this, pathString)
    }

    @Throws(IOException::class)
    override fun close(force: Boolean) {
        synchronized(_sync) {
            if (_exfatPtr != 0L) {
                closeFS()
                _exfatPtr = 0
            }
        }
    }

    override fun isClosed(): Boolean {
        return _exfatPtr == 0L
    }

    val freeSpaceVolumeStartOffset: Long
        get() = freeSpaceStartOffset

    @Throws(IOException::class)
    fun overwriteFreeSpace() {
        val res = randFreeSpace()
        if (res != 0) throw IOException("Failed overwriting the free space. code $res")
    }

    private enum class ModuleState {
        Unknown,
        Absent,
        Incompatible,
        Installed
    }

    private var _exfatPtr: Long

    val _sync: Any = Any()

    init {
        _exfatPtr = openFS(readOnly)
        if (_exfatPtr == 0L) throw IOException("Failed opening exfat file system")
    }

    external fun readDir(path: String?, files: Collection<String>?): Int

    external fun getAttr(stat: FileStat?, path: String?): Int

    external fun makeDir(path: String?): Int

    external fun makeFile(path: String?): Int

    val freeSpace: Long
        external get

    val totalSpace: Long
        external get

    external fun rename(oldPath: String?, newPath: String?): Int

    external fun delete(path: String?): Int

    external fun rmdir(path: String?): Int

    external fun truncate(handle: Long, size: Long): Int

    external fun openFile(path: String?): Long

    external fun closeFile(handle: Long): Int

    external fun getSize(handle: Long): Long

    external fun read(
        handle: Long,
        buf: ByteArray?,
        bufOffset: Int,
        count: Int,
        position: Long
    ): Int

    external fun write(
        handle: Long,
        buf: ByteArray?,
        bufOffset: Int,
        count: Int,
        position: Long
    ): Int

    external fun flush(handle: Long): Int

    external fun openFS(readOnly: Boolean): Long

    external fun closeFS(): Int

    val freeSpaceStartOffset: Long
        external get

    external fun randFreeSpace(): Int

    external fun updateTime(path: String?, time: Long): Int

    companion object {
        @Throws(IOException::class)
        fun isExFATImage(img: RandomAccessIO): Boolean {
            val buf = ByteArray(8)
            img.seek(3)
            return Util.readBytes(img, buf) == buf.size && EXFAT_SIGN.contentEquals(buf)
        }

        private val EXFAT_SIGN = byteArrayOf(
            'E'.code.toByte(),
            'X'.code.toByte(),
            'F'.code.toByte(),
            'A'.code.toByte(),
            'T'.code.toByte(),
            ' '.code.toByte(),
            ' '.code.toByte(),
            ' '.code.toByte()
        )
        private const val MIN_COMPATIBLE_NATIVE_MODULE_VERSION = 1001

        @JvmOverloads
        @Throws(IOException::class)
        fun makeNewFS(
            img: RandomAccessIO,
            label: String? = null,
            volumeSerial: Int = 0,
            firstSector: Long = 0,
            sectorsPerCluster: Int = -1
        ) {
            if (makeFS(
                    img,
                    label,
                    volumeSerial,
                    firstSector,
                    sectorsPerCluster
                ) != 0
            ) throw IOException("Failed formatting an ExFAT image")
        }

        private const val MODULE_NAME = "edsexfat"
        private const val LIB_NAME = "lib" + MODULE_NAME + ".so"

        @JvmStatic
        val isModuleInstalled: Boolean
            get() = _nativeModuleState == Installed

        val isModuleIncompatible: Boolean
            get() = _nativeModuleState == Incompatible

        @SuppressLint("UnsafeDynamicallyLoadedCode")
        fun loadNativeLibrary() {
            if (_nativeModuleState == Absent || _nativeModuleState == Unknown) {
                System.load(modulePath.absolutePath)
                _nativeModuleState = Incompatible
                if (version < MIN_COMPATIBLE_NATIVE_MODULE_VERSION) throw RuntimeException("Incompatible native exfat module version")
                debug("External exFAT module has been loaded.")
                _nativeModuleState = Installed
            }
        }

        val modulePath: File
            get() = File(
                SystemConfig.getInstance().fsmFolderPath,
                LIB_NAME
            )

        private var _nativeModuleState = Unknown

        init {
            if (_nativeModuleState == Unknown) {
                if (modulePath.exists()) {
                    debug("Module file exists")
                    try {
                        loadNativeLibrary()
                    } catch (e: Throwable) {
                        debug("Failed loading external exFAT module")
                        if (GlobalConfig.isDebug()) log(e)
                    }
                } else try {
                    System.loadLibrary(MODULE_NAME)
                    debug("Built-in exFAT module has been loaded.")
                    _nativeModuleState = Installed
                } catch (e: Throwable) {
                    _nativeModuleState = Absent
                }
            }
        }

        private external fun makeFS(
            raio: RandomAccessIO,
            label: String?,
            volumeSerial: Int,
            firstSector: Long,
            sectorsPerCluster: Int
        ): Int

        val version: Int
            external get
    }
}
