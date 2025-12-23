package com.sovworks.eds.fs.util

import android.os.ParcelFileDescriptor
import com.sovworks.eds.fs.DataInput
import com.sovworks.eds.fs.DataOutput
import com.sovworks.eds.fs.Directory
import com.sovworks.eds.fs.File
import com.sovworks.eds.fs.File.AccessMode
import com.sovworks.eds.fs.File.AccessMode.Read
import com.sovworks.eds.fs.File.AccessMode.ReadWrite
import com.sovworks.eds.fs.File.AccessMode.ReadWriteTruncate
import com.sovworks.eds.fs.File.AccessMode.Write
import com.sovworks.eds.fs.File.AccessMode.WriteAppend
import com.sovworks.eds.fs.File.ProgressInfo
import com.sovworks.eds.fs.FileSystem
import com.sovworks.eds.fs.Path
import com.sovworks.eds.fs.RandomAccessIO
import io.reactivex.functions.Cancellable
import java.io.BufferedReader
import java.io.EOFException
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Reader
import java.io.StringWriter
import java.io.Writer
import java.util.Arrays
import kotlin.Long.Companion
import kotlin.math.min

object Util {
    @Throws(IOException::class)
    fun copyFileToOutputStream(
        output: OutputStream,
        file: File,
        offset: Long,
        count: Long,
        pi: ProgressInfo?
    ): Long {
        val input: InputStream
        if (offset > 0) {
            val f = file.getRandomAccessIO(Read)
            try {
                f.seek(offset)
                input = RandomAccessInputStream(f)
            } catch (e: Throwable) {
                f.close()
                throw IOException(e)
            }
        } else input = file.inputStream
        try {
            return copyStream(input, output, count, pi)
        } finally {
            input.close()
        }
    }

    @Throws(IOException::class)
    fun copyFileFromInputStream(
        input: InputStream,
        file: File,
        offset: Long,
        count: Long,
        pi: ProgressInfo?
    ): Long {
        val output: OutputStream
        if (offset > 0) {
            val f = file.getRandomAccessIO(Write)
            try {
                f.seek(offset)
                output = RandomAccessOutputStream(f)
            } catch (e: Throwable) {
                f.close()
                throw IOException(e)
            }
        } else output = file.outputStream
        try {
            return copyStream(input, output, count, pi)
        } finally {
            output.close()
        }
    }

    @Throws(IOException::class)
    fun copyStream(
        src: InputStream, dst: OutputStream, count: Long, pi: ProgressInfo?
    ): Long {
        val limit = if (count <= 0) Long.MAX_VALUE else count
        var bytesRead: Long = 0
        val buf = ByteArray(4096)
        var n: Int
        while (bytesRead < limit
            && (src.read(buf, 0, min(buf.size.toDouble(), (limit - bytesRead).toDouble()).toInt())
                .also { n = it }) >= 0
        ) {
            dst.write(buf, 0, n)
            bytesRead += n.toLong()
            if (pi != null) {
                if (pi.isCancelled) break
                pi.setProcessed(bytesRead)
            }
        }
        return bytesRead
    }

    fun getParcelFileDescriptorModeFromAccessMode(mode: AccessMode): Int {
        return when (mode) {
            Read -> ParcelFileDescriptor.MODE_READ_ONLY
            Write -> (ParcelFileDescriptor.MODE_WRITE_ONLY
                    or ParcelFileDescriptor.MODE_TRUNCATE
                    or ParcelFileDescriptor.MODE_CREATE)

            WriteAppend -> (ParcelFileDescriptor.MODE_WRITE_ONLY
                    or ParcelFileDescriptor.MODE_APPEND
                    or ParcelFileDescriptor.MODE_CREATE)

            ReadWriteTruncate -> (ParcelFileDescriptor.MODE_READ_WRITE
                    or ParcelFileDescriptor.MODE_TRUNCATE
                    or ParcelFileDescriptor.MODE_CREATE)

            ReadWrite -> ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE
            else -> ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE
        }
    }

