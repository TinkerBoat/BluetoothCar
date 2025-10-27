package info.littleboat.bluetoothcar.di

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import info.littleboat.bluetoothcar.services.BluetoothServiceImpl
import info.littleboat.bluetoothcar.services.PairingStatus
import kotlinx.coroutines.flow.StateFlow
import android.bluetooth.BluetoothDevice
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BluetoothModule {

    @Binds
    @Singleton
    abstract fun bindBluetoothService(bluetoothServiceImpl: BluetoothServiceImpl): IBluetoothService

    companion object {
        @Provides
        @Singleton
        fun provideBluetoothAdapter(@ApplicationContext context: Context): BluetoothAdapter? {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
            return bluetoothManager?.adapter
        }
    }
}

interface IBluetoothService {
    val discoveredDevices: StateFlow<List<BluetoothDevice>>
    val pairingStatus: StateFlow<PairingStatus>

    fun isBluetoothEnabled(): Boolean
    fun startDiscovery()
    fun stopDiscovery()
    fun connectToDevice(deviceAddress: String): Boolean
    fun disconnect()
    fun sendCommand(command: String): Boolean
    fun pairDevice(device: BluetoothDevice)
    fun resetPairingStatus()
    fun getPairedDevices(): Set<BluetoothDevice>?
    fun getLastConnectedDeviceAddress(): String?
    fun providePinAndRetryPairing(pin: String)
}