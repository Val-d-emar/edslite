package com.sovworks.eds.android.dialogs

import android.app.FragmentManager
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.TextView
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.R
import com.sovworks.eds.android.helpers.Util
import com.sovworks.eds.android.service.FileOpsService
import com.sovworks.eds.fs.util.PathUtil
import com.sovworks.eds.fs.util.SrcDstCollection
import com.sovworks.eds.fs.util.SrcDstCollection.SrcDst
import com.sovworks.eds.fs.util.SrcDstPlain
import com.sovworks.eds.locations.Location
import com.trello.rxlifecycle2.android.FragmentEvent
import com.trello.rxlifecycle2.android.FragmentEvent.RESUME
import com.trello.rxlifecycle2.components.RxDialogFragment
import io.reactivex.Single
import io.reactivex.SingleEmitter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Consumer
import io.reactivex.functions.Predicate
import io.reactivex.schedulers.Schedulers
import java.io.IOException
import java.util.concurrent.CancellationException

class AskOverwriteDialog : RxDialogFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Util.setDialogStyle(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle
    ): View? {
        val v = inflater.inflate(R.layout.ask_overwrite_dialog, container)
        _textView = v.findViewById(R.id.askOverwriteDialogText)
        v.findViewById<View>(R.id.askOverwriteDialogSkipButton)
            .setOnClickListener { arg0: View? -> skipRecord() }
        v.findViewById<View>(R.id.askOverwriteDialogOverwriteButton)
            .setOnClickListener { arg0: View? -> overwriteRecord() }
        (v.findViewById<View>(R.id.applyToAllCheckBox) as CheckBox).setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean ->
            _applyToAll = isChecked
        }
        return v
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        _selectedPaths = if (savedInstanceState == null)
            SrcDstPlain()
        else
            savedInstanceState.getParcelable<Parcelable>(ARG_SELECTED_PATHS) as SrcDstPlain?
        var paths = arguments.getParcelable<SrcDstCollection>(ARG_PATHS)
        if (paths == null) paths = SrcDstPlain()
        _applyToAll = savedInstanceState != null && savedInstanceState.getBoolean(ARG_APPLY_TO_ALL)
        _numProc = savedInstanceState?.getInt(ARG_NUM_PROC) ?: 0
        _pathsIter = paths.iterator()
        for (i in 0..<_numProc) _next = _pathsIter.next()

        lifecycle().filter
        (Predicate<FragmentEvent> { event: FragmentEvent -> event == RESUME }).firstElement
        ().subscribe
        (Consumer { res: FragmentEvent? -> askNextRecord() }, { err ->
            if (err !is CancellationException) Logger.log(err)
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        try {
            outState.putParcelable(ARG_SELECTED_PATHS, _selectedPaths)
            outState.putBoolean(ARG_APPLY_TO_ALL, _applyToAll)
            outState.putInt(ARG_NUM_PROC, _numProc)
        } catch (e: Exception) {
            Logger.showAndLog(activity, e)
        }
    }

    private var _selectedPaths: SrcDstPlain? = null
    private var _pathsIter: Iterator<SrcDst>? = null
    private var _numProc = 0
    private var _applyToAll = false
    private var _textView: TextView? = null
    private var _next: SrcDst? = null

    private fun overwriteRecord() {
        _selectedPaths!!.add(_next)
        while (_applyToAll && _pathsIter!!.hasNext()) {
            _selectedPaths!!.add(_pathsIter!!.next())
            _numProc++
        }
        askNextRecord()
    }

    private fun skipRecord() {
        while (_applyToAll && _pathsIter!!.hasNext()) {
            _pathsIter!!.next()
            _numProc++
        }
        askNextRecord()
    }

    private fun askNextRecord() {
        try {
            if (!_pathsIter!!.hasNext()) {
                if (arguments.getBoolean(ARG_MOVE, false)) FileOpsService.moveFiles(
                    activity, _selectedPaths, true
                )
                else FileOpsService.copyFiles(activity, _selectedPaths, true)

                dismiss()
            } else {
                cancelLoadTask()
                _next = _pathsIter!!.next()
                _numProc++
                loadFileName(_next!!.srcLocation, _next!!.dstLocation)
            }
        } catch (e: IOException) {
            Logger.showAndLog(activity, e)
        }
    }

    private fun setText(srcName: String?, dstName: String?) {
        _textView!!.text = getString(
            R.string.file_already_exists,
            srcName,
            dstName
        )
    }

    private class Names {
        var srcName: String? = null
        var dstName: String? = null
    }

    private var _observer: Disposable? = null

    @Synchronized
    private fun cancelLoadTask() {
        if (_observer != null) {
            _observer!!.dispose()
            _observer = null
        }
    }

    @Synchronized
    private fun loadFileName(srcLoc: Location, dstLoc: Location) {
        val context = activity.applicationContext
        _observer = Single.create { emitter: SingleEmitter<Names?> ->
            val res = Names()
            res.srcName = PathUtil.getNameFromPath(srcLoc.currentPath)
            res.dstName = dstLoc.currentPath.pathDesc
            emitter.onSuccess(res)
        }.subscribeOn
        (Schedulers.io()).observeOn
        (AndroidSchedulers.mainThread()).compose<Names>
        (bindToLifecycle<Names>()).subscribe
        (Consumer { res: Names ->
            setText(
                res.srcName,
                res.dstName
            )
        }, { err ->
            if (err !is CancellationException) Logger.showAndLog(context, err)
        })
    }

    companion object {
        fun showDialog(
            fm: FragmentManager?,
            move: Boolean,
            records: SrcDstCollection?
        ) {
            val args = Bundle()
            args.putBoolean(ARG_MOVE, move)
            args.putParcelable(ARG_PATHS, records)
            showDialog(fm, args)
        }

        fun showDialog(
            fm: FragmentManager?,
            args: Bundle?
        ) {
            val d = AskOverwriteDialog()
            d.arguments = args
            d.show(fm, TAG)
        }

        const val TAG: String = "com.sovworks.eds.android.dialogs.AskOverwriteDialog"

        const val ARG_MOVE: String = "move"
        const val ARG_PATHS: String = "paths"
        private const val ARG_SELECTED_PATHS = "selected_paths"
        private const val ARG_APPLY_TO_ALL = "apply_to_all"
        private const val ARG_NUM_PROC = "num_proc"
    }
}
