package com.sovworks.eds.crypto

import android.annotation.SuppressLint
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.DigestException
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.math.min

class AF
    (private val _hash: MessageDigest, private val _blockSize: Int) {
    @SuppressLint("TrulyRandom")
    @Throws(DigestException::class)
    fun split(src: ByteArray, srcOffset: Int, dest: ByteArray, destOffset: Int, blockNumber: Int) {
        val block = ByteArray(_blockSize)
        val tmp = ByteArray(_blockSize)
        val sr = SecureRandom()
        for (i in 0..<blockNumber - 1) {
            sr.nextBytes(tmp)
            System.arraycopy(tmp, 0, dest, destOffset + _blockSize * i, _blockSize)
            xorBlock(dest, destOffset + i * _blockSize, block, 0, block)
            diffuse(block, 0, block, 0, _blockSize)
        }
        xorBlock(src, srcOffset, dest, destOffset + _blockSize * (blockNumber - 1), block)
    }

    @Throws(DigestException::class)
    fun merge(src: ByteArray, srcOffset: Int, dest: ByteArray, destOffset: Int, blockNumber: Int) {
        val block = ByteArray(_blockSize)
        for (i in 0..<blockNumber - 1) {
            xorBlock(src, srcOffset + i * _blockSize, block, 0, block)
            diffuse(block, 0, block, 0, _blockSize)
        }
        xorBlock(src, srcOffset + _blockSize * (blockNumber - 1), dest, destOffset, block)
    }

    fun calcNumRequiredSectors(numBlocks: Int): Int {
        return calcNumRequiredSectors(_blockSize, numBlocks)
    }


    private fun xorBlock(
        src: ByteArray,
        srcOffset: Int,
        dst: ByteArray,
        dstOffset: Int,
        xorBlock: ByteArray
    ) {
        for (i in xorBlock.indices) dst[dstOffset + i] =
            ((src[srcOffset + i].toInt() xor xorBlock[i].toInt()) and 0xFF).toByte()
    }

    @Throws(DigestException::class)
    private fun diffuse(src: ByteArray, srcOffset: Int, dst: ByteArray, dstOffset: Int, len: Int) {
        val ds = _hash.digestLength
        val blocks = len / ds
        val padding = len % ds

        for (i in 0..<blocks) hashBuf(src, srcOffset + ds * i, dst, dstOffset + ds * i, ds, i)
        if (padding > 0) hashBuf(
            src,
            srcOffset + ds * blocks,
            dst,
            dstOffset + ds * blocks,
            padding,
            blocks
        )
    }

    @Throws(DigestException::class)
    private fun hashBuf(
        src: ByteArray,
        srcOffset: Int,
        dst: ByteArray,
        dstOffset: Int,
        len: Int,
        iv: Int
    ) {
        val bb = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(iv)
        _hash.reset()
        _hash.update(bb.array())
        _hash.update(src, srcOffset, len)
        val res = _hash.digest()
        System.arraycopy(
            res,
            0,
            dst,
            dstOffset,
            min(res.size.toDouble(), (dst.size - dstOffset).toDouble()).toInt()
        )
    } /*
	private static int htonl(int value) 
	{
		return ByteOrder.nativeOrder().equals(ByteOrder.BIG_ENDIAN) ? value : Integer.reverseBytes(value);
	}
	*/

    companion object {
        const val SECTOR_SIZE: Int = 512

        fun calcNumRequiredSectors(blocksize: Int, numBlocks: Int): Int {
            val afSize = blocksize * numBlocks
            return (afSize + (SECTOR_SIZE - 1)) / SECTOR_SIZE
        }
    }
}
