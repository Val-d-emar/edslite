package com.sovworks.eds.fs.encfs

import com.sovworks.eds.android.Logger.Companion.log
import com.sovworks.eds.fs.util.PathBase
import com.sovworks.eds.fs.util.PathUtil
import com.sovworks.eds.fs.util.StringPathUtil
import java.io.IOException

open class Path(
    fs: FS?,
    val realPath: com.sovworks.eds.fs.Path,
    val namingCodecInfo: NameCodecInfo?,
    private val _encryptionKey: ByteArray?
) : PathBase(fs) {
    override fun getFileSystem(): FS {
        return super.getFileSystem() as FS
    }

    override fun getPathString(): String {
        return realPath.pathString
    }

    override fun getPathDesc(): String {
        return decodedPath.toString()
    }

    @Throws(IOException::class)
    override fun exists(): Boolean {
        return realPath.exists()
    }

    @Throws(IOException::class)
    override fun isFile(): Boolean {
        return realPath.isFile
    }

    @Throws(IOException::class)
    override fun isDirectory(): Boolean {
        return realPath.isDirectory
    }

    @Throws(IOException::class)
    override fun getParentPath(): Path? {
        if (realPath == fileSystem.encFSRootPath) return null
        val pp = realPath.parentPath
        return if (pp == null) null else fileSystem.getPathFromRealPath(pp)
    }

    @Throws(IOException::class)
    override fun combine(part: String): Path {
        val encodedParts = calcCombinedEncodedParts(part)
        val newRealPath = realPath.combine(encodedParts.fileName)
        val newPath = fileSystem.getPathFromRealPath(newRealPath)
        if (newPath!!._decodedPath == null) {
            var decodedParts = _decodedPath
            if (decodedParts != null) decodedParts = decodedParts.combine(part)
            newPath.decodedPath = decodedParts
        }
        if (newPath._encodedPath == null) newPath.encodedPath = encodedParts

        return newPath
    }

    @Throws(IOException::class)
    fun calcCombinedEncodedParts(part: String?): StringPathUtil {
        val encodedParts = encodedPath!!
        val codec = namingCodecInfo.getEncDec()
        val iv = if (namingCodecInfo!!.useChainedNamingIV()) chainedIV else null
        try {
            codec!!.init(_encryptionKey)
            if (iv != null) codec.iv = iv
            return encodedParts.combine(codec!!.encodeName(part))
        } finally {
            codec!!.close()
        }
    }

    @Throws(IOException::class)
    override fun getDirectory(): com.sovworks.eds.fs.Directory {
        return Directory(
            this,
            realPath.directory
        )
    }

    @Throws(IOException::class)
    override fun getFile(): com.sovworks.eds.fs.File {
        val c = fileSystem.config
        return File(
            this,
            realPath.file,
            c.dataCodecInfo,
            _encryptionKey!!,
            if (c!!.useExternalFileIV()) chainedIV else null,
            c.blockSize,
            c.useUniqueIV(),
            c.allowHoles(),
            c.macBytes,
            c.macRandBytes,
            false
        )
    }

    override fun getPathUtil(): StringPathUtil {
        return decodedPath!!
    }

    @get:Synchronized
    var decodedPath: StringPathUtil?
        get() {
            if (_decodedPath == null) try {
                _decodedPath = decodePath()
            } catch (e: IOException) {
                log(e)
                _decodedPath = StringPathUtil(realPath.pathString)
            }
            return _decodedPath
        }
        set(decodedPath) {
            _decodedPath = decodedPath
        }

    @get:Throws(IOException::class)
    @get:Synchronized
    var encodedPath: StringPathUtil?
        get() {
            if (_encodedPath == null) _encodedPath = buildEncodedPathFromRealPath(realPath)
            return _encodedPath
        }
        set(encodedPath) {
            _encodedPath = encodedPath
        }

    @get:Synchronized
    open val chainedIV: ByteArray?
        get() {
            if (_chainedIV == null) try {
                _chainedIV = calcChaindedIV()
            } catch (e: IOException) {
                log(e)
            }
            return _chainedIV
        }

    @Throws(IOException::class)
    override fun isRootDirectory(): Boolean {
        return encodedPath!!.isEmpty
    }

    private var _encodedPath: StringPathUtil? = null
    private var _chainedIV: ByteArray?
    private var _decodedPath: StringPathUtil? = null


    @Throws(IOException::class)
    private fun decodePath(): StringPathUtil {
        val encodedParts = encodedPath!!
        val parent = parentPath
        val decodedParent = if (parent == null) StringPathUtil() else parent.decodedPath!!
        val codec = namingCodecInfo.getEncDec()
        try {
            codec!!.init(_encryptionKey)
            if (namingCodecInfo!!.useChainedNamingIV() && parent != null) codec.iv =
                parent.chainedIV
            val decodedName = codec!!.decodeName(encodedParts.fileName)
            if (namingCodecInfo.useChainedNamingIV()) _chainedIV = codec!!.getChainedIV(decodedName)
            return decodedParent.combine(decodedName)
        } finally {
            codec!!.close()
        }
    }

    @Throws(IOException::class)
    private fun calcChaindedIV(): ByteArray? {
        val codec = namingCodecInfo.getEncDec()
        try {
            codec!!.init(_encryptionKey)
            if (namingCodecInfo!!.useChainedNamingIV()) {
                val parent = parentPath
                codec.iv = parent?.chainedIV
            }
            return codec!!.getChainedIV(decodedPath!!.fileName)
        } finally {
            codec!!.close()
        }
    }

    @Throws(IOException::class)
    fun buildEncodedPathFromRealPath(realPath: com.sovworks.eds.fs.Path): StringPathUtil {
        var realPath = realPath
        var encodedParts = StringPathUtil()
        val rootPath = fileSystem.encFSRootPath
        while (realPath != rootPath) {
            encodedParts = StringPathUtil(PathUtil.getNameFromPath(realPath), encodedParts)
            realPath = realPath.parentPath
            if (realPath == null) throw IOException("Failed building path")
        }
        return encodedParts
    }
}