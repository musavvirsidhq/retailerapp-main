package com.retailapp.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.retailapp.android.navigation.RetailAppRoot
import com.retailapp.android.ui.theme.RetailAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RetailAppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RetailAppRoot()
                }
            }
        }
    }
}