    fun getAccessModeFromString(mode: String): AccessMode {
        val read = mode.contains("r")
        val write = mode.contains("w")
        if (read && write) return if (mode.contains("t")) ReadWriteTruncate else ReadWrite
        if (read) return Read
        if (write) return if (mode.contains("a")) WriteAppend else Write
        throw IllegalArgumentException("Unsupported mode: $mode")
    }

    fun getStringModeFromAccessMode(mode: AccessMode): String {
        return when (mode) {
            Read -> "r"
            Write -> "rw"
            ReadWrite -> "rw"
            WriteAppend -> "wa"
            ReadWriteTruncate -> "rwt"
        }
        throw IllegalArgumentException("Unsupported mode: $mode")
    }

    fun getCStringModeFromAccessMode(mode: AccessMode): String {
        return when (mode) {
            Read -> "r"
            Write -> "w"
            WriteAppend -> "a"
            ReadWrite -> "r+"
            ReadWriteTruncate -> "w+"
        }
        throw IllegalArgumentException("Unsupported mode: $mode")
    }

    @Throws(IOException::class)
    fun appendFile(path: Path): RandomAccessIO {
        val pos: Long
        if (path.exists()) {
            if (!path.isFile) throw IOException(
                "getFileWriter error: path exists and it is not a file: " + path.pathString
            )
            pos = path.file.size
        } else pos = 0

        val res = path.file.getRandomAccessIO(ReadWrite)
        res.seek(pos)
        return res
    }

    @Throws(IOException::class)
    fun getNewFileName(baseDir: Directory, startName: String): String {
        var res = startName
        var testPath = PathUtil.buildPath(baseDir.path, startName)
        if (testPath == null || !testPath.exists()) return res
        val baseName: String = StringPathUtil.Companion.getFileNameWithoutExtension(startName) + " "
        var ext: String = StringPathUtil.Companion.getFileExtension(startName)
        if (ext.length > 0) ext = ".$ext"
        var i = 1
        do {
            res = baseName + (i++) + ext
            testPath = PathUtil.buildPath(baseDir.path, res)
        } while (testPath != null && testPath.exists())

        return res
    }

    @Throws(IOException::class)
    fun makePath(fs: FileSystem, vararg els: Any): Path? {
        var cur: Path? = null
        for (o in els) {
            cur =
                if (cur == null) if (o is Path) o else fs.getPath(
                    o.toString()
                )
                else cur.combine(o.toString())
        }
        return cur
    }

    @Throws(IOException::class)
    fun listDir(dir: Directory): List<Path> {
        val res = ArrayList<Path>()
        val dc = dir.list()
        try {
            for (p in dc) res.add(p)
        } finally {
            dc.close()
        }
        return res
    }

    @Throws(IOException::class)
    fun procDir(dir: Directory, dirProc: DirProcessor) {
        val dc = dir.list()
        try {
            for (p in dc) if (!dirProc.procPath(p)) break
        } finally {
            dc.close()
        }
    }

    @Throws(IOException::class)
    fun copyStream(src: DataInput, dst: DataOutput, length: Long): Long {
        var res: Long = 0
        val buf = ByteArray(1024)
        var tmp: Int
        while (length < 0 || res < length) {
            tmp = src.read(
                buf,
                0,
                if (length < 0) buf.size else min(
                    buf.size.toDouble(),
                    (length - res).toDouble()
                ).toInt()
            )
            if (tmp >= 0) {
                res += tmp.toLong()
                dst.write(buf, 0, tmp)
            } else break
        }
        return res
    }

    @Throws(IOException::class)
    fun restorePaths(fs: FileSystem, vararg pathStrings: String?): ArrayList<Path> {
        return restorePaths(fs, Arrays.asList(*pathStrings))
    }

    @Throws(IOException::class)
    fun restorePaths(fs: FileSystem, pathStrings: Collection<String?>): ArrayList<Path> {
        val tmp = ArrayList<Path>()
        restorePaths(tmp, fs, pathStrings)
        return tmp
    }

