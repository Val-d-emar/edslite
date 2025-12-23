package com.sovworks.eds.container

import com.sovworks.eds.android.locations.EncFsLocation
import com.sovworks.eds.android.settings.UserSettings
import com.sovworks.eds.crypto.SecureBuffer
import com.sovworks.eds.exceptions.ApplicationException
import com.sovworks.eds.fs.encfs.AlgInfo
import com.sovworks.eds.fs.encfs.Config
import com.sovworks.eds.fs.encfs.DataCodecInfo
import com.sovworks.eds.fs.encfs.FS
import com.sovworks.eds.fs.encfs.NameCodecInfo
import com.sovworks.eds.locations.EDSLocation
import com.sovworks.eds.locations.Location
import java.io.IOException

class EncFsFormatter : EDSLocationFormatter() {
    fun setDataCodecName(name: String?) {
        _dataCodecName = name
    }

    fun setNameCodecName(name: String?) {
        _nameCodecName = name
    }

    protected var _dataCodecName: String? = null
    protected var _nameCodecName: String? = null
    val config: Config = Config()

    init {
        config.initNew(_context)
    }

    @Throws(IOException::class, ApplicationException::class)
    override fun createLocation(location: Location): EDSLocation {
        var targetPath = location.currentPath
        if (targetPath.isFile) {
            val tmp = targetPath.parentPath
            if (tmp != null) {
                targetPath = tmp
                location.currentPath = targetPath
            }
        }
        /*
		if(!targetPath.isDirectory())
		{
			Path parentPath = targetPath.getParentPath();
			if(parentPath!=null)
			{
				String fn = PathUtil.getNameFromPath(targetPath);
				if(fn!=null)
					parentPath.getDirectory().createDirectory(fn);
			}
		}*/
        if (_dataCodecName != null) config.dataCodecInfo = findInfoByName(
            config, FS.getSupportedDataCodecs(), _dataCodecName!!
        ) as DataCodecInfo
        if (_nameCodecName != null) config.nameCodecInfo = findInfoByName(
            config, FS.getSupportedNameCodecs(), _nameCodecName!!
        ) as NameCodecInfo
        val pd = if (_password == null) ByteArray(0) else _password.dataArray
        try {
            return EncFsLocation(
                location,
                FS(targetPath, config, pd),
                _context,
                UserSettings.getSettings(_context)
            )
        } finally {
            SecureBuffer.eraseData(pd)
        }
    }

    companion object {
        fun findInfoByName(
            config: Config?,
            supportedAlgs: Iterable<AlgInfo>,
            name: String
        ): AlgInfo {
            for (info in supportedAlgs) {
                if (name == info.name) return info.select(config)
            }
            throw IllegalArgumentException("Unsupported codec: $name")
        }
    }
}
