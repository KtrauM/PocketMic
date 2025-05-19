package io.github.ktraum.pocketmic

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log

class AudioCaptureService {
    private val TAG = "AudioCaptureService"
    private val SAMPLE_RATE = 44100
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    private val BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * 2
        
    private var audioRecord: AudioRecord? = null
    private var isRecording = false

    val BUFFER_SIZE_ACTUAL get() = BUFFER_SIZE
    
    fun start() {
        if (isRecording) return
        audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, BUFFER_SIZE)
        audioRecord?.startRecording()
        isRecording = true
        Log.d(TAG, "Audio recording started")
    }

    fun stop() {
        if (!isRecording) return
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        isRecording = false
        Log.d(TAG, "Audio recording stopped")
    }

    fun read(buffer: ShortArray, offsetInShorts: Int, sizeInShorts: Int): Int {
        return audioRecord?.read(buffer, offsetInShorts, sizeInShorts) ?: 0
    }
} 