package fr.theskyblockman.lifechest.explorer

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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Card
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.theskyblockman.lifechest.R
import fr.theskyblockman.lifechest.vault.Crypto
import fr.theskyblockman.lifechest.vault.DirectoryNode
import fr.theskyblockman.lifechest.vault.EncryptedContentProvider
import fr.theskyblockman.lifechest.vault.FileNode
import fr.theskyblockman.lifechest.vault.TreeNode

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NodeTile(
    node: TreeNode,
    isGridView: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    setSelected: (isSelected: Boolean) -> Unit,
    reloadFiles: () -> Unit
) {
    val thumbnail = remember {
        if (node.attachedThumbnail == null) {
            null
        } else {
            try {
                Crypto.Decrypt.fileToInputStream(
                    node.attachedThumbnail!!,
                    ExplorerActivity.vault
                ).use {
                    BitmapFactory.decodeStream(it)
                        .asImageBitmap()
                }
            } catch (e: Exception) {
                Log.e("ThumbnailLoader", "Failed to load thumbnail", e)
                null
            }
        }
    }

    var renameDialogExpanded by remember {
        mutableStateOf(false)
    }

    if(renameDialogExpanded) {
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
                    Text(stringResource(R.string.rename),
                        style = MaterialTheme.typography.headlineSmall)
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
                                ExplorerActivity.vault.writeFileTree()
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
        Surface(shape = RoundedCornerShape(22.dp)) {
            OutlinedCard(
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .aspectRatio(1f)
                    .padding(3.dp)
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
                        )
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = node.name,
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .padding(end = 4.dp)
                            .basicMarquee(),
                        maxLines = 1,
                        overflow = TextOverflow.Clip
                    )

                    Box {
                        // Menu Icon Button
                        IconButton(
                            onClick = { expanded = true }
                        ) {
                            Icon(imageVector = Icons.Default.MoreVert, contentDescription = "More")
                        }

                        // Menu
                        NodeActionMenu(
                            node = node,
                            expended = expanded,
                            isSelected = selected,
                            dismiss = { expanded = false },
                            setSelected = setSelected,
                            reloadFiles = reloadFiles,
                            onRename = {
                                renameDialogExpanded = true
                            }
                        )
                    }
                }

                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail,
                        contentDescription = node.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Image(
                        painter = thumbnailFromMimeType(node.type),
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSecondaryContainer),
                        contentDescription = node.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(1.15f)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                    )
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
                            Icon(imageVector = Icons.Default.MoreVert, contentDescription = "More")
                        }
                        NodeActionMenu(
                            node = node,
                            expended = expanded,
                            isSelected = selected,
                            dismiss = { expanded = false },
                            setSelected = setSelected,
                            reloadFiles = reloadFiles,
                            onRename = {
                                renameDialogExpanded = true
                            }
                        )
                    }
                },
                leadingContent = {
                    if (thumbnail != null) {
                        Image(
                            bitmap = thumbnail,
                            contentDescription = "Thumbnail",
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
                                contentDescription = "Thumbnail",
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
    dismiss: () -> Unit,
    onRename: () -> Unit,
    setSelected: (isSelected: Boolean) -> Unit,
    reloadFiles: () -> Unit
) {
    val context = LocalContext.current

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
                    val uri = EncryptedContentProvider.getUriFromFile(node)
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    intent.setDataAndType(uri, node.type)
                    intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    dismiss()
                    context.startActivity(intent)
                })
            DropdownMenuItem(
                text = { Text(text = stringResource(id = R.string.export)) },
                enabled = ExplorerActivity.vault.currentMechanism.supportsExport,
                onClick = {
                    val activity = context as ExplorerActivity
                    activity.currentNodeToExport = node
                    activity.exporter.launch("${node.name}.lcef")
                    dismiss()
                })
        }
        DropdownMenuItem(text = { Text(text = stringResource(id = R.string.delete)) }, onClick = {
            node.delete()
            ExplorerActivity.vault.fileTree!!.goToParentOf(node.id)!!.children.remove(node)
            ExplorerActivity.vault.writeFileTree()
            dismiss()
            reloadFiles()
        })
        DropdownMenuItem(text = { Text(text = stringResource(id = R.string.share)) }, onClick = {
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