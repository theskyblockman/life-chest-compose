package fr.theskyblockman.lifechest.explorer.file_readers

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import fr.theskyblockman.lifechest.explorer.ExplorerViewModel
import fr.theskyblockman.lifechest.explorer.file_readers.utils.InteractiveImage
import fr.theskyblockman.lifechest.vault.Crypto
import fr.theskyblockman.lifechest.vault.EncryptedContentProvider
import fr.theskyblockman.lifechest.vault.FileNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ImageReader(override val node: FileNode) : FileReader {
    private var thumbnailState: MutableStateFlow<ImageBitmap?> = MutableStateFlow(null)
    private val thumbnail = thumbnailState.asStateFlow()
    private var uri: Uri? = null

    @Composable
    override fun Reader(fullscreen: Boolean, setFullscreen: (isFullscreen: Boolean) -> Unit) {
        val thumbnail by this.thumbnail.collectAsState()

        if (thumbnail == null) return

        return InteractiveImage(
            bitmap = thumbnail!!,
            uri = uri!!,
            fileName = node.name,
            isFullscreen = fullscreen
        ) {
            setFullscreen(!fullscreen)
        }
    }

    override suspend fun load(context: Context, explorerViewModel: ExplorerViewModel) {
        Crypto.Decrypt.fileToInputStream(
            node.attachedFile,
            explorerViewModel.vault.value!!
        ).use {
            var originalBitmap = BitmapFactory.decodeStream(it)

            val screenWidth = Resources.getSystem().displayMetrics.widthPixels
            val screenHeight = Resources.getSystem().displayMetrics.heightPixels

            val aspectRatio = originalBitmap.width.toFloat() / originalBitmap.height.toFloat()

            Log.d("ImageReader", "Screen size is ${screenWidth}x${screenHeight}")
            Log.d(
                "ImageReader",
                "Loaded image with ${originalBitmap.width}x${originalBitmap.height}"
            )

            if (originalBitmap.height > screenHeight || originalBitmap.width > screenWidth) {
                val newWidth: Int
                val newHeight: Int
                if (aspectRatio > 1f) {
                    // Image is taller than wide, fit to screen height
                    newHeight = Math.round(screenHeight / aspectRatio)
                    newWidth = screenHeight
                } else {
                    // Image is wider or equal to height, fit to screen width
                    newHeight = screenWidth
                    newWidth = Math.round(screenWidth * aspectRatio)
                }

                Log.d("ImageReader", "Resized image to ${newWidth}x${newHeight}")

                originalBitmap = Bitmap.createScaledBitmap(
                    originalBitmap,
                    newWidth,
                    newHeight,
                    true
                )
            }

            uri = EncryptedContentProvider.getUriFromFile(node)

            thumbnailState.update {
                originalBitmap.asImageBitmap()
            }
        }
    }

    override fun unload() {
        thumbnailState.update {
            null
        }
    }
}