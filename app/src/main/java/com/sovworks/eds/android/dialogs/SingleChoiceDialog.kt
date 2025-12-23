package com.sovworks.eds.android.dialogs

import android.app.DialogFragment
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckedTextView
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import com.sovworks.eds.android.R
import com.sovworks.eds.android.helpers.Util

abstract class SingleChoiceDialog<T> : DialogFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Util.setDialogStyle(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle
    ): View? {
        val v = inflater.inflate(layoutId, container)
        v.findViewById<View>(android.R.id.button1).setOnClickListener { view: View? ->
            onNo()
            dialog.dismiss()
        }
        _okButton = v.findViewById(android.R.id.button2)
        _okButton.setOnClickListener(View.OnClickListener { view: View? ->
            onYes()
            dismiss()
        })
        _okButton.setEnabled(false)
        _progressBar = v.findViewById(android.R.id.progress)
        listView = v.findViewById(android.R.id.list)
        (v.findViewById<View>(android.R.id.text1) as TextView).text = title
        if (_progressBar != null) {
            _progressBar!!.visibility = View.VISIBLE
            listView.setVisibility(View.GONE)
        }

        return v
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        onLoadItems()
    }

    protected fun onNo() {
    }

    protected fun onYes() {
        val pos = listView!!.checkedItemPosition
        onItemSelected(pos, listView!!.getItemAtPosition(pos) as T)
    }

    protected val layoutId: Int
        get() = R.layout.single_choice_dialog

    protected abstract fun onItemSelected(position: Int, item: T)
    protected abstract val title: String?
    protected abstract fun onLoadItems()

    protected fun fillList(items: List<T>) {
        val adapter = initAdapter(items)
        listView!!.adapter = adapter
        if (adapter.count > 0 && listView!!.checkedItemPosition < 0) listView!!.setItemChecked(
            0,
            true
        )
        _okButton!!.isEnabled = listView!!.checkedItemPosition >= 0
        if (_progressBar != null) {
            _progressBar!!.visibility = View.GONE
            listView!!.visibility = View.VISIBLE
        }
    }

    protected fun initAdapter(items: List<T>): ArrayAdapter<T> {
        return object :
            ArrayAdapter<T>(activity, android.R.layout.simple_list_item_single_choice, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val tv = super.getView(position, convertView, parent) as CheckedTextView
                tv.setOnClickListener { v: View? ->
                    this.listView.setItemChecked(position, true)
                    _okButton!!.isEnabled = true
                }
                tv.isChecked = this.listView.isItemChecked(position)
                return tv
            }
        }
    }

    protected var listView: ListView? = null
        private set
    private var _progressBar: ProgressBar? = null
    private var _okButton: Button? = null
}
