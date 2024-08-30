package com.nishith.mediapicker.base

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.nishith.mediapicker.fileselector.MediaSelectHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
abstract class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createViewBinding())
    }

    abstract fun createViewBinding(): View
}
