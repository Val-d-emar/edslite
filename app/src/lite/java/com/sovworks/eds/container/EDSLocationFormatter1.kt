package com.sovworks.eds.container

import android.os.Parcel

abstract class EDSLocationFormatter : EDSLocationFormatterBase {
    constructor()

    protected constructor(`in`: Parcel) : super(`in`)
}
