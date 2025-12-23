package com.sovworks.eds.fs.util

import com.sovworks.eds.fs.Directory
import com.sovworks.eds.fs.File
import com.sovworks.eds.fs.Path
import java.io.IOException

object PathUtil {
    @Throws(IOException::class)
    fun changeFileName(path: Path, newFileName: String?): Path {
        val bp = path.parentPath
        requireNotNull(bp) { "Can't change filename of the root path" }
        return bp.combine(newFileName)
    }

    @Throws(IOException::class)
    fun getNameFromPath(path: Path): String {
        return if (path.isFile)
            path.file.name
        else
            (if (path.isDirectory)
                path.directory.name
            else
                (if (path is PathBase)
                    path.pathUtil.fileName
                else
                    path.pathDesc))
    }

    @Throws(IOException::class)
    fun makeFullPath(path: Path) {
        if (!path.exists()) {
            val pu = StringPathUtil(path.pathString)
            val bp = path.parentPath
            if (bp != null) {
                makeFullPath(bp)
                bp.directory.createDirectory(pu.fileName)
            }
        } else if (!path.isDirectory) throw IOException("Can't create path: " + path.pathString)
    }

    @Throws(IOException::class)
    fun buildStringPathUtil(path: Path?): StringPathUtil {
        var path = path
        var res = StringPathUtil()
        while (path != null && path.exists() && !path.isRootDirectory) {
            res = StringPathUtil(getNameFromPath(path), res)
            path = path.parentPath
        }
        return res
    }

    @Throws(IOException::class)
    fun isParentDirectory(testParentPath: Path, testPath: Path): Boolean {
        if (testParentPath is PathBase && testPath is PathBase) return isParentDirectory(
            testParentPath, testPath
        )
        return isParentDirectoryRec(testParentPath, testPath)
    }

    fun isParentDirectory(testParentPath: PathBase, testPath: PathBase): Boolean {
        return testParentPath.pathUtil.isParentDir(testPath.pathUtil)
    }

    @Throws(IOException::class)
    fun isParentDirectoryRec(testParentPath: Path, testPath: Path): Boolean {
        var testPath = testPath
        while (true) {
            if (testPath.isRootDirectory) return false
            val parentPath = testPath.parentPath ?: return false
            if (parentPath == testParentPath) return true
            testPath = parentPath
        }
    }

    fun unwrapPath(wrappedPath: Path?): Path? {
        var path = wrappedPath
        while (path is PathWrapper) path = path.base
        return path
    }

    fun buildPath(startPath: Path, vararg parts: String?): Path? {
        var path = startPath
        for (p in parts) try {
            path = path.combine(p)
        } catch (e: IOException) {
            return null
        }
        return path
    }

    fun exists(startPath: Path, vararg parts: String?): Boolean {
        val path = buildPath(startPath, *parts)
        return try {
            path != null && path.exists()
        } catch (e: IOException) {
            false
        }
    }

    fun isFile(startPath: Path, vararg parts: String?): Boolean {
        val path = buildPath(startPath, *parts)
        return try {
            path != null && path.isFile
        } catch (e: IOException) {
            false
        }
    }

    fun isDirectory(startPath: Path, vararg parts: String?): Boolean {
        val path = buildPath(startPath, *parts)
        return try {
            path != null && path.isDirectory
        } catch (e: IOException) {
            false
        }
    }

    @Throws(IOException::class)
    fun getFile(startPath: Path, vararg parts: String?): File {
        if (parts.size == 0) {
            if (startPath.isFile) return startPath.file
            throw IOException("Start path is not a file")
        }
        var prevPath = startPath
        for (i in 0..<parts.size - 1) {
            val p = parts[i]
            val path = buildPath(prevPath, p)
            prevPath =
                if (path == null || !path.exists()) prevPath.directory.createDirectory(p).path
                else path
        }
        val p = parts[parts.size - 1]
        val path = buildPath(prevPath, p)
        return if (path == null || !path.exists()) prevPath.directory.createFile(p)
        else path.file
    }

    @Throws(IOException::class)
    fun getDirectory(startPath: Path, vararg parts: String): Directory {
        if (parts.size == 0) {
            if (startPath.isDirectory) return startPath.directory
            throw IOException("Start path is not a directory")
        }
        var prevPath = startPath
        for (p in parts) {
            val path = buildPath(prevPath, p)
            prevPath =
                if (path == null || !path.exists()) prevPath.directory.createDirectory(p).path
                else path
        }
        return prevPath.directory
    }
}
