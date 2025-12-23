package com.sovworks.eds.android.locations.tasks

import com.sovworks.eds.android.R
import com.sovworks.eds.android.errors.UserException
import com.sovworks.eds.container.EDSLocationFormatter
import com.sovworks.eds.container.EncFsFormatter
import com.sovworks.eds.crypto.SecureBuffer
import com.sovworks.eds.fs.encfs.Config
import com.sovworks.eds.fs.util.PathUtil
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.Openable

class CreateEncFsTaskFragment : CreateEDSLocationTaskFragment() {
    override fun createFormatter(): EDSLocationFormatter {
        return EncFsFormatter()
    }

    @Throws(Exception::class)
    override fun initFormatter(
        state: TaskState,
        formatter: EDSLocationFormatter,
        password: SecureBuffer?
    ) {
        super.initFormatter(state, formatter, password)
        val args = arguments
        val cf = formatter as EncFsFormatter
        cf.setDataCodecName(args.getString(CreateEDSLocationTaskFragmentBase.Companion.ARG_CIPHER_NAME))
        cf.setNameCodecName(args.getString(ARG_NAME_CIPHER_NAME))
        val c = cf.config
        c.keySize = args.getInt(ARG_KEY_SIZE)
        c.blockSize = args.getInt(ARG_BLOCK_SIZE)
        c.kdfIterations = args.getInt(Openable.PARAM_KDF_ITERATIONS)
        c.macBytes = args.getInt(ARG_MAC_BYTES)
        c.macRandBytes = args.getInt(ARG_RAND_BYTES)
        c.useUniqueIV(args.getBoolean(ARG_UNIQUE_IV))
        c.useExternalFileIV(args.getBoolean(ARG_EXTERNAL_IV))
        c.useChainedNameIV(args.getBoolean(ARG_CHAINED_NAME_IV))
        c.allowHoles(args.getBoolean(ARG_ALLOW_EMPTY_BLOCKS))
    }

    @Throws(Exception::class)
    override fun checkParams(state: TaskState, locationLocation: Location): Boolean {
        val args = arguments
        var path = locationLocation.currentPath
        if (path!!.isFile) path = path.parentPath
        if (path == null || !path.isDirectory) throw UserException(
            _context,
            R.string.wrong_encfs_root_path
        )
        if (!args.getBoolean(CreateEDSLocationTaskFragmentBase.Companion.ARG_OVERWRITE, false)) {
            val cfgPath = PathUtil.buildPath(path, Config.CONFIG_FILENAME)
            if (cfgPath != null && cfgPath.isFile
                && cfgPath.file.size > 0
            ) {
                state.setResult(CreateEDSLocationTaskFragmentBase.Companion.RESULT_REQUEST_OVERWRITE)
                return false
            }
        }

        return true
    }

    companion object {
        const val TAG: String = "com.sovworks.eds.android.locations.tasks.CreateEncFsTaskFragment"

        const val ARG_NAME_CIPHER_NAME: String = "com.sovworks.eds.android.NAME_CIPHER_NAME"
        const val ARG_KEY_SIZE: String = "com.sovworks.eds.android.KEY_SIZE"
        const val ARG_BLOCK_SIZE: String = "com.sovworks.eds.android.BLOCK_SIZE"
        const val ARG_MAC_BYTES: String = "com.sovworks.eds.android.MAC_BYTES"
        const val ARG_RAND_BYTES: String = "com.sovworks.eds.android.RAND_BYTES"
        const val ARG_UNIQUE_IV: String = "com.sovworks.eds.android.UNIQUE_IV"
        const val ARG_EXTERNAL_IV: String = "com.sovworks.eds.android.EXTERNAL_IV"
        const val ARG_CHAINED_NAME_IV: String = "com.sovworks.eds.android.CHAINED_NAME_IV"
        const val ARG_ALLOW_EMPTY_BLOCKS: String = "com.sovworks.eds.android.ALLOW_EMPTY_BLOCKS"
    }
}
