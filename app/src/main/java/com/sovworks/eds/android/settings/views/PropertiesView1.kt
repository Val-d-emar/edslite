package com.sovworks.eds.android.settings.views

import android.app.Fragment
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.util.SparseArray
import android.widget.LinearLayout
import com.sovworks.eds.android.settings.PropertyEditor
import com.sovworks.eds.android.settings.PropertyEditor.Host
import java.util.Collections

class PropertiesView : LinearLayout {
    constructor(context: Context?) : super(context)

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        for (pe in listDisplayedProperties()) {
            if (pe.onActivityResult(requestCode, resultCode, data)) return true
        }
        return false
    }

    fun addProperty(pe: PropertyEditor): Int {
        if (pe.id == 0) pe.id = _properties.size
        val pi = PropertyInfo()
        pi.isEnabled = true
        pi.property = pe
        _properties[pe.id] = pi
        if (pe.startPosition == 0) pe.startPosition = _properties.size
        displayProperty(pe)
        return pe.id
    }

    @Throws(Exception::class)
    fun saveProperties() {
        for (pe in listDisplayedProperties()) pe.save()
    }

    fun saveProperties(b: Bundle) {
        for (pe in _properties.values) pe.property!!.save(b)
    }

    fun loadProperties(b: Bundle?) {
        loadProperties(currentlyEnabledProperties, b)
    }

    fun beginUpdate() {
        isLoadingProperties = true
    }

    fun endUpdate(b: Bundle?) {
        isLoadingProperties = false
        commitProperties()
        if (!_propertiesToLoad.isEmpty()) {
            val next = ArrayList(_propertiesToLoad)
            _propertiesToLoad.clear()
            loadProperties(next, b)
        }
    }

    fun loadProperties(ids: Iterable<Int>, b: Bundle?) {
        val props = ArrayList<PropertyEditor?>()
        for (id in ids) props.add(getPropertyById(id))
        loadProperties(props, b)
    }

    @JvmOverloads
    fun loadProperties(
        properties: Collection<PropertyEditor> = currentlyEnabledProperties,
        b: Bundle? = null
    ) {
        if (isLoadingProperties) return
        beginUpdate()
        try {
            for (pe in properties) {
                val v = pe.getView(this)
                v!!.id = pe.id
                if (b == null) pe.load()
                else pe.load(b)
            }
        } finally {
            endUpdate(b)
        }
    }

    fun isPropertyEnabled(propertyId: Int): Boolean {
        val pi = _properties[propertyId]
        return pi != null && pi.isEnabled
    }

    @Synchronized
    fun getEnabledPropertyById(propertyId: Int): PropertyEditor? {
        val pi = _properties[propertyId]
        return if (pi != null && pi.isEnabled) pi.property else null
    }

    @Synchronized
    fun getPropertyById(propertyId: Int): PropertyEditor? {
        val pi = _properties[propertyId]
        return pi?.property
    }

    @Synchronized
    fun getPropertyByType(type: Class<out PropertyEditor?>): PropertyEditor? {
        for (pi in _properties.values) if (pi.property!!.javaClass == type) return pi.property
        return null
    }

    @Synchronized
    fun setPropertyState(propertyId: Int, enabled: Boolean) {
        val pi = _properties[propertyId]
        requireNotNull(pi) { "Property not found. id=$propertyId" }
        setPropertyState(pi, enabled)
        if (!isLoadingProperties) commitProperties()
    }

    @Suppress("unused")
    fun removeProperty(propertyId: Int) {
        val pi = _properties[propertyId]
        requireNotNull(pi) { "Property not found. id=$propertyId" }
        removeView(pi.property!!.getView(this))
        _properties.remove(propertyId)
    }

    @Synchronized
    fun setPropertiesState(ids: Iterable<Int>, enabled: Boolean) {
        for (id in ids) {
            val pi = _properties[id]
            requireNotNull(pi) { "Property not found. id=$id" }
            setPropertyState(pi, enabled)
        }
        if (!isLoadingProperties) commitProperties()
    }

    @Synchronized
    fun setPropertiesState(enabled: Boolean) {
        for (pi in _properties.values) setPropertyState(pi, enabled)
        if (!isLoadingProperties) commitProperties()
    }

    override fun dispatchSaveInstanceState(container: SparseArray<Parcelable>?) {
        if (container != null) {
            val b = Bundle()
            saveProperties(b)
            container.put(id, b)
        }
    }

    override fun dispatchRestoreInstanceState(container: SparseArray<Parcelable>?) {
        if (container != null) {
            val b = container[id] as Bundle
            if (b != null) loadProperties(b)
        }
    }

    private class PropertyInfo {
        var property: PropertyEditor? = null
        var isEnabled: Boolean = false
    }

    var isLoadingProperties: Boolean = false
        private set
    var isInstantSave: Boolean = false
        set(val) {
            field = `val`
        }
    private val _properties: MutableMap<Int, PropertyInfo> = HashMap()
    private val _propertiesToLoad: MutableList<PropertyEditor?> = ArrayList()

    private val _comparator =
        java.util.Comparator<PropertyEditor> { lhs, rhs ->
            lhs.startPosition.compareTo(
                rhs.startPosition
            )
        }

    private fun setPropertyState(pi: PropertyInfo, enabled: Boolean) {
        pi.isEnabled = enabled
        if (isLoadingProperties) {
            _propertiesToLoad.remove(pi.property)
            _propertiesToLoad.add(pi.property)
        }
    }

    private fun commitProperties() {
        val toRemove = ArrayList<PropertyEditor>()
        val added: MutableSet<Int> = HashSet()
        for (pe in listDisplayedProperties()) {
            if (!_properties[pe.id]!!.isEnabled) toRemove.add(pe)
            added.add(pe.id)
        }
        for (pe in toRemove) removeView(pe.getView(this))
        for (pi in _properties.values) if (pi.isEnabled && !added.contains(pi.property.getId())) displayProperty(
            pi.property!!
        )
    }

    @get:Synchronized
    private val currentlyEnabledProperties: ArrayList<PropertyEditor>
        get() {
            val res =
                ArrayList<PropertyEditor>()
            for (pi in _properties.values) if (pi.isEnabled) res.add(
                pi.property!!
            )
            return res
        }

    private fun listDisplayedProperties(): List<PropertyEditor> {
        val pes = ArrayList<PropertyEditor>()
        for (i in 0..<childCount) pes.add(getChildAt(i).tag as PropertyEditor)
        return pes
    }

    private fun displayProperty(pe: PropertyEditor) {
        val curList = listDisplayedProperties()
        var pos = Collections.binarySearch(curList, pe, _comparator)
        if (pos < 0) pos = -pos - 1
        val v = pe.getView(this)
        v!!.tag = pe
        v.setOnClickListener { pe.onClick() }
        addView(v, pos)
    }

    companion object {
        @Synchronized
        fun newId(): Int {
            return ++_ID_COUNTER
        }

        fun getHost(f: Fragment): Host {
            val host =
                if (f.arguments.containsKey(PropertyEditor.Companion.ARG_HOST_FRAGMENT_TAG)) f.fragmentManager.findFragmentByTag(
                    f.arguments.getString(PropertyEditor.Companion.ARG_HOST_FRAGMENT_TAG)
                ) as Host
                else f.activity as Host
            return host
        }

        fun getProperty(f: Fragment): PropertyEditor? {
            val host = getHost(f)
            return if (host == null || f.arguments == null || !f.arguments.containsKey(
                    PropertyEditor.Companion.ARG_PROPERTY_ID
                )
            )
                null
            else
                host.propertiesView.getPropertyById(f.arguments.getInt(PropertyEditor.Companion.ARG_PROPERTY_ID))
        }

        private var _ID_COUNTER = 1
    }
}
