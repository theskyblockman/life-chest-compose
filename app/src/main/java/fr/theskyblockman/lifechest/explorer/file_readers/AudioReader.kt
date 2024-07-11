package fr.theskyblockman.lifechest.explorer.file_readers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.google.android.material.color.DynamicColorsOptions
import fr.theskyblockman.lifechest.R
import fr.theskyblockman.lifechest.vault.EncryptedContentProvider
import fr.theskyblockman.lifechest.vault.FileNode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class AudioReader(override val node: FileNode) : FileReader {
    private var player: ExoPlayer? = null
    private var metadata: MediaMetadataRetriever? = null
    private var artwork: Bitmap? = null
    private var color: Color? = null

    @UnstableApi
    @ExperimentalStdlibApi
    @ExperimentalMaterial3Api
    @Composable
    override fun Reader(fullscreen: Boolean, setFullscreen: (isFullscreen: Boolean) -> Unit) {
        var currentPosition by remember { mutableLongStateOf(0L) }
        var isPlaying: Boolean? by remember { mutableStateOf(null) }

        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            val update = fun() {
                if (player?.isPlaying == true) {
                    currentPosition = player!!.currentPosition
                }
            }
            scope.launch {
                while (true) {
                    delay(1000 / 30)
                    update()
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val context = LocalContext.current

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
                    addListener(
                        object : Player.Listener {
                            override fun onIsPlayingChanged(isPlayerPlaying: Boolean) {
                                isPlaying = isPlayerPlaying
                            }
                        }
                    )
                    play()
                }

            metadata = metadata ?: MediaMetadataRetriever().apply {
                setDataSource(context, EncryptedContentProvider.getUriFromFile(node))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.25f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                if (artwork != null || metadata!!.embeddedPicture != null) {
                    if (artwork == null) {
                        val embeddedPicture = metadata!!.embeddedPicture!!

                        artwork = artwork ?: BitmapFactory.decodeByteArray(
                            embeddedPicture,
                            0,
                            embeddedPicture.size
                        )
                    }

                    if (color == null) {
                        val seed = DynamicColorsOptions.Builder()
                            .setContentBasedSource(artwork!!)
                            .build()
                            .contentBasedSeedColor

                        if (seed != null) {
                            color = Color(seed)
                        } else {
                            color = MaterialTheme.colorScheme.primary
                        }
                    }

                    Image(
                        bitmap = artwork!!.asImageBitmap(),
                        contentDescription = "Artwork for audio file",
                        modifier = Modifier
                            .fillMaxWidth(.75f)
                            .aspectRatio(1f)
                            .shadow(
                                ambientColor = color!!,
                                spotColor = color!!,
                                shape = MaterialTheme.shapes.extraLarge,
                                elevation = 128.dp
                            )
                    )
                } else {
                    Image(
                        painter = painterResource(R.drawable.outline_audio_file_24),
                        contentDescription = "Audio file without artwork",
                        modifier = Modifier
                            .fillMaxWidth(.5f)
                            .aspectRatio(1f)
                    )
                }

                val title = remember {
                    metadata!!.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                        ?: node.name
                }

                Text(
                    title, style = MaterialTheme.typography.headlineLarge
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    Slider(currentPosition / player!!.duration.toFloat(), onValueChange = {
                        if (player!!.isPlaying) {
                            player!!.pause()
                        }

                        currentPosition = (it * player!!.duration).toLong()
                        player!!.seekTo(currentPosition)
                    }, onValueChangeFinished = {
                        player!!.play()
                    }, modifier = Modifier
                        .weight(7f)
                        .fillMaxWidth(.95f)
                        .align(Alignment.CenterHorizontally)
                    )

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth(.9f)
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 8.dp)
                    ) {
                        Text(
                            currentPosition.milliseconds.inWholeSeconds.seconds.toString(),
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            player!!.duration.milliseconds.inWholeSeconds.seconds.toString(),
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    FilledTonalIconButton(
                        modifier = Modifier.scale(1.75f),
                        enabled = player != null,
                        onClick = {
                            if (player!!.isPlaying) {
                                player!!.pause()
                            } else {
                                player!!.play()
                            }
                        }
                    ) {
                        if (isPlaying == null) {
                            CircularProgressIndicator()
                        } else if (isPlaying!!) {
                            Icon(
                                painter = painterResource(R.drawable.outline_pause_24),
                                contentDescription = "Pause",
                            )
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.outline_play_arrow_24),
                                contentDescription = "Play",
                            )
                        }

                    }
                }
            }
        }
    }

    override suspend fun load() {
        Log.d("AudioReader", "Loading audio reader")
    }

    override fun unload() {
        player?.release()
    }

    override fun focused() {
        player?.play()
    }

    override fun unfocused() {
        player?.pause()
    }
}