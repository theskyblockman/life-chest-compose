package fr.theskyblockman.lifechest.explorer.file_readers

import android.view.View
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import fr.theskyblockman.lifechest.vault.EncryptedContentProvider
import fr.theskyblockman.lifechest.vault.FileNode

class VideoReader(override val node: FileNode) : FileReader {
    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null

    @OptIn(UnstableApi::class)
    @Composable
    override fun Reader(fullscreen: Boolean, setFullscreen: (isFullscreen: Boolean) -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AndroidView(factory = { context ->
                player = player ?: ExoPlayer
                    .Builder(context)
                    .build()
                    .apply {
                        setMediaItems(
                            listOf(
                                MediaItem.fromUri(
                                    EncryptedContentProvider.getUriFromFile(node)
                                )
                            )
                        )
                        prepare()
                        play()
                    }
                playerView = playerView ?: PlayerView(context).apply {
                    player = this@VideoReader.player
                    setBackgroundColor(Color.Black.toArgb())
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    setFullscreenButtonClickListener(setFullscreen)
                    keepScreenOn = true
                }
                playerView!!
            })
        }
    }

    override suspend fun load() { }

    override fun unload() {
        playerView?.visibility = View.GONE
        player?.release()
    }

    override fun focused() {
        player?.play()
    }

    override fun unfocused() {
        player?.pause()
    }
}