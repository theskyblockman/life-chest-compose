package fr.theskyblockman.lifechest.explorer.file_readers.utils

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable

@Composable
fun InteractiveImage(
    modifier: Modifier = Modifier,
    bitmap: ImageBitmap,
    fileName: String,
    isFullscreen: Boolean,
    contentScale: ContentScale = ContentScale.Fit,
    onClick: () -> Unit,
) {
    val interactionSource = remember {
        MutableInteractionSource()
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = if (isFullscreen) Modifier.background(Color.Black) else Modifier
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = "Image $fileName",
            modifier = modifier
                .zoomable(rememberZoomState())
                .clickable(indication = null, interactionSource = interactionSource) {
                    onClick()
                }
                .fillMaxSize(),
            contentScale = contentScale,
        )
    }
}