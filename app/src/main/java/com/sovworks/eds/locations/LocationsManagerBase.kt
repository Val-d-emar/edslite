package com.sovworks.eds.locations

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.os.Environment
import com.sovworks.eds.android.Logger.Companion.log
import com.sovworks.eds.android.Logger.Companion.showAndLog
import com.sovworks.eds.android.helpers.StorageOptions
import com.sovworks.eds.android.helpers.StorageOptionsBase.Companion.getStoragesList
import com.sovworks.eds.android.helpers.StorageOptionsBase.Companion.reloadStorageList
import com.sovworks.eds.android.helpers.Util
import com.sovworks.eds.android.helpers.UtilBase.Companion.loadStringArrayFromString
import com.sovworks.eds.android.locations.ContainerBasedLocation
import com.sovworks.eds.android.locations.ContentResolverLocation
import com.sovworks.eds.android.locations.DeviceRootNPLocation
import com.sovworks.eds.android.locations.DocumentTreeLocation
import com.sovworks.eds.android.locations.DocumentTreeLocation.Companion.fromLocationUri
import com.sovworks.eds.android.locations.DocumentTreeLocation.Companion.getLocationId
import com.sovworks.eds.android.locations.DocumentTreeLocation.Companion.isDocumentTreeUri
import com.sovworks.eds.android.locations.EncFsLocation
import com.sovworks.eds.android.locations.EncFsLocationBase
import com.sovworks.eds.android.locations.EncFsLocationBase.Companion.getLocationId
import com.sovworks.eds.android.locations.ExternalStorageLocation
import com.sovworks.eds.android.locations.InternalSDLocation
import com.sovworks.eds.android.locations.LUKSLocation
import com.sovworks.eds.android.locations.TrueCryptLocation
import com.sovworks.eds.android.locations.VeraCryptLocation
import com.sovworks.eds.android.locations.closer.fragments.OpenableLocationCloserFragment.Companion.closeLocation
import com.sovworks.eds.android.receivers.MediaMountedReceiver
import com.sovworks.eds.android.settings.UserSettings
import com.sovworks.eds.crypto.SimpleCrypto.calcStringMD5
import com.sovworks.eds.fs.Path
import com.sovworks.eds.fs.std.StdFs.Companion.stdFs
import com.sovworks.eds.fs.util.StringPathUtil
import com.sovworks.eds.fs.util.Util.restorePaths
import com.sovworks.eds.fs.util.Util.storePaths
import com.sovworks.eds.settings.Settings
import org.json.JSONException
import java.io.File
import java.io.IOException
import java.util.AbstractList
import java.util.Date
import java.util.Random
import java.util.Stack

