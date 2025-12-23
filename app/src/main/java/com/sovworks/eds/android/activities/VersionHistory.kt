package com.sovworks.eds.android.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import com.sovworks.eds.android.R.id
import com.sovworks.eds.android.R.layout
import com.sovworks.eds.android.R.string
import com.sovworks.eds.android.helpers.Util
import com.sovworks.eds.android.settings.UserSettings
import com.sovworks.eds.android.settings.UserSettingsCommon

class VersionHistory : Activity() {
    public override fun onCreate(savedInstanceState: Bundle?) {
        Util.setTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(layout.changes_dialog)
        //setStyle(STYLE_NO_TITLE, R.style.Dialog);
        markAsRead()
        val vw = findViewById<WebView>(id.changesWebView)
        vw.loadData(getString(string.changes_text), "text/html; charset=UTF-8", null)
        //vw.setBackgroundColor(0);
        //Spanned sp = Html.fromHtml( getString(R.string.promo_text));
        //((TextView)v.findViewById(R.id.promoTextView)).setText(sp);
        //tv.setText(sp);
        //((TextView)v.findViewById(R.id.promoTextView)).setText(Html.fromHtml(getString(R.string.promo_text)));
        findViewById<View>(id.okButton).setOnClickListener { v: View? -> finish() }
    }

    @SuppressLint("CommitPrefEdits", "ApplySharedPref")
    private fun markAsRead() {
        var vc = 135
        //if(!GlobalConfig.isDebug())
        try {
            vc = packageManager.getPackageInfo(packageName, 0).versionCode
        } catch (ignored: Exception) {
        }
        val s = UserSettings.getSettings(this)
        val edit = s.sharedPreferences.edit()
        edit.putInt(UserSettingsCommon.LAST_VIEWED_CHANGES, vc)
        edit.commit()
    }
}
