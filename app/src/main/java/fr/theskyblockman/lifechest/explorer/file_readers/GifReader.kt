package fr.theskyblockman.lifechest.explorer.file_readers

import android.content.Context
import android.os.Build.VERSION.SDK_INT
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import coil.request.ImageResult
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import fr.theskyblockman.lifechest.explorer.ExplorerViewModel
import fr.theskyblockman.lifechest.vault.EncryptedContentProvider
import fr.theskyblockman.lifechest.vault.FileNode

class GifReader(override val node: FileNode) : FileReader {
    private var imageResult: ImageResult? = null

    @Composable
    override fun Reader(fullscreen: Boolean, setFullscreen: (isFullscreen: Boolean) -> Unit) {
        val interactionSource = remember {
            MutableInteractionSource()
        }

        if (imageResult == null || imageResult!!.drawable == null) {
            var modifier = Modifier.fillMaxSize()
            if (fullscreen) {
                modifier = modifier.background(Color.Black)
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = modifier
            ) {
                Text("Could not decode GIF file")
            }

            return
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = if (fullscreen) Modifier.background(Color.Black) else Modifier
        ) {
            Image(
                painter = rememberDrawablePainter(imageResult!!.drawable!!),
                contentDescription = "Image ${node.name}",
                modifier = Modifier
                    .clickable(indication = null, interactionSource = interactionSource) {
                        setFullscreen(!fullscreen)
                    }
                    .fillMaxSize(),
            )
        }
    }

    override suspend fun load(context: Context, explorerViewModel: ExplorerViewModel) {
        imageResult = ImageLoader.Builder(context)
            .components {
                if (SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
            .execute(
                ImageRequest.Builder(context)
                    .data(EncryptedContentProvider.getUriFromFile(node))
                    .build()
            )
    }

    override fun unload() {
    }
}