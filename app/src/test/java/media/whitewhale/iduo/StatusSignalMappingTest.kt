package media.whitewhale.iduo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusSignalMappingTest {
    @Test fun wifiLevelsLightDotThenThreeArcs() {
        val visuals = (0..4).map { level ->
            wifiSignalVisual(connected = true, level = level) as WifiSignalVisual.Connected
        }

        visuals.forEachIndexed { level, visual ->
            assertEquals(level, visual.elements.count { it == SignalElementEmphasis.LIT })
            assertEquals(4 - level, visual.elements.count { it == SignalElementEmphasis.DIM })
        }
        assertEquals(5, visuals.map { it.elements }.distinct().size)
    }

    @Test fun connectedUnknownWifiUsesNeutralElements() {
        val visual = wifiSignalVisual(connected = true, level = null) as WifiSignalVisual.Connected

        assertEquals(List(4) { SignalElementEmphasis.NEUTRAL }, visual.elements)
    }

    @Test fun disconnectedWifiHasNoSignalLevel() {
        assertEquals(WifiSignalVisual.Disconnected, wifiSignalVisual(connected = false, level = 4))
    }

    @Test fun cellularKeepsFiveDotConversionForLevelsZeroThroughFour() {
        assertEquals(listOf(0, 2, 3, 4, 5), (0..4).map { level ->
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
        assertEquals(RingCentre.WIFI, ringCentre(wifiAndAirplane, percent = true))
        assertTrue(airplaneOutside(wifiAndAirplane, RingCentre.WIFI))
        val mobile = DeviceStatus(battery = 50, cellularData = true, cellularNetwork = "5G")
        assertEquals(RingCentre.CELLULAR, ringCentre(mobile, percent = false))
        // Above the dock the generation sits among the signal dots instead.
        assertEquals(RingCentre.BATTERY, ringCentre(mobile, percent = true))
        assertEquals(RingCentre.AIRPLANE, ringCentre(DeviceStatus(battery = 50, airplane = true), percent = true))
        assertEquals(RingCentre.BATTERY, ringCentre(DeviceStatus(battery = 50), percent = true))
        assertEquals(RingCentre.NONE, ringCentre(DeviceStatus(battery = 50), percent = false))
        // Mobile data without a known generation leaves the middle to the battery level.
        assertEquals(RingCentre.BATTERY, ringCentre(DeviceStatus(battery = 50, cellularData = true), percent = true))
    }
}
