package com.zynergylabs.forager.app.ui.track

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.theme.Spacing

/**
 * Records > Tracks > "Import GPX" (plan T16, the owner's "Button + Share/Open with"): opens Android's own
 * file picker (`ACTION_OPEN_DOCUMENT`, which needs no permission) for a GPX file, and hands the picked
 * file to [onPicked]. Closing the picker without a file does nothing, as backing out of any picker does.
 */
@Composable
internal fun ImportGpxButton(onPicked: (Uri) -> Unit, modifier: Modifier = Modifier) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(onPicked) }
    OutlinedButton(
        onClick = { launcher.launch(GPX_PICKER_MIME_TYPES) },
        modifier = modifier.testTag(IMPORT_GPX_BUTTON_TAG),
    ) {
        Icon(Icons.Filled.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(IMPORT_GPX_LABEL, modifier = Modifier.padding(start = Spacing.sm))
    }
}

internal const val IMPORT_GPX_LABEL = "Import GPX"
internal const val IMPORT_GPX_BUTTON_TAG = "import-gpx-button"

/**
 * What the picker offers: the types the Open with filters take (the owner's list in -636). Many providers
 * type a .gpx file as `application/octet-stream`, so that is offered too; a file that is not GPX is then
 * refused with "This file couldn't be read as GPX".
 */
internal val GPX_PICKER_MIME_TYPES: Array<String> = arrayOf("application/gpx+xml", "application/octet-stream", "text/xml", "application/xml")
