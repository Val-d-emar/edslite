package com.sovworks.eds.android.providers

import android.annotation.TargetApi
import android.os.Build.VERSION_CODES

@TargetApi(VERSION_CODES.KITKAT)
object ContainersDocumentProvider : ContainersDocumentProviderBase() {
    const val AUTHORITY: String = "com.sovworks.eds.android.providers.documents.lite"
}
