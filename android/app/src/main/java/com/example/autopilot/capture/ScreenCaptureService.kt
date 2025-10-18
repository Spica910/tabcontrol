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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START) {
            val mgr = getSystemService(MediaProjectionManager::class.java)
            val resultCode = intent.getIntExtra(EXTRA_CODE, 0)
            val data: Intent? = intent.getParcelableExtra(EXTRA_DATA)
            projection = mgr.getMediaProjection(resultCode, data!!)
            val dm = resources.displayMetrics
            imageReader = ImageReader.newInstance(dm.widthPixels, dm.heightPixels, PixelFormat.RGBA_8888, 2)
            val virt = projection!!.createVirtualDisplay(
                "ap-cap", dm.widthPixels, dm.heightPixels, dm.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader!!.surface, null, null
            )
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
        val tmp = IntArray(width)
        var offset = 0
        for (y in 0 until height) {
            buf.position(y * rowStride)
            val row = ByteArray(width * pixelStride)
            buf.get(row, 0, row.size)
            for (x in 0 until width) {
                val i = x * pixelStride
                val b = row[i].toInt() and 0xff
                val g = row[i+1].toInt() and 0xff
                val r = row[i+2].toInt() and 0xff
                val a = if (pixelStride > 3) row[i+3].toInt() and 0xff else 0xff
                tmp[x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
            bitmap.setPixels(tmp, 0, width, 0, y, width, 1)
        }
        img.close()
        return bitmap
    }

    companion object {
        const val ACTION_START = "cap.START"
        const val ACTION_STOP = "cap.STOP"
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
    }
}