abstract class LocationsManagerBase protected constructor(
    context: Context,
    protected val _settings: Settings
) {
    private fun startMountsMonitor() {
        if (_mediaChangedReceiver != null) return
        _mediaChangedReceiver = MediaMountedReceiver(this)
        context.registerReceiver(_mediaChangedReceiver, IntentFilter(Intent.ACTION_MEDIA_MOUNTED))
        context.registerReceiver(
            _mediaChangedReceiver, IntentFilter(Intent.ACTION_MEDIA_UNMOUNTED)
        )
        context.registerReceiver(_mediaChangedReceiver, IntentFilter(Intent.ACTION_MEDIA_REMOVED))
        context.registerReceiver(
            _mediaChangedReceiver, IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        )
        context.registerReceiver(
            _mediaChangedReceiver, IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED)
        )
    }

    fun close() {
        if (_mediaChangedReceiver != null) {
            context.unregisterReceiver(_mediaChangedReceiver)
            _mediaChangedReceiver = null
        }
        closeAllLocations(true, false)
        clearLocations()
    }

    fun getFromIntent(i: Intent, pathsHolder: MutableCollection<Path?>?): Location? {
        return getFromIntent(i, this, pathsHolder)
    }

    fun getFromBundle(b: Bundle?, pathsHolder: MutableCollection<Path?>?): Location? {
        return getFromBundle(b, this, pathsHolder)
    }

    @Throws(Exception::class)
    fun getLocationsFromIntent(i: Intent): ArrayList<Location> {
        return getLocationsFromIntent(this, i)
    }

    @Throws(Exception::class)
    fun getLocationsFromBundle(b: Bundle?): ArrayList<Location> {
        return getLocationsFromBundle(this, b)
    }

    val defaultDeviceLocation: Location?
        get() {
            var res: Location? = null
            var dba: Location? = null
            synchronized(_currentLocations) {
                for (li in _currentLocations) {
                    val loc = li.location
                    if (loc is InternalSDLocation) {
                        res = loc
                        break
                    }
                    if (res == null && loc is ExternalStorageLocation) res = loc
                    else if (dba == null && loc is DeviceBasedLocation) dba = loc
                }
            }
            if (res == null) res = if (dba == null) DeviceBasedLocation(_settings) else dba
            return res!!.copy()
        }

    fun closeAllLocations(
        locations: Iterable<Location?>, forceClose: Boolean, sendBroadcasts: Boolean
    ) {
        // boolean forceClose = _settings.alwaysForceClose();
        for (l in locations) {
            try {
                if (l is Openable) {
                    if (l.isOpen) {
                        closeLocation(l, forceClose)
                        if (sendBroadcasts) broadcastLocationChanged(
                            context, l
                        )
                    }
                }
            } catch (e: Exception) {
                showAndLog(context, e)
            }
        }
    }

    @Throws(Exception::class)
    fun getLocation(locationUri: Uri): Location {
        val locId = getLocationIdFromUri(locationUri)
        if (locId != null) {
            val prevLoc = findExistingLocation(locId)
            if (prevLoc != null) {
                val loc = prevLoc.copy()
                loc!!.loadFromUri(locationUri)
                return loc
            }
        }
        val loc = createLocationFromUri(locationUri)
        requireNotNull(loc) { "Unsupported location uri: $locationUri" }
        if (findExistingLocation(loc.id) == null) addNewLocation(loc, false)

        return loc
    }

    fun getLocations(uriStrings: Collection<String?>?): ArrayList<Location> {
        val res = ArrayList<Location>()
        if (uriStrings == null) return res
        for (uriString in uriStrings) {
            val uri = Uri.parse(uriString)
            try {
                res.add(getLocation(uri))
            } catch (ignored: Exception) {
            }
        }
        return res
    }

    fun addNewLocation(loc: Location, store: Boolean) {
        synchronized(_currentLocations) {
            _currentLocations.add(LocationInfo(loc, store))
            if (store) saveCurrentLocationLinks()
        }
    }

    private fun removeLocation(locationId: String?) {
        synchronized(_currentLocations) {
            val li = findExistingLocationInfo(locationId)
            if (li != null) {
                _currentLocations.remove(li)
                if (li.store) saveCurrentLocationLinks()
            }
        }
    }

    fun removeLocation(loc: Location) {
        removeLocation(loc.id)
    }

    fun replaceLocation(oldLoc: Location, newLoc: Location, store: Boolean) {
        synchronized(_currentLocations) {
            removeLocation(oldLoc)
            addNewLocation(newLoc, store)
        }
    }

    private fun clearLocations() {
        synchronized(_currentLocations) {
            _currentLocations.clear()
        }
    }

    fun loadStoredLocations() {
        synchronized(_currentLocations) {
            loadStaticLocations()
            for (u in getStoredLocationUris(_settings)) {
                try {
                    val loc = createLocationFromUri(u)
                    requireNotNull(loc) { "Unsupported location uri: $u" }
                    _currentLocations.add(LocationInfo(loc, true))
                } catch (e: Exception) {
                    log(e)
                }
            }
        }
    }

    @Throws(Exception::class)
    fun findExistingLocation(loc: Location): Location? {
        return findExistingLocation(loc.locationUri)
    }

    fun findExistingLocation(locationId: String?): Location? {
        synchronized(_currentLocations) {
            val li = findExistingLocationInfo(locationId)
            return li?.location
        }
    }

    fun isStoredLocation(locationId: String?): Boolean {
        synchronized(_currentLocations) {
            val li = findExistingLocationInfo(locationId)
            return li != null && li.store
        }
    }

    @Throws(Exception::class)
    fun findExistingLocation(locationUri: Uri): Location? {
        val t = createLocationFromUri(locationUri)
        requireNotNull(t) { "Unsupported location uri: $locationUri" }
        return findExistingLocation(t.id)
    }

    fun getLoadedLocations(onlyVisible: Boolean): List<Location?> {
        synchronized(_currentLocations) {
            return ArrayList(
                object : FilteredList<Location?>() {
                    override fun isValid(l: Location): Boolean {
                        return !onlyVisible || l.externalSettings.isVisibleToUser
                    }
                })
        }
    }

    fun getLoadedEDSLocations(onlyVisible: Boolean): List<EDSLocation> {
        synchronized(_currentLocations) {
            return ArrayList(
                object : FilteredList<EDSLocation?>() {
                    override fun isValid(l: Location): Boolean {
                        return l is EDSLocation
                                && (!onlyVisible || l.externalSettings.isVisibleToUser)
                    }
                })
        }
    }

    fun hasOpenLocations(): Boolean {
        synchronized(_currentLocations) {
            for (loc in _currentLocations) if ((loc.location is Openable && (loc.location as Openable).isOpen)) return true
            return false
        }
    }

    @Throws(Exception::class)
    fun getDefaultLocationFromPath(path: String): Location {
        val u = Uri.parse(path)
        if (u.scheme == null && !path.startsWith("/")) return DeviceBasedLocation(
            _settings,
            stdFs
                .getPath(Environment.getExternalStorageDirectory().path)
                .combine(path)
        )

        return if (u.scheme == null) createDeviceLocation(u) else getLocation(u)
    }

    fun getLocationsFromPaths(loc: Location, paths: List<Path?>): ArrayList<Location?> {
        val res = ArrayList<Location?>()
        for (p in paths) {
            val l = loc.copy()
            l.currentPath = p
            res.add(l)
        }
        return res
    }

    fun updateDeviceLocations() {
        synchronized(_currentLocations) {
            val prev: MutableList<LocationInfo> = ArrayList()
            for (li in _currentLocations) if (li.isDevice) prev.add(li)
            val cur: List<Location> = loadDeviceLocations()
            for (li in prev) {
                var remove = true
                for (loc in cur) {
                    if (loc.id == li.location.id) remove = false
                }
                if (remove) _currentLocations.remove(li)
            }
            for (loc in cur) {
                var add = true
                for (li in prev) {
                    if (loc.id == li.location.id) add = false
                }
                if (add) {
                    val li = LocationInfo(loc, false)
                    li.isDevice = true
                    _currentLocations.add(li)
                }
            }
        }
    }

    fun saveCurrentLocationLinks() {
        val links = ArrayList<String>()
        synchronized(_currentLocations) {
            for (li in _currentLocations) if (li.store) links.add(li.location.locationUri.toString())
        }
        _settings.storedLocations =
            Util.storeElementsToString(links)
    }

    fun genNewLocationId(): String {
        while (true) {
            val locId =
                calcStringMD5(
                    Date().time.toString() + Random().nextLong().toString()
                )
            if (findExistingLocation(locId) == null) return locId
        }
    }

    val locationsClosingOrder: Iterable<Location>
        get() {
            val locs =
                ArrayList<Location>()
            for (i in _openedLocationsStack.indices.reversed()) {
                val loc =
                    findExistingLocation(_openedLocationsStack[i])
                if (loc != null) locs.add(loc)
            }
            return locs
        }

    fun regOpenedLocation(loc: Location) {
        _openedLocationsStack.push(loc.id)
    }

    fun unregOpenedLocation(loc: Location) {
        val id = loc.id
        while (_openedLocationsStack.contains(id)) _openedLocationsStack.remove(id)
    }

    fun closeAllLocations(forceClose: Boolean, sendBroadcasts: Boolean) {
        closeAllLocations(locationsClosingOrder, forceClose, sendBroadcasts)
        closeAllLocations(ArrayList(getLoadedLocations(false)), forceClose, sendBroadcasts)
    }

    @Throws(Exception::class)
    fun unmountAndCloseLocation(location: Location?, forceClose: Boolean) {
        if (location is Openable) closeLocation(location, forceClose)
    }

    @Throws(Exception::class)
    fun closeLocation(loc: Location, forceClose: Boolean) {
        loc.closeFileSystem(forceClose)
        if (loc is Openable) closeLocation(context, loc, forceClose)
    }

    @Throws(Exception::class)
    fun createLocationFromUri(locationUri: Uri): Location? {
        val scheme = locationUri.scheme ?: return findOrCreateDeviceLocation(locationUri)
        return when (locationUri.scheme) {
            ContainerBasedLocation.URI_SCHEME -> createContainerLocation(locationUri)
            DeviceRootNPLocation.locationId -> createDeviceRootNPLocation(locationUri)
            DeviceBasedLocation.URI_SCHEME -> createDeviceLocation(locationUri)
            InternalSDLocation.URI_SCHEME -> createBuiltInMemLocation(locationUri)
            ExternalStorageLocation.URI_SCHEME -> createExtStorageLocation(locationUri)
            TrueCryptLocation.URI_SCHEME -> createTrueCryptLocation(locationUri)
            VeraCryptLocation.URI_SCHEME -> createVeraCryptLocation(locationUri)
            EncFsLocationBase.URI_SCHEME -> createEncFsLocation(locationUri)
            LUKSLocation.URI_SCHEME -> createLUKSLocation(locationUri)
            ContentResolver.SCHEME_CONTENT -> if (isDocumentTreeUri(
                    context,
                    locationUri
                )
            ) createDocumentTreeLocation(locationUri)
            else createContentResolverLocation(locationUri)

            DocumentTreeLocation.URI_SCHEME -> createDocumentTreeLocation(locationUri)
            else -> null
        }
    }

    val _currentLocations: MutableList<LocationInfo> = ArrayList()

    private val _openedLocationsStack = Stack<String?>()

    internal open inner class FilteredList<E> : AbstractList<E>() {
        override fun size(): Int {
            synchronized(_currentLocations) {
                var res = 0
                for (li in _currentLocations) if (isValid(li.location)) res++
                return res
            }
        }

        override fun get(location: Int): E {
            var res = 0
            synchronized(_currentLocations) {
                for (li in _currentLocations) {
                    if (isValid(li.location)) {
                        if (res == location) return li.location as E
                        res++
                    }
                }
            }
            throw IndexOutOfBoundsException()
        }

        protected open fun isValid(l: Location): Boolean {
            return true
        }
    }

    class LocationInfo internal constructor(var location: Location, var store: Boolean) {
        var isDevice: Boolean = false
    }

    private fun findExistingLocationInfo(locationId: String?): LocationInfo? {
        for (li in _currentLocations) if (li.location.id == locationId) return li
        return null
    }

    @Throws(Exception::class)
    protected fun getLocationIdFromUri(locationUri: Uri): String? {
        val scheme = locationUri.scheme ?: return null
        return when (locationUri.scheme) {
            ContainerBasedLocation.URI_SCHEME -> ContainerBasedLocation.getLocationId(
                this,
                locationUri
            )

            DeviceBasedLocation.URI_SCHEME -> DeviceBasedLocation.getLocationId(locationUri)
            InternalSDLocation.URI_SCHEME -> InternalSDLocation.getLocationId(locationUri)
            ExternalStorageLocation.URI_SCHEME -> ExternalStorageLocation.getLocationId(locationUri)
            TrueCryptLocation.URI_SCHEME -> TrueCryptLocation.getLocationId(
                this,
                locationUri
            )

            VeraCryptLocation.URI_SCHEME -> VeraCryptLocation.getLocationId(
                this,
                locationUri
            )

            EncFsLocationBase.URI_SCHEME -> getLocationId(
                this,
                locationUri
            )

            LUKSLocation.URI_SCHEME -> LUKSLocation.getLocationId(this, locationUri)
            ContentResolver.SCHEME_CONTENT -> if (isDocumentTreeUri(
                    context,
                    locationUri
                )
            ) getLocationId(locationUri)
            else ContentResolverLocation.getLocationId()

            DocumentTreeLocation.URI_SCHEME -> getLocationId(locationUri)
            else -> null
        }
    }

    @Throws(IOException::class)
    private fun findOrCreateDeviceLocation(locationUri: Uri): Location {
        val path = StringPathUtil(locationUri.path)
        var chroot: StringPathUtil = null
        val sdcardPath = StringPathUtil("sdcard")
        var maxComp = 0
        var res: Location? = null
        for (loc in getLoadedLocations(true)) {
            if (loc is InternalSDLocation || loc is ExternalStorageLocation) {
                val tmpChroot = StringPathUtil((loc as DeviceBasedLocation).rootPath)
                if ((loc is InternalSDLocation && sdcardPath.equals(path))) {
                    res = loc
                    chroot = sdcardPath
                    break
                } else if (tmpChroot.equals(path)) {
                    res = loc
                    chroot = tmpChroot
                    break
                }
                if (tmpChroot.components.size > maxComp) {
                    if (loc is InternalSDLocation && sdcardPath.isParentDir(path)) {
                        res = loc
                        maxComp = tmpChroot.components.size
                        chroot = sdcardPath
                    } else if (tmpChroot.isParentDir(path)) {
                        res = loc
                        maxComp = tmpChroot.components.size
                        chroot = tmpChroot
                    }
                }
            }
        }
        if (res != null) {
            res = res.copy()
            res.currentPath = res.fs.getPath(path.getSubPath(chroot).toString())
            return res!!
        }
        return DeviceBasedLocation(_settings, locationUri)
    }

    @Throws(Exception::class)
    private fun createContainerLocation(locationUri: Uri): Location {
        return ContainerBasedLocation(locationUri, this, context, _settings)
    }

    @Throws(Exception::class)
    private fun createTrueCryptLocation(locationUri: Uri): Location {
        return TrueCryptLocation(locationUri, this, context, _settings)
    }

    @Throws(Exception::class)
    private fun createVeraCryptLocation(locationUri: Uri): Location {
        return VeraCryptLocation(locationUri, this, context, _settings)
    }

    @Throws(Exception::class)
    private fun createEncFsLocation(locationUri: Uri): Location {
        return EncFsLocation(locationUri, this, context, _settings)
    }

    @Throws(Exception::class)
    private fun createLUKSLocation(locationUri: Uri): Location {
        return LUKSLocation(locationUri, this, context, _settings)
    }

    @Throws(IOException::class)
    private fun createDeviceLocation(locationUri: Uri): Location {
        /*String pathString = locationUri.getPath();
    if(pathString == null)
    	pathString = "/";
    PathUtil pu = new PathUtil(pathString);
    java.io.File extStore = Environment.getExternalStorageDirectory();
    if(
    		(extStore!=null && new PathUtil(extStore.getPath()).isParentDir(pu)) ||
    		pathString.startsWith("/sdcard/")
    )
    	return new InternalSDLocation(_context, pathString);
    */

        return DeviceBasedLocation(_settings, locationUri)
    }

    @Throws(IOException::class)
    private fun createDeviceRootNPLocation(locationUri: Uri): Location {
        return DeviceRootNPLocation(context, _settings, locationUri)
    }

    @Throws(IOException::class)
    private fun createBuiltInMemLocation(locationUri: Uri): Location {
        return InternalSDLocation(context, locationUri)
    }

    @Throws(IOException::class)
    private fun createExtStorageLocation(locationUri: Uri): Location {
        return ExternalStorageLocation(context, locationUri)
    }

    @Throws(Exception::class)
    private fun createDocumentTreeLocation(uri: Uri): Location {
        return if (DocumentTreeLocation.URI_SCHEME == uri.scheme) fromLocationUri(
            context, uri
        )
        else DocumentTreeLocation(context, uri)
    }

    @Throws(Exception::class)
    private fun createContentResolverLocation(locationUri: Uri): Location {
        return ContentResolverLocation(context, locationUri)
    }

    private fun loadStaticLocations() {
        for (l in loadDeviceLocations()) {
            val li = LocationInfo(l, false)
            li.isDevice = true
            _currentLocations.add(li)
        }
    }

    protected fun loadDeviceLocations(): ArrayList<Location> {
        val res = ArrayList<Location>()
        try {
            var location: Location = DeviceRootNPLocation(context)
            location.externalSettings.isVisibleToUser = true
            res.add(location)
            StorageOptions.reloadStorageList(context)
            for (si in StorageOptions.getStoragesList(
                context
            )) {
                if (si.isExternal) {
                    if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP
                        || File(si.path).isDirectory
                    ) {
                        val extLoc: Location = ExternalStorageLocation(
                            context, si.label, si.path, null
                        )
                        extLoc.fs // pre-create fs to use the same fs instance everywhere
                        extLoc.externalSettings.isVisibleToUser = true
                        res.add(extLoc)
                    }
                } else {
                    location = InternalSDLocation(context, si.label, si.path, null)
                    location.getFS() // pre-create fs to use the same fs instance everywhere
                    location.getExternalSettings().isVisibleToUser = true
                    res.add(location)
                }
            }
        } catch (e: IOException) {
            showAndLog(context, e)
        }
        return res
    }

    protected val context: Context = context.applicationContext
    private var _mediaChangedReceiver: MediaMountedReceiver? = null

    companion object {
        const val PARAM_LOCATION_URIS: String = "com.sovworks.eds.android.LOCATION_URIS"
        const val PARAM_PATHS: String = "com.sovworks.eds.android.PATHS"
        const val PARAM_LOCATION_URI: String = "com.sovworks.eds.android.LOCATION_URI"

        const val BROADCAST_LOCATION_CREATED: String = "com.sovworks.eds.BROADCAST_LOCATION_CREATED"
        const val BROADCAST_LOCATION_REMOVED: String = "com.sovworks.eds.BROADCAST_LOCATION_REMOVED"

        const val BROADCAST_ALL_CONTAINERS_CLOSED: String =
            "com.sovworks.eds.android.BROADCAST_ALL_CONTAINERS_CLOSED"

        const val BROADCAST_CLOSE_ALL: String = "com.sovworks.eds.CLOSE_ALL"
        const val BROADCAST_LOCATION_CHANGED: String =
            "com.sovworks.eds.android.BROADCAST_LOCATION_CHANGED"

        /*public static synchronized LocationsManager getLocationsManager()
  {
  	return getLocationsManager(null, false);
  }*/
        @JvmStatic
		@Synchronized
        fun getLocationsManager(context: Context): LocationsManager? {
            return getLocationsManager(context, true)
        }

        @Synchronized
        fun getLocationsManager(context: Context, create: Boolean): LocationsManager? {
            if (create && _instance == null) {
                _instance =
                    LocationsManager(context.applicationContext, UserSettings.getSettings(context))
                _instance.loadStoredLocations()
                _instance.startMountsMonitor()
            }
            return _instance as LocationsManager?
        }

        @Synchronized
        private fun closeLocationsManager() {
            if (_instance != null) {
                _instance!!.close()
                _instance = null
            }
        }

        @Synchronized
        fun setGlobalLocationsManager(lm: LocationsManagerBase?) {
            if (_instance != null) closeLocationsManager()
            _instance = lm
        }

        @SuppressLint("StaticFieldLeak")
        private var _instance: LocationsManagerBase? = null

        fun storePathsInBundle(b: Bundle, loc: Location, paths: Collection<Path?>?) {
            b.putParcelable(PARAM_LOCATION_URI, loc.locationUri)
            if (paths != null) b.putStringArrayList(PARAM_PATHS, storePaths(paths))
        }

        fun storePathsInIntent(i: Intent, loc: Location, paths: Collection<Path?>?) {
            i.setData(loc.locationUri)
            i.putExtra(PARAM_LOCATION_URI, loc.locationUri)
            if (paths != null) i.putStringArrayListExtra(PARAM_PATHS, storePaths(paths))
        }

        fun storeLocationsInBundle(b: Bundle, locations: Iterable<Location>) {
            val uris = ArrayList<Uri?>()
            for (loc in locations) uris.add(loc.locationUri)
            b.putParcelableArrayList(PARAM_LOCATION_URIS, uris)
        }

        fun storeLocationsInIntent(i: Intent, locations: Iterable<Location>) {
            val b = Bundle()
            storeLocationsInBundle(b, locations)
            i.putExtras(b)
        }

        @Throws(Exception::class)
        fun getLocationsFromBundle(lm: LocationsManagerBase, b: Bundle?): ArrayList<Location> {
            val res = ArrayList<Location>()
            if (b != null) {
                val uris = b.getParcelableArrayList<Uri>(PARAM_LOCATION_URIS)
                if (uris != null) for (uri in uris) res.add(lm.getLocation(uri))
            }
            return res
        }

        @Throws(Exception::class)
        fun getLocationsFromIntent(lm: LocationsManagerBase, i: Intent): ArrayList<Location> {
            val res = getLocationsFromBundle(lm, i.extras)
            if (res.isEmpty() && i.data != null) res.add(lm.getLocation(i.data!!))
            return res
        }

        fun getFromIntent(
            i: Intent, lm: LocationsManagerBase, pathsHolder: MutableCollection<Path?>?
        ): Location? {
            try {
                if (i.data == null) return null
                val loc = lm.getLocation(i.data!!)
                if (pathsHolder != null) {
                    val pathStrings = i.getStringArrayListExtra(PARAM_PATHS)
                    if (pathStrings != null) pathsHolder.addAll(restorePaths(loc.fs, pathStrings))
                }
                return loc
            } catch (e: Exception) {
                log(e)
                return null
            }
        }

        fun getFromBundle(
            b: Bundle?, lm: LocationsManagerBase, pathsHolder: MutableCollection<Path?>?
        ): Location? {
            if (b == null || !b.containsKey(PARAM_LOCATION_URI)) return null
            try {
                val loc = lm.getLocation(b.getParcelable(PARAM_LOCATION_URI)!!)
                if (pathsHolder != null) {
                    val pathStrings = b.getStringArrayList(PARAM_PATHS)
                    if (pathStrings != null) pathsHolder.addAll(restorePaths(loc.fs, pathStrings))
                }
                return loc
            } catch (e: Exception) {
                log(e)
                return null
            }
        }

        val locationRemovedIntentFilter: IntentFilter
            get() = IntentFilter(LocationsManager.BROADCAST_LOCATION_REMOVED)

        val locationAddedIntentFilter: IntentFilter
            get() = IntentFilter(LocationsManager.BROADCAST_LOCATION_CREATED)

        fun broadcastLocationChanged(context: Context, location: Location) {
            val i = Intent(BROADCAST_LOCATION_CHANGED)
            // i.setData(location.getLocationUri());
            i.putExtra(PARAM_LOCATION_URI, location.locationUri)
            context.sendBroadcast(i)
        }

        fun broadcastLocationAdded(context: Context, location: Location?) {
            val i = Intent(BROADCAST_LOCATION_CREATED)
            // i.setData(location.getLocationUri());
            if (location != null) i.putExtra(PARAM_LOCATION_URI, location.locationUri)
            context.sendBroadcast(i)
        }

        fun broadcastLocationRemoved(context: Context, location: Location?) {
            val i = Intent(BROADCAST_LOCATION_REMOVED)
            // i.setData(location.getLocationUri());
            if (location != null) i.putExtra(PARAM_LOCATION_URI, location.locationUri)
            context.sendBroadcast(i)
        }

        @JvmOverloads
        fun broadcastAllContainersClosed(context: Context = _context) {
            context.sendBroadcast(Intent(BROADCAST_ALL_CONTAINERS_CLOSED))
        }

        fun makeUriStrings(locations: Iterable<Location>): ArrayList<String> {
            val list = ArrayList<String>()
            for (l in locations) list.add(l.locationUri.toString())
            return list
        }

        @Throws(IOException::class)
        fun getPathsFromLocations(locations: Iterable<Location>): ArrayList<Path?> {
            val res = ArrayList<Path?>()
            for (loc in locations) res.add(loc.currentPath)
            return res
        }

        fun getStoredLocationUris(settings: Settings): List<Uri> {
            val res = ArrayList<Uri>()
            try {
                val locationStrings: List<String> =
                    Util.loadStringArrayFromString(
                        settings.storedLocations
                    )
                for (p in locationStrings) {
                    try {
                        res.add(Uri.parse(p))
                    } catch (e: Exception) {
                        log(e)
                    }
                }
            } catch (e: JSONException) {
                log(e)
                return res
            }
            return res
        }
    }
}
