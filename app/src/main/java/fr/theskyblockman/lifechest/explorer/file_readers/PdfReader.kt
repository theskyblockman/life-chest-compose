package fr.theskyblockman.lifechest.explorer.file_readers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.navigation.NavController
import com.github.panpf.zoomimage.ZoomImage
import com.github.panpf.zoomimage.compose.rememberZoomState
import fr.theskyblockman.lifechest.R
import fr.theskyblockman.lifechest.explorer.ExplorerViewModel
import fr.theskyblockman.lifechest.vault.EncryptedContentProvider
import fr.theskyblockman.lifechest.vault.FileNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PdfReader(override val node: FileNode) : FileReader {
    private var fd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private var currentPageState = MutableStateFlow(0)
    private val currentPage = currentPageState.asStateFlow()

    private fun setPage(page: Int) {
        bitmapLoadedState.update {
            false
        }
        currentPageState.update {
            page
        }
        try {
            renderer!!.openPage(page).use {
                val loadedPage = it
                val newBitmap = Bitmap.createBitmap(
                    loadedPage.width,
                    loadedPage.height,
                    Bitmap.Config.ARGB_8888
                )
                it.render(
                    newBitmap,
                    null,
                    null,
                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                )
                bitmapState.update {
                    newBitmap
                }
            }
        } catch (e: Exception) {
            Log.e("PdfReader", "Failed to render page $page", e)
        } finally {
            Log.d("PdfReader", "Rendered page $page")
            bitmapLoadedState.update {
                true
            }
        }
    }

    private var bitmapState: MutableStateFlow<Bitmap?> = MutableStateFlow(null)
    private val bitmap = bitmapState.asStateFlow()

    private var bitmapLoadedState = MutableStateFlow(false)
    private val bitmapLoaded = bitmapLoadedState.asStateFlow()

    @Composable
    override fun Reader(fullscreen: Boolean, setFullscreen: (isFullscreen: Boolean) -> Unit) {
        val index by currentPage.collectAsState()
        val bitmapLoaded by this.bitmapLoaded.collectAsState()
        val bitmap by this.bitmap.collectAsState()

        if (bitmap == null) {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize()
            ) {
                if (!bitmapLoaded) {
                    CircularProgressIndicator()
                } else {
                    Text(
                        stringResource(R.string.could_not_load_page, index + 1),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
            return
        }

        val zoomState = rememberZoomState()

        ZoomImage(
            zoomState = zoomState,
            painter = BitmapPainter(bitmap!!.asImageBitmap()),
            contentDescription = stringResource(R.string.image_alt_text, node.name),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillWidth
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun TopAppBar(name: String, pageIndex: Int, navController: NavController) {
        val currentPage by currentPage.collectAsState()

        MediumTopAppBar(
            title = {
                if (renderer == null) {
                    Text(text = name)
                } else {
                    Text(text = "$name $currentPage/${renderer!!.pageCount - 1}")
                }
            },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        painter = painterResource(R.drawable.outline_arrow_back_24),
                        contentDescription = stringResource(id = R.string.go_back)
                    )
                }
            },
            actions = {
                val scope = rememberCoroutineScope()
                IconButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            setPage(0)
                        }
                    },
                    enabled = currentPage > 0
                ) {
                    Icon(
                        painter = painterResource(R.drawable.outline_first_page_24),
                        contentDescription = stringResource(id = R.string.first_page)
                    )
                }
                IconButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            setPage(currentPage - 1)
                        }
                    },
                    enabled = currentPage > 0
                ) {
                    Icon(
                        painter = painterResource(R.drawable.outline_chevron_backward_24),
                        contentDescription = stringResource(id = R.string.previous_page)
                    )
                }

                Text(text = "$currentPage/${renderer!!.pageCount - 1}")

                IconButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            setPage(currentPage + 1)
                        }
                    },
                    enabled = currentPage < (renderer?.pageCount?.minus(1) ?: Int.MIN_VALUE)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.outline_chevron_forward_24),
                        contentDescription = stringResource(id = R.string.next_page)
                    )
                }
                IconButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            setPage(renderer!!.pageCount - 1)
                        }
                    },
                    enabled = currentPage < (renderer?.pageCount?.minus(1) ?: Int.MIN_VALUE)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.outline_last_page_24),
                        contentDescription = stringResource(id = R.string.last_page)
                    )
                }
            }
        )
    }

    override suspend fun load(context: Context, explorerViewModel: ExplorerViewModel) {
        Log.d("PdfReader", "Rendering ${node.name}, size: ${node.size}")

        fd = context.contentResolver.openFileDescriptor(
            EncryptedContentProvider.getUriFromFile(node),
            "r"
        )
        try {
            renderer = fd?.let { PdfRenderer(it) }
        } catch (e: Exception) {
            Log.e("PdfReader", "Failed to open renderer", e)
            return
        }

        setPage(0)
    }

    override fun unload() {
        fd?.close()
        renderer?.close()
    }
}