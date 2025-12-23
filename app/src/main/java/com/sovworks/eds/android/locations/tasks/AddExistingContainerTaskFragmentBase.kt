package com.sovworks.eds.android.locations.tasks

import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.R
import com.sovworks.eds.android.errors.UserException
import com.sovworks.eds.android.locations.EncFsLocation
import com.sovworks.eds.android.settings.UserSettings
import com.sovworks.eds.container.ContainerFormatter
import com.sovworks.eds.fs.encfs.Config
import com.sovworks.eds.locations.ContainerLocation
import com.sovworks.eds.locations.EDSLocation
import com.sovworks.eds.locations.Location
import com.sovworks.eds.settings.Settings
import java.io.IOException

abstract class AddExistingContainerTaskFragmentBase : AddExistingEDSLocationTaskFragment() {
    @Throws(Exception::class)
    override fun createEDSLocation(locationLocation: Location): EDSLocation {
        Logger.debug("Adding EDS loc at " + locationLocation.locationUri)
        val cp = locationLocation.currentPath
        var isEncFs = false
        if (cp.isFile) {
            val fn = cp.file.name
            if (Config.CONFIG_FILENAME.equals(
                    fn,
                    ignoreCase = true
                ) || Config.CONFIG_FILENAME2.equals(fn, ignoreCase = true)
            ) {
                val parentPath = cp.parentPath
                if (parentPath != null) {
                    locationLocation.currentPath = parentPath
                    isEncFs = true
                }
            }
        } else if (cp.isDirectory) {
            val cfgPath = Config.getConfigFilePath(cp.directory)
                ?: throw UserException(
                    "EncFs config file doesn't exist",
                    R.string.encfs_config_file_not_found
                )
            isEncFs = true
        } else throw UserException("Wrong path", R.string.wrong_path)

        return if (isEncFs) EncFsLocation(locationLocation, _context)
        else createContainerBasedLocation(locationLocation)
    }

    @Throws(Exception::class)
    protected fun createContainerBasedLocation(locationLocation: Location?): ContainerLocation {
        val settings: Settings = UserSettings.getSettings(_context)
        return createContainerLocationBase(locationLocation, settings)
    }

    @Throws(IOException::class)
    protected fun createContainerLocationBase(
        locationLocation: Location?,
        settings: Settings?
    ): ContainerLocation {
        var formatName =
            arguments.getString(CreateContainerTaskFragmentBase.Companion.ARG_CONTAINER_FORMAT)
        if (formatName == null) formatName = ""
        return ContainerFormatter.createBaseContainerLocationFromFormatInfo(
            formatName,
            locationLocation,
            null,
            _context,
            settings
        )
    }

    companion object {
        const val TAG: String =
            "com.sovworks.eds.android.locations.tasks.AddExistingContainerTaskFragment"
    }
}