    @Throws(IOException::class)
    fun restorePaths(
        pathsReceiver: MutableList<Path>, fs: FileSystem, pathStrings: Collection<String?>
    ) {
        for (s in pathStrings) pathsReceiver.add(fs.getPath(s))
    }

    fun storePaths(vararg paths: Path?): ArrayList<String> {
        return storePaths(Arrays.asList(*paths))
    }

    fun storePaths(paths: Collection<Path>): ArrayList<String> {
        val res = ArrayList<String>()
        for (path in paths) res.add(path.pathString)
        return res
    }

    @Throws(IOException::class)
    fun copyFile(
        src: File, dest: Directory, fileName: String
    ): File {
        val dstFile = PathUtil.getFile(dest.path, fileName)
        copyFile(src, dstFile!!)
        return dstFile
    }

    @Throws(IOException::class)
    fun copyFile(src: File, dst: File) {
        val out = dst.outputStream
        try {
            src.copyToOutputStream(out, 0, 0, null)
        } finally {
            out.close()
        }
    }

    @Throws(IOException::class)
    fun copyFile(src: File, dest: Directory): File {
        return copyFile(src, dest, src.name)
    }

    @Throws(IOException::class)
    fun copyFiles(paths: Iterable<Path>, directory: Directory) {
        for (p in paths) copyFiles(p, directory)
    }

    /**
     * This function will copy files or directories from one location to another. note that the source
     * and the destination must be mutually exclusive. This function can not be used to copy a
     * directory to a sub directory of itself. The function will also have problems if the destination
     * files already exist.
     *
     * @param src -- Source path
     * @param dest -- Destination path
     * @throws IOException if unable to copy.
     */
    @Throws(IOException::class)
    fun copyFiles(src: Path, dest: Directory): Path? {
        if (!src.exists()) throw IOException("copyFiles: Can not find source: " + src.pathString)

        if (src.isDirectory) {
            val newDir = dest.createDirectory(src.directory.name)
            val dc = src.directory.list()
            try {
                for (p in dc) copyFiles(p, newDir)
            } finally {
                dc.close()
            }
            return newDir.path
        } else if (src.isFile) return copyFile(src.file, dest).path
        return null
    }

    /**
     * This function will copy files or directories from one location to another. note that the source
     * and the destination must be mutually exclusive. This function can not be used to copy a
     * directory to a sub directory of itself. The function will also have problems if the destination
     * files already exist.
     *
     * @param src -- A File object that represents the source for the copy
     * @param dest -- A File object that represents the destination for the copy.
     * @throws IOException if unable to copy.
     */
    @Throws(IOException::class)
    fun copyFiles(src: java.io.File, dest: java.io.File) {
        // Check to ensure that the source is valid...
        if (!src.exists()) throw IOException("copyFiles: Cannot find source: " + src.absolutePath + ".")
        else if (!src.canRead()) throw IOException("copyFiles: No right to source: " + src.absolutePath + ".")
        // is this a directory copy?
        if (src.isDirectory) {
            if (!dest.exists())  // if not we need to make it exist if possible (note this is
            // mkdirs not mkdir)
                if (!dest.mkdirs()) throw IOException(
                    "copyFiles: Could not create direcotry: " + dest.absolutePath + "."
                )
            // get a listing of files...
            val list = src.list()
            // copy all the files in the list.
            for (element in list) {
                val dest1 = java.io.File(dest, element)
                val src1 = java.io.File(src, element)
                copyFiles(src1, dest1)
            }
        } else {
            // This was not a directory, so lets just copy the file
            var fin: FileInputStream? = null
            var fout: FileOutputStream? = null
            val buffer = ByteArray(4096) // Buffer 4K at a time (you
            // can change this).
            var bytesRead: Int
            try {
                // open the files for input and output
                fin = FileInputStream(src)
                fout = FileOutputStream(dest)
                // while bytesRead indicates a successful read, lets write...
                while ((fin.read(buffer).also { bytesRead = it }) >= 0) fout.write(
                    buffer,
                    0,
                    bytesRead
                )
            } catch (e: IOException) { // Error copying file...
                val wrapper =
                    IOException(
                        ("copyFiles: Unable to copy file: "
                                + src.absolutePath
                                + "to"
                                + dest.absolutePath
                                + ".")
                    )
                wrapper.initCause(e)
                wrapper.stackTrace = e.stackTrace
                throw wrapper
            } finally { // Ensure that the files are closed (if they were open).
                fin?.close()
                fout?.close()
            }
        }
    }

