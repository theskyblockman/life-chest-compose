package fr.theskyblockman.life_chest.explorer

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import fr.theskyblockman.life_chest.BuildConfig
import fr.theskyblockman.life_chest.R
import fr.theskyblockman.life_chest.explorer.file_readers.AudioReader
import fr.theskyblockman.life_chest.explorer.file_readers.DirectoryReader
import fr.theskyblockman.life_chest.explorer.file_readers.FileReader
import fr.theskyblockman.life_chest.explorer.file_readers.GifReader
import fr.theskyblockman.life_chest.explorer.file_readers.ImageReader
import fr.theskyblockman.life_chest.explorer.file_readers.PdfReader
import fr.theskyblockman.life_chest.explorer.file_readers.VideoReader
import fr.theskyblockman.life_chest.vault.DirectoryNode
import fr.theskyblockman.life_chest.vault.FileNode
import fr.theskyblockman.life_chest.vault.TreeNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.properties.Delegates

class FileReaderViewModel : ViewModel() {
    private val loadingFiles = mutableSetOf<Int>()
    private val loadedFiles = mutableSetOf<Int>()
    val fileLoaders = mutableMapOf<Int, FileReader>()

    private val pageDataState = MutableStateFlow(mutableStateMapOf<Int, PageData>())
    val pageData = pageDataState.asStateFlow()

    var fileLength by Delegates.notNull<Int>()

    fun getFileReader(fileIndex: Int): FileReader? {
        return fileLoaders[fileIndex]
    }

    fun loadFile(
        fileReader: FileReader,
        fileIndex: Int,
        explorerViewModel: ExplorerViewModel,
        context: Context,
        onPageLoaded: (Int) -> Unit
    ) {
        // Prevent loading the same file multiple times
        if (fileIndex in loadingFiles || fileIndex in loadedFiles) {
            onPageLoaded(fileIndex)
            return
        }

        loadingFiles.add(fileIndex)
        pageDataState.update {
            it[fileIndex] = PageData(isLoading = true)
            it
        }

        fileLoaders[fileIndex] = fileReader

        viewModelScope.launch(Dispatchers.Main) { // Use Main dispatcher for FileReader calls
            Log.d("FileReaderViewModel", "Loading file $fileIndex")
            try {
                fileReader.load(context, explorerViewModel)
            } catch (e: Exception) {
                Log.e("FileReaderViewModel", "Error loading file $fileIndex", e)
                loadingFiles.remove(fileIndex)
                loadedFiles.add(fileIndex)
                pageDataState.update {
                    it[fileIndex] = PageData(isError = true, isLoading = false)
                    it
                }
                return@launch
            } finally {
                Log.d("FileReaderViewModel", "Loaded file $fileIndex")
                loadingFiles.remove(fileIndex)
                loadedFiles.add(fileIndex)
                Log.d("FileReaderViewModel", "Loaded: $loadedFiles")
                pageDataState.update {
                    it[fileIndex] = PageData(isLoading = false)
                    it
                }
                onPageLoaded(fileIndex)
            }
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
        explorerViewModel: ExplorerViewModel,
        context: Context,
        onPageLoaded: (Int) -> Unit
    ) {
        loadFile(
            fileLoaders[pageIndex] ?: getFileReaderFromPage(pageIndex) ?: return,
            pageIndex,
            explorerViewModel,
            context
        ) {
            onPageLoaded(pageIndex)
            val pagesToKeepAlive = (pageIndex - 2..pageIndex + 2).filter { it in (0..<fileLength) }
            // Load pages around the selected page
            val pagesToLoad =
                pagesToKeepAlive.filter { it !in loadedFiles && it !in loadingFiles }
            Log.d("FileReaderViewModel", "To load: $pagesToLoad")
            pagesToLoad.forEach { index ->
                val possibleFileReader = getFileReaderFromPage(index) ?: return@forEach
                loadFile(
                    possibleFileReader,
                    index,
                    explorerViewModel,
                    context,
                    onPageLoaded = onPageLoaded
                )
            }

            for (pageToUnload in loadedFiles.filter { it !in pagesToKeepAlive }) {
                unloadFile(pageToUnload)
            }
        }
    }
}

data class PageData(
    val isLoading: Boolean = false,
    val isFocused: Boolean = false,
    val isError: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileReader(
    files: List<TreeNode>,
    currentFileId: String,
    activity: ExplorerActivity,
    navController: NavController,
    explorerViewModel: ExplorerViewModel
) {
    val pagerState = rememberPagerState(pageCount = { files.size },
        initialPage = files.indexOfFirst { it.id == currentFileId })
    val viewModel: FileReaderViewModel = viewModel()
    val pageData by viewModel.pageData.collectAsState()

    // Load the initial page
    LaunchedEffect(Unit) {
        viewModel.fileLength = files.size

        val initialPageIndex = pagerState.currentPage
        val fileReader = nodeToFileReader(files[initialPageIndex])
        viewModel.loadFile(
            fileReader ?: return@LaunchedEffect,
            initialPageIndex,
            explorerViewModel,
            activity,
        ) { loadedPageIndex ->
            pageData[loadedPageIndex] =
                PageData(isFocused = pagerState.currentPage == loadedPageIndex)
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
                explorerViewModel = explorerViewModel,
                context = activity,
                getFileReaderFromPage = { nodeToFileReader(files[it]) }
            ) {
                if (pageData[it]?.isLoading == true) return@onPageSelected

                pageData[it] = PageData(isFocused = pagerState.currentPage == it)
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

    HorizontalPager(state = pagerState, beyondViewportPageCount = 0) { pageIndex ->
        val page = files[pageIndex]
        val snackbarHostState = remember { SnackbarHostState() }

        var currentPageData = pageData[pageIndex]
        if (currentPageData == null) {
            currentPageData = PageData(isLoading = true)
            pageData[pageIndex] = currentPageData
        }
        val fileReader = viewModel.getFileReader(pageIndex)

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if (!isFullscreen) {
                    if (fileReader == null) {
                        TopAppBar(
                            title = {
                                Text(text = if (BuildConfig.DEBUG) "${page.name} - $pageIndex" else page.name)
                            },
                            navigationIcon = {
                                IconButton(onClick = { navController.popBackStack() }) {
                                    Icon(
                                        painter = painterResource(R.drawable.outline_arrow_back_24),
                                        contentDescription = stringResource(id = R.string.go_back)
                                    )
                                }
                            }
                        )
                    } else {
                        fileReader.TopAppBar(
                            name = page.name,
                            pageIndex = pageIndex,
                            navController = navController
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                if (currentPageData.isLoading) {
                    var modifier = Modifier.fillMaxSize()
                    if (isFullscreen) {
                        modifier = modifier.background(Color.Black)
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = modifier
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (currentPageData.isError) {
                    var modifier = Modifier.fillMaxSize()
                    if (isFullscreen) {
                        modifier = modifier.background(Color.Black)
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = modifier
                    ) {
                        Text(stringResource(R.string.the_following_file_could_not_be_read))
                    }
                } else if (fileReader != null) {
                    Log.d("FileManager", "Drawing $pageIndex")
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
            "image" -> when (node.type.split("/").last()) {
                "gif" -> GifReader(node)
                else -> ImageReader(node)
            }

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