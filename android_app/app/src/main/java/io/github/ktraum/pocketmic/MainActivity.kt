package io.github.ktraum.pocketmic

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.net.NetworkInterface
import android.util.Log

class MainActivity : AppCompatActivity() {
    private lateinit var startStopButton: Button
    private lateinit var portInput: EditText
    private lateinit var statusText: TextView
    private lateinit var ipText: TextView
    private val handler = Handler(Looper.getMainLooper())
    
    private val serverStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioServerService.ACTION_SERVER_STATUS_CHANGED) {
                val isRunning = intent.getBooleanExtra(AudioServerService.EXTRA_SERVER_RUNNING, false)
                Log.d("MainActivity", "Received broadcast: server running = $isRunning")
                handler.post { updateUI() }
            }
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 123
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 124
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        startStopButton = findViewById(R.id.startStopButton)
        portInput = findViewById(R.id.portInput)
        statusText = findViewById(R.id.statusText)
        ipText = findViewById(R.id.ipText)

        portInput.setText("50005")
        portInput.hint = getString(R.string.port_hint)

        val localIp = getLocalIpAddress()
        ipText.text = getString(R.string.local_ip, localIp ?: "N/A")


        startStopButton.setOnClickListener {
            if (!AudioServerService.IS_SERVICE_RUNNING) {
                checkAndRequestPermissions()
            } else {
                stopServerService()
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(serverStatusReceiver, 
                IntentFilter(AudioServerService.ACTION_SERVER_STATUS_CHANGED),
                Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(serverStatusReceiver, 
                IntentFilter(AudioServerService.ACTION_SERVER_STATUS_CHANGED))
        }
        
        updateUI()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }
    
    override fun onDestroy() {
        unregisterReceiver(serverStatusReceiver)
        handler.removeCallbacksAndMessages(null)
        
        if (isFinishing && AudioServerService.IS_SERVICE_RUNNING) {
           stopServerService()
        }
        super.onDestroy()
    }

    private fun updateUI() {
        Log.d("MainActivity", "Updating UI, server running: ${AudioServerService.IS_SERVICE_RUNNING}")
        if (AudioServerService.IS_SERVICE_RUNNING) {
            val port = portInput.text.toString().toIntOrNull() ?: 50005
            statusText.text = getString(R.string.status_listening, port)
            startStopButton.text = getString(R.string.stop_server)
            portInput.isEnabled = false
        } else {
            statusText.text = getString(R.string.status_not_running)
            startStopButton.text = getString(R.string.start_server)
            portInput.isEnabled = true
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (!checkMicrophonePermission()) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        } else {
            startServerService()
        }
    }


    private fun checkMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            var allGranted = true
            if (grantResults.isEmpty()) {
                allGranted = false
            } else {
                for (result in grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        allGranted = false
                        break
                    }
                }
            }

            if (allGranted) {
                startServerService()
            } else {
                Toast.makeText(
                    this,
                    "Required permissions (Microphone and/or Notifications) were not granted.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun startServerService() {
        if (AudioServerService.IS_SERVICE_RUNNING) return

        val port = portInput.text.toString().toIntOrNull() ?: 50005
        val serviceIntent = Intent(this, AudioServerService::class.java).apply {
            action = AudioServerService.ACTION_START_SERVER
            putExtra(AudioServerService.EXTRA_PORT, port)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        
        handler.postDelayed({
            Log.d("MainActivity", "Delayed UI update check, running: ${AudioServerService.IS_SERVICE_RUNNING}")
            updateUI()
            
            if (!AudioServerService.IS_SERVICE_RUNNING) {
                handler.postDelayed({
                    updateUI()
                }, 1000)
            }
        }, 500)
    }

    private fun stopServerService() {
        if (!AudioServerService.IS_SERVICE_RUNNING) return

        val serviceIntent = Intent(this, AudioServerService::class.java).apply {
            action = AudioServerService.ACTION_STOP_SERVER
        }
        startService(serviceIntent)
        
        handler.postDelayed({
            updateUI()
        }, 500)
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address.hostAddress.indexOf(':') < 0) {
                        return address.hostAddress
                    }
                }
            }
        } catch (ex: Exception) {
            Log.e("MainActivity", "getLocalIpAddress Error: ${ex.message}")
        }
        return null
    }
}