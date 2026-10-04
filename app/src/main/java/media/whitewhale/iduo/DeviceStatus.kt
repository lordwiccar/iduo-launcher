package media.whitewhale.iduo

import androidx.core.content.ContextCompat
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.SupplicantState
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.telephony.SignalStrength
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class DeviceStatus(
    val battery: Int? = null,
    val charging: Boolean = false,
    val wifiConnected: Boolean = false,
    val wifiLevel: Int? = null,
    val cellularLevel: Int? = null,
    val airplane: Boolean = false,
    val powerSave: Boolean = false,
    /** Whether mobile data carries the connection, and its generation, such as 5G, when Android tells it. */
    val cellularData: Boolean = false,
    val cellularNetwork: String? = null,
    /** Each SIM's signal, 0 to 4, by slot, on a phone with two SIMs in use; empty otherwise. */
    val simLevels: List<Int?> = emptyList(),
)

/** Observe only while visible. No location, phone-state, or notification access required. */
class DeviceStatusMonitor(private val context: Context) : DefaultLifecycleObserver {
    private val connection = context.getSystemService(ConnectivityManager::class.java)
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
    private val phone = context.getSystemService(TelephonyManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)
    private val mutable = MutableStateFlow(DeviceStatus())
    val state = mutable.asStateFlow()
    private var networkRegistered = false
    private var phoneRegistered = false
    private var displayInfoRegistered = false
    private val simCallbacks = mutableListOf<Pair<TelephonyManager, TelephonyCallback>>()
    private var receiverRegistered = false
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = updateConnection()
        override fun onLost(network: Network) = updateConnection()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = updateConnection()
    }
    private val phoneCallback = object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
            mutable.update { it.copy(cellularLevel = signalStrength.level.coerceIn(0, 4)) }
        }
    }
    // Separate from the signal listener, so a device that refuses one still reports the other.
    private val displayInfoCallback = object : TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
        override fun onDisplayInfoChanged(info: TelephonyDisplayInfo) {
            mutable.update { it.copy(cellularNetwork = cellularNetworkLabel(info.networkType, info.overrideNetworkType)) }
        }
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val charge = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                mutable.update { it.copy(battery = if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else null,
                    charging = charge == BatteryManager.BATTERY_STATUS_CHARGING || charge == BatteryManager.BATTERY_STATUS_FULL) }
            }
            updateConnection()
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        // Only the system sends these; no other app may deliver to this receiver.
        ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED); addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(WifiManager.RSSI_CHANGED_ACTION); addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
        networkRegistered = runCatching { connection.registerDefaultNetworkCallback(networkCallback); true }.getOrDefault(false)
        phoneRegistered = runCatching { phone.registerTelephonyCallback(context.mainExecutor, phoneCallback); true }.getOrDefault(false)
        displayInfoRegistered = runCatching { phone.registerTelephonyCallback(context.mainExecutor, displayInfoCallback); true }.getOrDefault(false)
        mutable.update { it.copy(cellularLevel = runCatching { phone.signalStrength?.level }.getOrNull()) }
        observeSims()
        updateConnection()
    }

    @Suppress("DEPRECATION")
    private fun updateConnection() {
        val caps = runCatching { connection.getNetworkCapabilities(connection.activeNetwork) }.getOrNull()
        // Only the network Android uses counts: Wi-Fi turned off can leave a stale connection behind.
        val connected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val info = if (!connected) null else (caps?.transportInfo as? WifiInfo)
            ?: runCatching { wifi.connectionInfo }.getOrNull()?.takeIf { it.supplicantState == SupplicantState.COMPLETED }
        val cellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true && !connected
        val level = info?.rssi?.takeIf { connected && it > -127 }?.let { WifiManager.calculateSignalLevel(it, 5) }
        val airplane = Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
        val powerSave = runCatching { power.isPowerSaveMode }.getOrDefault(false)
        mutable.update { it.copy(wifiConnected = connected, wifiLevel = level, airplane = airplane, powerSave = powerSave,
            cellularData = cellular) }
    }

    /**
     * With two SIMs in use, follows each one's signal. Android names a slot's subscription without
     * phone-state access only from Android 14; earlier versions keep the single signal.
     */
    private fun observeSims() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val slots = runCatching { phone.activeModemCount }.getOrDefault(1)
        if (slots < 2) return
        val sims = (0 until slots).mapNotNull { slot ->
            if (runCatching { phone.getSimState(slot) }.getOrNull() != TelephonyManager.SIM_STATE_READY) return@mapNotNull null
            val id = SubscriptionManager.getSubscriptionId(slot)
            if (!SubscriptionManager.isValidSubscriptionId(id)) null else runCatching { phone.createForSubscriptionId(id) }.getOrNull()
        }
        if (sims.size < 2) { mutable.update { it.copy(simLevels = emptyList()) }; return }
        mutable.update { it.copy(simLevels = sims.map { sim -> runCatching { sim.signalStrength?.level }.getOrNull() }) }
        sims.forEachIndexed { index, sim ->
            val callback = object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
                override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                    mutable.update { state -> state.copy(simLevels = state.simLevels.toMutableList()
                        .also { if (index < it.size) it[index] = signalStrength.level.coerceIn(0, 4) }) }
                }
            }
            if (runCatching { sim.registerTelephonyCallback(context.mainExecutor, callback) }.isSuccess) simCallbacks += sim to callback
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        simCallbacks.forEach { (sim, callback) -> runCatching { sim.unregisterTelephonyCallback(callback) } }
        simCallbacks.clear()
        if (networkRegistered) runCatching { connection.unregisterNetworkCallback(networkCallback) }
        if (phoneRegistered) runCatching { phone.unregisterTelephonyCallback(phoneCallback) }
        if (displayInfoRegistered) runCatching { phone.unregisterTelephonyCallback(displayInfoCallback) }
        if (receiverRegistered) runCatching { context.unregisterReceiver(receiver) }
        networkRegistered = false; phoneRegistered = false; displayInfoRegistered = false; receiverRegistered = false
    }
}
