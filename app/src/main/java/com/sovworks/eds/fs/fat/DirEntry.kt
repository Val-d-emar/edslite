package com.sovworks.eds.fs.fat

import android.annotation.SuppressLint
import com.sovworks.eds.android.Logger.Companion.log
import com.sovworks.eds.fs.DataInput
import com.sovworks.eds.fs.DataOutput
import com.sovworks.eds.fs.File.AccessMode.ReadWrite
import com.sovworks.eds.fs.RandomAccessIO
import com.sovworks.eds.fs.RandomStorageAccess
import com.sovworks.eds.fs.fat.FatFS.FatPath
import com.sovworks.eds.fs.util.RandomAccessInputStream
import com.sovworks.eds.fs.util.RandomAccessOutputStream
import com.sovworks.eds.fs.util.Util
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.util.Calendar
import java.util.Collections
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale

@SuppressLint("DefaultLocale")
class DirEntry @JvmOverloads constructor(streamOffset: Int = -1) {
    var name: String? = null
    var dosName: String? = null
    var attributes: Byte = 0
    var createDateTime: Date
    var lastAccessDate: Date
    var lastModifiedDateTime: Date
    var startCluster: Int = 0
    var fileSize: Long = 0
    var offset: Int = -1
    var numLFNRecords: Int = 0

    fun copyFrom(src: DirEntry, copyName: Boolean) {
        if (copyName) {
            name = src.name
            dosName = src.dosName
            numLFNRecords = src.numLFNRecords
        }
        attributes = src.attributes
        createDateTime = src.createDateTime
        lastAccessDate = src.lastAccessDate
        lastModifiedDateTime = src.lastModifiedDateTime
        fileSize = src.fileSize
        startCluster = src.startCluster
    }

    var isDir: Boolean
        get() = (attributes.toInt() and Attributes.subDir.toInt()) != 0
        set(val) {
            attributes = if (`val`) (attributes.toInt() or Attributes.subDir.toInt()).toByte()
            else (attributes.toInt() and Attributes.subDir.inv()).toByte()
        }

    var isReadOnly: Boolean
        get() = (attributes.toInt() and Attributes.readOnly.toInt()) != 0
        set(val) {
            attributes = if (`val`) (attributes.toInt() or Attributes.readOnly.toInt()).toByte()
            else (attributes.toInt() and Attributes.readOnly.inv()).toByte()
        }

    val isVolumeLabel: Boolean
        get() = (attributes.toInt() and Attributes.volumeLabel.toInt()) != 0

    fun setVolume(`val`: Boolean) {
        attributes =
            if (`val`) (attributes.toInt() or Attributes.volumeLabel.toInt()).toByte()
            else (attributes.toInt() and Attributes.volumeLabel.inv()).toByte()
    }

    val isFile: Boolean
        get() = !isDir && (attributes.toInt() and Attributes.volumeLabel.toInt()) == 0

    @Throws(IOException::class)
    fun writeEntry(fat: FatFS, basePath: FatPath, opTag: Any) {
        fat.lockPath(basePath, ReadWrite, opTag)
        try {
            var isLast = false
            val fn = FileName(name)
            if (dosName == null) initDosName(fn, fat, basePath, opTag)
            if (offset >= 0 && numLFNRecords == 0 && fn.isLFN) {
                deleteEntry(fat, basePath, opTag)
                offset = -1
            }
            if (offset < 0) isLast =
                getFreeDirEntryOffset(
                    fat,
                    basePath,
                    if (fn.isLFN) (getNumLFNRecords() + 1) else 1,
                    opTag
                )

            val os = fat.getDirWriter(basePath, opTag)
            try {
                // DEBUG
                // Log.d("EDS", String.format("Writing dir entry %s at offset %d",name,offset));
                os!!.seek(offset.toLong())
                writeEntry(fn, os)
                if (isLast) os.write(0)

                // zeroRemainingClusterSpace(fat, os, ((IFSStream) os).getPosition());
            } finally {
                os!!.close()
            }
        } finally {
            fat.releasePathLock(basePath)
        }
    }

