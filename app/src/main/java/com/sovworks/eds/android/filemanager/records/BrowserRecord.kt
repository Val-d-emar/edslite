package com.sovworks.eds.android.filemanager.records

import android.view.View
import android.view.ViewGroup
import com.sovworks.eds.android.filemanager.activities.FileManagerActivity
import com.sovworks.eds.android.helpers.CachedPathInfo
import com.sovworks.eds.android.helpers.ExtendedFileInfoLoader.ExtendedFileInfo
import com.sovworks.eds.fs.Path
import com.sovworks.eds.locations.Location
import java.io.IOException

interface BrowserRecord : CachedPathInfo {
    @Throws(IOException::class)
    fun init(location: Location?, path: Path?)
    override fun getName(): String

    @Throws(Exception::class)
    fun open(): Boolean

    @Throws(Exception::class)
    fun openInplace(): Boolean
    fun allowSelect(): Boolean
    @JvmField
    var isSelected: Boolean
    fun setHostActivity(host: FileManagerActivity?)
    @JvmField
    val viewType: Int
    fun createView(position: Int, parent: ViewGroup?): View?
    fun updateView(view: View, position: Int)
    fun updateView()
    fun setExtData(data: ExtendedFileInfo?)
    fun loadExtendedInfo(): ExtendedFileInfo?
    fun needLoadExtendedInfo(): Boolean
}
