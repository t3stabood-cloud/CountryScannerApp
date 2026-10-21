package com.countryscanner.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.countryscanner.app.ui.scan.ScanScreen
import com.countryscanner.app.ui.theme.CountryScannerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CountryScannerTheme {
                ScanScreen()
            }
        }
    }
}