    /**
     * Deletes files recursively
     *
     * @param src -- A File object that represents the file or directory to delete *
     * @throws IOException if unable to copy.
     */
    @Throws(IOException::class)
    fun deleteFiles(src: java.io.File) {
        // Check to ensure that the source is valid...
        if (!src.exists()) return

        if (!src.canRead()) throw IOException("deleteFiles: No right to source: " + src.absolutePath + ".")

        // is this a directory copy?
        if (src.isDirectory) {
            // get a listing of files...
            val list = src.list()
            // copy all the files in the list.
            for (element in list) {
                val src1 = java.io.File(src, element)
                deleteFiles(src1)
            }
        }
        src.delete()
    }

    /**
     * Deletes files recursively
     *
     * @param path -- Path to file or directory to delete
     * @throws IOException if unable to copy.
     */
    @Throws(IOException::class)
    fun deleteFiles(path: Path) {
        if (!path.exists()) return

        if (path.isDirectory) {
            val dir = path.directory
            for (p in listDir(dir)) deleteFiles(p)
            dir.delete()
        } else path.file.delete()
    }

    @Throws(IOException::class)
    fun writeToFile(path: Path, content: CharSequence?): File {
        return writeToFile(
            path.parentPath.directory, PathUtil.getNameFromPath(path), content
        )
    }

    @JvmStatic
	@Throws(IOException::class)
    fun writeToFile(
        dir: Directory, fileName: String?, content: CharSequence?
    ): File {
        var dstPath = try {
            dir.path.combine(fileName)
        } catch (e: IOException) {
            null
        }
        val res =
            if (dstPath != null && dstPath.isFile) dstPath.file else dir.createFile(fileName)
        writeToFile(res, content)
        return res
    }

    @Throws(IOException::class)
    fun writeToFile(dst: File, content: CharSequence?) {
        val w = OutputStreamWriter(dst.outputStream)
        try {
            w.append(content)
        } finally {
            w.close()
        }
    }

    @Throws(IOException::class)
    fun writeToFile(path: String?, content: CharSequence?) {
        val w = OutputStreamWriter(FileOutputStream(path))
        try {
            w.append(content)
        } finally {
            w.close()
        }
    }

    @Throws(IOException::class)
    fun writeAll(out: OutputStream?, content: CharSequence?) {
        val w = OutputStreamWriter(out)
        try {
            w.append(content)
        } finally {
            w.flush()
        }
    }

    /**
     * Reads the whole file to the string
     *
     * @param path - path to file
     * @return - result string
     * @throws FileNotFoundException - if specified file cannot be found
     * @throws IOException - if internal io error occured
     */
    @JvmStatic
	@Throws(IOException::class)
    fun readFromFile(path: Path): String {
        return readFromFile(path.file)
    }

    @Throws(IOException::class)
    fun readFromFile(file: File): String {
        return readFromFile(file.inputStream)
    }

    /**
     * Reads the whole stream to the string
     *
     * @param input - input stream
     * @return - result string
     * @throws IOException - if internal io error occured
     */
    @Throws(IOException::class)
    fun readFromFile(input: InputStream?): String {
        val sb = StringBuilder()
        val r = InputStreamReader(input)
        try {
            val buf = CharArray(1024)
            var b: Int
            while ((r.read(buf).also { b = it }) >= 0) sb.append(buf, 0, b)
            return sb.toString()
        } finally {
            r.close()
        }
    }

