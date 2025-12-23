package com.sovworks.eds.android.filemanager.tasks

import android.content.Context
import com.sovworks.eds.locations.Location

class CreateNewFile internal constructor(
    context: Context,
    location: Location,
    fileName: String,
    fileType: Int,
    returnExisting: Boolean
) :
    CreateNewFileBase(context, location, fileName, fileType, returnExisting)
