package com.sovworks.eds.fs.fat

import android.annotation.SuppressLint
import com.sovworks.eds.fs.util.StringPathUtil
import java.nio.charset.Charset
import java.nio.charset.CharsetEncoder
import java.util.Locale
import kotlin.math.min

@SuppressLint("DefaultLocale")
internal class FileName(private val _name: String?) {
    var isLFN: Boolean = false
    var isLowerCaseName: Boolean = false
    var isLowerCaseExtension: Boolean = false

    fun getDosName(counter: Int): String {
        if (_name == "." || _name == "..") return extendName(_name, 11)

        val p = StringPathUtil(_name)
        var fn = p.fileNameWithoutExtension.uppercase(Locale.getDefault())
        var filteredName = ""
        run {
            var i = 0
            val l = fn.length
            while (i < l) {
                var c = fn[i]
                if (c == ' ') break
                if (!isLegalDosChar(c)) c = '~'
                filteredName += c
                i++
            }
        }
        if (counter > 0) {
            val counterString = counter.toString()
            filteredName = if (counterString.length >= 8) counterString.substring(0, 8)
            else (filteredName.substring(
                0,
                (min(
                    8.0,
                    filteredName.length.toDouble()
                ) - counterString.length).toInt()
            )
                    + counterString)
        }
        fn = extendName(filteredName, 8)

        var ex = p.fileExtension.uppercase(Locale.getDefault())
        filteredName = ""
        var i = 0
        val l = ex.length
        while (i < l) {
            var c = ex[i]
            if (c == ' ') break
            if (!isLegalDosChar(c)) c = '~'
            filteredName += c
            i++
        }
        ex = extendName(filteredName, 3)
        return fn + ex
    }

    private fun init() {
        isLowerCaseName = false
        isLowerCaseExtension = isLowerCaseName
        isLFN = isLowerCaseExtension
        if (_name == "." || _name == "..") return

        val p = StringPathUtil(_name)
        val fn = p.fileNameWithoutExtension
        if (fn == toUpperCase(fn)) isLowerCaseName = false
        else if (fn == toLowerCase(fn)) isLowerCaseName = true
        else isLFN = true

        val ex = p.fileExtension
        if (ex == toUpperCase(ex)) isLowerCaseExtension = false
        else if (ex == toLowerCase(ex)) isLowerCaseExtension = true
        else isLFN = true
        // DEBUG
        // Log.d("EDS",String.format("%s.%s lcn=%s lce=%s lfn=%s",
        // fn,ex,isLowerCaseName,isLowerCaseExtension,isLFN));
        if (!isLFN
            && (fn.length > 8 || ex.length > 3 || !isLegalDosName(fn) || !isPureAscii(_name))
        ) isLFN = true
    }

    private fun extendName(name: String, targetLen: Int): String {
        var name = name
        if (name.length > targetLen) return name.substring(0, targetLen - 1) + '~'
        for (i in name.length..<targetLen) name += ' '
        return name
    }

    init {
        init()
    }

    companion object {
        fun isLegalDosChar(c: Char): Boolean {
            if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || (c.code >= 128 && c.code <= 255)) return true
            for (b in DirEntry.Companion.ALLOWED_SYMBOLS) if (b == c.code.toByte()) return true
            return false

            // Arrays.asList(DirEntry.ALLOWED_SYMBOLS). .contains(c);
        }

        fun isLegalDosName(fn: String): Boolean {
            var space = false
            var i = 0
            val l = fn.length
            while (i < l) {
                val c = fn[i]
                if (c == ' ') {
                    if (space) return false
                    space = true
                } else if (!isLegalDosChar(c)) return false
                i++
            }
            return true
        }

        fun isPureAscii(v: String?): Boolean {
            return asciiEncoder.canEncode(v)
        }

        fun toUpperCase(s: String): String {
            val res = StringBuilder()
            for (i in 0..<s.length) {
                val c = s[i]
                if (c >= 'a' || c <= 'z') res.append(c.uppercaseChar())
                else res.append(c)
            }
            return res.toString()
        }

        fun toLowerCase(s: String): String {
            val res = StringBuilder()
            for (i in 0..<s.length) {
                val c = s[i]
                if (c >= 'a' || c <= 'z') res.append(c.lowercaseChar())
                else res.append(c)
            }
            return res.toString()
        }

        private val asciiEncoder: CharsetEncoder = Charset.forName("US-ASCII").newEncoder() // or
        // "ISO-8859-1"
        // for
        // ISO
        // Latin
        // 1
    }
}
