package fr.theskyblockman.lifechest.explorer.file_readers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import fr.theskyblockman.lifechest.vault.TreeNode

class DirectoryReader(override val node: TreeNode) : FileReader {
    @Composable
    override fun Reader(fullscreen: Boolean, setFullscreen: (isFullscreen: Boolean) -> Unit) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = node.name, textAlign = TextAlign.Center)
        }
    }

    override suspend fun load() {
        return
    }

    override fun unload() {
        return
    }

}