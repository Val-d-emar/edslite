package com.sovworks.eds.util

import com.sovworks.eds.util.exec.ExternalProgramFailedException
import java.io.IOException

interface TextShell {
    @Throws(IOException::class)
    fun executeCommand(vararg args: Any)
    
    @Throws(IOException::class)
    fun writeStdInput(data: String)
    
    @Throws(ExternalProgramFailedException::class, IOException::class)
    fun waitResult(): String
    
    fun close()
} 