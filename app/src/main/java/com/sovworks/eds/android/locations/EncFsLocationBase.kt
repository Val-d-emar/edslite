package com.sovworks.eds.android.locations

import android.content.Context
import android.net.Uri
import com.sovworks.eds.android.helpers.ContainerOpeningProgressReporter
import com.sovworks.eds.android.locations.EDSLocationBase.SharedData
import com.sovworks.eds.android.providers.MainContentProvider
import com.sovworks.eds.crypto.SimpleCrypto
import com.sovworks.eds.fs.FileSystem
import com.sovworks.eds.fs.Path
import com.sovworks.eds.fs.encfs.FS
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.LocationsManagerBase
import com.sovworks.eds.settings.Settings
import java.io.IOException
import java.util.Arrays

abstract class EncFsLocationBase : EDSLocationBase {
    constructor(uri: Uri, lm: LocationsManagerBase?, context: Context?, settings: Settings?) : this(
        getContainerLocationFromUri(uri, lm), null, context, settings
    ) {
        loadFromUri(uri)
    }

    constructor(
        containerLocation: Location,
        encFs: FS?,
        context: Context?,
        settings: Settings?
    ) : super(
        settings, SharedData(
            getId(containerLocation),
            createInternalSettings(),
            containerLocation,
            context
        )
    ) {
        sharedData.encFs = encFs
    }

    constructor(sibling: EncFsLocationBase?) : super(sibling)

    override fun loadFromUri(uri: Uri) {
        super.loadFromUri(uri)
        var p = uri.path
        if (p != null && p.startsWith("/")) p = p.substring(1)
        _currentPathString = if (p == null || "/" == p || p.isEmpty()) null else p
    }

    @Throws(Exception::class)
    override fun open() {
        if (isOpenOrMounted) return
        val pass = finalPassword
        try {
            val encfsLocation =
                sharedData.containerLocation //Mounter.getNonEmulatedDeviceLocationIfNeeded(_globalSettings, _context, _location);

            //if(encfsLocation == null)
            //	encfsLocation = _location;
            sharedData.encFs = FS(
                encfsLocation.currentPath,
                pass,
                _openingProgressReporter as ContainerOpeningProgressReporter
            )
        } finally {
            Arrays.fill(pass, 0.toByte())
        }
    }

    @Throws(IOException::class)
    override fun close(force: Boolean) {
        super.close(force)
        sharedData.encFs = null
    }

    override fun getLocationUri(): Uri {
        return makeUri(URI_SCHEME).build()
    }

    override fun isOpen(): Boolean {
        return sharedData.encFs != null
    }

    val encFs: FS?
        get() = sharedData.encFs

    override fun getDeviceAccessibleUri(path: Path): Uri? {
        return if (!_globalSettings.dontUseContentProvider()) MainContentProvider.getContentUriFromLocation(
            this, path
        ) else null
    }

    protected class SharedData(
        id: String?,
        settings: InternalSettings?,
        location: Location?,
        ctx: Context?
    ) :
        EDSLocationBase.SharedData(id, settings, location, ctx) {
        var encFs: FS? = null
    }

    override fun getSharedData(): SharedData {
        return super.getSharedData() as SharedData
    }

    @Throws(IOException::class)
    override fun createBaseFS(readOnly: Boolean): FileSystem {
        if (sharedData.encFs == null) throw RuntimeException("File system is closed")
        return sharedData.encFs!!
    }

    companion object {
        @JvmStatic
        @Throws(Exception::class)
        fun getLocationId(lm: LocationsManagerBase?, locationUri: Uri): String {
            return getId(getContainerLocationFromUri(locationUri, lm))
        }

        fun getId(containerLocation: Location): String {
            return SimpleCrypto.calcStringMD5(containerLocation.locationUri.toString())
        }

        const val URI_SCHEME: String = "encfs"
    }
}
