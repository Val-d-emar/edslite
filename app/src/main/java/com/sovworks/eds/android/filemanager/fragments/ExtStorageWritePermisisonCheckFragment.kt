package com.sovworks.eds.android.filemanager.fragments

import android.Manifest.permission
import android.annotation.TargetApi
import android.content.pm.PackageManager
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.sovworks.eds.android.dialogs.AskPrimaryStoragePermissionDialog.Companion.showDialog
import com.trello.rxlifecycle2.android.FragmentEvent
import com.trello.rxlifecycle2.android.FragmentEvent.RESUME
import com.trello.rxlifecycle2.components.RxActivity
import com.trello.rxlifecycle2.components.RxFragment
import io.reactivex.Completable
import io.reactivex.functions.Consumer
import io.reactivex.functions.Predicate
import io.reactivex.subjects.CompletableSubject

class ExtStorageWritePermisisonCheckFragment : RxFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle().filter
        (Predicate<FragmentEvent> { event: FragmentEvent -> event == RESUME }).firstElement
        ().subscribe
        (Consumer { event: FragmentEvent? -> requestExtStoragePermission() })
    }

    @TargetApi(VERSION_CODES.M)
    fun requestExtStoragePermission() {
        requestPermissions(
            arrayOf(
                permission.READ_EXTERNAL_STORAGE,
                permission.WRITE_EXTERNAL_STORAGE
            ),
            REQUEST_EXT_STORAGE_PERMISSIONS
        )
    }

    fun cancelExtStoragePermissionRequest() {
        _extStoragePermissionCheckSubject.onComplete()
        fragmentManager.beginTransaction().remove(this).commitAllowingStateLoss()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_EXT_STORAGE_PERMISSIONS) {
            if ((grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                        grantResults[1] == PackageManager.PERMISSION_GRANTED) ||
                !requestExtStoragePermissionWithRationale()
            ) {
                _extStoragePermissionCheckSubject.onComplete()
                fragmentManager.beginTransaction().remove(this).commitAllowingStateLoss()
            }
        }
    }

    private val _extStoragePermissionCheckSubject = CompletableSubject.create()


    @TargetApi(VERSION_CODES.M)
    private fun requestExtStoragePermissionWithRationale(): Boolean {
        if (shouldShowRequestPermissionRationale(
                permission.READ_EXTERNAL_STORAGE
            )
            || shouldShowRequestPermissionRationale(
                permission.WRITE_EXTERNAL_STORAGE
            )
        ) {
            showDialog(fragmentManager)
            return true
        }
        return false
    }

    companion object {
        const val TAG: String =
            "com.sovworks.eds.android.filemanager.fragments.ExtStorageWritePermisisonCheckFragment"

        @JvmStatic
        fun getObservable(activity: RxActivity): Completable {
            if (VERSION.SDK_INT < VERSION_CODES.M || (ContextCompat.checkSelfPermission(
                    activity,
                    permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
                        && ContextCompat.checkSelfPermission(
                    activity,
                    permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED)
            ) return Completable.complete()

            val fm = activity.fragmentManager
            var f = fm.findFragmentByTag(TAG) as ExtStorageWritePermisisonCheckFragment
            if (f == null) {
                f = ExtStorageWritePermisisonCheckFragment()
                activity.fragmentManager.beginTransaction().add(f, TAG).commit()
            }
            return f._extStoragePermissionCheckSubject
        }

        private const val REQUEST_EXT_STORAGE_PERMISSIONS = 1
    }
}
