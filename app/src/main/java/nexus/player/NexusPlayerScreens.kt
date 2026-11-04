@file:OptIn(UnstableApi::class)

package nexus.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Tipo de player: /live/ = canais, /series/ = séries, restante = filmes. */
enum class PlayerKind { LIVE, MOVIE, SERIES }

fun detectPlayerKind(url: String): PlayerKind = when {
    "/live/" in url -> PlayerKind.LIVE
    "/series/" in url -> PlayerKind.SERIES
    else -> PlayerKind.MOVIE
}

private val ControlWhite = Color.White
private val ControlMuted = Color(0xFF6B6B70)
private val ButtonFrom = Color(0xFF5B3FD1)
private val ButtonTo = Color(0xFF2E3A9E)
private val SeriesBackdrop = Color(0xFF575757)
private const val SEEK_MS = 10_000L

private val resizeModes = listOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIT,
    AspectRatioFrameLayout.RESIZE_MODE_FILL,
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
)
private val playbackSpeeds = listOf(1f, 1.25f, 1.5f, 2f, 0.5f)

/** Ponto de entrada usado pela NexusApp para a rota Player. */
@Composable
fun NexusPlayerRoute(
    url: String,
    title: String,
    onBack: () -> Unit,
    nowTitle: String? = null,
    nowRange: String? = null,
    nextTitle: String? = null,
    nextRange: String? = null,
    logoUrl: String? = null,
    onChannelList: () -> Unit = {},
    onPrevChannel: () -> Unit = {},
    onNextChannel: () -> Unit = {},
    onEpisodes: () -> Unit = {},
    onNextEpisode: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    when (detectPlayerKind(url)) {
        PlayerKind.LIVE -> NexusLivePlayerScreen(
            url, title, onBack, nowTitle, nowRange, nextTitle, nextRange,
            logoUrl, onChannelList, onPrevChannel, onNextChannel, modifier
        )
        PlayerKind.MOVIE -> NexusMoviePlayerScreen(url, title, onBack, modifier)
        PlayerKind.SERIES -> NexusSeriesPlayerScreen(url, title, onBack, onEpisodes, onNextEpisode, modifier)
    }
}

@Composable
fun NexusLivePlayerScreen(
    url: String,
    title: String,
    onBack: () -> Unit,
    nowTitle: String? = null,
    nowRange: String? = null,
    nextTitle: String? = null,
    nextRange: String? = null,
    logoUrl: String? = null,
    onChannelList: () -> Unit = {},
    onPrevChannel: () -> Unit = {},
    onNextChannel: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val player = rememberExoPlayer(url)
    val clock = rememberPlayerClock(player)
    val levels = rememberSystemLevels()
    var resizeIndex by remember { mutableIntStateOf(0) }
    var locked by remember { mutableStateOf(false) }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        VideoSurface(player, resizeModes[resizeIndex], Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0x66000000), Color.Transparent, Color(0xB3000000)))
            )
        )
        if (locked) {
            CircleButton(Icons.Filled.Lock, { locked = false }, Modifier.align(Alignment.CenterEnd).padding(end = 24.dp))
        } else {
            Column(Modifier.fillMaxSize()) {
                TopBar(title, onBack) { locked = true }
                Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    LevelColumn(Icons.Filled.BrightnessHigh, levels.brightness, levels::setBrightness)
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(72.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BigIcon(Icons.Filled.SkipPrevious, 64.dp, onPrevChannel)
                            BigIcon(if (clock.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, 72.dp) {
                                player.togglePlay()
                            }
                            BigIcon(Icons.Filled.SkipNext, 64.dp, onNextChannel)
                        }
                    }
                    LevelColumn(Icons.Filled.VolumeUp, levels.volume, levels::setVolume)
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    if (logoUrl != null) {
                        AsyncImage(
                            model = logoUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        if (nowTitle != null) EpgLine("Agora: $nowTitle")
                        if (nextTitle != null) EpgLine("Próximo: $nextTitle")
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        if (nowRange != null) EpgLine(nowRange)
                        if (nextRange != null) EpgLine(nextRange)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(48.dp, Alignment.CenterHorizontally)
                ) {
                    BottomAction(Icons.Filled.Layers, "Lista de Canais", onChannelList)
                    BottomAction(Icons.Filled.Fullscreen, "Proporção da tela") {
                        resizeIndex = (resizeIndex + 1) % resizeModes.size
                    }
                    BottomAction(Icons.Filled.Apps, "Multi-tela") {}
                }
            }
        }
    }
}

