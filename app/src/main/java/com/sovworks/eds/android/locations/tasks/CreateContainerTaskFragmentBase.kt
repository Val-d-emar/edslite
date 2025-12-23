package com.sovworks.eds.android.locations.tasks

import com.sovworks.eds.android.R
import com.sovworks.eds.android.errors.UserException
import com.sovworks.eds.container.ContainerFormatInfo
import com.sovworks.eds.container.ContainerFormatter
import com.sovworks.eds.container.ContainerFormatterBase
import com.sovworks.eds.container.EDSLocationFormatter
import com.sovworks.eds.container.EdsContainer
import com.sovworks.eds.crypto.SecureBuffer
import com.sovworks.eds.fs.FileSystemInfo
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.Openable

abstract class CreateContainerTaskFragmentBase : CreateEDSLocationTaskFragment() {
    override fun createFormatter(): EDSLocationFormatter {
        return ContainerFormatter()
    }

    @Throws(Exception::class)
    override fun initFormatter(
        state: TaskState,
        formatter: EDSLocationFormatter,
        password: SecureBuffer?
    ) {
        super.initFormatter(state, formatter, password)
        val args = arguments
        val cf = formatter as ContainerFormatterBase
        cf.setContainerFormat(getContainerFormatByName(args.getString(ARG_CONTAINER_FORMAT)))
        cf.setContainerSize(args.getInt(ARG_SIZE) * 1024L * 1024L)
        cf.setNumKDFIterations(args.getInt(Openable.PARAM_KDF_ITERATIONS, 0))
        val fst = args.getParcelable<FileSystemInfo>(ARG_FILE_SYSTEM_TYPE)
        if (fst != null) cf.setFileSystemType(fst)
        val encAlgName = args.getString(CreateEDSLocationTaskFragmentBase.Companion.ARG_CIPHER_NAME)
        val encModeName = args.getString(ARG_CIPHER_MODE_NAME)
        if (encAlgName != null && encModeName != null) cf.setEncryptionEngine(
            encAlgName,
            encModeName
        )
        val hashAlgName = args.getString(ARG_HASHING_ALG)
        if (hashAlgName != null) cf.setHashFunc(hashAlgName)
        cf.enableFreeSpaceRand(args.getBoolean(ARG_FILL_FREE_SPACE))
    }

    @Throws(Exception::class)
    override fun checkParams(state: TaskState, locationLocation: Location): Boolean {
        val args = arguments
        val path = locationLocation.currentPath
        if (path.exists() && path.isDirectory) throw UserException(
            _context,
            R.string.container_file_name_is_not_specified
        )
        if (args.getInt(ARG_SIZE) < 1) throw UserException(
            activity,
            R.string.err_container_size_is_too_small
        )

        if (!arguments.getBoolean(
                CreateEDSLocationTaskFragmentBase.Companion.ARG_OVERWRITE,
                false
            )
        ) {
            if (path.exists()
                && path.isFile
                && path.file.size > 0
            ) {
                state.setResult(CreateEDSLocationTaskFragmentBase.Companion.RESULT_REQUEST_OVERWRITE)
                return false
            }
        }
        return true
    }

    companion object {
        fun getContainerFormatByName(name: String?): ContainerFormatInfo? {
            for (ci in EdsContainer.getSupportedFormats()) if (ci.formatName == name) return ci
            return null
        }

        const val ARG_CONTAINER_FORMAT: String = "com.sovworks.eds.android.CONTAINER_FORMAT"
        const val ARG_CIPHER_MODE_NAME: String = "com.sovworks.eds.android.CIPHER_MODE_NAME"
        const val ARG_HASHING_ALG: String = "com.sovworks.eds.android.HASHING_ALG"
        const val ARG_SIZE: String = "com.sovworks.eds.android.SIZE"
        const val ARG_FILL_FREE_SPACE: String = "com.sovworks.eds.android.FILL_FREE_SPACE"
        const val ARG_FILE_SYSTEM_TYPE: String = "com.sovworks.eds.android.FILE_SYSTEM_TYPE"
    }
}
