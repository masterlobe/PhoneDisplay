package com.example.phonedisplayandroid

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object FrameStore {

    private val _latestFrame =
        MutableStateFlow<Bitmap?>(null)

    val latestFrame =
        _latestFrame.asStateFlow()

    fun publish(frame: Bitmap) {
        _latestFrame.value = frame
    }

    fun clear() {
        _latestFrame.value = null
    }
}