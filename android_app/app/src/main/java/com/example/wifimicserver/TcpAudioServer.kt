package io.github.ktraum.pocketmic

import android.util.Log
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

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
            while (isRunning && clientSocket?.isConnected == true) {
                val bytesRead = audioCaptureService.read(buffer, 0, buffer.size)
                if (bytesRead > 0) {
                    val byteArray = ShortArray(bytesRead) { buffer[it] }.toByteArray()
                    outputStream?.write(byteArray)
                    outputStream?.flush()
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error streaming audio", e)
        } finally {
            try {
                clientSocket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing client socket", e)
            }
        }
    }

    private fun ShortArray.toByteArray(): ByteArray {
        val byteArray = ByteArray(size * 2)
        for (i in indices) {
            byteArray[i * 2] = (this[i].toInt() and 0xFF).toByte()
            byteArray[i * 2 + 1] = (this[i].toInt() shr 8 and 0xFF).toByte()
        }
        return byteArray
    }
} 