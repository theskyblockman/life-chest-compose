package fr.theskyblockman.lifechest.explorer.file_readers.utils

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.github.panpf.zoomimage.ZoomImage
import com.github.panpf.zoomimage.compose.ZoomState
import com.github.panpf.zoomimage.compose.rememberZoomState
import com.github.panpf.zoomimage.subsampling.ImageSource
import com.github.panpf.zoomimage.subsampling.fromContent
import fr.theskyblockman.lifechest.R

@Composable
fun InteractiveImage(
    modifier: Modifier = Modifier,
    bitmap: ImageBitmap,
    uri: Uri?,
    fileName: String,
    isFullscreen: Boolean,
    contentScale: ContentScale = ContentScale.Fit,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val zoomState: ZoomState = rememberZoomState()
    LaunchedEffect(context, zoomState, uri) {
        if (uri != null) {
            zoomState.subsampling.setImageSource(ImageSource.fromContent(context, uri))
        }
    }

    val painter = remember {
        BitmapPainter(bitmap)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = if (isFullscreen) Modifier.background(Color.Black) else Modifier
    ) {
        ZoomImage(
            painter = painter,
            contentDescription = stringResource(R.string.image_alt_text, fileName),
            modifier = modifier
                .fillMaxSize(),
            contentScale = contentScale,
            onTap = {
                onClick()
            },
            zoomState = zoomState
        )
    }
}