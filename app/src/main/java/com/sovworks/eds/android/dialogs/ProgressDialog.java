package com.sovworks.eds.android.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.sovworks.eds.android.R;
import com.sovworks.eds.android.helpers.Util;
import com.trello.rxlifecycle3.components.support.RxDialogFragment;

public class ProgressDialog extends RxDialogFragment {
    public static final String TAG = "ProgressDialog";
    public static final String ARG_TITLE = "com.sovworks.eds.android.TITLE";

    public static ProgressDialog showDialog(FragmentManager fm, String title) {
        Bundle args = new Bundle();
        args.putString(ARG_TITLE, title);
        ProgressDialog d = new ProgressDialog();
        d.setArguments(args);
        d.show(fm, TAG);
        return d;
    }

    public void setProgress(int progress) {
        if (_progressBar != null) {
            _progressBar.setProgress(progress);
        }
    }

    public void setTitle(CharSequence title) {
        if (_titleTextView != null) {
            _titleTextView.setText(title);
        }
    }

    public void setText(CharSequence text) {
        if (_statusTextView != null) {
            _statusTextView.setText(text);
        }
    }

    protected String getTitle() {
        return getArguments().getString(ARG_TITLE);
    }

    public void setOnCancelListener(DialogInterface.OnCancelListener listener) {
        _cancelListener = listener;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Util.setDialogStyle(this);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        LayoutInflater inflater = (LayoutInflater) requireActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (inflater == null) {
            throw new RuntimeException("Inflater is null");
        }
        View v = inflater.inflate(R.layout.progress_dialog, null);
        _statusTextView = v.findViewById(android.R.id.text2);
        _progressBar = v.findViewById(android.R.id.progress);

        MaterialAlertDialogBuilder alert = new MaterialAlertDialogBuilder(requireActivity(), R.style.ThemeOverlay_Catalog_MaterialAlertDialog_Centered_FullWidthButtons);
        alert.setView(v);
        alert.setTitle(getTitle());
        alert.setNegativeButton(android.R.string.cancel, (dialog, whichButton) -> {
            dialog.dismiss();
        });
        return alert.create();
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        super.onCancel(dialog);
        if (_cancelListener != null) {
            _cancelListener.onCancel(dialog);
        }
    }

    private DialogInterface.OnCancelListener _cancelListener;
    private TextView _statusTextView, _titleTextView;
    private ProgressBar _progressBar;


}
