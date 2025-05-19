package io.github.ktraum.pocketmic

import android.util.Log
import java.io.DataOutputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class TcpAudioServer(private val port: Int, private val audioCaptureService: AudioCaptureService) {
    private val TAG = "TcpAudioServer"
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var dataOutputStream: DataOutputStream? = null
    private val isRunning = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor()
    private val clientHandlerExecutor = Executors.newSingleThreadExecutor() 

    fun start() {
        if (isRunning.getAndSet(true)) {
            Log.w(TAG, "Server already running")
            return
        }
        executor.execute {
            try {
                serverSocket = ServerSocket(port)
                Log.d(TAG, "Server socket listening on port $port")
                while (isRunning.get()) {
                    try {
                        Log.d(TAG, "Waiting for client connection...")
                        val socket = serverSocket!!.accept()
                        Log.d(TAG, "Client connected: ${socket.inetAddress}")

                        closeClientConnection()
                        clientSocket = socket
                        dataOutputStream = DataOutputStream(socket.getOutputStream())

                        clientHandlerExecutor.execute { handleClient(socket) }

                    } catch (e: IOException) {
                        if (isRunning.get()) {
                            Log.e(TAG, "Error accepting client connection or socket closed: ${e.message}")
                        }
                        break
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Could not listen on port $port: ${e.message}")
            } finally {
                Log.d(TAG, "Server accept loop ended.")
                isRunning.set(false)
                closeServerSocket()
            }
        }
    }

    private fun handleClient(socket: Socket) {
        val bufferSize = audioCaptureService.BUFFER_SIZE_ACTUAL 
        val audioBuffer = ShortArray(bufferSize / 2)

        try {
            while (isRunning.get() && socket.isConnected && !socket.isClosed) {
                val shortsRead = audioCaptureService.read(audioBuffer, 0, audioBuffer.size)
                if (shortsRead > 0) {
                    val byteBuffer = ByteArray(shortsRead * 2)
                    for (i in 0 until shortsRead) {
                        byteBuffer[i * 2] = (audioBuffer[i].toInt() and 0xFF).toByte()
                        byteBuffer[i * 2 + 1] = (audioBuffer[i].toInt() shr 8 and 0xFF).toByte()
                    }
                    try {
                        dataOutputStream?.write(byteBuffer, 0, byteBuffer.size)
                        dataOutputStream?.flush()
                    } catch (e: IOException) {
                        Log.e(TAG, "Error sending audio data: ${e.message}")
                        break
                    }
                } else if (shortsRead < 0) {
                    Log.e(TAG, "Error reading audio data: $shortsRead")
                }
            }
        } catch (e: Exception) { 
            Log.e(TAG, "Exception in client handler: ${e.message}")
        } finally {
            Log.d(TAG, "Client disconnected or handler stopping: ${socket.inetAddress}")
            closeClientConnection()
        }
    }


    private fun closeClientConnection() {
        try {
            dataOutputStream?.close()
            clientSocket?.close()
        } catch (e: IOException) {
            Log.w(TAG, "Error closing client socket: ${e.message}")
        }
        dataOutputStream = null
        clientSocket = null
    }

    private fun closeServerSocket() {
        try {
            serverSocket?.close()
        } catch (e: IOException) {
            Log.w(TAG, "Error closing server socket: ${e.message}")
        }
        serverSocket = null
    }

    fun stop() {
        Log.d(TAG, "Stopping TCP server...")
        isRunning.set(false)
        closeClientConnection()
        closeServerSocket()
        executor.shutdown()
        clientHandlerExecutor.shutdown()
        Log.d(TAG, "TCP server stopped.")
    }
}