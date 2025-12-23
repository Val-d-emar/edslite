package com.sovworks.eds.util.exec

import com.sovworks.eds.exceptions.ApplicationException

class ExternalProgramFailedException // _command = command;
    (
    val exitCode: Int, // private final String _command;
    val commandOutput: String, vararg command: String?
) :
    ApplicationException(
        Companion.getMsg(
            exitCode,
            commandOutput, *command
        )
    ) {
    companion object {
        /**  */
        private const val serialVersionUID = 1L

        //	public String getCommand()
        //	{
        //		return _command;
        //	}
        private fun getMsg(exitCode: Int, output: String, vararg command: String): String {
            var tmp = ""
            for (s in command) tmp += "$s "
            return String.format(
                "External program failed.\nCommand: %s\nExit code: %d\nOutput: %s",
                tmp,
                exitCode,
                output
            )
        }
    }
}
