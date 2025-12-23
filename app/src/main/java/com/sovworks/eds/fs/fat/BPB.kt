package com.sovworks.eds.fs.fat

import com.sovworks.eds.fs.RandomAccessIO
import com.sovworks.eds.fs.errors.WrongImageFormatException
import com.sovworks.eds.fs.util.Util
import java.io.EOFException
import java.io.IOException

/** Fat Bios Parameter Block structure  */
open class BPB {
    var bytesPerSector: Int = 0
    var sectorsPerCluster: Int = 0
    var reservedSectors: Int = 0
    var numberOfFATs: Short = 0
    var rootDirEntries: Int = 0
    var totalSectorsNumber: Int = 0
    var mediaType: Short = 0
    open var sectorsPerFat: Int = 0
    var sectorsPerTrack: Int = 1
    var numberOfHeads: Int = 1
    var hiddenSectors: Long = 0
    var sectorsBig: Long = 0

    var physicalDriveNumber: Short = 0

    // 1 byte reserved
    var extendedBootSignature: Short = 0
    var volumeSerialNumber: Long = 0
    var volumeLabel: ByteArray = ByteArray(12)
    var fileSystemLabel: ByteArray = ByteArray(8)

    @Throws(IOException::class, WrongImageFormatException::class)
    open fun read(input: RandomAccessIO) {
        input.seek(0xB)
        bytesPerSector = Util.readWordLE(input)
        sectorsPerCluster = Util.readUnsignedByte(input).toInt()
        reservedSectors = Util.readWordLE(input)
        numberOfFATs = Util.readUnsignedByte(input)
        rootDirEntries = Util.readWordLE(input)
        totalSectorsNumber = Util.readWordLE(input)
        mediaType = Util.readUnsignedByte(input)
        sectorsPerFat = Util.readWordLE(input)
        sectorsPerTrack = Util.readWordLE(input)
        numberOfHeads = Util.readWordLE(input)
        hiddenSectors = Util.readDoubleWordLE(input)
        sectorsBig = Util.readDoubleWordLE(input)
    }

    @Throws(IOException::class)
    open fun write(output: RandomAccessIO) {
        output.seek(0xB)
        Util.writeWordLE(output, bytesPerSector.toShort())
        output.write(sectorsPerCluster)
        Util.writeWordLE(output, reservedSectors.toShort())
        output.write(numberOfFATs.toInt())
        Util.writeWordLE(output, rootDirEntries.toShort())
        Util.writeWordLE(output, totalSectorsNumber.toShort())
        output.write(mediaType.toInt())
        Util.writeWordLE(output, sectorsPerFat.toShort())
        Util.writeWordLE(output, sectorsPerTrack.toShort())
        Util.writeWordLE(output, numberOfHeads.toShort())
        Util.writeDoubleWordLE(output, hiddenSectors.toInt())
        Util.writeDoubleWordLE(output, sectorsBig.toInt())
    }

    open fun getTotalSectorsNumber(): Long {
        return totalSectorsNumber.toLong()
    }

    fun getClusterOffset(cluster: Int): Long {
        return _clusterOffsetStart + (cluster.toLong() - 2) * sectorsPerCluster * bytesPerSector
    }

    open fun calcParams() {
        _clusterOffsetStart =
            (
                    bytesPerSector * (reservedSectors + sectorsPerFat * numberOfFATs) + rootDirEntries * 32).toLong()
    }

    protected var _clusterOffsetStart: Long = 0

    @Throws(IOException::class)
    protected fun readCommonPart(input: RandomAccessIO) {
        physicalDriveNumber = Util.readUnsignedByte(input)
        input.read()
        extendedBootSignature = Util.readUnsignedByte(input)
        volumeSerialNumber = Util.readDoubleWordLE(input)
        if (Util.readBytes(input, volumeLabel, 11) != 11) throw EOFException()
        if (Util.readBytes(input, fileSystemLabel, 8) != 8) throw EOFException()
    }

