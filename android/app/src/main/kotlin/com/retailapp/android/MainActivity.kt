package com.retailapp.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.retailapp.android.navigation.RetailAppRoot
import com.retailapp.android.session.AppShortcut
import com.retailapp.android.session.PendingShortcut
import com.retailapp.android.ui.common.WatermarkBackground
import com.retailapp.android.ui.theme.RetailAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A launcher shortcut (res/xml/shortcuts.xml) - but not again after a rotation.
        if (savedInstanceState == null) takeShortcut(intent)
        enableEdgeToEdge()
        setContent {
            RetailAppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    WatermarkBackground {
                        RetailAppRoot()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        takeShortcut(intent)
    }

    private fun takeShortcut(intent: Intent?) {
        AppShortcut.fromIntent(intent)?.let { PendingShortcut.value = it }
    }
}