@Composable
fun NexusMoviePlayerScreen(
    url: String,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    VodPlayer(url, title, onBack, Color.Black, false, {}, {}, modifier)
}

@Composable
fun NexusSeriesPlayerScreen(
    url: String,
    title: String,
    onBack: () -> Unit,
    onEpisodes: () -> Unit = {},
    onNextEpisode: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    VodPlayer(url, title, onBack, SeriesBackdrop, true, onEpisodes, onNextEpisode, modifier)
}

@Composable
private fun VodPlayer(
    url: String,
    title: String,
    onBack: () -> Unit,
    backdrop: Color,
    isSeries: Boolean,
    onEpisodes: () -> Unit,
    onNextEpisode: () -> Unit,
    modifier: Modifier
) {
    val player = rememberExoPlayer(url)
    val clock = rememberPlayerClock(player)
    val levels = rememberSystemLevels()
    var resizeIndex by remember { mutableIntStateOf(0) }
    var speedIndex by remember { mutableIntStateOf(0) }
    var locked by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val duration = clock.duration
    val fraction = if (duration > 0L) {
        (clock.position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val speed = playbackSpeeds[speedIndex]
    val speedText = if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()

    Box(modifier.fillMaxSize().background(backdrop)) {
        VideoSurface(player, resizeModes[resizeIndex], Modifier.fillMaxSize())
        if (locked) {
            CircleButton(Icons.Filled.Lock, { locked = false }, Modifier.align(Alignment.CenterEnd).padding(end = 24.dp))
        } else {
            Column(Modifier.fillMaxSize()) {
                TopBar(title, onBack) { locked = true }
                Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    LevelColumn(Icons.Filled.BrightnessHigh, levels.brightness, levels::setBrightness)
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(96.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BigIcon(Icons.Filled.Replay10, 56.dp) {
                                player.seekTo((player.currentPosition - SEEK_MS).coerceAtLeast(0L))
                            }
                            BigIcon(if (clock.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, 72.dp) {
                                player.togglePlay()
                            }
                            BigIcon(Icons.Filled.Forward10, 56.dp) {
                                player.seekTo(player.currentPosition + SEEK_MS)
                            }
                        }
                    }
                    LevelColumn(Icons.Filled.VolumeUp, levels.volume, levels::setVolume)
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        formatTime(dragFraction?.let { (it * duration).toLong() } ?: clock.position),
                        color = ControlWhite,
                        fontSize = 16.sp
                    )
                    Slider(
                        value = dragFraction ?: fraction,
                        onValueChange = { dragFraction = it },
                        onValueChangeFinished = {
                            dragFraction?.let { f -> player.seekTo((f * duration).toLong()) }
                            dragFraction = null
                        },
                        enabled = duration > 0L,
                        modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = ControlWhite,
                            activeTrackColor = ControlWhite,
                            inactiveTrackColor = ControlMuted
                        )
                    )
                    Text(formatTime(duration), color = ControlWhite, fontSize = 16.sp)
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(40.dp, Alignment.CenterHorizontally)
                ) {
                    if (isSeries) BottomAction(Icons.Filled.Layers, "EPISÓDIOS", onEpisodes)
                    BottomAction(Icons.Filled.Fullscreen, "Proporção da tela") {
                        resizeIndex = (resizeIndex + 1) % resizeModes.size
                    }
                    BottomAction(Icons.Filled.Speed, "Velocidade (${speedText}x)") {
                        speedIndex = (speedIndex + 1) % playbackSpeeds.size
                        player.setPlaybackSpeed(playbackSpeeds[speedIndex])
                    }
                    if (isSeries) BottomAction(Icons.Filled.SkipNext, "Próximo episódio", onNextEpisode)
                }
            }
        }
    }
}

