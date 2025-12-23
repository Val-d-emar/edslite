package com.sovworks.eds.android.settings

import android.content.Context
import com.sovworks.eds.settings.SystemConfig
import java.io.File

abstract class SystemConfigCommon(protected val _context: Context) : SystemConfig() {
    override fun getTmpFolderPath(): File {
        return _context.filesDir
    }

    override fun getCacheFolderPath(): File {
        return _context.cacheDir
    }

    override fun getPrivateExecFolderPath(): File {
        return _context.filesDir
    }

    override fun getFSMFolderPath(): File {
        return File(_context.filesDir, "fsm")
    }
}
