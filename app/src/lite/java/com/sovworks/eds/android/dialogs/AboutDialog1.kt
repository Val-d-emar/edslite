package com.sovworks.eds.android.dialogs

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.sovworks.eds.android.Logger.Companion.showAndLog
import com.sovworks.eds.android.R
import com.sovworks.eds.settings.GlobalConfig

class AboutDialog : AboutDialogBase() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle
    ): View? {
        val v = super.onCreateView(inflater, container, savedInstanceState) ?: return null
        v.findViewById<View>(R.id.donation_button)
            .setOnClickListener { openDonationsPage() }

        v.findViewById<View>(R.id.check_source_code_button)
            .setOnClickListener { openSourceCodePage() }

        v.findViewById<View>(R.id.check_full_version_button)
            .setOnClickListener { openFullVersionPage() }
        return v
    }

    private fun openDonationsPage() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GlobalConfig.DONATIONS_URL)))
        } catch (e: Exception) {
            showAndLog(activity, e)
        }
    }

    private fun openFullVersionPage() {
        try {
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_VIEW, Uri.parse(GlobalConfig.FULL_VERSION_URL)),
                    "Select application"
                )
            )
        } catch (e: Exception) {
            showAndLog(activity, e)
        }
    }

    private fun openSourceCodePage() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GlobalConfig.SOURCE_CODE_URL)))
        } catch (e: Exception) {
            showAndLog(activity, e)
        }
    }
}
