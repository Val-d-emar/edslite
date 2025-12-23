package com.sovworks.eds.android.providers.cursor

import com.sovworks.eds.locations.Location

// full version compatibility
class SelectionChecker(
    location: Location,
    selectionString: String?,
    selectionArgs: Array<String?>?
) :
    SelectionCheckerBase(location, selectionString, selectionArgs)
