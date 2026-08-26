package com.example.audio

import android.content.Context
import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class AudioPlayerManager(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var tickerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

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
        mediaPlayer?.let {
            val targetMs = (progressFraction * it.duration).toInt()
            it.seekTo(targetMs)
            _currentPositionMs.value = targetMs
            _progress.value = progressFraction
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

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive && _isPlaying.value) {
                delay(100)
                mediaPlayer?.let {
                    if (it.isPlaying && it.duration > 0) {
                        _currentPositionMs.value = it.currentPosition
                        _progress.value = (it.currentPosition.toFloat() / it.duration.toFloat()).coerceIn(0f, 1f)
                    }
                }
            }
        }
    }
}