    @Throws(IOException::class)
    protected fun writeCommonPart(output: RandomAccessIO) {
        output.write(physicalDriveNumber.toInt())
        output.write(0)
        output.write(extendedBootSignature.toInt())
        Util.writeDoubleWordLE(output, volumeSerialNumber.toInt())
        output.write(volumeLabel, 0, volumeLabel.size)
        output.write(fileSystemLabel, 0, fileSystemLabel.size)
    }

    @Throws(WrongImageFormatException::class, IOException::class)
    protected fun checkEndingSignature(input: RandomAccessIO) {
        input.seek(0x1FE)
        if (Util.readWordLE(input) != 0xAA55) throw WrongImageFormatException("Invalid bpb sector signature")
        if (fileSystemLabel[0] != 'F'.code.toByte() || fileSystemLabel[1] != 'A'.code.toByte() || fileSystemLabel[2] != 'T'.code.toByte()) throw WrongImageFormatException(
            "Looks like the file system is not FAT"
        )
    }

    @Throws(IOException::class)
    protected fun writeBPBSignature(output: RandomAccessIO) {
        output.seek(0x1FE)
        Util.writeWordLE(output, 0xAA55.toShort())
    }

    companion object {
        var FAT12_LABEL: String = "FAT12"
        var FAT16_LABEL: String = "FAT16"
        var FAT32_LABEL: String = "FAT32"
    }
}

internal class BPB16 : BPB() {
    @Throws(IOException::class, WrongImageFormatException::class)
    override fun read(input: RandomAccessIO) {
        super.read(input)
        readCommonPart(input)
        checkEndingSignature(input)

        calcParams()
    }

    @Throws(IOException::class)
    override fun write(output: RandomAccessIO) {
        super.write(output)
        writeCommonPart(output)
        writeBPBSignature(output)
    }

    override fun getTotalSectorsNumber(): Long {
        return if (totalSectorsNumber == 0) sectorsBig else totalSectorsNumber.toLong()
    }
}

internal class BPB32 : BPB() {
    var sectorsPerFat32: Long = 0
    var updateMode: Int = 0
    var versionNumber: Int = 0
    var rootClusterNumber: Int = 0
    var FSInfoSector: Int = 0
    var bootSectorReservedCopySector: Int = 0

    @Throws(IOException::class, WrongImageFormatException::class)
    override fun read(input: RandomAccessIO) {
        super.read(input)
        sectorsPerFat32 = Util.readDoubleWordLE(input)
        updateMode = Util.readWordLE(input)
        versionNumber = Util.readWordLE(input)
        rootClusterNumber = Util.readDoubleWordLE(input).toInt()
        FSInfoSector = Util.readWordLE(input)
        bootSectorReservedCopySector = Util.readWordLE(input)
        input.seek(input.filePointer + 12)
        readCommonPart(input)
        checkEndingSignature(input)
        calcParams()
    }

    @Throws(IOException::class)
    override fun write(output: RandomAccessIO) {
        super.write(output)
        Util.writeDoubleWordLE(output, sectorsPerFat32.toInt())
        Util.writeWordLE(output, updateMode.toShort())
        Util.writeWordLE(output, versionNumber.toShort())
        Util.writeDoubleWordLE(output, rootClusterNumber)
        Util.writeWordLE(output, FSInfoSector.toShort())
        Util.writeWordLE(output, bootSectorReservedCopySector.toShort())
        for (i in 0..11) output.write(0)
        writeCommonPart(output)
        writeBPBSignature(output)
    }

    override var sectorsPerFat: Int
        get() = sectorsPerFat32.toInt()
        set(sectorsPerFat) {
            super.sectorsPerFat = sectorsPerFat
        }

    override fun getTotalSectorsNumber(): Long {
        return if (totalSectorsNumber == 0) sectorsBig else totalSectorsNumber.toLong()
    }

    override fun calcParams() {
        _clusterOffsetStart = bytesPerSector * (reservedSectors + sectorsPerFat32 * numberOfFATs)
    }
}
