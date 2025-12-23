package com.sovworks.eds.android.providers.cursor

import com.sovworks.eds.android.helpers.CachedPathInfo
import com.sovworks.eds.locations.Location
import io.reactivex.functions.Predicate
import java.util.Arrays

open class SelectionCheckerBase(
    protected val _location: Location,
    selectionString: String?,
    selectionArgs: Array<String?>?
) :
    Predicate<CachedPathInfo?> {
    @Throws(Exception::class)
    override fun test(cachedPathInfo: CachedPathInfo?): Boolean {
        for (pc in _filters) if (!pc.test(
                cachedPathInfo!!
            )
        ) return false
        return true
    }

    val _filters: MutableList<Predicate<CachedPathInfo?>> = ArrayList()

    init {
        if (selectionString != null) {
            val filtNames =
                selectionString.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            var i = 0
            for (filtName in filtNames) {
                if (selectionArgs == null || i >= selectionArgs.size) break
                val f = getFilter(filtName, selectionArgs[i++])
                //if (f == null)
                //    throw new IllegalArgumentException("Unsupported search filter: " + filtName);
                //else
                if (f != null) _filters.add(f)
            }
        }
    }

    protected val allFilters: Collection<SearchFilter>
        get() = Arrays.asList(*ALL_FILTERS)

    private fun getFilter(filtName: String, arg: String?): Predicate<CachedPathInfo?>? {
        for (f in allFilters) if (f.name == filtName) return f.getChecker(_location, arg)
        return null
    }

    companion object {
        private val ALL_FILTERS = arrayOf<SearchFilter>()
    }
}
