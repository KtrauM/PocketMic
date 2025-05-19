package io.github.ktraum.pocketmic

import android.util.Log
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.nio.ByteBuffer
import java.nio.ByteOrder

class TcpAudioServer(private val port: Int, private val audioCaptureService: AudioCaptureService) {
    private val TAG = "TcpAudioServer"
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var isRunning = false
    private val executor = Executors.newSingleThreadExecutor()

    fun start() {
        if (isRunning) return
        executor.execute {
            try {
                serverSocket = ServerSocket(port)
                isRunning = true
                Log.d(TAG, "Server started on port $port")
                while (isRunning) {
                    try {
                        clientSocket = serverSocket?.accept()
                        Log.d(TAG, "Client connected")
                        streamAudio()
                    } catch (e: IOException) {
                        Log.e(TAG, "Error accepting client connection", e)
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error starting server", e)
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            clientSocket?.close()
            serverSocket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing sockets", e)
        }
        executor.shutdown()
    }

    private fun streamAudio() {
        val buffer = ShortArray(1024)
        try {
            val outputStream = clientSocket?.getOutputStream()
            // Start audio capture before streaming
            audioCaptureService.start()
            Log.d(TAG, "Audio capture started")
            
            while (isRunning && clientSocket?.isConnected == true) {
                val bytesRead = audioCaptureService.read(buffer, 0, buffer.size)
                if (bytesRead > 0) {
                    val byteArray = ShortArray(bytesRead) { buffer[it] }.toByteArray()
                    outputStream?.write(byteArray)
                    outputStream?.flush()
                    Log.d(TAG, "Sent $bytesRead samples")
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error streaming audio", e)
        } finally {
            try {
                audioCaptureService.stop()
                clientSocket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing client socket", e)
            }
        }
    }

    private fun ShortArray.toByteArray(): ByteArray {
        val byteBuffer = ByteBuffer.allocate(size * 2)
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
        for (sample in this) {
            byteBuffer.putShort(sample)
        }
        return byteBuffer.array()
    }
} 