package fr.theskyblockman.life_chest.explorer.file_readers

import android.content.Context
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.google.android.material.color.DynamicColorsOptions
import fr.theskyblockman.life_chest.R
import fr.theskyblockman.life_chest.explorer.ExplorerViewModel
import fr.theskyblockman.life_chest.vault.EncryptedContentProvider
import fr.theskyblockman.life_chest.vault.FileNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class AudioReader(override val node: FileNode) : FileReader {
    private var player: ExoPlayer? = null

    @UnstableApi
    @ExperimentalStdlibApi
    @ExperimentalMaterial3Api
    @Composable
    override fun Reader(fullscreen: Boolean, setFullscreen: (isFullscreen: Boolean) -> Unit) {
        var currentPosition by remember { mutableLongStateOf(0L) }
        var isPlaying: Boolean? by remember { mutableStateOf(null) }

        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            player!!.addListener(
                object : Player.Listener {
                    override fun onIsPlayingChanged(isPlayerPlaying: Boolean) {
                        isPlaying = isPlayerPlaying
                    }
                }
            )

            val update = suspend {
                withContext(Dispatchers.Main) {
                    if (player?.isPlaying == true) {
                        currentPosition = player!!.currentPosition
                    }
                }
            }
            scope.launch(Dispatchers.IO) {
                while (true) {
                    delay(1000 / 15)
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

            val metadata: MediaMetadataRetriever = remember {
                MediaMetadataRetriever().apply {
                    setDataSource(context, EncryptedContentProvider.getUriFromFile(node))
                }
            }

            DisposableEffect(metadata) {
                onDispose {
                    metadata.release()
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.4f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                val artwork = remember {
                    val embeddedPicture = metadata.embeddedPicture ?: return@remember null
                    BitmapFactory.decodeByteArray(
                        embeddedPicture,
                        0,
                        embeddedPicture.size
                    )
                }

                if (artwork != null) {
                    val imageBitmap = remember {
                        artwork.asImageBitmap()
                    }

                    var seed: Color? by remember {
                        mutableStateOf(null)
                    }


                    LaunchedEffect(artwork) {
                        scope.launch(Dispatchers.IO) {
                            seed = Color(
                                DynamicColorsOptions.Builder()
                                    .setContentBasedSource(
                                        artwork
                                    )
                                    .build()
                                    .contentBasedSeedColor ?: return@launch
                            )
                        }
                    }

                    Image(
                        bitmap = imageBitmap,
                        contentDescription = stringResource(R.string.artwork_for_audio_file),
                        modifier = Modifier
                            .fillMaxWidth(.75f)
                            .aspectRatio(1f)
                            .shadow(
                                ambientColor = seed ?: MaterialTheme.colorScheme.primary,
                                spotColor = seed ?: MaterialTheme.colorScheme.primary,
                                shape = MaterialTheme.shapes.extraLarge,
                                elevation = 128.dp
                            )
                    )
                } else {
                    Image(
                        painter = painterResource(R.drawable.outline_music_note_24),
                        contentDescription = stringResource(R.string.audio_file_without_artwork),
                        modifier = Modifier
                            .fillMaxWidth(.5f)
                            .aspectRatio(1f),
                        colorFilter = ColorFilter.tint(
                            MaterialTheme.colorScheme.onBackground
                        )
                    )
                }

                val title = remember {
                    metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
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
                        .fillMaxWidth(.95f)
                        .align(Alignment.CenterHorizontally)
                    )

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth(.9f)
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 6.dp)
                    ) {
                        Text(
                            currentPosition.milliseconds.inWholeSeconds.seconds.toString(),
                            maxLines = 1,
                            textAlign = TextAlign.Start
                        )
                        
                        if (player!!.duration == Long.MIN_VALUE + 1) {
                            Text(
                                "00:00",
                                maxLines = 1,
                                textAlign = TextAlign.End
                            )
                        } else {
                            Text(
                                player!!.duration.milliseconds.inWholeSeconds.seconds.toString(),
                                maxLines = 1,
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    IconButton(
                        modifier = Modifier.scale(1.65f),
                        enabled = player != null && isPlaying != null,
                        onClick = {
                            player!!.seekBack()
                            currentPosition = player!!.currentPosition
                        }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.outline_fast_rewind_24),
                            contentDescription = stringResource(androidx.media3.session.R.string.media3_controls_seek_back_description),
                        )
                    }

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
                        if (isPlaying == null || player!!.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.scale(.75f)
                            )
                        } else if (isPlaying!!) {
                            Icon(
                                painter = painterResource(R.drawable.outline_pause_24),
                                contentDescription = stringResource(R.string.pause),
                            )
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.outline_play_arrow_24),
                                contentDescription = stringResource(R.string.play),
                            )
                        }

                    }

                    IconButton(
                        modifier = Modifier.scale(1.65f),
                        enabled = player != null && isPlaying != null,
                        onClick = {
                            player!!.seekForward()
                            currentPosition = player!!.currentPosition
                        }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.outline_fast_forward_24),
                            contentDescription = stringResource(androidx.media3.session.R.string.media3_controls_seek_forward_description),
                        )
                    }
                }
            }
        }
    }

    override suspend fun load(context: Context, explorerViewModel: ExplorerViewModel) {
        Log.d("AudioReader", "Loading audio reader")
        player = ExoPlayer
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