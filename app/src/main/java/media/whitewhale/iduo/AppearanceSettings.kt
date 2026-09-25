package media.whitewhale.iduo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun AppearanceSettings(state: AppearanceState, onMode: (AppearanceMode) -> Unit,
    onManual: (String, Double, Double) -> Unit, onDeviceLocation: () -> Unit, onClear: () -> Unit) {
    // Device fixes store an English marker; show it in the current language instead.
    val approximate = stringResource(R.string.approximate_location)
    var place by remember(state.place, approximate) { mutableStateOf(if (state.deviceLocation) approximate else state.place) }
    var latitude by remember(state.latitude) { mutableStateOf(state.latitude?.toString().orEmpty()) }
    var longitude by remember(state.longitude) { mutableStateOf(state.longitude?.toString().orEmpty()) }
    var inputError by remember { mutableStateOf<String?>(null) }
    val rangeError = stringResource(R.string.appearance_range_error)
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("appearance-settings")) {
        Text(stringResource(R.string.appearance), style = MaterialTheme.typography.titleMedium)
        AppearanceMode.entries.forEach { mode ->
            FilterChip(selected = state.mode == mode, onClick = { onMode(mode) }, label = { Text(stringResource(when (mode) {
                AppearanceMode.LIGHT -> R.string.appearance_light; AppearanceMode.DARK -> R.string.appearance_dark
                AppearanceMode.SYSTEM -> R.string.appearance_system; AppearanceMode.SUNRISE_SUNSET -> R.string.appearance_sun
        })) }, modifier = Modifier.testTag("appearance-${mode.name.lowercase()}"))
        }
        if (state.mode == AppearanceMode.SUNRISE_SUNSET) {
            state.fallback?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedTextField(place, { place = it }, Modifier.testTag("appearance-place").releasesDiscoverWhileTyping("appearance-place"), label = { Text(stringResource(R.string.place_name)) }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(latitude, { latitude = it }, Modifier.weight(1f).testTag("appearance-latitude").releasesDiscoverWhileTyping("appearance-latitude"), label = { Text(stringResource(R.string.latitude_label)) }, singleLine = true)
                OutlinedTextField(longitude, { longitude = it }, Modifier.weight(1f).testTag("appearance-longitude").releasesDiscoverWhileTyping("appearance-longitude"), label = { Text(stringResource(R.string.longitude_label)) }, singleLine = true)
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = {
                    focusManager.clearFocus(); keyboard?.hide()
                    val lat = latitude.toDoubleOrNull(); val lon = longitude.toDoubleOrNull()
                    if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
                        inputError = null; onManual(place, lat, lon)
                    } else inputError = rangeError },
                    modifier = Modifier.fillMaxWidth().testTag("appearance-save-place")) { Text(stringResource(R.string.use_this_place)) }
                AppearanceFeedback(inputError, MaterialTheme.colorScheme.error, "appearance-manual-status")
                OutlinedButton(onClick = {
                    focusManager.clearFocus(); keyboard?.hide(); onDeviceLocation()
                }, modifier = Modifier.fillMaxWidth()
                    .testTag("appearance-device-location")) { Text(stringResource(R.string.use_device_location)) }
                AppearanceFeedback(state.locationStatus, MaterialTheme.colorScheme.onSurfaceVariant,
                    "appearance-location-status")
                if (state.latitude != null) TextButton(onClick = onClear, Modifier.fillMaxWidth()) { Text(stringResource(R.string.clear_location)) }
            }
        }
    }
}

@Composable
private fun AppearanceFeedback(message: String?, color: androidx.compose.ui.graphics.Color, tag: String) {
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(message) { if (message != null) bringIntoView.bringIntoView() }
    message?.let {
        Text(it, color = color, modifier = Modifier.bringIntoViewRequester(bringIntoView)
            .semantics { liveRegion = LiveRegionMode.Polite }.testTag(tag))
    }
}
