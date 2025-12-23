package com.sovworks.eds.android.filemanager.dialogs

import android.annotation.SuppressLint
import android.app.AlertDialog.Builder
import android.app.Dialog
import android.app.DialogFragment
import android.app.FragmentManager
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.TextView
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.R
import com.sovworks.eds.android.filemanager.fragments.FileListViewFragment
import com.sovworks.eds.android.filemanager.fragments.FileListViewFragmentBase
import com.sovworks.eds.fs.Path
import com.sovworks.eds.fs.util.PathUtil
import com.sovworks.eds.locations.LocationsManager
import io.reactivex.Single
import io.reactivex.SingleEmitter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.Consumer
import io.reactivex.schedulers.Schedulers

class DeleteConfirmationDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle): Dialog {
        val args = arguments
        val paths = ArrayList<Path>()
        val loc = LocationsManager.getLocationsManager(
            activity
        ).getFromBundle(args, paths)
        val wipe = args.getBoolean(FileListViewFragmentBase.ARG_WIPE_FILES, true)

        val builder = Builder(activity)
        val inflater =
            builder.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                ?: throw RuntimeException("Inflater is null")

        @SuppressLint("InflateParams") val v =
            inflater.inflate(R.layout.delete_confirmation_dialog, null)
        val tv = v.findViewById<TextView>(android.R.id.text1)
        tv.text = getString(R.string.do_you_really_want_to_delete_selected_files, "...")
        builder.setView(v)

        builder //.setMessage(getActivity().getString(R.string.do_you_really_want_to_delete_selected_files, fn))
            .setCancelable(true)
            .setPositiveButton(
                R.string.yes
            ) { dialog: DialogInterface, id: Int ->
                val frag =
                    fragmentManager.findFragmentByTag(FileListViewFragment.TAG) as FileListViewFragment
                frag?.deleteFiles(loc, paths, wipe)
                dialog.dismiss()
            }
            .setNegativeButton(
                R.string.no
            ) { dialog: DialogInterface, id: Int -> dialog.cancel() }

        Single.create<String?> { c: SingleEmitter<String?> ->
            var fn: String? = ""
            if (loc != null) {
                fn = if (paths.size > 1) paths.size.toString()
                else if (paths.isEmpty()) PathUtil.getNameFromPath(loc.currentPath)
                else PathUtil.getNameFromPath(paths[0])
            }
            c.onSuccess(fn!!)
        }.subscribeOn
        (Schedulers.io()).observeOn
        (AndroidSchedulers.mainThread()).subscribe
        (
                Consumer { fn: String? ->
                    tv.text =
                        getString(R.string.do_you_really_want_to_delete_selected_files, fn)
                },
        Consumer { err: Throwable? ->
            Logger.showAndLog(
                activity.applicationContext, err
            )
        }
        )
        return builder.create()
    }

    companion object {
        const val TAG: String = "DeleteConfirmationDialog"
        @JvmStatic
		fun showDialog(fm: FragmentManager?, args: Bundle?) {
            val newFragment: DialogFragment = DeleteConfirmationDialog()
            newFragment.arguments = args
            newFragment.show(fm, TAG)
        }
    }
}
