package com.sovworks.eds.fs.encfs.macs

import java.nio.ByteBuffer
import java.nio.ByteOrder

abstract class MACCalculator {
    open fun init(key: ByteArray) {
    }

    var chainedIV: ByteArray?
        get() = _chainedIV
        set(iv) {
            _chainedIV = iv
            isChainedIVEnabled = iv != null
        }

    fun calc64(buf: ByteArray, offset: Int, count: Int): Long {
        return ByteBuffer.wrap(calcChecksum(buf, offset, count)).order(ByteOrder.BIG_ENDIAN)
            .getLong()
    }

    fun calc32(buf: ByteArray?, offset: Int, count: Int): Int {
        val cs = calcChecksum(buf!!, offset, count)
        for (i in 0..3) cs[i] = (cs[i].toInt() xor cs[i + 4].toInt()).toByte()
        return ByteBuffer.wrap(cs).order(ByteOrder.BIG_ENDIAN).getInt()
    }

    fun calc16(buf: ByteArray, offset: Int, count: Int): Short {
        val cs = calcChecksum(buf, offset, count)
        for (i in 0..3) cs[i] = (cs[i].toInt() xor cs[i + 4].toInt()).toByte()
        for (i in 0..1) cs[i] = (cs[i].toInt() xor cs[i + 2].toInt()).toByte()
        return ByteBuffer.wrap(cs).order(ByteOrder.BIG_ENDIAN).getShort()
    }

    open fun close() {
    }

    abstract fun calcChecksum(buf: ByteArray, offset: Int, count: Int): ByteArray

    private var _chainedIV: ByteArray?
    var isChainedIVEnabled: Boolean = false
        private set
}
