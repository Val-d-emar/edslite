package com.sovworks.eds.crypto

import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import android.util.SparseArray
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.security.SecureRandom
import java.util.Arrays

class SecureBuffer : Parcelable, CharSequence {
    constructor(data: ByteArray?) : this(data, 0, data?.size ?: 0)

    @JvmOverloads
    constructor(data: ByteArray? = null as ByteArray?, offset: Int = 0, count: Int = 0) {
        _id = reserveNewId()
        if (data != null) adoptData(data, offset, count)
    }

    constructor(data: CharArray?) : this(data, 0, data?.size ?: 0)

    constructor(data: CharArray?, offset: Int, count: Int) {
        _id = reserveNewId()
        if (data != null) adoptData(data, offset, count)
    }

    override fun length(): Int {
        val cb = charBuffer
        return cb?.length ?: 0
    }

    override fun charAt(index: Int): Char {
        return charBuffer!![index]
    }

    override fun subSequence(start: Int, end: Int): CharSequence {
        return charBuffer!!.subSequence(start, end)
    }

    val charBuffer: CharBuffer?
        get() = getCharBuffer(_id)

    override fun equals(obj: Any?): Boolean {
        if (obj is SecureBuffer) {
            val d1 = byteBuffer
            val d2 = obj.byteBuffer
            return d1 == d2
        } else return super.equals(obj)
    }

    override fun hashCode(): Int {
        val d = byteBuffer
        return d?.hashCode() ?: 0
    }

    fun adoptData(cb: CharBuffer) {
        if (cb.hasArray()) adoptData(cb.array(), cb.position(), cb.remaining())
        else {
            val arr = CharArray(cb.length)
            cb[arr]
            cb.rewind()
            if (!cb.isReadOnly) {
                while (cb.hasRemaining()) cb.put(_secureRandom.nextInt().toChar())
            }
            adoptData(arr, 0, arr.size)
        }
    }

    fun close() {
        closeBuffer(_id)
    }

    fun adoptData(data: ByteArray?) {
        adoptData(data!!, 0, data?.size ?: 0)
    }

    fun adoptData(data: CharArray?) {
        adoptData(data!!, 0, data?.size ?: 0)
    }

    fun adoptData(data: ByteArray, offset: Int, count: Int) {
        setData(_id, data, offset, count)
    }

    fun adoptData(data: CharArray, offset: Int, count: Int) {
        setData(_id, data, offset, count)
    }

    val byteBuffer: ByteBuffer?
        get() = getByteBuffer(_id)

    val dataArray: ByteArray?
        get() {
            val bb = byteBuffer ?: return null
            val res = ByteArray(bb.remaining())
            bb[res]
            return res
        }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(parcel: Parcel, i: Int) {
        parcel.writeInt(_id)
    }

    protected constructor(`in`: Parcel) {
        _id = `in`.readInt()
    }

    protected class Buffer {
        val byteData: ByteBuffer?
            get() {
                if (_isByteDataValid) return if (_isByteDataRO) _byteData!!.asReadOnlyBuffer() else _byteData!!.duplicate()

                if (_charData == null || !_isCharDataValid) return null
                if (_byteData != null) eraseData(_byteData!!.array())
                _charData!!.mark()
                _byteData = _charset.encode(_charData)
                _charData!!.reset()
                _isByteDataRO = true
                return _byteData.asReadOnlyBuffer()
            }

        val charData: CharBuffer?
            get() {
                if (_isCharDataValid) return if (_isCharDataRO) _charData!!.asReadOnlyBuffer() else _charData!!.duplicate()

                if (_byteData == null || !_isByteDataValid) return null
                if (_charData != null) eraseData(_charData!!.array())
                _byteData!!.mark()
                _charData = _charset.decode(_byteData)
                _byteData!!.reset()
                _isCharDataRO = true
                return _charData.asReadOnlyBuffer()
            }

        fun setData(newData: ByteArray, offset: Int, count: Int) {
            _isCharDataValid = false
            if (_byteData == null) _byteData = ByteBuffer.wrap(newData, offset, count)
            else if (_byteData!!.capacity() <= newData.size) {
                eraseData(_byteData!!.array())
                _byteData = ByteBuffer.wrap(newData, offset, count)
            } else {
                _byteData!!.clear().mark()
                _byteData!!.put(newData, offset, count).reset()
                _byteData!!.limit(count)
                eraseData(newData)
            }
            _isByteDataValid = true
        }

