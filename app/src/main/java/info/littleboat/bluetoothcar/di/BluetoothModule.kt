package info.littleboat.bluetoothcar.di // Or your preferred package name

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import dagger.Module
import info.littleboat.bluetoothcar.services.BluetoothService
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent // Or another appropriate component
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class) // Or ActivityComponent::class if you want it scoped to an Activity
object BluetoothModule {

    @Provides
    @Singleton // Scope this if BluetoothAdapter should be a singleton in this context
    fun provideBluetoothAdapter(@ApplicationContext context: Context): BluetoothAdapter? {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
        return bluetoothManager?.adapter
    }

    @Provides
    @Singleton // Scope your BluetoothService as needed (e.g., Singleton for one instance app-wide)
    fun provideBluetoothService(
        @ApplicationContext context: Context,
        bluetoothAdapter: BluetoothAdapter?
    ): BluetoothService {
        return BluetoothService(context, bluetoothAdapter)
    }
}
