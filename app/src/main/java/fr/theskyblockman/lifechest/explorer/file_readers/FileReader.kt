package fr.theskyblockman.lifechest.explorer.file_readers

import androidx.compose.runtime.Composable
import fr.theskyblockman.lifechest.vault.TreeNode

interface FileReader {
    val node: TreeNode

    @Composable
    fun Reader(fullscreen: Boolean,
               setFullscreen: (isFullscreen: Boolean) -> Unit)

    fun focused() {}

    fun unfocused() {}

    suspend fun load()

    fun unload()
}