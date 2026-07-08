package com.fruit.ffmpeggui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.fruit.ffmpeggui.ui.CodecNestApp
import com.fruit.ffmpeggui.ui.CodecNestViewModel
import com.fruit.ffmpeggui.ui.theme.CodecNestForAndroidTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val codecNestViewModel = ViewModelProvider(this)[CodecNestViewModel::class.java]
        setContent {
            CodecNestForAndroidTheme {
                CodecNestApp(codecNestViewModel)
            }
        }
    }
}
