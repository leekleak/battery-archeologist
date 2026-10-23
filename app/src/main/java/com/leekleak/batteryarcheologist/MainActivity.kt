package com.leekleak.batteryarcheologist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.leekleak.batteryarcheologist.ui.BatteryArcheologistRoot
import com.leekleak.batteryarcheologist.ui.theme.BatteryArcheologistTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val gateway = (application as BatteryArcheologistApp).gateway
        setContent {
            BatteryArcheologistTheme {
                BatteryArcheologistRoot(gateway)
            }
        }
    }
}
