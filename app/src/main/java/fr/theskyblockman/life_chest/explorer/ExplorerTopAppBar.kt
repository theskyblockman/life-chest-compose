package fr.theskyblockman.life_chest.explorer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import fr.theskyblockman.life_chest.R
import fr.theskyblockman.life_chest.vault.TreeNode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerTopAppBar(
    selectedElements: Set<String>,
    current: TreeNode?,
    sortMethod: SortMethod,
    fileImportsState: FileImportsState,
    isGridView: Boolean,
    onNavigationClick: () -> Unit,
    onToggleView: () -> Unit,
    onInvertSelection: () -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onCreateDirectory: () -> Unit,
    setSortMethod: (SortMethod) -> Unit,
) {
    val isSelectionMode = selectedElements.isNotEmpty()

    Column {
        TopAppBar(
            title = {
                Text(
                    text = if (isSelectionMode) {
                        stringResource(R.string.selected_files, selectedElements.size)
                    } else {
                        current?.name ?: ""
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                if (isSelectionMode) {
                    IconButton(onClick = onClearSelection) {
                        Icon(
                            painter = painterResource(R.drawable.outline_close_24),
                            contentDescription = stringResource(R.string.exit_selection_mode)
                        )
                    }
                } else {
                    IconButton(onClick = onNavigationClick) {
                        Icon(
                            painter = painterResource(R.drawable.outline_arrow_back_24),
                            contentDescription = stringResource(R.string.go_back)
                        )
                    }
                }

            },
            actions = {
                IconButton(onClick = onToggleView) {
                    Icon(
                        painter = painterResource(
                            id = if (isGridView) {
                                R.drawable.outline_list_24
                            } else {
                                R.drawable.outline_grid_view_24
                            }
                        ),
                        contentDescription = stringResource(R.string.toggle_view)
                    )
                }

                // More actions dropdown
                var expanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            painter = painterResource(R.drawable.outline_more_vert_24),
                            contentDescription = stringResource(R.string.more)
                        )
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        if (selectedElements.size != current!!.children.size) {
                            DropdownMenuItem(
                                { Text(stringResource(R.string.select_all)) },
                                onClick = {
                                    expanded = false
                                    onSelectAll()
                                }
                            )
                        }
                        if (isSelectionMode) {
                            DropdownMenuItem(
                                { Text(stringResource(R.string.invert_selection)) },
                                onClick = {
                                    expanded = false
                                    onInvertSelection()
                                }
                            )
                            DropdownMenuItem(
                                { Text(stringResource(R.string.delete_selection)) },
                                onClick = {
                                    expanded = false
                                    onDeleteSelection()
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                { Text(stringResource(R.string.new_directory)) },
                                onClick = {
                                    expanded = false
                                    onCreateDirectory()
                                }
                            )
                            var sortByExpanded by remember { mutableStateOf(false) }
                            DropdownMenu(
                                sortByExpanded,
                                onDismissRequest = { sortByExpanded = false }) {
                                for (possibleSortMethod in SortMethod.entries) {
                                    DropdownMenuItem(
                                        { Text(stringResource(possibleSortMethod.displayName)) },
                                        leadingIcon = {
                                            RadioButton(
                                                selected = sortMethod == possibleSortMethod,
                                                onClick = null
                                            )
                                        },
                                        onClick = {
                                            sortByExpanded = false
                                            expanded = false
                                            setSortMethod(possibleSortMethod)
                                        }
                                    )
                                }
                            }

                            DropdownMenuItem(
                                { Text(stringResource(R.string.sort_by)) },
                                trailingIcon = {
                                    Icon(
                                        painterResource(R.drawable.outline_arrow_right_24),
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    sortByExpanded = true
                                }
                            )
                        }
                    }
                }
            },
            colors = if (isSelectionMode) TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) else TopAppBarDefaults.topAppBarColors()
        )

        if (fileImportsState.filesPicked) {
            if (fileImportsState.fileProcessed == null || fileImportsState.fileAmount == null) {
                LinearProgressIndicator()
            } else {
                LinearProgressIndicator(
                    progress = {
                        var result =
                            (fileImportsState.fileProcessed.toFloat() / fileImportsState.fileAmount.toFloat())

                        if (result.isNaN()) {
                            result = 0f
                        }

                        result
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}