    @Throws(IOException::class)
    fun writeEntry(fn: FileName, output: DataOutput) {
        val record = ByteArray(32)
        if (dosName == null) dosName = fn.getDosName(0)
        putDosName(record)
        if (fn.isLowerCaseName) record[0x0C] = (record[0x0C].toInt() or 0x8).toByte()
        if (fn.isLowerCaseExtension) record[0x0C] = (record[0x0C].toInt() or 0x4).toByte()
        if (fn.isLFN) {
            // DEBUG
            // Log.d("EDS", "Entry is lfn");
            writeLFNRecords(output, calcChecksum(record, 0).toInt())
        }
        record[0x0b] = attributes
        Util.shortToBytesLE(startCluster.toShort(), record, 0x1A)
        Util.shortToBytesLE((startCluster shr 16).toShort(), record, 0x14)
        Util.intToBytesLE(fileSize.toInt(), record, 0x1C)
        record[0x0D] = getGreaterTimeRes(createDateTime)
        Util.shortToBytesLE(encodeTime(createDateTime), record, 0x0E)
        Util.shortToBytesLE(encodeDate(createDateTime), record, 0x10)
        Util.shortToBytesLE(encodeDate(lastAccessDate), record, 0x12)
        Util.shortToBytesLE(encodeTime(lastModifiedDateTime), record, 0x16)
        Util.shortToBytesLE(encodeDate(lastModifiedDateTime), record, 0x18)
        output.write(record, 0, record.size)
    }

    @Throws(IOException::class)
    fun deleteEntry(fat: FatFS, basePath: FatPath, opTag: Any) {
        val s = fat.getDirWriter(basePath, opTag)
        try {
            deleteEntry(s!!)
        } finally {
            s!!.close()
        }
    }

    @Synchronized
    @Throws(IOException::class)
    fun deleteEntry(output: DirWriter) {
        if (offset < 0) throw IOException("deleteEntry error: can't delete new entry")

        for (i in 0..numLFNRecords) {
            output.seek((offset + i * 32).toLong())
            output.write(0xE5)
        }
    }

    init {
        lastModifiedDateTime = Date()
        lastAccessDate = lastModifiedDateTime
        createDateTime = lastAccessDate
        offset = streamOffset
    }

    @Throws(IOException::class)
    private fun initDosName(fn: FileName, fat: FatFS, basePath: FatPath, opTag: Any) {
        if (fn.isLFN) {
            val dosNames = readDosNames(fat, basePath, opTag)
            Collections.sort(dosNames)
            var counter = 0
            do {
                dosName = fn.getDosName(counter++)
            } while (Collections.binarySearch<String?>(
                    dosNames,
                    dosName
                ) { lhs, rhs -> lhs.compareTo(rhs) }
                >= 0
            )
        } else dosName = fn.getDosName(0)
    }

    @Throws(IOException::class)
    private fun getFreeDirEntryOffset(
        fat: FatFS, parentDirPath: FatPath, numEntries: Int, opTag: Any
    ): Boolean {
        var r = false
        var numDeletedEntries = 0
        var res = 0
        val buf = ByteArray(RECORD_SIZE)
        val dirStream = fat.getDirReader(parentDirPath, opTag)
        try {
            while (numDeletedEntries < numEntries && ((Util.readBytes(
                    dirStream,
                    buf
                ) == RECORD_SIZE).also { r = it })
                && buf[0].toInt() != 0
            ) {
                numDeletedEntries =
                    if (Util.unsignedByteToInt(buf[0]) == 0xE5) numDeletedEntries + 1 else 0
                res += RECORD_SIZE
            }
        } finally {
            dirStream!!.close()
        }
        if (!r) throw EOFException("getFreeDirEntryOffset error: no more free space")
        offset = res - numDeletedEntries * RECORD_SIZE
        return buf[0].toInt() == 0
    }

    private fun getGreaterTimeRes(date: Date): Byte {
        val calend: Calendar = GregorianCalendar()
        calend.time = date
        var `val` = calend[Calendar.SECOND]
        if (`val` % 2 != 0) `val` = 1000
        `val` += calend[Calendar.MILLISECOND]
        return (`val` / 10).toByte()
    }

    private fun encodeDate(date: Date): Short {
        val calend: Calendar = GregorianCalendar()
        calend.time = date
        var `val` = (calend[Calendar.YEAR] - 1980) shl 9
        `val` = `val` or ((calend[Calendar.MONTH] + 1) shl 5)
        `val` = `val` or calend[Calendar.DAY_OF_MONTH]
        return `val`.toShort()
    }

    private fun encodeTime(date: Date): Short {
        val calend: Calendar = GregorianCalendar()
        calend.time = date
        var `val` = (calend[Calendar.HOUR_OF_DAY] shl 11)
        `val` = `val` or (calend[Calendar.MINUTE] shl 5)
        `val` = `val` or calend[Calendar.SECOND] / 2
        return `val`.toShort()
    }

    private fun getNumLFNRecords(): Int {
        val numChars = name!!.length
        return if (numChars % 13 == 0) numChars / 13 else (numChars / 13) + 1
    }

    @Throws(IOException::class)
    private fun readDosNames(fs: FatFS, path: FatPath, opTag: Any): ArrayList<String?> {
        val res = ArrayList<String?>()
        val stream = fs.getDirReader(path, opTag)
        try {
            var entry: DirEntry?
            while (true) {
                entry = readEntry(stream!!)
                if (entry != null) res.add(entry.dosName!!.uppercase(Locale.getDefault()))
                else break
            }
        } finally {
            stream!!.close()
        }
        return res
    }

