package com.anall.taximeter

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anall.taximeter.meter.IdleSign
import com.anall.taximeter.meter.MeterController
import com.anall.taximeter.ui.MeterActions
import com.anall.taximeter.ui.MeterScreen

class MainActivity : ComponentActivity() {

    private val controller: MeterController get() = (application as TaxiMeterApp).controller

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { controller.onPermissionChanged() }

    private val actions = object : MeterActions {
        override fun start() = controller.startTrip()
        override fun end() = controller.endTrip()
        override fun vacant() = controller.clearToVacant()
        override fun toggleOutOfCity() = controller.toggleOutOfCity()
        override fun toggleIdleSign(sign: IdleSign) = controller.toggleIdleSign(sign)
        override fun setSimulation(on: Boolean) = controller.setSimulation(on)
        override fun setAutoNight(on: Boolean) = controller.setAutoNight(on)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        setContent {
            val state by controller.state.collectAsStateWithLifecycle()
            MeterScreen(state, actions)
        }

        val perms = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    override fun onStart() {
        super.onStart()
        controller.onUiVisible()
    }

    override fun onStop() {
        controller.onUiHidden()
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /** 헤드유닛 화면을 꽉 채운다. 가장자리를 스와이프하면 시스템 바가 잠깐 나온다. */
    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
