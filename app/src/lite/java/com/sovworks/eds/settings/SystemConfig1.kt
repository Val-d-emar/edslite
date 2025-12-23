package com.sovworks.eds.settings

abstract class SystemConfig : SystemConfigCommon() {
    companion object {
        var instance: SystemConfig? = null
    }
}
