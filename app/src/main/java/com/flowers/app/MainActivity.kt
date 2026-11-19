package com.flowers.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.flowers.app.ui.navigation.FlowersApp
import com.flowers.app.ui.theme.FlowersTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as FlowersApplication
        setContent {
            FlowersTheme {
                FlowersApp(application = app)
            }
        }
    }
}
