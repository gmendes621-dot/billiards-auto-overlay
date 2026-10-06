package com.example.billiardsoverlay

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient

class CaptureService : Service() {

    companion object {
        const val ACTION_START = "START"
        const val ACTION_STOP = "STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "result_data"
        private const val CHANNEL_ID = "billiards_capture"
        private const val NOTIFICATION_ID = 77
        private const val MIN_INTERVAL_MS = 100L
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var webView: WebView? = null
    private var detector: BallDetector? = null
    private var thread: HandlerThread? = null
    private var bg: Handler? = null
    private val main = Handler(Looper.getMainLooper())
    private var lastRun = 0L
    private var capW = 0
    private var capH = 0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
        if (Settings.canDrawOverlays(this)) createOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START && projection == null) {
            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            @Suppress("DEPRECATION")
            val data: Intent? = if (Build.VERSION.SDK_INT >= 33)
                intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
            else intent.getParcelableExtra(EXTRA_DATA)
            if (data != null && resultCode == Activity.RESULT_OK) startCapture(resultCode, data)
        } else if (intent?.action == ACTION_STOP) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    @Suppress("DEPRECATION")
    private fun realMetrics(): DisplayMetrics {
        val dm = DisplayMetrics()
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(dm)
        return dm
    }

    private fun createOverlay() {
        webView = WebView(this).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
            loadUrl("file:///android_asset/index.html")
        }
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= 28) {
            p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        (getSystemService(WINDOW_SERVICE) as WindowManager).addView(webView, p)
    }

    private fun newReader(w: Int, h: Int) {
        reader?.close()
        capW = w
        capH = h
        detector = BallDetector()
        reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2).also { r ->
            r.setOnImageAvailableListener({ ir ->
                val image = ir.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val now = SystemClock.uptimeMillis()
                    if (now - lastRun < MIN_INTERVAL_MS) return@setOnImageAvailableListener
                    lastRun = now
                    val json = detector?.process(image)?.toJson()
                    if (json != null) main.post {
                        webView?.evaluateJavascript("window.updateDetectedState($json);", null)
                    }
                } finally {
                    image.close()
                }
            }, bg)
        }
    }

    private fun startCapture(resultCode: Int, data: Intent) {
        thread = HandlerThread("billiards-detect").also { it.start() }
        bg = Handler(thread!!.looper)

        val mgr = getSystemService(MediaProjectionManager::class.java)
        projection = mgr.getMediaProjection(resultCode, data)
        // Obrigatório no Android 14+ antes de createVirtualDisplay.
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopSelf() }
        }, bg)

        val dm = realMetrics()
        newReader(dm.widthPixels, dm.heightPixels)
        virtualDisplay = projection?.createVirtualDisplay(
            "BilliardsAutoCapture", dm.widthPixels, dm.heightPixels, dm.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader!!.surface, null, bg
        )
    }

    // Girou a tela: refaz o tamanho da captura.
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val vd = virtualDisplay ?: return
        val dm = realMetrics()
        if (dm.widthPixels == capW && dm.heightPixels == capH) return
        newReader(dm.widthPixels, dm.heightPixels)
        vd.resize(dm.widthPixels, dm.heightPixels, dm.densityDpi)
        vd.surface = reader!!.surface
    }

    override fun onDestroy() {
        reader?.close()
        virtualDisplay?.release()
        projection?.stop()
        thread?.quitSafely()
        webView?.let {
            try { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) } catch (_: Exception) {}
            it.destroy()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Captura da mesa", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildNotification(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Billiards Auto Overlay")
            .setContentText("Analisando a tela para detectar bolas e caçapas")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
}
