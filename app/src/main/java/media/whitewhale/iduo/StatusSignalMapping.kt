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
    else -> CellularSignalVisual.Available((level.coerceIn(0, 4) * 5 + 3) / 4)
}