    fun unsignedByteToInt(b: Byte): Int {
        return b.toInt() and 0xFF
    }

    /**
     * Convert the byte array to a long.
     *
     * @param b The byte array at least 4 bytes long
     * @return The integer
     */
    @JvmOverloads
    fun bytesToLong(b: ByteArray, offset: Int = 0): Long {
        return (unsignedByteToInt(b[offset]).toLong() shl 56 or (unsignedByteToInt(
            b[offset + 1]
        ).toLong() shl 48
                ) or (unsignedByteToInt(b[offset + 2]).toLong() shl 40
                ) or (unsignedByteToInt(b[offset + 3]).toLong() shl 32
                ) or (unsignedByteToInt(b[offset + 4]).toLong() shl 24
                ) or (unsignedByteToInt(b[offset + 5]).toLong() shl 16
                ) or (unsignedByteToInt(b[offset + 6]) shl 8
                ).toLong() or unsignedByteToInt(b[offset + 7]).toLong())
    }

    /**
     * Convert the byte array to a long.
     *
     * @param b The byte array at least 4 bytes long
     * @return The integer
     */
    @JvmOverloads
    fun unsignedIntToLongLE(b: ByteArray, offset: Int = 0): Long {
        return (unsignedByteToInt(b[offset])
            .toLong()
                or (unsignedByteToInt(b[offset + 1]).toLong() shl 8
                ) or (unsignedByteToInt(b[offset + 2]).toLong() shl 16
                ) or (unsignedByteToInt(b[offset + 3]).toLong() shl 24))
    }

    /**
     * Convert the byte array to a int
     *
     * @param b byte array at least 2 bytes long
     * @return The integer
     */
    @JvmOverloads
    fun unsignedShortToIntLE(b: ByteArray, offset: Int = 0): Int {
        return unsignedByteToInt(b[offset]) or (unsignedByteToInt(
            b[offset + 1]
        ) shl 8)
    }

    /**
     * Reads specified number of bytes from the input stream
     *
     * @param input The input stream
     * @param b target array
     * @param len number of bytes to read
     * @throws IOException exception propagated from the input stream
     * @return true if all the requested bytes were read
     */
    @Throws(IOException::class)
    fun readBytes(input: InputStream, b: ByteArray?, len: Int): Int {
        return readBytes(input, b, 0, len)
    }

    /**
     * Reads specified number of bytes from the input stream
     *
     * @param input The input stream
     * @param b target array
     * @param count number of bytes to read
     * @throws IOException exception propagated from the input stream
     * @return true if all the requested bytes were read
     */
    @Throws(IOException::class)
    fun readBytes(input: InputStream, b: ByteArray?, offset: Int, count: Int): Int {
        var res = 0
        var tmp: Int
        while (res < count) {
            tmp = input.read(b, offset + res, count - res)
            if (tmp >= 0) res += tmp
            else break
        }
        return res
    }

    @Throws(IOException::class)
    fun readBytes(input: InputStream, b: ByteArray): Int {
        return readBytes(input, b, b.size)
    }

    /**
     * Skips specified number of bytes
     *
     * @param input The input stream
     * @param num number of bytes to skip
     * @throws IOException error in the input stream
     */
    @Throws(IOException::class)
    fun skip(input: InputStream, num: Long) {
        var res: Long = 0
        while (res < num) {
            val tmp = input.skip(num - res)
            if (tmp < 0) throw IOException("Unexpected end of stream")
            res += tmp
        }
    }

    /**
     * Reads specified number of bytes from the input stream
     *
     * @param input The input stream
     * @param b target array
     * @param len number of bytes to read
     * @throws IOException exception propagated from the input stream
     * @return true if all the requested bytes were read
     */
    @JvmStatic
	@Throws(IOException::class)
    fun readBytes(input: DataInput, b: ByteArray?, len: Int): Int {
        var res = 0
        var tmp: Int
        while (res < len) {
            tmp = input.read(b, res, len - res)
            if (tmp >= 0) res += tmp
            else break
        }
        return res
    }

