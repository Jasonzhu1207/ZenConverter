package org.zenconverter.app.ui.studio

import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import org.zenconverter.app.studio.StudioStore
import java.io.File

/** Remembers a single-file picker restricted to audio. */
@Composable
internal fun rememberAudioPicker(onPicked: (Uri) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onPicked(uri)
    }
    return remember(launcher) { { launcher.launch(arrayOf("audio/*")) } }
}

/**
 * Photo picker for images. [ActivityResultContracts.PickVisualMedia] uses the
 * system picker on Android 13+ and the scoped-storage picker before that, so no
 * broad storage permission is needed.
 */
@Composable
internal fun rememberImagePicker(onPicked: (Uri) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onPicked(uri)
    }
    return remember(launcher) {
        { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    }
}

@Composable
internal fun rememberFolderPicker(onPicked: (Uri) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) onPicked(uri)
    }
    return remember(launcher) { { launcher.launch(null) } }
}

/**
 * Minimal local playback helper. Uses the platform MediaPlayer so no extra
 * dependency is needed, and releases the player when the page leaves.
 */
internal class StudioPlayer {
    private var player: MediaPlayer? = null
    var isPlaying by mutableStateOf(false)
        private set
    var currentLabel by mutableStateOf<String?>(null)
        private set

    fun play(uri: Uri, label: String, onError: () -> Unit = {}) {
        stop()
        val context = appContext ?: return
        val media = MediaPlayer()
        val started = runCatching {
            media.setDataSource(context, uri)
            media.setOnCompletionListener { clearPlayback() }
            media.setOnErrorListener { _, _, _ ->
                clearPlayback()
                onError()
                true
            }
            media.prepare()
            media.start()
        }.isSuccess
        if (started) {
            player = media
            isPlaying = true
            currentLabel = label
        } else {
            runCatching { media.release() }
            clearPlayback()
            onError()
        }
    }

    private fun clearPlayback() {
        isPlaying = false
        currentLabel = null
    }

    fun stop() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        isPlaying = false
        currentLabel = null
    }

    internal var appContext: android.content.Context? = null
}

@Composable
internal fun rememberStudioPlayer(): StudioPlayer {
    val context = LocalContext.current
    val player = remember { StudioPlayer() }
    player.appContext = context.applicationContext
    DisposableEffect(player) {
        onDispose { player.stop() }
    }
    return player
}

internal fun tempOutput(context: android.content.Context, prefix: String, extension: String): File =
    File.createTempFile(prefix, ".$extension", context.cacheDir)

/**
 * Speaks short result messages when the accessibility "audio descriptions"
 * preference is on, so a screen reader user hears what an edit produced without
 * having to hunt for the status line. A no-op when the preference is off.
 */
@Composable
internal fun rememberResultAnnouncer(): (String) -> Unit {
    val context = LocalContext.current
    val tts = remember {
        runCatching { android.speech.tts.TextToSpeech(context) { } }.getOrNull()
    }
    DisposableEffect(tts) {
        onDispose { runCatching { tts?.shutdown() } }
    }
    return remember(tts) {
        { message ->
            if (StudioStore.audioDescriptions.value && message.isNotBlank()) {
                runCatching {
                    tts?.speak(message, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "zen-result")
                }
            }
        }
    }
}