        fun setData(newData: CharArray, offset: Int, count: Int) {
            _isByteDataValid = false
            if (_charData == null) _charData = CharBuffer.wrap(newData, offset, count)
            else if (_charData!!.capacity() <= newData.size) {
                eraseData(_charData!!.array())
                _charData = CharBuffer.wrap(newData, offset, count)
            } else {
                _charData!!.clear().mark()
                _charData!!.put(newData, offset, count).reset()
                _charData!!.limit(count)
                eraseData(newData)
            }
            _isCharDataValid = true
        }

        fun erase() {
            if (_charData != null) {
                eraseData(_charData!!.array())
                _charData = null
            }
            if (_byteData != null) {
                eraseData(_byteData!!.array())
                _byteData = null
            }
            _isCharDataValid = false
            _isByteDataValid = _isCharDataValid
        }

        private var _byteData: ByteBuffer? = null
        private var _charData: CharBuffer? = null
        private var _isCharDataValid = false
        private var _isByteDataValid = false
        private var _isCharDataRO = false
        private var _isByteDataRO = false
    }

    private val _id: Int

    companion object {
        @JvmStatic
        fun eraseData(data: ByteArray?) {
            if (data != null) eraseData(data, 0, data.size)
        }

        fun eraseData(data: ByteArray?, offset: Int, count: Int) {
            if (data != null) {
                /*byte[] randomBytes = new byte[count];
            _secureRandom.nextBytes(randomBytes);
            System.arraycopy(randomBytes, 0, data, offset, count);*/
                Arrays.fill(data, 0.toByte())
            }
        }

        fun eraseData(data: CharArray?) {
            if (data != null) {
                //for(int i=0, l=data.length;i<l;i++)
                //    data[i] = (char) _secureRandom.nextInt();
                Arrays.fill(data, 0.toChar())
            }
        }

        @Synchronized
        fun closeAll() {
            var i = 0
            val l = _data.size()
            while (i < l) {
                val b = _data.valueAt(i)
                b.erase()
                i++
            }
            _data.clear()
        }

        fun reserveBytes(capacity: Int): SecureBuffer {
            return SecureBuffer(ByteArray(capacity), 0, 0)
        }

        fun reserveChars(capacity: Int): SecureBuffer {
            val sb = SecureBuffer()
            sb.adoptData(CharArray(capacity), 0, 0)
            return sb
        }

        @Synchronized
        private fun reserveNewId(): Int {
            return _counter++
        }

        @Synchronized
        private fun setData(id: Int, data: ByteArray, offset: Int, count: Int) {
            var b = _data[id]
            if (b != null) b.setData(data, offset, count)
            else {
                b = Buffer()
                b.setData(data, offset, count)
                _data.put(id, b)
            }
        }

        @Synchronized
        private fun setData(id: Int, data: CharArray, offset: Int, count: Int) {
            var b = _data[id]
            if (b != null) b.setData(data, offset, count)
            else {
                b = Buffer()
                b.setData(data, offset, count)
                _data.put(id, b)
            }
        }

        @Synchronized
        private fun getByteBuffer(id: Int): ByteBuffer? {
            val b = _data[id]
            return b?.byteData
        }

        @Synchronized
        private fun getCharBuffer(id: Int): CharBuffer? {
            val b = _data[id]
            return b?.charData
        }

        @Synchronized
        private fun closeBuffer(id: Int) {
            val b = _data[id]
            if (b != null) {
                b.erase()
                _data.remove(id)
            }
        }

        val CREATOR: Creator<SecureBuffer> = object : Creator<SecureBuffer?> {
            override fun createFromParcel(`in`: Parcel): SecureBuffer? {
                return SecureBuffer(`in`)
            }

            override fun newArray(size: Int): Array<SecureBuffer?> {
                return arrayOfNulls(size)
            }
        }

        protected val _secureRandom: SecureRandom = SecureRandom()
        private val _data = SparseArray<Buffer>()
        private var _counter = 0
        private val _charset: Charset = Charset.forName("UTF-8")
    }
}