    @Throws(IOException::class)
    fun readBytes(input: DataInput, b: ByteArray): Int {
        return readBytes(input, b, b.size)
    }

    @Throws(IOException::class)
    fun readUnsignedByte(input: RandomAccessIO): Short {
        return input.read().toShort()
    }

    @Throws(IOException::class)
    fun readWordLE(input: RandomAccessIO): Int {
        val buf = ByteArray(2)
        if (readBytes(input, buf) != buf.size) throw EOFException()
        return unsignedShortToIntLE(buf)
    }

    @Throws(IOException::class)
    fun readDoubleWordLE(input: RandomAccessIO): Long {
        val buf = ByteArray(4)
        if (readBytes(input, buf) != buf.size) throw EOFException()
        return unsignedIntToLongLE(buf)
    }

    @Throws(IOException::class)
    fun writeWordLE(input: RandomAccessIO, data: Short) {
        input.write(data.toInt() and 0xFF)
        input.write(data.toInt() shr 8)
    }

    @Throws(IOException::class)
    fun writeDoubleWordLE(input: RandomAccessIO, data: Int) {
        writeWordLE(input, (data and 0xFFFF).toShort())
        writeWordLE(input, (data shr 16).toShort())
    }

    @Throws(IOException::class)
    fun convertStreamToString(`is`: InputStream?, encodingName: String?): String {
        if (`is` != null) {
            val writer: Writer = StringWriter()

            val buffer = CharArray(1024)
            try {
                val reader: Reader = BufferedReader(InputStreamReader(`is`, encodingName))
                var n: Int
                while ((reader.read(buffer).also { n = it }) != -1) writer.write(buffer, 0, n)
            } finally {
                `is`.close()
            }
            return writer.toString()
        } else return ""
    }

    fun shortToBytesLE(`val`: Short, res: ByteArray, offset: Int) {
        res[offset] = (`val`.toInt() and 0xFF).toByte()
        res[offset + 1] = ((`val`.toInt() shr 8) and 0xFF).toByte()
    }

    fun intToBytesLE(`val`: Int, res: ByteArray, offset: Int) {
        shortToBytesLE((`val` and 0xFFFF).toShort(), res, offset)
        shortToBytesLE(((`val` shr 16) and 0xFFFF).toShort(), res, offset + 2)
    }

    // exfat_pread expects that the pread function reads all the requested bytes at once
    @Throws(IOException::class)
    fun pread(
        io: RandomAccessIO,
        buf: ByteArray?,
        bufOffset: Int,
        count: Int,
        position: Long
    ): Int {
        val cur = io.filePointer
        io.seek(position)
        try {
            var res = 0
            var tmp: Int
            while (res < count) {
                tmp = io.read(buf, res, count - res)
                if (tmp > 0) res += tmp
                else break
            }
            // int res = io.read(buf, bufOffset, count);
            return res
        } finally {
            io.seek(cur)
        }
    }

    @Throws(IOException::class)
    fun pwrite(
        io: RandomAccessIO,
        buf: ByteArray?,
        bufOffset: Int,
        count: Int,
        position: Long
    ): Int {
        val cur = io.filePointer
        io.seek(position)
        try {
            io.write(buf, bufOffset, count)
            return count
        } finally {
            io.seek(cur)
        }
    }

    @Throws(IOException::class)
    fun countFolderSize(dir: Directory): Long {
        var res: Long = 0
        val dc = dir.list()
        for (p in dc) {
            res += if (p.isFile) p.file.size
            else countFolderSize(p.directory)
        }
        return res
    }

    interface DirProcessor {
        @Throws(IOException::class)
        fun procPath(path: Path?): Boolean
    }

    class CancellableProgressInfo

        : ProgressInfo, Cancellable {
        override fun setProcessed(num: Long) {}

        override fun isCancelled(): Boolean {
            return _isCancelled
        }

        @Throws(Exception::class)
        override fun cancel() {
            _isCancelled = true
        }

        private var _isCancelled = false
    }
}
