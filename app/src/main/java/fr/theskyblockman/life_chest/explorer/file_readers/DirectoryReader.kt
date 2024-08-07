package fr.theskyblockman.life_chest.explorer.file_readers

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import fr.theskyblockman.life_chest.BuildConfig
import fr.theskyblockman.life_chest.R
import fr.theskyblockman.life_chest.explorer.ExplorerViewModel
import fr.theskyblockman.life_chest.vault.TreeNode

class DirectoryReader(override val node: TreeNode) : FileReader {
    @Composable
    override fun Reader(fullscreen: Boolean, setFullscreen: (isFullscreen: Boolean) -> Unit) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            ListItem(
                supportingContent = {
                    Text(text = node.name)
                },
                headlineContent = {
                    Text(text = stringResource(R.string.title_directory_info_title))
                }
            )
            ListItem(
                supportingContent = {
                    Text(text = node.count().toString())
                },
                headlineContent = {
                    Text(text = stringResource(R.string.file_amount_directory_info_title))
                }
            )
            ListItem(
                supportingContent = {
                    Text(text = node.size.toString())
                },
                headlineContent = {
                    Text(text = stringResource(R.string.file_sizes_with_children_directory_info_title))
                }
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun TopAppBar(
        name: String,
        pageIndex: Int,
        navController: NavController
    ) {
        androidx.compose.material3.TopAppBar(
            title = {
                if (BuildConfig.DEBUG) {
                    Text(text = "${stringResource(R.string.title_directory, name)} - $pageIndex")
                } else {
                    Text(text = stringResource(R.string.title_directory, name))
                }
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
    }

    override suspend fun load(context: Context, explorerViewModel: ExplorerViewModel) {
        return
    }

    override fun unload() {
        return
    }
}