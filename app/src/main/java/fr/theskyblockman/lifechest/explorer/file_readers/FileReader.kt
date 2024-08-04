package fr.theskyblockman.lifechest.explorer.file_readers

import android.content.Context
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import fr.theskyblockman.lifechest.BuildConfig
import fr.theskyblockman.lifechest.R
import fr.theskyblockman.lifechest.explorer.ExplorerViewModel
import fr.theskyblockman.lifechest.vault.TreeNode

interface FileReader {
    val node: TreeNode

    @Composable
    fun Reader(
        fullscreen: Boolean,
        setFullscreen: (isFullscreen: Boolean) -> Unit
    )

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun TopAppBar(
        name: String,
        pageIndex: Int,
        navController: NavController
    ) {
        androidx.compose.material3.TopAppBar(
            title = {
                if (BuildConfig.DEBUG) {
                    Text(text = "$name - $pageIndex")
                } else {
                    Text(text = name)
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

    fun focused() {}

    fun unfocused() {}

    suspend fun load(context: Context, explorerViewModel: ExplorerViewModel)

    fun unload()
}