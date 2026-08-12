package com.example.phonedisplayandroid
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.view.WindowManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.graphics.Bitmap
import java.nio.ByteBuffer
import androidx.core.graphics.createBitmap

class MediaProjectionService : Service() {

    companion object {
        private const val TAG = "PhoneDisplay"
        private const val CHANNEL_ID = "phone_display_capture"
        private const val NOTIFICATION_ID = 1001

        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        val notification = createNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(
                NOTIFICATION_ID,
                notification
            )
        }

        Log.d(TAG, "MediaProjectionService started")
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (intent == null) {
            Log.e(TAG, "Service started without intent")
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode =
            intent.getIntExtra(EXTRA_RESULT_CODE, -1)

        val resultData =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(
                    EXTRA_RESULT_DATA,
                    Intent::class.java
                )
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_RESULT_DATA)
            }


        Log.d(
            TAG,
            "Received resultCode=$resultCode, resultDataPresent=${resultData != null}"
        )

        if (resultData == null || resultCode != Activity.RESULT_OK) {
            Log.e(
                TAG,
                "Invalid MediaProjection data: resultCode=$resultCode, data=$resultData"
            )

            stopSelf()
            return START_NOT_STICKY
        }

        startProjection(resultCode, resultData)

        return START_NOT_STICKY
    }
    private fun startProjection(
        resultCode: Int,
        resultData: Intent
    ) {

        val manager =
            getSystemService(
                Context.MEDIA_PROJECTION_SERVICE
            ) as MediaProjectionManager

        mediaProjection =
            manager.getMediaProjection(
                resultCode,
                resultData
            )

        if (mediaProjection == null) {
            Log.e(TAG, "Could not obtain MediaProjection")
            stopSelf()
            return
        }

        val windowManager =
            getSystemService(WindowManager::class.java)

        val bounds =
            windowManager.maximumWindowMetrics.bounds

        val width = bounds.width()
        val height = bounds.height()

        val density =
            resources.configuration.densityDpi

        Log.d(
            TAG,
            "Capture resolution: ${width}x${height}"
        )

        Log.d(
            TAG,
            "Density: $density"
        )

        imageReader = ImageReader.newInstance(
            width,
            height,
            PixelFormat.RGBA_8888,
            3
        )

        imageReader?.setOnImageAvailableListener(
            { reader ->

                val image = reader.acquireLatestImage()

                if (image != null) {

                    try {

                        Log.d(
                            TAG,
                            "FRAME RECEIVED: ${image.width}x${image.height}"
                        )

                        val plane = image.planes[0]

                        val pixelStride = plane.pixelStride
                        val rowStride = plane.rowStride
                        val rowPadding =
                            rowStride - pixelStride * image.width

                        val bitmapWidth =
                            image.width + rowPadding / pixelStride

                        val bitmap = createBitmap(bitmapWidth, image.height)

                        val buffer: ByteBuffer =
                            plane.buffer

                        bitmap.copyPixelsFromBuffer(buffer)

                        val croppedBitmap =
                            Bitmap.createBitmap(
                                bitmap,
                                0,
                                0,
                                image.width,
                                image.height
                            )

                        bitmap.recycle()

                        FrameStore.publish(
                            croppedBitmap
                        )

                        Log.d(
                            TAG,
                            "FRAME STORED: ${croppedBitmap.width}x${croppedBitmap.height}"
                        )

                        // We only need ONE frame for V1.2.
                        stopProjection()

                    } catch (e: Exception) {

                        Log.e(
                            TAG,
                            "Failed to convert frame",
                            e
                        )

                    } finally {

                        image.close()
                    }
                }

            },
            null
        )

        mediaProjection?.registerCallback(
            object : MediaProjection.Callback() {

                override fun onStop() {

                    Log.d(
                        TAG,
                        "MediaProjection stopped"
                    )

                    stopProjection()
                }
            },
            null
        )

        virtualDisplay =
            mediaProjection?.createVirtualDisplay(
                "PhoneDisplayCapture",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader!!.surface,
                null,
                null
            )

        Log.d(
            TAG,
            "SCREEN CAPTURE STARTED"
        )
    }

    private fun stopProjection() {

        virtualDisplay?.release()
        virtualDisplay = null

        imageReader?.close()
        imageReader = null

        mediaProjection?.stop()
        mediaProjection = null

        Log.d(
            TAG,
            "Capture resources released"
        )

        stopSelf()
    }

    override fun onDestroy() {

        stopProjection()

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(
                CHANNEL_ID,
                "PhoneDisplay Screen Capture",
                NotificationManager.IMPORTANCE_LOW
            )

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("PhoneDisplay")
                .setContentText("Screen capture is active")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(true)
                .build()

        } else {

            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("PhoneDisplay")
                .setContentText("Screen capture is active")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(true)
                .build()
        }
    }
}