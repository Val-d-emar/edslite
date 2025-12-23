package com.sovworks.eds.android.dialogs

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import com.sovworks.eds.android.R
import com.sovworks.eds.android.helpers.Util
import com.sovworks.eds.android.settings.activities.OpeningOptionsActivity
import com.sovworks.eds.android.views.EditSB
import com.sovworks.eds.crypto.SecureBuffer
import com.sovworks.eds.locations.LocationsManager
import com.sovworks.eds.locations.Openable
import com.trello.rxlifecycle2.components.RxDialogFragment

abstract class PasswordDialogBase : RxDialogFragment() {
    interface PasswordReceiver {
        fun onPasswordEntered(dlg: PasswordDialog?)
        fun onPasswordNotEntered(dlg: PasswordDialog?)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Util.setDialogStyle(this)
        _location = LocationsManager.getLocationsManager
        (activity).getFromBundle
        (arguments, null) as Openable?
        options = savedInstanceState ?: arguments
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle
    ): View? {
        val v = inflater.inflate(R.layout.password_dialog, container)
        _labelTextView = v.findViewById(R.id.label)
        if (_labelTextView != null) {
            val label = loadLabel()
            if (label != null) {
                _labelTextView!!.text = label
                _labelTextView!!.visibility = View.VISIBLE
            } else _labelTextView!!.visibility = View.GONE
        }
        _passwordEditText = v.findViewById(R.id.password_et)
        _repeatPasswordEditText = v.findViewById(R.id.repeat_password_et)

        if (_passwordEditText != null) {
            if (hasPassword()) {
                _passwordResult = SecureBuffer.reserveChars(50)
                _passwordEditText!!.visibility = View.VISIBLE
                _passwordEditText!!.setSecureBuffer(_passwordResult)
            } else {
                _passwordResult = null
                _passwordEditText!!.visibility = View.GONE
            }
        } else _passwordResult = null

        if (_repeatPasswordEditText != null) {
            if (hasPassword() && isPasswordVerificationRequired) {
                _repeatPasswordSB = SecureBuffer.reserveChars(50)
                _repeatPasswordEditText!!.visibility = View.VISIBLE
                _repeatPasswordEditText!!.setSecureBuffer(_repeatPasswordSB)
            } else {
                _repeatPasswordSB = null
                _repeatPasswordEditText!!.visibility = View.GONE
            }
        } else _repeatPasswordSB = null

        val passwordLayout = v.findViewById<View>(R.id.password_layout)
        if (passwordLayout != null) {
            if (hasPassword()) {
                passwordLayout.visibility = View.VISIBLE
                _passwordEditText.requestFocus()
                /*lifecycle().
                        filter(event -> event == FragmentEvent.RESUME).
                        subscribe(event -> {
                            final InputMethodManager imm = (InputMethodManager) _passwordEditText.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                            if(imm != null)
                                imm.showSoftInput(_passwordEditText, InputMethodManager.SHOW_FORCED);
                            //_passwordEditText.requestFocus();
                        });*/
            } else passwordLayout.visibility =
                if (hasPassword()) View.VISIBLE else View.GONE
        }

        val b = v.findViewById<Button>(android.R.id.button1)
        b?.setOnClickListener { view: View? -> confirm() }

        var ib = v.findViewById<ImageButton>(R.id.toggle_show_pass)
        ib?.setOnClickListener { v12: View ->
            toggleShowPassword(
                v12 as ImageButton
            )
        }

        ib = v.findViewById(R.id.settings)
        if (ib != null) {
            if (_location == null) ib.visibility = View.GONE
            ib.setOnClickListener { v1: View? -> openOptions() }
        }
        return v
    }


    override fun onDestroyView() {
        super.onDestroyView()
        if (_passwordResult != null) {
            _passwordResult!!.close()
            _passwordResult = null
        }
        if (_repeatPasswordSB != null) {
            _repeatPasswordSB!!.close()
            _repeatPasswordSB = null
        }
    }

