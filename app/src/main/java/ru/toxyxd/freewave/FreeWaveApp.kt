package ru.toxyxd.freewave

import android.app.Application
import android.bluetooth.BluetoothManager

class FreeWaveApp: Application() {
    override fun onCreate() {
        super.onCreate()
        DI.init(this)
    }
}

object DI {
    lateinit var app: Application

    fun init(app: Application) {
        this.app = app
    }
    val context by lazy { app }
    val bluetoothManager: BluetoothManager by lazy { context.getSystemService(BluetoothManager::class.java) }
}