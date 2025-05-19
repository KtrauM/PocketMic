package io.github.ktraum.pocketmic

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.net.NetworkInterface

class MainActivity : AppCompatActivity() {
    private lateinit var startStopButton: Button
    private lateinit var portInput: EditText
    private lateinit var statusText: TextView
    private lateinit var ipText: TextView
    private var audioCaptureService: AudioCaptureService? = null
    private var tcpAudioServer: TcpAudioServer? = null
    private var isServerRunning = false

    companion object {
        private const val PERMISSION_REQUEST_CODE = 123
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        startStopButton = findViewById(R.id.startStopButton)
        portInput = findViewById(R.id.portInput)
        statusText = findViewById(R.id.statusText)
        ipText = findViewById(R.id.ipText)

        // Set default port
        portInput.setText("50005")
        portInput.hint = getString(R.string.port_hint)

        // Display local IP address
        val localIp = getLocalIpAddress()
        ipText.text = getString(R.string.local_ip, localIp)

        startStopButton.text = getString(R.string.start_server)
        statusText.text = getString(R.string.status_not_running)

        startStopButton.setOnClickListener {
            if (!isServerRunning) {
                if (checkMicrophonePermission()) {
                    startServer()
                } else {
                    requestMicrophonePermission()
                }
            } else {
                stopServer()
            }
        }
    }

    private fun checkMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestMicrophonePermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startServer()
            } else {
                Toast.makeText(
                    this,
                    "Microphone permission is required to use this app",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun startServer() {
        val port = portInput.text.toString().toIntOrNull() ?: 50005
        audioCaptureService = AudioCaptureService()
        tcpAudioServer = TcpAudioServer(port, audioCaptureService!!)
        tcpAudioServer?.start()
        statusText.text = getString(R.string.status_listening, port)
        startStopButton.text = getString(R.string.stop_server)
        isServerRunning = true
    }

    private fun stopServer() {
        tcpAudioServer?.stop()
        audioCaptureService?.stop()
        statusText.text = getString(R.string.status_stopped)
        startStopButton.text = getString(R.string.start_server)
        isServerRunning = false
    }

    private fun getLocalIpAddress(): String {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            val addresses = networkInterface.inetAddresses
            while (addresses.hasMoreElements()) {
                val address = addresses.nextElement()
                if (!address.isLoopbackAddress && address.hostAddress.indexOf(':') < 0) {
                    return address.hostAddress
                }
            }
        }
        return "Unknown"
    }
} 