package com.sovworks.eds.android.providers.cursor

import com.sovworks.eds.fs.Path

interface PathChecker {
    fun checkPath(path: Path?): Boolean
}
