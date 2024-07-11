package fr.theskyblockman.lifechest.explorer.file_readers

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import fr.theskyblockman.lifechest.R
import fr.theskyblockman.lifechest.explorer.file_readers.utils.InteractiveImage
import fr.theskyblockman.lifechest.vault.EncryptedContentProvider
import fr.theskyblockman.lifechest.vault.FileNode

class PdfReader(override val node: FileNode): FileReader {
    private var fd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null

    @Composable
    override fun Reader(fullscreen: Boolean, setFullscreen: (isFullscreen: Boolean) -> Unit) {
        Log.d("PdfReader", "Rendering ${node.name}, size: ${node.size}")
        fd = fd ?: LocalContext.current.contentResolver.openFileDescriptor(
            EncryptedContentProvider.getUriFromFile(node),
            "r"
        )
        try {
            renderer = renderer ?: fd?.let { PdfRenderer(it) }
        } catch(e: Exception) {
            Log.e("PdfReader", "Failed to open renderer", e)
        }

        LazyColumn {
            items(renderer?.pageCount ?: 0) { index ->
                Column {
                    val page: PdfRenderer.Page
                    try {
                        page = renderer!!.openPage(index)
                    } catch(e: Exception) {
                        Log.i("PdfReader", "Failed to render page $index")
                        LaunchedEffect(Unit) {
//                            snackbarHostState.showSnackbar(
//                                context.getString(
//                                    R.string.could_not_load_page,
//                                    index + 1
//                                ))
                        }
                        Column(verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
                            Text("Could not load page ${index + 1}", textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge)
                        }
                        return@items
                    }

                    val bitmap = Bitmap.createBitmap(
                        page.width,
                        page.height,
                        Bitmap.Config.ARGB_8888
                    )

                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    InteractiveImage(Modifier.background(Color.White), bitmap.asImageBitmap(), stringResource(R.string.current_page, index + 1), false, contentScale = ContentScale.FillWidth) { }

                    page.close()
                }
            }
        }
    }

    override suspend fun load() {
    }

    override fun unload() {
        fd?.close()
        renderer?.close()
    }

}