@Composable
private fun rememberExoPlayer(url: String): ExoPlayer {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().also {
            it.setMediaItem(MediaItem.fromUri(url))
            it.prepare()
            it.playWhenReady = true
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    return player
}

private class PlayerClock {
    var position by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)
    var playing by mutableStateOf(false)
}

@Composable
private fun rememberPlayerClock(player: ExoPlayer): PlayerClock {
    val clock = remember(player) { PlayerClock() }
    LaunchedEffect(player) {
        while (true) {
            clock.position = player.currentPosition.coerceAtLeast(0L)
            clock.duration = player.duration.coerceAtLeast(0L)
            clock.playing = player.isPlaying
            delay(400L)
        }
    }
    return clock
}

private fun ExoPlayer.togglePlay() {
    if (isPlaying) pause() else play()
}

@Composable
private fun VideoSurface(player: ExoPlayer, resizeMode: Int, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                this.player = player
                setResizeMode(resizeMode)
            }
        },
        update = { view ->
            view.player = player
            view.setResizeMode(resizeMode)
        }
    )
}

private class SystemLevels(private val activity: Activity?, private val audio: AudioManager) {
    private val maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)

    var brightness by mutableFloatStateOf(
        activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: 0.5f
    )

    var volume by mutableFloatStateOf(
        audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
    )

    fun setBrightness(value: Float) {
        brightness = value.coerceIn(0.01f, 1f)
        activity?.window?.let { window ->
            val params = window.attributes
            params.screenBrightness = brightness
            window.attributes = params
        }
    }

    fun setVolume(value: Float) {
        volume = value.coerceIn(0f, 1f)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (volume * maxVolume).roundToInt(), 0)
    }

    fun resetBrightness() {
        activity?.window?.let { window ->
            val params = window.attributes
            params.screenBrightness = -1f
            window.attributes = params
        }
    }
}

@Composable
private fun rememberSystemLevels(): SystemLevels {
    val context = LocalContext.current
    val levels = remember {
        SystemLevels(
            context as? Activity,
            context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        )
    }
    DisposableEffect(levels) {
        onDispose { levels.resetBrightness() }
    }
    return levels
}

@Composable
private fun LevelColumn(
    icon: ImageVector,
    level: Float,
    onLevel: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.width(56.dp).fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = ControlWhite, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .width(28.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, _ ->
                        val h = size.height.toFloat()
                        if (h > 0f) onLevel(1f - (change.position.y / h).coerceIn(0f, 1f))
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(ControlMuted),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(level.coerceIn(0f, 1f))
                        .background(ControlWhite)
                )
            }
        }
    }
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit, onLock: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircleButton(Icons.Filled.ArrowBack, onBack)
        Spacer(Modifier.width(24.dp))
        Text(
            title,
            color = ControlWhite,
            fontSize = 20.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Icon(Icons.Filled.Cast, contentDescription = null, tint = ControlWhite, modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(32.dp))
        Icon(
            Icons.Filled.LockOpen,
            contentDescription = null,
            tint = ControlWhite,
            modifier = Modifier.size(32.dp).clickable(onClick = onLock)
        )
        Spacer(Modifier.width(32.dp))
        CircleButton(Icons.Filled.Settings, {})
    }
}

@Composable
private fun CircleButton(icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(ButtonFrom, ButtonTo)))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = ControlWhite, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun BigIcon(icon: ImageVector, size: Dp, onClick: () -> Unit) {
    Icon(
        icon,
        contentDescription = null,
        tint = ControlWhite,
        modifier = Modifier.size(size).clickable(onClick = onClick)
    )
}

@Composable
private fun BottomAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = ControlWhite, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = ControlWhite, fontSize = 16.sp, maxLines = 1)
    }
}

@Composable
private fun EpgLine(text: String) {
    Text(
        text,
        color = ControlWhite,
        fontSize = 18.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

private fun formatTime(ms: Long): String {
    val total = ms.coerceAtLeast(0L) / 1000L
    val h = total / 3600L
    val m = (total % 3600L) / 60L
    val s = total % 60L
    return if (h > 0L) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
