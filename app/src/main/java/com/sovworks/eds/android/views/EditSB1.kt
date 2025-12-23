package com.sovworks.eds.android.views

import android.content.Context
import android.text.Editable
import android.text.Editable.Factory
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText
import com.sovworks.eds.crypto.EditableSecureBuffer
import com.sovworks.eds.crypto.SecureBuffer
import java.nio.CharBuffer

class EditSB @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle
) :
    AppCompatEditText(context, attrs, defStyleAttr) {
    init {
        isSaveEnabled = false
    }

    override fun setText(text: CharSequence, type: BufferType) {
        val et = editableText
        et?.clear()
        super.setText(text, type)
    }

    /*@Override
    public Parcelable onSaveInstanceState()
    {
        Parcelable p = super.onSaveInstanceState();
        EditableSecureBuffer e = getEditableSB();
        SecureBuffer sb = e == null ? null : e.getSecureBuffer();
        return new State(p, sb);

    }

    @Override
    public void onRestoreInstanceState(Parcelable state)
    {
        //State s = (State)state;
        super.onRestoreInstanceState(s._parent);
        //setSecureBuffer(s._buf);

    }

    private static class State implements Parcelable
    {
        State(Parcelable parent, SecureBuffer cur)
        {
            _parent = parent;
            _buf = cur;
        }

        protected State(Parcel in)
        {
            _parent = in.readParcelable(ClassLoader.getSystemClassLoader());
            _buf = in.readParcelable(ClassLoader.getSystemClassLoader());
        }

        public static final Creator<State> CREATOR = new Creator<State>()
        {
            @Override
            public State createFromParcel(Parcel in)
            {
                return new State(in);
            }

            @Override
            public State[] newArray(int size)
            {
                return new State[size];
            }
        };

        @Override
        public int describeContents()
        {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags)
        {
            dest.writeParcelable(_parent, 0);
            dest.writeParcelable(_buf, 0);
        }

        private Parcelable _parent;
        private SecureBuffer _buf;
    }
*/
    fun setSecureBuffer(sb: SecureBuffer?) {
        setEditableFactory(object : Factory() {
            override fun newEditable(source: CharSequence): Editable {
                if (sb != null) {
                    sb.adoptData(CharBuffer.wrap(source))
                    return EditableSecureBuffer(sb)
                }
                return super.newEditable(source)
            }
        })
    }


    val editableSB: EditableSecureBuffer?
        get() {
            val et = editableText
            return if (et is EditableSecureBuffer) et else null
        }
}
