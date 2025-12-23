package com.sovworks.eds.android.service

import android.content.Intent
import com.sovworks.eds.android.R
import com.sovworks.eds.android.filemanager.activities.FileManagerActivity
import com.sovworks.eds.android.filemanager.activities.FileManagerActivityBase.Companion.getOverwriteRequestIntent
import com.sovworks.eds.android.helpers.ExtendedFileInfoLoader.Companion.instance
import com.sovworks.eds.android.helpers.WipeFilesTask.Companion.wipeFile
import com.sovworks.eds.android.helpers.WipeFilesTask.ITask
import com.sovworks.eds.fs.Directory
import com.sovworks.eds.fs.FSRecord
import com.sovworks.eds.fs.File
import com.sovworks.eds.fs.errors.NoFreeSpaceLeftException
import com.sovworks.eds.fs.util.SrcDstCollection
import com.sovworks.eds.fs.util.SrcDstCollection.SrcDst
import org.json.JSONException
import java.io.IOException

internal class MoveFilesTask : CopyFilesTask() {
    override fun getErrorMessage(ex: Throwable?): String {
        return _context!!.getString(R.string.move_failed)
    }

    @Throws(IOException::class, JSONException::class)
    override fun getOverwriteRequestIntent(filesToOverwrite: SrcDstCollection?): Intent {
        return FileManagerActivity.getOverwriteRequestIntent(
            _context,
            true,
            filesToOverwrite
        )
    }

    @Throws(Exception::class)
    override fun processSrcDstCollection(col: SrcDstCollection) {
        super.processSrcDstCollection(col)
        for (dir in _foldersToDelete) deleteEmptyDir(dir)
    }

    @Throws(Exception::class)
    override fun processRecord(record: SrcDst): Boolean {
        try {
            val srcLocation = record.srcLocation
            val dstLocation = record.dstLocation
                ?: throw IOException("Failed to determine destination location for " + srcLocation.locationUri)
            _wipe = !srcLocation.isEncrypted && dstLocation.isEncrypted
            if (tryMove(record)) return true
            copyFiles(record)
            val srcPath = srcLocation.currentPath
            if (srcPath.isDirectory) _foldersToDelete.add(0, srcPath.directory)
        } catch (e: NoFreeSpaceLeftException) {
            throw com.sovworks.eds.android.errors.NoFreeSpaceLeftException(_context)
        } catch (e: IOException) {
            setError(e)
        }
        return true
    }

    @Throws(IOException::class)
    private fun tryMove(srcDst: SrcDst): Boolean {
        val srcLocation = srcDst.srcLocation
        val srcPath = srcLocation.currentPath
        val dstLocation = srcDst.dstLocation
            ?: throw IOException("Failed to determine destination location for " + srcLocation.locationUri)
        val dstPath = dstLocation.currentPath
        if (srcPath.fileSystem === dstPath.fileSystem) {
            if (srcPath.isFile) {
                if (tryMove(srcPath.file, dstPath.directory)) {
                    instance.discardCache(srcLocation, srcPath)
                    return true
                }
            } else if (srcPath.isDirectory) return tryMove(srcPath.directory, dstPath.directory)
        }
        return false
    }

    @Throws(IOException::class)
    private fun tryMove(srcFile: FSRecord, newParent: Directory): Boolean {
        try {
            val dstRec = calcDstPath(srcFile, newParent)
            if (dstRec != null) {
                if (dstRec.exists()) return false
            }
            srcFile.moveTo(newParent)
            return true
        } catch (e: UnsupportedOperationException) {
            return false
        }
    }

    @Throws(IOException::class)
    override fun copyFile(record: SrcDst): Boolean {
        if (super.copyFile(record)) {
            val srcLoc = record.srcLocation
            instance.discardCache(srcLoc, srcLoc.currentPath)
            return true
        }
        return false
    }

    @Throws(IOException::class)
    override fun copyFile(srcFile: File, dstFile: File): Boolean {
        if (super.copyFile(srcFile, dstFile)) {
            deleteFile(srcFile)
            return true
        }
        return false
    }

    @Throws(IOException::class)
    private fun deleteFile(file: File) {
        wipeFile(
            file,
            _wipe,
            object : ITask {
                override fun progress(sizeInc: Int) {
                    //incProcessedSize(sizeInc);
                }

                override fun cancel(): Boolean {
                    return isCancelled
                }
            }
        )
    }

    @Throws(IOException::class)
    private fun deleteEmptyDir(startDir: Directory): Boolean {
        val dc = startDir.list()
        try {
            if (dc.iterator().hasNext()) return false
        } finally {
            dc.close()
        }
        startDir.delete()
        return true
    }

    /*
	protected boolean deleteEmptyDirsRec(Directory startDir) throws IOException
	{
		Directory.Contents dc = startDir.list();
		try
		{
			for(Path p: dc)
			{
				if(p.isDirectory())
				{
					if(!deleteEmptyDirsRec(p.getDirectory()))
						return false;
				}
				else
					return false;
			}
		}
		finally
		{
			dc.close();
		}
		startDir.delete();
		return true;
	}
*/
    private var _wipe = false
    private val _foldersToDelete: List<Directory> = ArrayList()
}