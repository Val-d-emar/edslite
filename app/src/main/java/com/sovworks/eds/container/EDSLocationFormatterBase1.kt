package com.sovworks.eds.container

import android.content.Context
import android.os.Parcel
import com.sovworks.eds.android.errors.UserException
import com.sovworks.eds.android.filemanager.DirectorySettings
import com.sovworks.eds.android.settings.UserSettings
import com.sovworks.eds.crypto.SecureBuffer
import com.sovworks.eds.exceptions.ApplicationException
import com.sovworks.eds.fs.FileSystem
import com.sovworks.eds.fs.util.StringPathUtil
import com.sovworks.eds.locations.EDSLocation
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.LocationsManager
import com.sovworks.eds.settings.DefaultSettings
import com.sovworks.eds.settings.Settings
import java.io.IOException

abstract class EDSLocationFormatterBase {
    constructor()

    protected constructor(`in`: Parcel) {
        _disableDefaultSettings = `in`.readByte().toInt() != 0
        _password = `in`.readParcelable(ClassLoader.getSystemClassLoader())
    }

    open fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeByte((if (_disableDefaultSettings) 1 else 0).toByte())
        dest.writeParcelable(_password, 0)
    }

    interface ProgressReporter {
        fun report(prc: Byte): Boolean
    }


    val settings: Settings
        get() = if (context == null) DefaultSettings() else UserSettings.getSettings(context)

    fun setPassword(pass: SecureBuffer?) {
        _password = pass
    }

    fun disableDefaultSettings(`val`: Boolean) {
        _disableDefaultSettings = `val`
    }

    fun setProgressReporter(reporter: ProgressReporter?) {
        _progressReporter = reporter
    }

    @Throws(Exception::class)
    fun format(location: Location): EDSLocation {
        val loc = createLocation(location)
        if (!_dontReg) addLocationToList(loc)
        loc.fs
        initLocationSettings(loc)
        loc.close(false)
        if (!_dontReg) notifyLocationCreated(loc)
        return loc
    }

    fun setDontRegLocation(dontReg: Boolean) {
        _dontReg = dontReg
    }

    protected var _disableDefaultSettings: Boolean = false
    protected var _dontReg: Boolean = false
    protected var _password: SecureBuffer? = null
    protected var _progressReporter: ProgressReporter? = null
    var context: Context? = null

    @Throws(IOException::class, ApplicationException::class, UserException::class)
    protected abstract fun createLocation(location: Location): EDSLocation

    @Throws(Exception::class)
    protected fun addLocationToList(
        loc: EDSLocation, store: Boolean = !UserSettings.getSettings(
            context
        ).neverSaveHistory()
    ) {
        val lm = LocationsManager.getLocationsManager(context, true)
        if (lm != null) {
            val prevLoc = lm.findExistingLocation(loc)
            if (prevLoc == null) lm.addNewLocation(loc, store)
            else lm.replaceLocation(prevLoc, loc, store)
        }
    }

    protected fun notifyLocationCreated(loc: EDSLocation?) {
        if (context != null) LocationsManager.broadcastLocationAdded(context, loc)
    }

    @Throws(IOException::class, ApplicationException::class)
    protected fun initLocationSettings(loc: EDSLocation) {
        writeInternalContainerSettings(loc)
        if (!_dontReg) setExternalContainerSettings(loc)
    }

    @Throws(ApplicationException::class, IOException::class)
    protected open fun setExternalContainerSettings(loc: EDSLocation) {
        val lm = LocationsManager.getLocationsManager(context, true)
        val title = makeTitle(loc, lm)
        loc.externalSettings.title = title
        loc.externalSettings.isVisibleToUser = true

        if (context == null || !UserSettings.getSettings(context)
                .neverSaveHistory()
        ) loc.saveExternalSettings()
    }

    @Throws(IOException::class)
    protected fun writeInternalContainerSettings(loc: EDSLocation) {
        if (_disableDefaultSettings) return
        val ds = DirectorySettings()
        ds.setHiddenFilesMasks(listOf("(?iu)\\.eds.*"))
        val fs: FileSystem = loc.fs
        ds.saveToDir(fs.rootPath.directory)
    }

    protected fun reportProgress(prc: Byte): Boolean {
        return _progressReporter == null || _progressReporter!!.report(prc)
    }

    companion object {
        const val FORMAT_ENCFS: String = "EncFs"

        fun makeTitle(cont: EDSLocation, lm: LocationsManager): String {
            var startTitle = try {
                StringPathUtil(cont.location.currentPath.pathDesc).fileNameWithoutExtension
            } catch (e: IOException) {
                cont.title
            }
            return makeTitle(startTitle, lm, cont)
        }

        fun makeTitle(startTitle: String, lm: LocationsManager, ignore: EDSLocation): String {
            var title = startTitle
            var i = 1
            while (checkExistingTitle(title, lm, ignore)) title = startTitle + " " + i++
            return title
        }

        private fun checkExistingTitle(
            title: String,
            lm: LocationsManager,
            ignore: EDSLocation
        ): Boolean {
            val igUri = ignore.location.locationUri
            for (cnt in lm.getLoadedEDSLocations(true)) if (cnt !== ignore && (cnt.location.locationUri != igUri) && cnt.title == title) return true
            return false
        }
    }
}
