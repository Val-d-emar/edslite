package com.sovworks.eds.container

import android.annotation.SuppressLint
import android.content.Context
import android.os.Parcel
import com.sovworks.eds.android.errors.UserException
import com.sovworks.eds.android.locations.ContainerBasedLocation
import com.sovworks.eds.android.locations.LUKSLocation
import com.sovworks.eds.android.locations.TrueCryptLocation
import com.sovworks.eds.android.locations.VeraCryptLocation
import com.sovworks.eds.crypto.FileEncryptionEngine
import com.sovworks.eds.crypto.SecureBuffer
import com.sovworks.eds.exceptions.ApplicationException
import com.sovworks.eds.fs.File.AccessMode.ReadWrite
import com.sovworks.eds.fs.FileSystemInfo
import com.sovworks.eds.fs.Path
import com.sovworks.eds.fs.RandomAccessIO
import com.sovworks.eds.fs.fat.FATInfo
import com.sovworks.eds.fs.fat.FatFS
import com.sovworks.eds.locations.ContainerLocation
import com.sovworks.eds.locations.EDSLocation
import com.sovworks.eds.locations.Location
import com.sovworks.eds.settings.Settings
import com.sovworks.eds.truecrypt.FormatInfo
import com.sovworks.eds.truecrypt.StdLayout
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom

abstract class ContainerFormatterBase : EDSLocationFormatter {
    protected constructor(`in`: Parcel) : super(`in`) {
        var s = `in`.readString()
        if (s != null) _containerFormat = getContainerFormatByName(s)
        _containerSize = `in`.readLong()
        _randFreeSpace = `in`.readByte().toInt() != 0
        s = `in`.readString()
        val s2 = `in`.readString()
        if (s != null && s2 != null) setEncryptionEngine(s, s2)
        s = `in`.readString()
        if (s != null) setHashFunc(s)
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        super.writeToParcel(dest, flags)
        dest.writeString(if (_containerFormat == null) null else _containerFormat.getFormatName())
        dest.writeLong(_containerSize)
        dest.writeByte((if (_randFreeSpace) 1 else 0).toByte())
        if (_encryptionEngine != null) {
            dest.writeString(_encryptionEngine!!.cipherName)
            dest.writeString(_encryptionEngine!!.cipherModeName)
        } else {
            dest.writeString(null)
            dest.writeString(null)
        }
        dest.writeString(if (_hashFunc != null) _hashFunc!!.algorithm else null)
    }

    protected constructor()

    fun setContainerFormat(containerFormat: ContainerFormatInfo?) {
        _containerFormat = containerFormat
    }

    fun setNumKDFIterations(num: Int) {
        _numKDFIterations = num
    }

    fun setEncryptionEngine(encAlgName: String, hashFuncName: String) {
        setEncryptionEngine(
            VolumeLayoutBase.Companion.findCipher(
                layout.getSupportedEncryptionEngines(),
                encAlgName,
                hashFuncName
            ) as FileEncryptionEngine
        )
    }

    fun setEncryptionEngine(engine: FileEncryptionEngine?) {
        _encryptionEngine = engine
    }

    fun setHashFunc(name: String) {
        setHashFunc(VolumeLayoutBase.Companion.findHashFunc(layout.getSupportedHashFuncs(), name))
    }

    fun setHashFunc(md: MessageDigest?) {
        _hashFunc = md
    }

    fun setContainerSize(containerSize: Long) {
        _containerSize = containerSize
    }

    fun enableFreeSpaceRand(`val`: Boolean) {
        _randFreeSpace = `val`
    }

    fun setFileSystemType(fsInfo: FileSystemInfo) {
        _fileSystemType = fsInfo
    }

    protected var _containerFormat: ContainerFormatInfo? = null
    protected var _fileSystemType: FileSystemInfo = FATInfo()
    protected var _encryptionEngine: FileEncryptionEngine? = null
    protected var _hashFunc: MessageDigest? = null
    protected var _containerSize: Long = 0
    protected var _randFreeSpace: Boolean = false
    protected var _numKDFIterations: Int = 0

    @Throws(IOException::class, ApplicationException::class, UserException::class)
    override fun createLocation(location: Location): EDSLocation {
        checkNotNull(_containerFormat) { "Container format is not specified" }
        check(_containerSize >= 1024L * 1024L) { "Container size is too small" }

        val layout = layout
        setVolumeLayoutPassword(layout!!)
        if (_numKDFIterations > 0) layout.setNumKDFIterations(_numKDFIterations)
        if (_encryptionEngine != null) layout.engine = _encryptionEngine
        if (_hashFunc != null) layout.hashFunc = _hashFunc

        /*Path contPath = location.getCurrentPath();
		if(!contPath.isFile())
		{
			Path parentPath = contPath.getParentPath();
			if(parentPath!=null)
			{
				String fn = PathUtil.getNameFromPath(contPath);
				if(fn!=null)
					parentPath.getDirectory().createFile(fn);
			}
		}*/
        val io = getIO(location)
        try {
            format(io, layout)
        } finally {
            io.close()
        }
        return createContainerBasedLocation(location, layout)
    }

    @Throws(IOException::class)
    protected fun getIO(targetLocation: Location): RandomAccessIO {
        return targetLocation.currentPath.file.getRandomAccessIO(ReadWrite)
    }

    @Throws(IOException::class)
    protected fun createContainerBasedLocation(
        containerLocation: Location,
        layout: VolumeLayout?
    ): ContainerLocation {
        val cont = getEdsContainer(containerLocation.currentPath, layout)
        return createBaseContainerLocationFromFormatInfo(
            containerLocation,
            cont,
            context,
            settings
        )
    }

