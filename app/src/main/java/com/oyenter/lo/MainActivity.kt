package com.oyenter.lo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.oyenter.lo.ui.AppRoot
import com.oyenter.lo.ui.theme.LoTheme

class MainActivity : ComponentActivity() {

    private val audioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val app = application as LoApp
        if (granted) {
            app.library.onPermissionChanged()
            app.library.startObserving()
        }
    }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LoTheme {
                AppRoot(application as LoApp)
            }
        }
        askPermissions()
    }

    override fun onResume() {
        super.onResume()
        (application as LoApp).library.onResume()
    }

    private fun askPermissions() {
        val audio = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (checkSelfPermission(audio) != PackageManager.PERMISSION_GRANTED) {
            audioPermission.launch(audio)
        } else {
            (application as LoApp).library.startObserving()
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
