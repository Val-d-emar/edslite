package com.sovworks.eds.util.exec

import com.sovworks.eds.exceptions.ApplicationException
import com.sovworks.eds.util.TextShell
import java.io.IOException

class CmdRunner(private val _exec: TextShell, vararg command: Any) : Thread() {
    @get:Throws(Throwable::class)
    val result: String?
        get() {
            if (_error != null) throw _error
            return _result
        }

    override fun run() {
        try {
            _exec.executeCommand(*_command)
            _result = _exec.waitResult()
        } catch (e: ExternalProgramFailedException) {
            _error = e
        } catch (e: IOException) {
            _error = e
        }
    }

    private var _error: Throwable? = null
    private var _result: String? = null
    private val _command: Array<Any>

    init {
        _command = command
    }

    companion object {
        @Throws(ApplicationException::class)
        fun executeCommand(timeout: Int, exec: TextShell, vararg command: Any?): String? {
            val cmr = CmdRunner(exec, *command)
            cmr.start()
            try {
                cmr.join(timeout.toLong())
            } catch (ignored: InterruptedException) {
            }
            if (cmr.isAlive) {
                exec.close()
                throw ApplicationException("Timeout error")
            }
            try {
                return cmr.result
            } catch (e: Throwable) {
                throw ApplicationException("Failed executing command", e)
            }
        }
    }
}