    private fun putDosName(recordData: ByteArray) {
        System.arraycopy(dosName!!.toByteArray(), 0, recordData, 0, 11)
        if (Util.unsignedByteToInt(recordData[0]) == 0xE5) recordData[0] = 0x05
    }

    @Throws(IOException::class)
    private fun writeLFNRecords(output: DataOutput, checkSum: Int) {
        val nameCharPos = intArrayOf(1, 3, 5, 7, 9, 14, 16, 18, 20, 22, 24, 28, 30)
        val bytes: ByteArray
        try {
            bytes = name!!.toByteArray(charset("UTF-16LE"))
        } catch (e: UnsupportedEncodingException) {
            return
        }
        val numChars = name!!.length
        var numRecords = numChars / 13
        val lastChar = numChars % 13
        if (lastChar != 0) numRecords++

        val recData = ByteArray(RECORD_SIZE)
        recData[0x0b] = 0x0f
        recData[0x0d] = checkSum.toByte()

        for (seq in numRecords downTo 1) {
            var charIdx = (seq - 1) * 13
            recData[0] = (seq or (if (seq == numRecords) 0x40 else 0)).toByte()
            for (idx in nameCharPos) {
                if (charIdx < numChars) {
                    recData[idx] = bytes[charIdx * 2]
                    recData[idx + 1] = bytes[charIdx * 2 + 1]
                } else Util.shortToBytesLE(
                    if (charIdx == numChars) 0 else 0xFFFF.toShort(),
                    recData,
                    idx
                )
                charIdx++
            }
            // DEBUG
            // Log.d("EDS", String.format("Writing lfn seq=%d",seq));
            output.write(recData, 0, recData.size)
        }
        numLFNRecords = numRecords
    }

    internal object Attributes {
        const val readOnly: Byte = 0x1
        const val hidden: Byte = 0x2
        const val system: Byte = 0x4
        const val volumeLabel: Byte = 0x8
        const val subDir: Byte = 0x10
        const val archive: Byte = 0x20
        const val device: Byte = 0x40
    }

