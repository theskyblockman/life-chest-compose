package fr.theskyblockman.lifechest.explorer

import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import fr.theskyblockman.lifechest.R
import fr.theskyblockman.lifechest.transactions.Importer
import fr.theskyblockman.lifechest.vault.Crypto
import fr.theskyblockman.lifechest.vault.DirectoryNode
import fr.theskyblockman.lifechest.vault.FileNode
import fr.theskyblockman.lifechest.vault.TreeNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Explorer(
    navController: NavController,
    viewModel: FileImportViewModel = viewModel(),
    currentFileId: String,
    defaultIsGridView: Boolean = true,
    activity: ExplorerActivity,
    toImport: Uri? = null,
) {
    var current by remember {
        mutableStateOf(ExplorerActivity.vault.fileTree!!.goTo(currentFileId))
    }

    var currentSortMethod by remember {
        mutableStateOf(ExplorerActivity.vault.sortMethod)
    }

    val items = remember(current) {
        mutableStateListOf<TreeNode>().apply {
            addAll(
                currentSortMethod.sortItems(
                    current?.children ?: emptyList()
                )
            )
        }
    }


    fun updateCurrent(newNode: TreeNode?) {
        current = newNode
        items.apply {
            items.clear()
            addAll(
                if (current?.children != null)
                    currentSortMethod.sortItems(current!!.children)
                else
                    emptyList()
            )
        }
    }

    var isGridView by remember { mutableStateOf(defaultIsGridView) }
    val uiState by viewModel.uiStateFlow.collectAsState()
    var selectedElements by remember { mutableStateOf<Set<String>>(emptySet()) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(current) {
        selectedElements = emptySet()
    }

    BackHandler {
        if (selectedElements.isNotEmpty()) {
            selectedElements = emptySet()
        } else {
            if (ExplorerActivity.vault.fileTree!!.id == currentFileId) {
                activity.finish()
            } else {
                navController.navigateUp()
            }
        }
    }

    var deleteDialogExpanded by remember {
        mutableStateOf(false)
    }
    var toDelete = remember {
        listOf<TreeNode>()
    }

    var createDirectoryDialogExpanded by remember {
        mutableStateOf(false)
    }

    Scaffold(
        snackbarHost = { SnackbarHost((snackbarHostState)) },
        topBar = {
            ExplorerTopAppBar(
                selectedElements = selectedElements,
                sortMethod = currentSortMethod,
                current = current,
                fileImportsState = uiState,
                isGridView = isGridView,
                onNavigationClick = {
                    if (ExplorerActivity.vault.fileTree!!.id == currentFileId) {
                        activity.finish()
                    } else {
                        navController.navigateUp()
                    }
                },
                onToggleView = { isGridView = !isGridView },
                onInvertSelection = {
                    selectedElements = items.map { it.id }.filter {
                        !selectedElements.contains(it)
                    }.toSet()
                },
                onClearSelection = { selectedElements = emptySet() },
                onDeleteSelection = {
                    deleteDialogExpanded = true
                    toDelete = items.filter { selectedElements.contains(it.id) }
                },
                onSelectAll = {
                    selectedElements = items.map { it.id }.toSet()
                },
                onCreateDirectory = {
                    createDirectoryDialogExpanded = true
                },
                setSortMethod = {
                    currentSortMethod = it
                    ExplorerActivity.vault.sortMethod = it
                    ExplorerActivity.vault.writeFileTree()
                    updateCurrent(current)
                }
            )
        },
        floatingActionButton = {
            if (toImport == null) {
                FloatingActionButton(onClick = {
                    ExplorerActivity.bypassChestClosure = true
                    viewModel.moveFiles(
                        lastResultCompleter = activity.pickFiles(),
                        vault = ExplorerActivity.vault,
                        currentPath = currentFileId,
                        onLcefFiles = {
                            if (it.isEmpty()) return@moveFiles

                            val transactionId = Crypto.createID()
                            ExplorerActivity.lcefUrisTransactions[transactionId] = it

                            viewModel.viewModelScope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = activity.getString(
                                        R.string.lcef_files_were_found,
                                        it.size
                                    ),
                                    actionLabel = activity.getString(
                                        R.string.import_
                                    ),
                                    withDismissAction = true,
                                    duration = SnackbarDuration.Indefinite
                                )
                                when (result) {
                                    SnackbarResult.ActionPerformed -> {
                                        navController.navigate("import-lcef/$currentFileId/$transactionId")
                                    }

                                    SnackbarResult.Dismissed -> {
                                        ExplorerActivity.lcefUrisTransactions.remove(transactionId)
                                    }
                                }
                            }
                        }
                    ) {
                        ExplorerActivity.bypassChestClosure = false
                        updateCurrent(it.goTo(currentFileId))
                        selectedElements = emptySet()
                    }
                }) {
                    Icon(Icons.Filled.Add, stringResource(R.string.import_files))
                }
            } else {
                var isImporterExpanded by remember {
                    mutableStateOf(false)
                }
                if (isImporterExpanded) {
                    Importer(
                        currentFileId,
                        toImport,
                        viewModel,
                        snackbarHostState,
                        activity
                    ) { isImporterExpanded = false }
                }
                ExtendedFloatingActionButton(text = {
                    Text(stringResource(R.string.import_here))
                }, icon = {
                    Icon(Icons.Filled.Check, stringResource(R.string.import_here))
                }, onClick = {
                    isImporterExpanded = true
                })
            }
        },
        floatingActionButtonPosition = if (toImport == null) FabPosition.End else FabPosition.Center
    ) { innerPadding ->
        val scope = rememberCoroutineScope()

        if (deleteDialogExpanded) {
            BasicAlertDialog(
                onDismissRequest = {
                    deleteDialogExpanded = false
                    toDelete = emptyList()
                }
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            stringResource(R.string.delete),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Box(modifier = Modifier.padding(bottom = 16.dp))
                        Text(
                            stringResource(
                                R.string.are_you_sure_to_delete_files,
                                toDelete.map { it.count() }.reduce { acc, i -> acc + i })
                        )
                        Box(modifier = Modifier.padding(bottom = 24.dp))
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(onClick = {
                                deleteDialogExpanded = false
                                toDelete = emptyList()
                            }) {
                                Text(stringResource(R.string.cancel))
                            }
                            val context = LocalContext.current
                            TextButton(
                                onClick = {
                                    val newDir =
                                        ExplorerActivity.vault.fileTree!!.goTo(currentFileId)!!
                                    for (element in selectedElements) {
                                        newDir.children.remove(newDir.children.first { it.id == element }
                                            .also {
                                                it.delete()
                                            })
                                    }
                                    ExplorerActivity.vault.writeFileTree()
                                    updateCurrent(newDir)
                                    selectedElements = emptySet()

                                    deleteDialogExpanded = false

                                    scope.launch(Dispatchers.Main) {
                                        snackbarHostState.showSnackbar(
                                            message = context.getString(R.string.files_deleted),
                                            actionLabel = null,
                                            withDismissAction = false,
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                }
                            ) {
                                Text(stringResource(R.string.delete))
                            }
                        }
                    }
                }
            }
        }

        if (createDirectoryDialogExpanded) {
            BasicAlertDialog(
                onDismissRequest = {
                    createDirectoryDialogExpanded = false
                }
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        var newName by remember {
                            mutableStateOf(TextFieldValue(""))
                        }
                        Text(
                            stringResource(R.string.create_new_directory),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Box(modifier = Modifier.padding(bottom = 16.dp))

                        val focusRequester = remember { FocusRequester() }

                        OutlinedTextField(
                            value = newName,
                            onValueChange = {
                                newName = it
                            },
                            label = { Text(stringResource(R.string.name)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                            singleLine = true,
                            modifier = Modifier.focusRequester(focusRequester)
                        )

                        LaunchedEffect(createDirectoryDialogExpanded) {
                            focusRequester.requestFocus()
                        }

                        Box(modifier = Modifier.padding(bottom = 24.dp))
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(onClick = {
                                createDirectoryDialogExpanded = false
                            }) {
                                Text(stringResource(R.string.cancel))
                            }
                            TextButton(
                                onClick = {
                                    createDirectoryDialogExpanded = false
                                    val newDir =
                                        ExplorerActivity.vault.fileTree!!.goTo(currentFileId)!!
                                    newDir.children.add(
                                        DirectoryNode(
                                            children = mutableListOf(),
                                            name = newName.text,
                                            id = Crypto.createID()
                                        )
                                    )
                                    ExplorerActivity.vault.writeFileTree()
                                    updateCurrent(newDir)
                                    selectedElements = emptySet()
                                },
                                enabled = newName.text.isNotBlank()
                            ) {
                                Text(stringResource(R.string.create))
                            }
                        }
                    }
                }
            }
        }

        when (current) {
            null -> {
                Column(modifier = Modifier.padding(innerPadding)) {
                    Text(text = "Not found \"${currentFileId}\"")
                }
            }

            is FileNode -> {
                Column(modifier = Modifier.padding(innerPadding)) {
                    Text(text = current!!.name)
                }
            }

            else -> {
                ExplorerContent(
                    items = items,
                    isGridView = isGridView,
                    innerPadding = innerPadding,
                    selectedElements = selectedElements,
                    onNodeTileClick = { node ->
                        if (selectedElements.isEmpty()) {
                            when (node) {
                                is FileNode -> {
                                    val transactionId = Crypto.createID()
                                    ExplorerActivity.fileListTransactions[transactionId] =
                                        items.map { it.id }
                                    navController.navigate("reader/${node.id}?transactionId=$transactionId")
                                }

                                is DirectoryNode -> {
                                    if (toImport != null) {
                                        navController.navigate(
                                            "explorer/${node.id}?toImport=${
                                                Uri.encode(
                                                    toImport.toString()
                                                )
                                            }"
                                        )
                                    } else {
                                        Log.i("Explorer", "Navigating to ${node.name}")
                                        navController.navigate("explorer/${node.id}")
                                    }
                                }
                            }
                        } else {
                            selectedElements = if (!selectedElements.contains(node.id)) {
                                selectedElements + node.id
                            } else {
                                selectedElements - node.id
                            }
                        }
                    },
                    onNodeTileLongClick = { node ->
                        selectedElements = if (!selectedElements.contains(node.id)) {
                            selectedElements + node.id
                        } else {
                            selectedElements - node.id
                        }
                    },
                    setSelected = { node, isSelected ->
                        selectedElements = if (isSelected) {
                            selectedElements + node.id
                        } else {
                            selectedElements - node.id
                        }
                    },
                    reloadFiles = {
                        Log.i("Explorer", "Reloading files")
                        updateCurrent(ExplorerActivity.vault.fileTree!!.goTo(currentFileId))
                    }
                )

            }
        }
    }
}

