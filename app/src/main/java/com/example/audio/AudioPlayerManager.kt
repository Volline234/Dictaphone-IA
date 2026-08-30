package com.example.audio

import android.content.Context
import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class AudioPlayerManager(context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var tickerJob: Job? = null

    // Managed CoroutineScope — cancelled in release() to prevent memory leaks
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPlayingPath = MutableStateFlow<String?>(null)
    val currentPlayingPath: StateFlow<String?> = _currentPlayingPath.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _durationMs = MutableStateFlow(0)
    val durationMs: StateFlow<Int> = _durationMs.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0)
    val currentPositionMs: StateFlow<Int> = _currentPositionMs.asStateFlow()

    fun playAudio(filePath: String) {
        if (_isPlaying.value && _currentPlayingPath.value == filePath) {
            pause()
            return
        }

        stop()

        val file = File(filePath)
        if (!file.exists()) return

        try {
            val player = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()

                setOnCompletionListener {
                    stop()
                }

                // Handle playback errors (corrupted file, unsupported codec, etc.)
                // Without this, MediaPlayer enters Error state silently and _isPlaying
                // stays true indefinitely, freezing the UI playback controls.
                setOnErrorListener { _, what, extra ->
                    android.util.Log.e("AudioPlayerManager", "MediaPlayer error: what=$what extra=$extra")
                    stop()
                    true // true = we handled the error
                }
            }
            mediaPlayer = player
            _durationMs.value = player.duration
            _currentPlayingPath.value = filePath
            player.start()
            _isPlaying.value = true

            startTicker()
        } catch (e: Exception) {
            e.printStackTrace()
            stop()
        }
    }

    fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                _isPlaying.value = false
            }
        }
    }

    fun resume() {
        mediaPlayer?.let {
            it.start()
            _isPlaying.value = true
        }
    }

    fun seekTo(progressFraction: Float) {
        try {
            mediaPlayer?.let {
                val duration = it.duration
                if (duration > 0) {
                    val targetMs = (progressFraction * duration).toInt()
                    it.seekTo(targetMs)
                    _currentPositionMs.value = targetMs
                    _progress.value = progressFraction
                }
            }
        } catch (e: IllegalStateException) {
            // MediaPlayer is in an invalid state (released, not prepared, or in error)
            // Silently ignore — the user can retry after playback restarts
            e.printStackTrace()
        }
    }

    fun stop() {
        tickerJob?.cancel()
        tickerJob = null

        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.reset()
                it.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaPlayer = null
            _isPlaying.value = false
            _currentPlayingPath.value = null
            _progress.value = 0f
            _currentPositionMs.value = 0
            _durationMs.value = 0
        }
    }

    /**
     * Releases all resources held by this manager.
     * Must be called when the ViewModel is cleared to prevent memory leaks
     * from the internal CoroutineScope.
     */
    fun release() {
        stop()
        scope.cancel()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive && _isPlaying.value) {
                delay(100)
                try {
                    mediaPlayer?.let {
                        if (it.isPlaying && it.duration > 0) {
                            _currentPositionMs.value = it.currentPosition
                            _progress.value = (it.currentPosition.toFloat() / it.duration.toFloat()).coerceIn(0f, 1f)
                        }
                    }
                } catch (e: IllegalStateException) {
                    // MediaPlayer was released between the check and the call
                    break
                }
            }
        }
    }
}