    companion object {
        val ALLOWED_SYMBOLS: ByteArray = byteArrayOf(
            '!'.code.toByte(),
            '#'.code.toByte(),
            '$'.code.toByte(),
            '%'.code.toByte(),
            '&'.code.toByte(),
            '('.code.toByte(),
            ')'.code.toByte(),
            '-'.code.toByte(),
            '@'.code.toByte(),
            '^'.code.toByte(),
            '_'.code.toByte(),
            '`'.code.toByte(),
            '{'.code.toByte(),
            '}'.code.toByte(),
            '~'.code.toByte(),
            '\''.code.toByte()
        )
        val RESTRICTED_SYMBOLS: ByteArray = byteArrayOf(
            '+'.code.toByte(),
            ','.code.toByte(),
            '.'.code.toByte(),
            ';'.code.toByte(),
            '='.code.toByte(),
            '['.code.toByte(),
            ']'.code.toByte()
        )
        val SYSTEM_SYMBOLS: ByteArray = byteArrayOf(
            '*'.code.toByte(),
            '?'.code.toByte(),
            '<'.code.toByte(),
            ':'.code.toByte(),
            '>'.code.toByte(),
            '/'.code.toByte(),
            '\\'.code.toByte(),
            '|'.code.toByte()
        )

        /**
         * Reads directory entry from the stream at the current position
         *
         * @param input InputStream.
         * @return next entry or null if there are no more entries
         * @throws IOException
         */
        @Throws(IOException::class)
        fun readEntry(input: DirReader): DirEntry? {
            val buf = ByteArray(RECORD_SIZE)
            var lfnSb: StringBuilder? = null
            var lfn: String?
            var expectedChecksum: Byte = 0
            var seq = -1
            var pSeq: Int
            var lfns = 0

            while (true) {
                if (Util.readBytes(input, buf) != RECORD_SIZE) return null
                val fb = Util.unsignedByteToInt(buf[0])
                // if (fb == 0) return null;
                if (fb == 0xE5 || fb == 0) {
                    // deleted record
                    continue
                }
                if (buf[0x0B].toInt() == 0x0F)  // LFN record
                {
                    if (seq < 0) {
                        pSeq = -1
                        expectedChecksum = 0
                        lfnSb = null
                    } else pSeq = seq

                    seq = fb
                    if (lfnSb == null) {
                        if ((seq and 0x40) == 0) {
                            seq = -1
                            continue
                        }
                        lfnSb = StringBuilder()
                        lfns = 0
                    }
                    seq = seq and 0x3F
                    if (pSeq >= 0) {
                        if (buf[0x0d] != expectedChecksum || pSeq != seq + 1) {
                            seq = -1
                            continue
                        }
                    } else expectedChecksum = buf[0x0d]
                    lfnSb.insert(
                        0,
                        (String(buf, 1, 10, charset("UTF-16LE")) + String(
                            buf,
                            0x0E,
                            12,
                            charset("UTF-16LE")
                        ) + String(buf, 0x1C, 4, charset("UTF-16LE")))
                    )
                    lfns++
                } else {
                    if (seq == 1) {
                        lfn = lfnSb.toString()
                        val idx = lfn.indexOf(0.toChar())
                        if (idx >= 0) lfn = lfn.substring(0, idx)
                    } else lfn = null
                    // if (fb == 0x05 || fb == 0xE5)
                    //	seq = -1;
                    // else
                    val entry = DirEntry()
                    entry.attributes = buf[0x0B]
                    if (entry.isVolumeLabel) {
                        seq = -1
                        continue
                    }

                    if (lfn != null) {
                        val cs = calcChecksum(buf, 0)
                        if (expectedChecksum != cs) lfn = null
                    }

                    entry.dosName = String(buf, 0, 11, charset("ASCII"))
                    if (lfn == null) {
                        var tmp = entry.dosName!!.substring(0, 8).trim { it <= ' ' }
                        if ((buf[0x0C].toInt() and 0x8) != 0)  // lower case base name
                            tmp = FileName.Companion.toLowerCase(tmp)
                        entry.name = tmp
                        tmp = entry.dosName!!.substring(8, 11).trim { it <= ' ' }
                        if (tmp.length > 0) {
                            if ((buf[0x0C].toInt() and 0x4) != 0)  // lower case extension
                                tmp = FileName.Companion.toLowerCase(tmp)
                            entry.name += ".$tmp"
                        }
                    } else {
                        entry.name = lfn
                        entry.numLFNRecords = lfns
                    }

                    entry.offset =
                        (input.filePointer.toInt() - RECORD_SIZE * (entry.numLFNRecords + 1))
                    if (("." != entry.name) && (".." != entry.name) && !FatFS.Companion.isValidFileNameImpl(
                            entry.name!!
                        )
                    ) {
                        log(
                            String.format(
                                "DirEntry.readEntry: incorrect file name: %s; dir offset: %d",
                                entry.name, entry.offset
                            )
                        )
                        continue
                    }

                    var dv = Util.unsignedShortToIntLE(buf, 0x10)
                    var tv = Util.unsignedShortToIntLE(buf, 0x0E)
                    val secs = Util.unsignedByteToInt(buf[0x0D]) / 100

                    entry.createDateTime =
                        GregorianCalendar(
                            1980 + (dv shr 9),
                            ((dv shr 5) and 0xF) - 1,
                            dv and 0x1F,
                            tv shr 11,
                            (tv shr 5) and 0x3F,
                            (tv and 0x1F) * 2 + secs
                        )
                            .time
                    dv = Util.unsignedShortToIntLE(buf, 0x12)
                    entry.lastAccessDate =
                        GregorianCalendar(
                            1980 + (dv shr 9),
                            ((dv shr 5) and 0xF) - 1,
                            dv and 0x1F
                        ).time
                    dv = Util.unsignedShortToIntLE(buf, 0x18)
                    tv = Util.unsignedShortToIntLE(buf, 0x16)
                    entry.lastModifiedDateTime =
                        GregorianCalendar(
                            1980 + (dv shr 9),
                            ((dv shr 5) and 0xF) - 1,
                            dv and 0x1F,
                            tv shr 11,
                            (tv shr 5) and 0x3F,
                            (tv and 0x1F) * 2
                        )
                            .time
                    entry.fileSize = Util.unsignedIntToLongLE(buf, 0x1C)
                    entry.startCluster =
                        (Util.unsignedShortToIntLE(buf, 0x14) shl 16) or Util.unsignedShortToIntLE(
                            buf,
                            0x1A
                        )
                    // DEBUG
                    // Log.d("EDS", String.format("Read entry %s at %d. lfns=%d",
                    // entry.name,entry.offset,lfns));
                    return entry
                }
            }
        }

        const val RECORD_SIZE: Int = 32

        private fun calcChecksum(fn: ByteArray, offset: Int): Byte {
            var sum = 0

            for (i in 0..10) sum =
                Util.unsignedByteToInt((((sum and 1) shl 7) + (sum shr 1) + fn[i + offset]).toByte())
            return sum.toByte()
        }
    }
}

interface DirReader : DataInput, RandomStorageAccess, Closeable

interface DirWriter : DataOutput, RandomStorageAccess, Closeable

internal class DirOutputStream(io: RandomAccessIO?) : RandomAccessOutputStream(io), DirWriter

internal class DirInputStream(io: RandomAccessIO?) : RandomAccessInputStream(io), DirReader
