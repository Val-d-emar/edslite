package com.sovworks.eds.util.exec

import com.sovworks.eds.exceptions.ApplicationException
import com.sovworks.eds.util.TextShell
import com.sovworks.eds.util.TextShell.executeCommand
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.Reader
import java.io.StringReader
import java.io.StringWriter

class ExecuteExternalProgram : TextShell {
    class OutputLinesIterator(private val _input: Reader) : MutableIterator<String?>,
        Iterable<String?> {
        override fun hasNext(): Boolean {
            if (!_isLineRead) readLine()
            return _line != null
        }

        override fun next(): String? {
            if (!hasNext()) throw NoSuchElementException()
            _isLineRead = false
            return _line
        }

        override fun remove() {
            throw UnsupportedOperationException()
        }

        override fun iterator(): MutableIterator<String> {
            return this
        }

        private var _line: String? = null
        private var _isLineRead = false
        private var _eof = false

        private fun readLine() {
            _line = null
            _isLineRead = true
            if (_eof) return
            val out = StringWriter()
            try {
                while (true) {
                    val n = _input.read()
                    if (n < 0 || n == '\n'.code) {
                        if (n < 0) _eof = true
                        _line = out.toString()
                        break
                    }
                    if (n != '\r'.code) out.write(n)
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    @Throws(IOException::class)
    override fun executeCommand(vararg args: Any) {
        executeCommand(*objectsToStrings(*args))
    }

    @Throws(ExternalProgramFailedException::class, IOException::class)
    override fun waitResult(): String {
        val res = waitProcess()
        val out = readAll(procInputStream)
        if (res != 0) throw ExternalProgramFailedException(
            res, """
     $out
     $out
     """.trimIndent(), *_currentArgs
        )

        return out
    }

    @Throws(IOException::class)
    override fun writeStdInput(data: String) {
        procOutputStream!!.write(data.toByteArray())
        closeProcOutputStream()
    }

    fun waitProcess(): Int {
        return try {
            _process!!.waitFor()
        } catch (e: InterruptedException) {
            -1
        }
    }

    val procErrorStream: InputStream
        get() = _process!!.errorStream

    @Throws(IOException::class)
    fun closeProcOutputStream() {
        if (procOutputStream != null) {
            procOutputStream!!.close()
            procOutputStream = null
        }
    }

    @Throws(IOException::class)
    fun closeProcInputStream() {
        if (procInputStream != null) {
            procInputStream!!.close()
            procInputStream = null
        }
    }

    fun redirectErrorStream(`val`: Boolean) {
        _redirectErrorStream = `val`
    }

    @Throws(IOException::class)
    fun executeCommand(vararg command: String) {
        if (_process != null) throw RuntimeException("Previous process is active")
        _currentArgs = command
        _process =
            ProcessBuilder().command(*command).redirectErrorStream(_redirectErrorStream).start()
        procInputStream = _process.getInputStream()
        procOutputStream = _process.getOutputStream()
    }

    override fun close() {
        try {
            try {
                closeProcInputStream()
            } catch (e: IOException) {
                e.printStackTrace()
            }

            try {
                closeProcOutputStream()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        } finally {
            if (_process != null) {
                _process!!.destroy()
                _process = null
            }
        }
    }

    protected var _process: Process? = null
    var procInputStream: InputStream? = null
        protected set
    var procOutputStream: OutputStream? = null
        protected set
    protected var _redirectErrorStream: Boolean = true

    protected var _currentArgs: Array<String>

    companion object {
        @Throws(ApplicationException::class)
        fun makeExecutable(path: String?) {
            executeAndReadString("chmod", "0700", path)
        }

        @Throws(ApplicationException::class)
        fun executeAndReadLines(vararg commands: String?): Iterable<String> {
            val exec = ExecuteExternalProgram()
            try {
                return executeAndReadLines(exec, *commands)
            } finally {
                exec.close()
            }
        }

        @Throws(ApplicationException::class)
        fun executeAndReadString(vararg commands: String?): String? {
            val exec = ExecuteExternalProgram()
            try {
                return executeAndReadString(exec, 0, *commands)
            } finally {
                exec.close()
            }
        }

        @Throws(ApplicationException::class)
        fun executeAndReadString(timeout: Int, vararg commands: String?): String? {
            val exec = ExecuteExternalProgram()
            try {
                return executeAndReadString(exec, timeout, *commands)
            } finally {
                exec.close()
            }
        }

        @Throws(ApplicationException::class)
        fun execute(vararg commands: String?) {
            val exec = ExecuteExternalProgram()
            try {
                execute(exec, *commands)
            } finally {
                exec.close()
            }
        }

        fun getStringIterable(inp: InputStream?): Iterable<String> {
            return OutputLinesIterator(InputStreamReader(inp))
        }

        fun getStringIterable(inp: Reader): Iterable<String> {
            return OutputLinesIterator(inp)
        }

        fun objectsToStrings(vararg objects: Any): Array<String?> {
            val strArgs = arrayOfNulls<String>(objects.size)
            for (i in objects.indices) strArgs[i] = objects[i].toString()
            return strArgs
        }

        @Throws(ApplicationException::class)
        protected fun executeAndReadLines(
            exec: ExecuteExternalProgram, vararg command: String?
        ): Iterable<String> {
            return OutputLinesIterator(StringReader(executeAndReadString(exec, 0, *command)))
        }

        @Throws(ApplicationException::class)
        protected fun execute(exec: ExecuteExternalProgram, vararg command: String?) {
            try {
                exec.executeCommand(*command)
                val res = exec.waitProcess()
                if (res != 0) throw ExternalProgramFailedException(res, "", *command)
            } catch (e: IOException) {
                throw ApplicationException("Failed executing external program", e)
            }
        }

        @Throws(ApplicationException::class)
        protected fun executeAndReadString(
            exec: ExecuteExternalProgram, timeout: Int, vararg command: String?
        ): String? {
            try {
                if (timeout > 0) return CmdRunner.Companion.executeCommand(
                    timeout,
                    exec,
                    *command as Array<Any?>
                )
                else {
                    exec.executeCommand(*command)
                    return exec.waitResult()
                }
            } catch (e: IOException) {
                throw ApplicationException("Failed executing external program", e)
            }
        }

        @Throws(IOException::class)
        fun readAll(inp: InputStream?): String {
            val reader = InputStreamReader(inp)
            val sw = StringWriter()
            val buf = CharArray(512)
            var n: Int
            while ((reader.read(buf).also { n = it }) >= 0) sw.write(buf, 0, n)
            return sw.toString()
        }
    }
}
