package com.example.phonedisplayandroid

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.phonedisplayandroid.ui.theme.PhoneDisplayAndroidTheme
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.layout.ContentScale

class MainActivity : ComponentActivity() {

    private val screenCaptureLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            if (
                result.resultCode != Activity.RESULT_OK ||
                result.data == null
            ) {
                return@registerForActivityResult
            }

            startMediaProjectionService(
                result.resultCode,
                result.data!!
            )
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContent {

            PhoneDisplayAndroidTheme {

                val latestFrame by FrameStore.latestFrame.collectAsState()

                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {

                    if (latestFrame != null) {

                        Image(
                            bitmap = latestFrame!!.asImageBitmap(),
                            contentDescription = "Captured phone screen",
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.Fit
                        )

                    } else {

                        Button(
                            onClick = {
                                requestScreenCapture()
                            }
                        ) {
                            Text("Capture Phone Screen")
                        }
                    }
                }
            }
        }
    }

    private fun requestScreenCapture() {

        val manager =
            getSystemService(
                Context.MEDIA_PROJECTION_SERVICE
            ) as MediaProjectionManager

        val captureIntent =
            manager.createScreenCaptureIntent()

        screenCaptureLauncher.launch(
            captureIntent
        )
    }

    private fun startMediaProjectionService(
        resultCode: Int,
        data: Intent
    ) {

        val serviceIntent =
            Intent(
                this,
                MediaProjectionService::class.java
            ).apply {

                putExtra(
                    MediaProjectionService.EXTRA_RESULT_CODE,
                    resultCode
                )

                putExtra(
                    MediaProjectionService.EXTRA_RESULT_DATA,
                    data
                )
            }

        startForegroundService(
            serviceIntent
        )
    }
}