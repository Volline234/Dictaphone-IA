package com.example.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
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
import java.io.IOException

class AudioRecorderManager(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var scope = CoroutineScope(Dispatchers.Main)
    private var tickerJob: Job? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0)
    val elapsedSeconds: StateFlow<Int> = _elapsedSeconds.asStateFlow()

    private val _currentAmplitude = MutableStateFlow(0f)
    val currentAmplitude: StateFlow<Float> = _currentAmplitude.asStateFlow()

    private val _amplitudesHistory = MutableStateFlow<List<Float>>(emptyList())
    val amplitudesHistory: StateFlow<List<Float>> = _amplitudesHistory.asStateFlow()

    fun startRecording(): String? {
        stopRecordingInternal()

        val audioDir = File(context.filesDir, "audio_notes").apply { mkdirs() }
        val fileName = "audio_${System.currentTimeMillis()}.mp4"
        val outputFile = File(audioDir, fileName)
        currentOutputFile = outputFile

        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        try {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(128000)
            recorder.setAudioSamplingRate(44100)
            recorder.setOutputFile(outputFile.absolutePath)
            recorder.prepare()
            recorder.start()

            mediaRecorder = recorder
            _isRecording.value = true
            _isPaused.value = false
            _elapsedSeconds.value = 0
            _amplitudesHistory.value = emptyList()

            startTicker()
            return outputFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            stopRecordingInternal()
            return null
        }
    }

    fun pauseRecording() {
        if (_isRecording.value && !_isPaused.value && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                mediaRecorder?.pause()
                _isPaused.value = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun resumeRecording() {
        if (_isRecording.value && _isPaused.value && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                mediaRecorder?.resume()
                _isPaused.value = false
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stopRecording(): String? {
        val path = currentOutputFile?.absolutePath
        stopRecordingInternal()
        return path
    }

    fun cancelRecording() {
        currentOutputFile?.delete()
        currentOutputFile = null
        stopRecordingInternal()
    }

    private fun stopRecordingInternal() {
        tickerJob?.cancel()
        tickerJob = null

        try {
            mediaRecorder?.let {
                it.stop()
                it.reset()
                it.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaRecorder = null
            _isRecording.value = false
            _isPaused.value = false
            _currentAmplitude.value = 0f
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            var totalTime = 0
            while (isActive && _isRecording.value) {
                delay(100)
                if (!_isPaused.value) {
                    totalTime += 100
                    _elapsedSeconds.value = totalTime / 1000

                    val maxAmp = try {
                        mediaRecorder?.maxAmplitude ?: 0
                    } catch (e: Exception) {
                        0
                    }
                    val normalized = (maxAmp / 32767f).coerceIn(0.05f, 1f)
                    _currentAmplitude.value = normalized
                    val currentList = _amplitudesHistory.value.toMutableList()
                    currentList.add(normalized)
                    if (currentList.size > 50) {
                        currentList.removeAt(0)
                    }
                    _amplitudesHistory.value = currentList
                }
            }
        }
    }
}