    override fun onResume() {
        super.onResume()
        setWidthHeight()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        when (requestCode) {
            REQUEST_OPTIONS -> if (resultCode == Activity.RESULT_OK) options = data.extras
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    val password: CharArray?
        get() {
            if (hasPassword() && _passwordEditText != null) {
                val pwd = _passwordEditText!!.text
                val res = CharArray(pwd!!.length)
                pwd.getChars(0, res.size, res, 0)
                return res
            }
            return null
        }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (options != null) outState.putAll(options)
    }

    open fun hasPassword(): Boolean {
        val args = arguments
        return args != null && args.getBoolean(
            ARG_HAS_PASSWORD,
            _location != null && _location!!.hasPassword()
        )
    }

    protected var _labelTextView: TextView? = null
    protected var _passwordEditText: EditSB? = null
    protected var _repeatPasswordEditText: EditSB? = null
    protected var _location: Openable? = null
    var options: Bundle? = null
        protected set

    protected var _passwordResult: SecureBuffer? = null
    protected var _repeatPasswordSB: SecureBuffer? = null

    protected fun setWidthHeight() {
        val w = dialog.window
        w?.setLayout(calcWidth(), calcHeight())
    }

    protected fun calcWidth(): Int {
        return resources.getDimensionPixelSize(R.dimen.password_dialog_width)
    }

    protected fun calcHeight(): Int {
        return LayoutParams.WRAP_CONTENT
        /*int height = getResources().getDimensionPixelSize(R.dimen.password_dialog_height);
        if(isPasswordVerificationRequired())
            height += 80;
        return height;*/
    }

    protected open fun loadLabel(): String? {
        val args = arguments
        return args?.getString(ARG_LABEL)
    }

    protected val isPasswordVerificationRequired: Boolean
        get() {
            val args = arguments
            return args != null && args.getBoolean(
                ARG_VERIFY_PASSWORD,
                false
            )
        }

    protected fun toggleShowPassword(b: ImageButton) {
        val inputType = _passwordEditText!!.inputType
        if ((inputType and EditorInfo.TYPE_MASK_VARIATION) == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) {
            _passwordEditText!!.inputType =
                EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_PASSWORD
            if (_repeatPasswordEditText != null) _repeatPasswordEditText!!.inputType =
                EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_PASSWORD
            b.setImageResource(R.drawable.ic_show_pass)
        } else {
            _passwordEditText!!.inputType =
                EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            if (_repeatPasswordEditText != null) _repeatPasswordEditText!!.inputType =
                EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            b.setImageResource(R.drawable.ic_hide_pass)
        }
    }

    protected fun openOptions() {
        val i = Intent(activity, OpeningOptionsActivity::class.java)
        if (_location != null) LocationsManager.storePathsInIntent(i, _location, null)
        i.putExtras(options!!)
        startActivityForResult(i, REQUEST_OPTIONS)
    }

    protected val resultReceiver: PasswordReceiver?
        get() {
            val args = arguments
            val recTag =
                args?.getString(ARG_RECEIVER_FRAGMENT_TAG)
            return if (recTag != null) fragmentManager.findFragmentByTag(recTag) as PasswordReceiver else null
        }

    protected fun checkInput(): Boolean {
        if (hasPassword() && isPasswordVerificationRequired) {
            if (!checkPasswordsMatch()) {
                Toast.makeText(activity, R.string.password_does_not_match, Toast.LENGTH_LONG).show()
                return false
            }
        }
        return true
    }

    protected fun confirm() {
        if (!checkInput()) return

        onPasswordEntered()
        dismiss()
    }

    protected fun checkPasswordsMatch(): Boolean {
        return _passwordEditText!!.text == _repeatPasswordEditText!!.text
    }

    protected open fun onPasswordEntered() {
        val r = resultReceiver
        if (r != null) r.onPasswordEntered(this as PasswordDialog)
        else {
            val act = activity
            if (act is PasswordReceiver) (act as PasswordReceiver).onPasswordEntered(this as PasswordDialog)
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        onPasswordNotEntered()
    }

    protected open fun onPasswordNotEntered() {
        val r = resultReceiver
        if (r != null) r.onPasswordNotEntered(this as PasswordDialog)
        else {
            val act = activity
            if (act is PasswordReceiver) (act as PasswordReceiver).onPasswordNotEntered(this as PasswordDialog)
        }
    }

    companion object {
        const val TAG: String = "com.sovworks.eds.android.dialogs.PasswordDialog"

        const val ARG_LABEL: String = "com.sovworks.eds.android.LABEL"
        const val ARG_VERIFY_PASSWORD: String = "com.sovworks.eds.android.VERIFY_PASSWORD"
        const val ARG_HAS_PASSWORD: String = "com.sovworks.eds.android.HAS_PASSWORD"
        const val ARG_RECEIVER_FRAGMENT_TAG: String =
            "com.sovworks.eds.android.RECEIVER_FRAGMENT_TAG"

        protected const val REQUEST_OPTIONS: Int = 1
    }
}