    protected fun createBaseContainerLocationFromFormatInfo(
        containerLocation: Location?,
        cont: EdsContainer?,
        context: Context?,
        settings: Settings?
    ): ContainerLocation {
        return createBaseContainerLocationFromFormatInfo(
            _containerFormat.getFormatName(),
            containerLocation,
            cont,
            context,
            settings
        )
    }

    protected fun getEdsContainer(pathToContainer: Path?, layout: VolumeLayout?): EdsContainer {
        return EdsContainer(pathToContainer, _containerFormat, layout)
    }

    @Throws(IOException::class, ApplicationException::class, UserException::class)
    protected fun format(io: RandomAccessIO, layout: VolumeLayout?) {
        if (layout is StdLayout) formatTCBasedContainer(
            io,
            (_containerFormat as FormatInfo?)!!,
            layout,
            _containerSize,
            _randFreeSpace
        )
        else if (layout is com.sovworks.eds.luks.VolumeLayout) formatLUKSContainer(
            io,
            (_containerFormat as com.sovworks.eds.luks.FormatInfo?)!!,
            layout,
            _containerSize,
            _randFreeSpace
        )
    }

    @Throws(IOException::class, ApplicationException::class)
    protected fun formatLUKSContainer(
        io: RandomAccessIO,
        containerFormat: com.sovworks.eds.luks.FormatInfo,
        layout: com.sovworks.eds.luks.VolumeLayout,
        containerSize: Long,
        randFreeSpace: Boolean
    ) {
        if (randFreeSpace) {
            io.seek(0)
            fillFreeSpace(io, containerSize)
        } else {
            io.seek(containerSize - 1)
            io.write(0)
        }
        layout.initNew()
        //setContainerSize should be called after initNew
        layout.setContainerSize(containerSize)
        containerFormat.formatContainer(io, layout, _fileSystemType)
    }

    protected val layout: VolumeLayout?
        get() = _containerFormat.getVolumeLayout()

    @Throws(IOException::class)
    protected fun setVolumeLayoutPassword(layout: VolumeLayout) {
        if (_password != null) {
            val pass = _password.dataArray
            layout.setPassword(
                EdsContainerBase.Companion.cutPassword(
                    pass, _containerFormat.getMaxPasswordLength()
                )
            )
            SecureBuffer.eraseData(pass)
        } else layout.setPassword(ByteArray(0))
    }

    protected fun setHints(cont: ContainerLocation) {
        cont.externalSettings.containerFormatName = _containerFormat.getFormatName()
        if (_encryptionEngine != null) cont.externalSettings.encEngineName =
            VolumeLayoutBase.Companion.getEncEngineName(
                _encryptionEngine!!
            )
        if (_hashFunc != null) cont.externalSettings.hashFuncName = _hashFunc!!.algorithm
    }

    @Throws(ApplicationException::class, IOException::class)
    override fun setExternalContainerSettings(loc: EDSLocation) {
        setHints(loc as ContainerLocation)
        super.setExternalContainerSettings(loc)
    }

    @Throws(IOException::class, ApplicationException::class)
    protected fun formatTCBasedContainer(
        io: RandomAccessIO,
        containerFormat: FormatInfo,
        layout: StdLayout,
        containerSize: Long,
        randFreeSpace: Boolean
    ) {
        if (randFreeSpace) {
            io.seek(0)
            fillFreeSpace(io, containerSize)
        } else {
            io.setLength(containerSize)
            //io.seek(containerSize - 1);
            //io.write(0);
        }
        layout.setContainerSize(containerSize)
        layout.initNew()
        containerFormat.formatContainer(io, layout, _fileSystemType)
    }


    @SuppressLint("TrulyRandom")
    @Throws(IOException::class)
    protected fun fillFreeClustersWithRandomData(fat: FatFS) {
        val f = fat.containerFile
        val clusterTable = fat.clusterTable
        val buf = ByteArray(fat.sectorsPerCluster * fat.bytesPerSector)
        val rand = SecureRandom()
        for (i in 2..<clusterTable.size) {
            if (clusterTable[i] == 0) {
                rand.nextBytes(buf)
                f.seek(fat.getClusterOffset(i))
                f.write(buf, 0, buf.size)
            }
            if (!reportProgress((i * 100 / clusterTable.size).toByte())) break
        }
    }

    @SuppressLint("TrulyRandom")
    @Throws(IOException::class)
    protected fun fillFreeSpace(f: RandomAccessIO, size: Long) {
        val r = SecureRandom()
        val buf = ByteArray(512)
        var i: Long = 0
        while (i < size) {
            r.nextBytes(buf)
            f.write(buf, 0, buf.size)
            if (!reportProgress((i * 100 / size).toByte())) break
            i += buf.size.toLong()
        }
    }

    private fun getContainerFormatByName(name: String): ContainerFormatInfo? {
        for (ci in EdsContainer.getSupportedFormats()) if (ci.formatName == name) return ci
        return null
    }

    companion object {
        fun createBaseContainerLocationFromFormatInfo(
            formatName: String,
            containerLocation: Location?,
            cont: EdsContainer?,
            context: Context?,
            settings: Settings?
        ): ContainerLocation {
            return when (formatName) {
                com.sovworks.eds.luks.FormatInfo.formatName -> LUKSLocation(
                    containerLocation,
                    cont,
                    context,
                    settings
                )

                FormatInfo.FORMAT_NAME -> TrueCryptLocation(
                    containerLocation,
                    cont,
                    context,
                    settings
                )

                com.sovworks.eds.veracrypt.FormatInfo.formatName -> VeraCryptLocation(
                    containerLocation,
                    cont,
                    context,
                    settings
                )

                else -> ContainerBasedLocation(containerLocation, cont, context, settings)
            }
        }
    }
}
