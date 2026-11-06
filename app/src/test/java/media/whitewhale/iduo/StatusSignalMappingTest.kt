package media.whitewhale.iduo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusSignalMappingTest {
    @Test fun wifiLevelsLightFanThenTwoArcs() {
        val lit = (0..4).map { level ->
            val visual = wifiSignalVisual(connected = true, level = level) as WifiSignalVisual.Connected
            assertEquals(3, visual.elements.size)
            visual.elements.takeWhile { it == SignalElementEmphasis.LIT }.size
        }

        assertEquals(listOf(0, 1, 2, 3, 3), lit)
    }

    @Test fun connectedUnknownWifiUsesNeutralElements() {
        val visual = wifiSignalVisual(connected = true, level = null) as WifiSignalVisual.Connected

        assertEquals(List(3) { SignalElementEmphasis.NEUTRAL }, visual.elements)
    }

    @Test fun disconnectedWifiHasNoSignalLevel() {
        assertEquals(WifiSignalVisual.Disconnected, wifiSignalVisual(connected = false, level = 4))
    }

    @Test fun cellularLightsOneOfFourDotsPerLevel() {
        assertEquals(listOf(0, 1, 2, 3, 4), (0..4).map { level ->
            (cellularSignalVisual(level, airplane = false) as CellularSignalVisual.Available).activeDots
        })
    }

    @Test fun cellularUnavailableAndAirplaneRemainDistinctWithoutLitDots() {
        assertEquals(CellularSignalVisual.Unavailable, cellularSignalVisual(level = null, airplane = false))
        assertEquals(CellularSignalVisual.Airplane, cellularSignalVisual(level = 4, airplane = true))
        assertTrue(cellularSignalVisual(level = null, airplane = true) is CellularSignalVisual.Airplane)
    }

    @Test fun networkGenerationsReadLikeAPhone() {
        assertEquals("5G", cellularNetworkLabel(networkType = 13, overrideType = 3))
        assertEquals("5G", cellularNetworkLabel(networkType = 20, overrideType = 0))
        assertEquals("4G+", cellularNetworkLabel(networkType = 13, overrideType = 1))
        assertEquals("4G", cellularNetworkLabel(networkType = 13, overrideType = 0))
        assertEquals("3G", cellularNetworkLabel(networkType = 15, overrideType = 0))
        assertEquals(null, cellularNetworkLabel(networkType = 0, overrideType = 0))
    }

    @Test fun ringMiddleShowsTheMostImportantThing() {
        val wifiAndAirplane = DeviceStatus(battery = 50, wifiConnected = true, airplane = true)
        assertEquals(RingCentre.WIFI, ringCentre(wifiAndAirplane))
        assertTrue(airplaneOutside(wifiAndAirplane, RingCentre.WIFI))
        val mobile = DeviceStatus(battery = 50, cellularData = true, cellularNetwork = "5G")
        assertEquals(RingCentre.CELLULAR, ringCentre(mobile))
        assertEquals(RingCentre.AIRPLANE, ringCentre(DeviceStatus(battery = 50, airplane = true)))
        assertEquals(RingCentre.SIGNAL, ringCentre(DeviceStatus(battery = 50)))
        assertEquals(RingCentre.SIGNAL, ringCentre(DeviceStatus(battery = 50, cellularLevel = 3)))
        // Without Wi-Fi or a known mobile data generation the middle shows the mobile signal.
        assertEquals(RingCentre.SIGNAL, ringCentre(DeviceStatus(battery = 50, cellularData = true)))
    }
}
