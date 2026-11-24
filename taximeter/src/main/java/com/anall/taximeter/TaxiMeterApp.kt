package com.anall.taximeter

import android.app.Application
import com.anall.taximeter.meter.MeterController

class TaxiMeterApp : Application() {
    lateinit var controller: MeterController
        private set

    override fun onCreate() {
        super.onCreate()
        controller = MeterController(this)
    }
}
