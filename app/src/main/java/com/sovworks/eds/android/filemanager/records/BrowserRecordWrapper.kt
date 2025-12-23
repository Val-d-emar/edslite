package com.sovworks.eds.android.filemanager.records

import android.view.View
import android.view.ViewGroup
import com.sovworks.eds.android.filemanager.activities.FileManagerActivity
import com.sovworks.eds.android.helpers.ExtendedFileInfoLoader.ExtendedFileInfo
import com.sovworks.eds.fs.Path
import com.sovworks.eds.locations.Location
import java.io.IOException
import java.util.Date

class BrowserRecordWrapper protected constructor(val baseRecord: BrowserRecord) : BrowserRecord {
    @Throws(IOException::class)
    override fun init(location: Location?, path: Path?) {
        baseRecord.init(location, path)
    }

    override fun getName(): String {
        return baseRecord.name
    }

    @Throws(Exception::class)
    override fun open(): Boolean {
        return baseRecord.open()
    }

    @Throws(Exception::class)
    override fun openInplace(): Boolean {
        return baseRecord.openInplace()
    }

    override fun allowSelect(): Boolean {
        return baseRecord.allowSelect()
    }

    override var isSelected: Boolean
        get() = baseRecord.isSelected
        set(val) {
            baseRecord.isSelected = `val`
        }

    override fun setHostActivity(host: FileManagerActivity?) {
        this.host = host
        baseRecord.setHostActivity(host)
    }

    override val viewType: Int
        get() = baseRecord.viewType

    override fun createView(position: Int, parent: ViewGroup?): View? {
        return baseRecord.createView(position, parent)
    }

    override fun updateView(view: View, position: Int) {
        baseRecord.updateView(view, position)
    }

    override fun updateView() {
        FsBrowserRecord.Companion.updateRowView(host!!, this)
    }

    override fun setExtData(data: ExtendedFileInfo?) {
        baseRecord.setExtData(data)
    }

    override fun loadExtendedInfo(): ExtendedFileInfo? {
        return baseRecord.loadExtendedInfo()
    }

    override fun needLoadExtendedInfo(): Boolean {
        return baseRecord.needLoadExtendedInfo()
    }

    override fun getPath(): Path {
        return baseRecord.path
    }

    override fun getPathDesc(): String {
        return baseRecord.pathDesc
    }

    override fun isFile(): Boolean {
        return baseRecord.isFile
    }

    override fun isDirectory(): Boolean {
        return baseRecord.isDirectory
    }

    override fun getModificationDate(): Date {
        return baseRecord.modificationDate
    }

    override fun getSize(): Long {
        return baseRecord.size
    }

    @Throws(IOException::class)
    override fun init(path: Path) {
        baseRecord.init(path)
    }

    var host: FileManagerActivity? = null
        private set
}
