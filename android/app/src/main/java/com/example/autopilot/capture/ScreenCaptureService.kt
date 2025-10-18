package com.example.autopilot.capture

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.MediaProjection
import android.hardware.display.MediaProjectionManager
import android.media.ImageReader
import android.os.IBinder
import android.view.WindowManager
import java.nio.ByteBuffer

class ScreenCaptureService : Service() {
    private var projection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var vd: android.hardware.display.VirtualDisplay? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START) {
            val mgr = getSystemService(MediaProjectionManager::class.java)
            val resultCode = intent.getIntExtra(EXTRA_CODE, 0)
            val data: Intent? = intent.getParcelableExtra(EXTRA_DATA)
            projection = mgr.getMediaProjection(resultCode, data!!)
            val dm = resources.displayMetrics
            imageReader = ImageReader.newInstance(dm.widthPixels, dm.heightPixels, PixelFormat.RGBA_8888, 2)
            vd = projection!!.createVirtualDisplay(
                "ap-cap", dm.widthPixels, dm.heightPixels, dm.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader!!.surface, null, null
            )
            instance = this
        } else if (intent?.action == ACTION_STOP) {
            stopSelf()
        }
        return START_STICKY
    }

    fun capture(): Bitmap? {
        val img = imageReader?.acquireLatestImage() ?: return null
        val p = img.planes[0]
        val buf: ByteBuffer = p.buffer
        val pixelStride = p.pixelStride
        val rowStride = p.rowStride
        val width = img.width
        val height = img.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        // Efficient copy respecting rowStride
        val rowBuffer = ByteArray(rowStride)
        for (y in 0 until height) {
            buf.position(y * rowStride)
            buf.get(rowBuffer, 0, rowStride)
            var xPixel = 0
            var idx = 0
            while (xPixel < width) {
                val b = rowBuffer[idx].toInt() and 0xFF
                val g = rowBuffer[idx + 1].toInt() and 0xFF
                val r = rowBuffer[idx + 2].toInt() and 0xFF
                val a = if (pixelStride >= 4) rowBuffer[idx + 3].toInt() and 0xFF else 0xFF
                val color = (a shl 24) or (r shl 16) or (g shl 8) or b
                bitmap.setPixel(xPixel, y, color)
                xPixel += 1
                idx += pixelStride
            }
        }
        img.close()
        return bitmap
    }

    override fun onDestroy() {
        super.onDestroy()
        vd?.release(); vd = null
        imageReader?.close(); imageReader = null
        projection?.stop(); projection = null
        if (instance === this) instance = null
    }

    companion object {
        const val ACTION_START = "cap.START"
        const val ACTION_STOP = "cap.STOP"
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"

        @Volatile
        var instance: ScreenCaptureService? = null

        fun captureLatest(): Bitmap? = instance?.capture()
    }
}
