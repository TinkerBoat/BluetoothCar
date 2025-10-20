package info.littleboat.bluetoothcar

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MyApplication : Application() {
    // You can add onCreate logic here if needed
    // For example, Timber.plant(Timber.DebugTree()) if using Timber for logging
}