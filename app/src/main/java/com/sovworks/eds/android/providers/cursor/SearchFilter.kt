package com.sovworks.eds.android.providers.cursor

import com.sovworks.eds.android.helpers.CachedPathInfo
import com.sovworks.eds.locations.Location
import io.reactivex.functions.Predicate

interface SearchFilter {
    val name: String
    fun getChecker(location: Location?, arg: String?): Predicate<CachedPathInfo?>?
}
