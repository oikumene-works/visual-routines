package io.github.ewoc2026.visualroutines

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loads only the requested display size; an unreadable image has no frame. */
@Composable
internal fun rememberStepImagePainter(source: RoutineImageSource, assetId: String, maxSide: Int): Painter? {
    if (source == RoutineImageSource.BUNDLED) {
        return BundledImageLibrary.resolve(assetId)?.let { painterResource(it.drawableResourceId) }
    }
    val application = LocalContext.current.applicationContext as? VisualRoutinesApplication
    val loaded by produceState<Pair<String, Painter>?>(null, application, assetId, maxSide) {
        value = null
        value = withContext(Dispatchers.IO) {
            application?.importedImages?.decode(assetId, maxSide)?.let {
                assetId to BitmapPainter(it.asImageBitmap())
            }
        }
    }
    return loaded?.takeIf { it.first == assetId }?.second
}
