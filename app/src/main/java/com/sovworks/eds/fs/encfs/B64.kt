package com.sovworks.eds.fs.encfs

object B64 {
    fun B256ToB64Bytes(numB256Bytes: Int): Int {
        return (numB256Bytes * 8 + 5) / 6 // round up
    }

    fun B256ToB32Bytes(numB256Bytes: Int): Int {
        return (numB256Bytes * 8 + 4) / 5 // round up
    }

    fun B64ToB256Bytes(numB64Bytes: Int): Int {
        return (numB64Bytes * 6) / 8 // round down
    }

    fun B32ToB256Bytes(numB32Bytes: Int): Int {
        return (numB32Bytes * 5) / 8 // round down
    }

    @JvmOverloads
    fun changeBase2Inline(
        src: ByteArray,
        offset: Int,
        srcLen: Int,
        src2Pow: Int,
        dst2Pow: Int,
        outputPartialLastByte: Boolean,
        work: Long = 0,
        workBits: Int = 0,
        outLoc: ByteArray? = null,
        outOffset: Int = 0
    ) {
        var offset = offset
        var srcLen = srcLen
        var work = work
        var workBits = workBits
        var outLoc = outLoc
        var outOffset = outOffset
        val mask = (1 shl dst2Pow) - 1
        if (outLoc == null) {
            outLoc = src
            outOffset = offset
        }

        // copy the new bits onto the high bits of the stream.
        // The bits that fall off the low end are the output bits.
        while (srcLen > 0 && workBits < dst2Pow) {
            work = work or ((src[offset++].toLong() and 0xFFL) shl workBits)
            workBits += src2Pow
            --srcLen
        }

        // we have at least one value that can be output
        val outVal = (work and mask.toLong()).toByte()
        work = work shr dst2Pow
        workBits -= dst2Pow

        if (srcLen > 0) {
            // more input left, so recurse
            changeBase2Inline(
                src, offset, srcLen, src2Pow, dst2Pow, outputPartialLastByte,
                work, workBits, outLoc, outOffset + 1
            )
            outLoc.get(outOffset) = outVal
        } else {
            // no input left, we can write remaining values directly
            outLoc.get(outOffset++) = outVal
            // we could have a partial value left in the work buffer..
            if (outputPartialLastByte) {
                while (workBits > 0) {
                    outLoc.get(outOffset++) = (work and mask.toLong()).toByte()
                    work = work shr dst2Pow
                    workBits -= dst2Pow
                }
            }
        }
    }

    // character set for ascii b64:
    // ",-0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    // a standard base64 (eg a64l doesn't use ',-' but uses './'.  We don't
    // do that because '/' is a reserved character, and it is useful not to have
    // '.' included in the encrypted names, so that it can be reserved for files
    // with special meaning.
    private val B642AsciiTable = ",-0123456789".toCharArray()
    fun B64ToString(`in`: ByteArray, offset: Int, count: Int): String {
        val sb = StringBuilder()
        for (cnt in 0..<count) {
            var ch = `in`[offset + cnt].toInt()
            if (ch > 11) {
                ch += if (ch > 37) 'a'.code - 38
                else 'A'.code - 12
            } else ch = B642AsciiTable[ch].code
            sb.append(ch.toChar())
        }
        return sb.toString()
    }

    fun B32ToString(buf: ByteArray, offset: Int, count: Int): String {
        val sb = StringBuilder()
        for (cnt in 0..<count) {
            var ch = buf[offset + cnt].toInt()
            ch += if (ch >= 0 && ch < 26) 'A'.code
            else '2'.code - 26

            sb.append(ch.toChar())
        }
        return sb.toString()
    }

    fun StringToB32(s: String): ByteArray {
        val res = ByteArray(s.length)
        var i = 0
        for (ch in s.toCharArray()) {
            var lch = ch.uppercaseChar().code
            if (lch >= 'A'.code) lch -= 'A'.code
            else lch += 26 - '2'.code
            res[i++] = (lch and 0xFF).toByte()
        }
        return res
    }

    private val Ascii2B64Table =
        "                                            01  23456789:;       ".toCharArray()

    fun StringToB64(s: String): ByteArray {
        val res = ByteArray(s.length)
        var i = 0
        for (ch in s.toCharArray()) {
            var ch = ch
            if (ch >= 'A') {
                ch += if (ch >= 'a') (38 - 'a'.code).toChar().code
                else (12 - 'A'.code).toChar().code
            } else ch = (Ascii2B64Table[ch.code].code - '0'.code).toChar()
            res[i++] = (ch.code and 0xFF).toByte()
        }
        return res
    }
}
