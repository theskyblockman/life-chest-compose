package fr.theskyblockman.lifechest.explorer.file_readers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asImageBitmap
import fr.theskyblockman.lifechest.explorer.ExplorerActivity
import fr.theskyblockman.lifechest.explorer.file_readers.utils.InteractiveImage
import fr.theskyblockman.lifechest.vault.Crypto
import fr.theskyblockman.lifechest.vault.FileNode

class ImageReader(override val node: FileNode): FileReader {
    private var image: Bitmap? = null

    @Composable
    override fun Reader(fullscreen: Boolean, setFullscreen: (isFullscreen: Boolean) -> Unit) {
        return InteractiveImage(
            bitmap = image!!.asImageBitmap(),
            fileName = node.name,
            isFullscreen = fullscreen
        ) {
            setFullscreen(!fullscreen)
        }
    }

    override suspend fun load() {
        Crypto.Decrypt.fileToInputStream(node.attachedFile, ExplorerActivity.vault).use {
            image = BitmapFactory
                .decodeStream(it)
        }
    }

    override fun unload() {
        image?.recycle()
        image = null
    }

}