package fr.theskyblockman.lifechest.explorer

import android.annotation.SuppressLint
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import fr.theskyblockman.lifechest.BuildConfig
import fr.theskyblockman.lifechest.R
import fr.theskyblockman.lifechest.explorer.file_readers.AudioReader
import fr.theskyblockman.lifechest.explorer.file_readers.DirectoryReader
import fr.theskyblockman.lifechest.explorer.file_readers.FileReader
import fr.theskyblockman.lifechest.explorer.file_readers.ImageReader
import fr.theskyblockman.lifechest.explorer.file_readers.PdfReader
import fr.theskyblockman.lifechest.explorer.file_readers.VideoReader
import fr.theskyblockman.lifechest.vault.DirectoryNode
import fr.theskyblockman.lifechest.vault.FileNode
import fr.theskyblockman.lifechest.vault.TreeNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FileReaderViewModel : ViewModel() {
    private val loadingFiles = mutableSetOf<Int>()
    private val loadedFiles = mutableSetOf<Int>()
    val fileLoaders = mutableMapOf<Int, FileReader>()

    fun getFileReader(fileIndex: Int): FileReader? {
        return fileLoaders[fileIndex]
    }

    fun loadFile(
        fileReader: FileReader,
        fileIndex: Int,
        onPageLoaded: (Int) -> Unit
    ) {
        // Prevent loading the same file multiple times
        if (fileIndex in loadingFiles || fileIndex in loadedFiles) return
        loadingFiles.add(fileIndex)

        fileLoaders[fileIndex] = fileReader

        viewModelScope.launch(Dispatchers.Main) { // Use Main dispatcher for FileReader calls
            fileReader.load()
            onPageLoaded(fileIndex)
            loadingFiles.remove(fileIndex)
            loadedFiles.add(fileIndex)
        }
    }

    fun unloadFile(fileIndex: Int) {
        if (fileIndex in loadedFiles) {
            fileLoaders[fileIndex]?.unload()
            fileLoaders.remove(fileIndex)
            loadedFiles.remove(fileIndex)
        }
    }

    fun onPageSelected(
        pageIndex: Int,
        getFileReaderFromPage: (Int) -> FileReader?,
        onPageLoaded: (Int) -> Unit
    ) {
        // First, load the page we are currently on
        // TODO: Figure out the return
        loadFile(fileLoaders[pageIndex] ?: getFileReaderFromPage(pageIndex) ?: return, pageIndex) {
            onPageLoaded(pageIndex)
            // Load pages around the selected page
            val pagesToLoad =
                (pageIndex - 2..pageIndex + 2).filter { it in (0..<loadedFiles.size) && it !in loadedFiles }
            pagesToLoad.forEach { index ->
                val possibleFileReader = getFileReaderFromPage(index) ?: return@forEach
                loadFile(possibleFileReader, index, onPageLoaded = onPageLoaded)
            }
        }

        // Unload unnecessary pages
        loadedFiles.filter { kotlin.math.abs(it - pageIndex) > 5 }
            .forEach { unloadFile(it) }
    }
}

data class PageData(
    val isLoading: Boolean = false,
    val isFocused: Boolean = false
)

@SuppressLint("MutableCollectionMutableState")
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FileReader(
    files: List<TreeNode>,
    currentFileId: String,
    activity: ExplorerActivity,
    navController: NavController
) {
    val pagerState = rememberPagerState(pageCount = { files.size },
        initialPage = files.indexOfFirst { it.id == currentFileId })

    val pageData = remember {
        mutableStateMapOf<Int, PageData>()
    }
    val viewModel: FileReaderViewModel = viewModel()

    // Load the initial page
    LaunchedEffect(Unit) {
        val initialPageIndex = pagerState.currentPage
        val fileReader = nodeToFileReader(files[initialPageIndex])
        viewModel.loadFile(
            fileReader ?: return@LaunchedEffect,
            initialPageIndex,

            ) { loadedPageIndex ->
            pageData[loadedPageIndex] =
                PageData(isLoading = false, isFocused = pagerState.currentPage == loadedPageIndex)
            if (pageData[loadedPageIndex]!!.isFocused) {
                viewModel.getFileReader(loadedPageIndex)?.focused()
            } else {
                viewModel.getFileReader(loadedPageIndex)?.unfocused()
            }
        }
    }

    // Handle page selections and load/unload accordingly
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collectLatest { currentPage ->
            viewModel.onPageSelected(
                pageIndex = currentPage,
                getFileReaderFromPage = { nodeToFileReader(files[it]) }
            ) {
                pageData[it] = PageData(isLoading = false, isFocused = pagerState.currentPage == it)
                if (pageData[it]!!.isFocused) {
                    viewModel.getFileReader(it)?.focused()
                } else {
                    viewModel.getFileReader(it)?.unfocused()
                }
            }

            pageData.forEach { (pageIndex, data) ->
                val isFocused = pageIndex == currentPage
                if (data.isFocused != isFocused) {
                    if (isFocused) viewModel.getFileReader(pageIndex)
                        ?.focused() else viewModel.getFileReader(pageIndex)?.unfocused()
                    pageData[pageIndex] = data.copy(isFocused = isFocused)
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            for (file in viewModel.fileLoaders.keys.toSet()) {
                viewModel.unloadFile(file)
            }
        }
    }

    var isFullscreen by remember {
        mutableStateOf(false)
    }

    if (isFullscreen) {
        activity.hideSystemUI()
    } else {
        activity.showSystemUI()
    }

    HorizontalPager(state = pagerState) { pageIndex ->
        val page = files[pageIndex]
        val snackbarHostState = remember { SnackbarHostState() }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if (!isFullscreen) {
                    TopAppBar(
                        title = { Text(text = if (BuildConfig.DEBUG) "${page.name} - $pageIndex" else page.name) },
                        navigationIcon = {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(id = R.string.go_back)
                                )
                            }
                        }
                    )
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                val currentPageData = pageData[pageIndex] ?: PageData(isLoading = true)
                val fileReader = viewModel.getFileReader(pageIndex)

                if (currentPageData.isLoading) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (fileReader != null) {
                    fileReader.Reader(
                        fullscreen = isFullscreen,
                        setFullscreen = { isFullscreen = it }
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            page.type,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }
        }
    }
}

fun nodeToFileReader(node: TreeNode): FileReader? {
    return when (node) {
        is FileNode -> when (node.type.split("/").first()) {
            "image" -> ImageReader(node)
            "video" -> VideoReader(node)
            "application" -> when (node.type.split("/").last()) {
                "pdf" -> PdfReader(node)
                else -> null
            }

            "audio" -> AudioReader(node)
            else -> null
        }

        is DirectoryNode -> DirectoryReader(node)
    }
}