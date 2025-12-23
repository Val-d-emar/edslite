package com.sovworks.eds.fs.fat

import com.sovworks.eds.fs.RandomAccessIO
import com.sovworks.eds.fs.util.Util
import java.io.IOException
import java.util.zip.DataFormatException

internal class FSInfo(private val _bpb: BPB32) {
    var freeCount: Int = -1
    var lastAllocatedCluster: Int = -1

    @Throws(IOException::class, DataFormatException::class)
    fun read(input: RandomAccessIO) {
        input.seek((_bpb.bytesPerSector * _bpb.FSInfoSector).toLong())

        if (Util.readDoubleWordLE(input) != 0x41615252L) throw DataFormatException("Wrong file system information structure signature")

        input.seek(input.filePointer + 480)
        if (Util.readDoubleWordLE(input) != 0x61417272L) throw DataFormatException("Wrong file system information structure signature")
        freeCount = Util.readDoubleWordLE(input).toInt()
        lastAllocatedCluster = Util.readDoubleWordLE(input).toInt()
        input.seek(input.filePointer + 12)
        if (Util.readDoubleWordLE(input) != 0xAA550000L) throw DataFormatException("Wrong file system information structure signature")
    }

    @Throws(IOException::class)
    fun write(output: RandomAccessIO) {
        output.seek((_bpb.bytesPerSector * _bpb.FSInfoSector).toLong())
        Util.writeDoubleWordLE(output, 0x41615252)
        for (i in 0..479) output.write(0)
        Util.writeDoubleWordLE(output, 0x61417272)
        Util.writeDoubleWordLE(output, freeCount)
        Util.writeDoubleWordLE(output, lastAllocatedCluster)
        for (i in 0..11) output.write(0)
        Util.writeDoubleWordLE(output, -0x55ab0000)
    }
}