@Composable
fun ExplorerContent(
    items: SnapshotStateList<TreeNode>,
    isGridView: Boolean,
    innerPadding: PaddingValues,
    selectedElements: Set<String>,
    onNodeTileClick: (TreeNode) -> Unit,
    onNodeTileLongClick: (TreeNode) -> Unit,
    setSelected: (TreeNode, isSelected: Boolean) -> Unit,
    reloadFiles: () -> Unit
) {
    if (isGridView) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(128.dp),
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(3.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            items(items, key = { it.id }) { node ->
                NodeTile(
                    node = node,
                    isGridView = true,
                    selected = selectedElements.contains(node.id),
                    onClick = {
                        onNodeTileClick(node)
                    },
                    onLongClick = {
                        onNodeTileLongClick(node)
                    },
                    setSelected = {
                        setSelected(node, it)
                    },
                    reloadFiles = reloadFiles
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
        ) {
            items(items, key = { it.id }) { node ->
                NodeTile(
                    node = node,
                    isGridView = false,
                    selected = selectedElements.contains(node.id),
                    onClick = {
                        onNodeTileClick(node)
                    },
                    onLongClick = {
                        onNodeTileLongClick(node)
                    },
                    setSelected = {
                        setSelected(node, it)
                    },
                    reloadFiles = reloadFiles
                )
            }
        }
    }
}