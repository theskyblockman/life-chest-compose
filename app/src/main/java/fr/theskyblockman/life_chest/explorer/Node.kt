package fr.theskyblockman.life_chest.explorer

import android.content.ClipData
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.theskyblockman.life_chest.R
import fr.theskyblockman.life_chest.vault.Crypto
import fr.theskyblockman.life_chest.vault.DirectoryNode
import fr.theskyblockman.life_chest.vault.EncryptedContentProvider
import fr.theskyblockman.life_chest.vault.EncryptedFile
import fr.theskyblockman.life_chest.vault.FileNode
import fr.theskyblockman.life_chest.vault.TreeNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

@Preview
@Composable
private fun NodeTilePreview() {
    Column {
        Box(Modifier.size(128.dp)) {
            NodeTile(
                node = FileNode(
                    attachedFile = EncryptedFile(
                        "",
                        "",
                        ByteArray(0),
                        Clock.System.now(),
                        Clock.System.now()
                    ),
                    null,
                    name = "REALLY REALLY LONG test thumbnail",
                    type = "image/png",
                    size = 1000L,
                    creationDate = Clock.System.now(),
                    importDate = Clock.System.now()
                ),
                setSelected = {},
                isGridView = true,
                selected = false,
                explorerViewModel = ExplorerViewModel(),
                onClick = {},
                onLongClick = {},
                reloadFiles = {}
            )
        }
        Box(Modifier.size(128.dp)) {
            NodeTile(
                node = FileNode(
                    attachedFile = EncryptedFile(
                        "",
                        "",
                        ByteArray(0),
                        Clock.System.now(),
                        Clock.System.now()
                    ),
                    null,
                    name = "R",
                    type = "image/png",
                    size = 1000L,
                    creationDate = Clock.System.now(),
                    importDate = Clock.System.now()
                ),
                explorerViewModel = ExplorerViewModel(),
                setSelected = {},
                isGridView = true,
                selected = false,
                onClick = {},
                onLongClick = {},
                reloadFiles = {}
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NodeTile(
    node: TreeNode,
    isGridView: Boolean,
    selected: Boolean,
    explorerViewModel: ExplorerViewModel,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    setSelected: (isSelected: Boolean) -> Unit,
    reloadFiles: () -> Unit
) {
    var thumbnail: ImageBitmap? by remember {
        mutableStateOf(null)
    }
    var thumbnailLoaded by remember {
        mutableStateOf(false)
    }
    val vault by explorerViewModel.vault.collectAsState()

    LaunchedEffect(Unit) {
        if (node.attachedThumbnail == null) {
            Log.i("ThumbnailLoader", "No thumbnail for ${node.name}")
            thumbnailLoaded = true
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            try {
                Crypto.Decrypt.fileToInputStream(
                    node.attachedThumbnail!!,
                    vault!!
                ).use {
                    thumbnail = BitmapFactory.decodeStream(it)
                        .asImageBitmap()
                    thumbnailLoaded = true
                }
            } catch (e: Exception) {
                Log.e("ThumbnailLoader", "Failed to load thumbnail", e)
            }
        }
    }

    var renameDialogExpanded by remember {
        mutableStateOf(false)
    }

    if (renameDialogExpanded) {
        BasicAlertDialog(
            onDismissRequest = {
                renameDialogExpanded = false
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
                        stringResource(R.string.rename),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Box(modifier = Modifier.padding(bottom = 16.dp))

                    val focusRequester = remember { FocusRequester() }

                    OutlinedTextField(
                        value = newName,
                        onValueChange = {
                            newName = it
                        },
                        placeholder = { Text(node.name) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        singleLine = true,
                        modifier = Modifier.focusRequester(focusRequester)
                    )

                    LaunchedEffect(renameDialogExpanded) {
                        focusRequester.requestFocus()
                    }

                    Box(modifier = Modifier.padding(bottom = 24.dp))
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(onClick = {
                            renameDialogExpanded = false
                        }) {
                            Text(stringResource(R.string.cancel))
                        }
                        TextButton(
                            onClick = {
                                renameDialogExpanded = false
                                node.name = newName.text
                                vault!!.writeFileTree()
                                reloadFiles()
                            },
                            enabled = newName.text.isNotBlank() && newName.text != node.name
                        ) {
                            Text(stringResource(R.string.rename))
                        }
                    }
                }
            }
        }
    }

    if (isGridView) {
        var expanded by remember { mutableStateOf(false) }
        Surface(shape = RoundedCornerShape(12.dp)) {
            OutlinedCard(
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .aspectRatio(1f)
                    .combinedClickable(
                        onLongClick = onLongClick,
                        onClick = onClick
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (selected)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surface.copy(alpha = .75f)
                        ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = node.name,
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .padding(end = 1.dp)
                            .basicMarquee(),
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        textAlign = TextAlign.Center
                    )

                    Box {
                        IconButton(
                            onClick = { expanded = true }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.outline_more_vert_24),
                                contentDescription = stringResource(R.string.more)
                            )
                        }

                        // Menu
                        NodeActionMenu(
                            node = node,
                            expended = expanded,
                            isSelected = selected,
                            explorerViewModel = explorerViewModel,
                            dismiss = { expanded = false },
                            setSelected = setSelected,
                            reloadFiles = reloadFiles,
                            onRename = {
                                renameDialogExpanded = true
                            }
                        )
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (!thumbnailLoaded && thumbnail == null) {
                        CircularProgressIndicator(modifier = Modifier)
                    } else if (thumbnail != null) {
                        Image(
                            bitmap = thumbnail!!,
                            contentDescription = node.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Image(
                            painter = thumbnailFromMimeType(node.type),
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSecondaryContainer),
                            contentDescription = node.name,
                            contentScale = ContentScale.FillHeight,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                        )
                    }
                }
            }
        }

    } else {
        Surface(
            modifier = Modifier.combinedClickable(
                onLongClick = onLongClick,
                onClick = onClick
            )
        ) {
            var expanded by remember {
                mutableStateOf(false)
            }

            ListItem(
                colors = ListItemDefaults.colors(
                    containerColor = if (selected)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        ListItemDefaults.containerColor
                ),
                headlineContent = { Text(text = node.name) },
                trailingContent = {
                    Box(
                        modifier = Modifier
                            .wrapContentSize(Alignment.TopEnd)
                    ) {
                        IconButton(
                            onClick = { expanded = true }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.outline_more_vert_24),
                                contentDescription = stringResource(R.string.more)
                            )
                        }
                        NodeActionMenu(
                            node = node,
                            expended = expanded,
                            isSelected = selected,
                            dismiss = { expanded = false },
                            setSelected = setSelected,
                            reloadFiles = reloadFiles,
                            explorerViewModel = explorerViewModel,
                            onRename = {
                                renameDialogExpanded = true
                            }
                        )
                    }
                },
                leadingContent = {
                    if (!thumbnailLoaded && thumbnail == null) {
                        CircularProgressIndicator()
                    } else if (thumbnail != null) {
                        Image(
                            bitmap = thumbnail!!,
                            contentDescription = stringResource(R.string.thumbnail),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .height(48.dp)
                                .aspectRatio(1f)
                                .clip(shape = RoundedCornerShape(8.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .height(48.dp)
                                .aspectRatio(1f)
                                .clip(shape = RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Image(
                                painter = thumbnailFromMimeType(node.type),
                                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSecondaryContainer),
                                contentDescription = stringResource(R.string.thumbnail),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .height(48.dp)
                                    .aspectRatio(1f)
                            )
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun NodeActionMenu(
    modifier: Modifier = Modifier,
    node: TreeNode,
    expended: Boolean,
    isSelected: Boolean,
    explorerViewModel: ExplorerViewModel,
    dismiss: () -> Unit,
    onRename: () -> Unit,
    setSelected: (isSelected: Boolean) -> Unit,
    reloadFiles: () -> Unit
) {
    val context = LocalContext.current
    val vault by explorerViewModel.vault.collectAsState()

    DropdownMenu(modifier = modifier, expanded = expended, onDismissRequest = { dismiss() }) {
        DropdownMenuItem(
            text = { Text(text = stringResource(id = R.string.rename)) },
            onClick = {
                onRename()
                dismiss()
            }
        )
        DropdownMenuItem(
            text = { Text(text = stringResource(id = if (isSelected) R.string.deselect else R.string.select)) },
            onClick = {
                setSelected(!isSelected)
                dismiss()
            })
        if (node is FileNode) {
            DropdownMenuItem(
                text = { Text(text = stringResource(id = R.string.open_with)) },
                onClick = {
                    explorerViewModel.setBypassChestClosure(true)
                    val uri = EncryptedContentProvider.getUriFromFile(node)
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    intent.setDataAndType(uri, node.type)
                    intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    dismiss()
                    context.startActivity(intent)
                })
            DropdownMenuItem(
                text = { Text(text = stringResource(id = R.string.export)) },
                enabled = vault!!.currentMechanism.supportsExport,
                onClick = {
                    val activity = context as ExplorerActivity
                    activity.currentNodeToExport = node
                    activity.exporter.launch("${node.name}.lcef")
                    dismiss()
                })
        }
        DropdownMenuItem(text = { Text(text = stringResource(id = R.string.delete)) }, onClick = {
            node.delete()

            vault!!.fileTree!!.goToParentOf(node.id)!!.children.remove(node)
            vault!!.writeFileTree()
            dismiss()
            reloadFiles()
        })
        DropdownMenuItem(text = { Text(text = stringResource(id = R.string.share)) }, onClick = {
            explorerViewModel.setBypassChestClosure(true)
            if (node is FileNode) {
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    val createdUri = EncryptedContentProvider.getUriFromFile(node)
                    clipData = ClipData.newRawUri(node.name, createdUri)
                    putExtra(Intent.EXTRA_STREAM, createdUri)
                    type = node.type
                }

                sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                dismiss()

                context.startActivity(
                    Intent.createChooser(sendIntent, null),
                    null
                )
            } else if (node is DirectoryNode) {
                val files = node.listFiles()
                val uris = ArrayList<Uri>()
                for (file in files) {
                    uris.add(EncryptedContentProvider.getUriFromFile(file))
                }
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND_MULTIPLE
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    type = "*/*"
                    clipData = ClipData.newPlainText(
                        context.getString(R.string.file_amount, files.size),
                        context.getString(R.string.file_amount, files.size)
                    )
                }

                sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                dismiss()

                context.startActivity(
                    Intent.createChooser(sendIntent, null),
                    null
                )
            }
        })
    }
}