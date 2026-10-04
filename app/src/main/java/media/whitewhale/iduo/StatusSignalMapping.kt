package media.whitewhale.iduo

internal enum class SignalElementEmphasis {
    DIM,
    NEUTRAL,
    LIT,
}

internal sealed interface WifiSignalVisual {
    data object Disconnected : WifiSignalVisual
    data class Connected(val elements: List<SignalElementEmphasis>) : WifiSignalVisual
}

internal fun wifiSignalVisual(connected: Boolean, level: Int?): WifiSignalVisual {
    if (!connected) return WifiSignalVisual.Disconnected
    if (level == null) return WifiSignalVisual.Connected(List(4) { SignalElementEmphasis.NEUTRAL })

    val activeElements = level.coerceIn(0, 4)
    return WifiSignalVisual.Connected(List(4) { index ->
        if (index < activeElements) SignalElementEmphasis.LIT else SignalElementEmphasis.DIM
    })
}

internal sealed interface CellularSignalVisual {
    data object Airplane : CellularSignalVisual
    data object Unavailable : CellularSignalVisual
    data class Available(val activeDots: Int) : CellularSignalVisual
}

internal fun cellularSignalVisual(level: Int?, airplane: Boolean): CellularSignalVisual = when {
    airplane -> CellularSignalVisual.Airplane
    level == null -> CellularSignalVisual.Unavailable
    else -> CellularSignalVisual.Available(level.coerceIn(0, 4))
}

/** The mobile network's generation as phones show it, such as 5G or 4G+, or null when unknown. */
internal fun cellularNetworkLabel(networkType: Int, overrideType: Int): String? = when (overrideType) {
    // OVERRIDE_NETWORK_TYPE_NR_NSA, the retired NR_NSA_MMWAVE, and NR_ADVANCED.
    3, 4, 5 -> "5G"
    // OVERRIDE_NETWORK_TYPE_LTE_CA and LTE_ADVANCED_PRO.
    1, 2 -> "4G+"
    else -> when (networkType) {
        20 -> "5G" // NR
        13 -> "4G" // LTE
        3, 5, 6, 8, 9, 10, 12, 14, 15, 17 -> "3G" // UMTS, EVDO, HSPA and their kin, TD-SCDMA
        2 -> "E" // EDGE
        1, 4, 7, 11, 16 -> "2G" // GPRS, CDMA, 1xRTT, iDEN, GSM
        else -> null
    }
}

/** What the middle of the status ring shows, from the most to the least important. */
internal enum class RingCentre { WIFI, CELLULAR, AIRPLANE, BATTERY, NONE }

/**
 * Wi-Fi while connected; otherwise mobile data's generation, then airplane mode, and in a ring
 * that holds the battery level ([percent]), the level itself.
 */
internal fun ringCentre(status: DeviceStatus, percent: Boolean): RingCentre = when {
    status.wifiConnected -> RingCentre.WIFI
    status.cellularData && status.cellularNetwork != null && !status.airplane -> RingCentre.CELLULAR
    status.airplane -> RingCentre.AIRPLANE
    percent -> RingCentre.BATTERY
    else -> RingCentre.NONE
}

/** Airplane mode shows beside or above the ring while something more important fills its middle. */
internal fun airplaneOutside(status: DeviceStatus, centre: RingCentre) = status.airplane && centre != RingCentre.AIRPLANE
