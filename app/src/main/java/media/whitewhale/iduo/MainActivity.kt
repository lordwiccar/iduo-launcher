package media.whitewhale.iduo

import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.UserManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.fillMaxSize
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.result.contract.ActivityResultContracts
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter

/** The current Home activity; Home is a single task, so at most one is live at a time. */
internal object LauncherHost {
    var activity = java.lang.ref.WeakReference<MainActivity>(null)
}

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) { super.attachBaseContext(AppLanguage.wrap(newBase)) }

    private val model: LauncherModel by viewModels()
    private lateinit var widgets: WidgetController
    internal lateinit var backups: BackupController
        private set
    internal lateinit var backgrounds: LauncherBackgroundController
        private set
    private val homeRequests = mutableIntStateOf(0)
    private val searchRequests = mutableIntStateOf(0)
    private val changelogRequests = mutableIntStateOf(0)
    // Asked once, right after an update, so the next update can be announced.
    private val notificationPermission = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    /** Whether the optional Home gestures accessibility service is on, checked on each resume. */
    internal var homeGesturesEnabled by androidx.compose.runtime.mutableStateOf(false)
        private set
    private val defaultHome = mutableStateOf(false)
    private val showFirstRun = mutableStateOf(false)
    private lateinit var setupExperience: SetupExperience
    private lateinit var status: DeviceStatusMonitor
    private lateinit var appearance: AppearanceStore
    private var appearanceLocationGeneration = 0
    private var appearancePermissionGeneration = -1
    private var appearanceLocationCancellation: CancellationSignal? = null
    private var timeReceiverRegistered = false
    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { appearance.refresh(systemDark()) }
    }
    private val locationPermission = activityResultRegistry.register("duo.appearance.location", this,
        ActivityResultContracts.RequestPermission(), permissionResult@{ granted ->
        if (appearancePermissionGeneration != appearanceLocationGeneration || isDestroyed) return@permissionResult
        appearancePermissionGeneration = -1
        if (granted) requestAppearanceLocation(keepPending = true)
        else finishAppearanceLocation(getString(R.string.location_denied))
    })
    private var shadeSetupDialog: android.app.AlertDialog? = null
    private lateinit var hinge: HingeMonitor
    private var returningFromShadeSettings = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LauncherHost.activity = java.lang.ref.WeakReference(this)
        setupExperience = SetupExperience(this)
        showFirstRun.value = setupExperience.entryDecision(SetupExperience.hadLauncherState(this)) ==
            SetupEntryDecision.SHOW
        returningFromShadeSettings = savedInstanceState?.getBoolean(SHADE_SETTINGS_PENDING) == true
        val restoreShadeDialog = savedInstanceState?.getBoolean(SHADE_DIALOG_VISIBLE) == true
        appearance = AppearanceStore(this)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        // Pick the cover's or the inner screen's Home before the first frame, so folding never shows the other one.
        model.showDisplay(onCover() || resources.configuration.screenWidthDp < 650)
        widgets = WidgetController(this, model) { }.also { it.restore(savedInstanceState) }
        backups = BackupController(this, model, widgets) { }.also { it.restore() }
        backgrounds = LauncherBackgroundController(this) { }
        status = DeviceStatusMonitor(this).also { lifecycle.addObserver(it) }
        hinge = HingeMonitor(this).also { lifecycle.addObserver(it) }
        val arrivedByFold = FoldHandoff.arrived(onCover())
        updateDefaultHome()
        if (savedInstanceState == null && intent.getStringExtra("duo_destination") == "search") searchRequests.intValue++
        if (savedInstanceState == null && intent.getStringExtra("duo_destination") == UpdateNotice.DESTINATION) changelogRequests.intValue++
        intent.removeExtra("duo_destination")
        if (savedInstanceState == null && UpdateNotice.shouldAskAfterUpdate(this))
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        applyCoverRotation(model.state.value.coverRotation, onCover())
        setContent {
            val state = model.state.collectAsStateWithLifecycle().value
            val smallestWidth = androidx.compose.ui.platform.LocalConfiguration.current.smallestScreenWidthDp
            androidx.compose.runtime.LaunchedEffect(state.coverRotation, smallestWidth) {
                applyCoverRotation(state.coverRotation, onCover())
            }
            val deviceStatus = status.state.collectAsStateWithLifecycle().value
            // The window's real size, which follows Samsung moving this window between screens.
            val window = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
            val onCover = if (window.width <= 0) onCover()
                else isCoverWindow(minOf(window.width, window.height), resources.displayMetrics.xdpi,
                    androidx.compose.ui.platform.LocalDensity.current.density)
            val foldFrost = rememberFoldFrost(onCover, arrivedByFold, hinge)
            DuoTheme(appearance.state.dark) { androidx.compose.foundation.layout.Box {
                if (state.foldAnimation) FoldWallpaperBlur(foldFrost, onCover)
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()
                    .then(if (state.foldAnimation) androidx.compose.ui.Modifier.foldFrost(foldFrost, onCover) else androidx.compose.ui.Modifier)) {
                androidx.compose.runtime.CompositionLocalProvider(LocalBadgeCounts provides
                    if (state.notificationBadges && NotificationBadges.accessGranted) NotificationBadges.counts else emptyMap()) {
                LauncherScreen(state, model, widgets, homeRequests.intValue,
                    onLaunch = { launchApp(it) }, onMakeDefault = ::makeDefault, onAppInfo = ::appInfo,
                    isDefaultHome = defaultHome.value, deviceStatus = deviceStatus, onStatusMode = ::setStatusMode, onWallpaperSettings = ::openWallpaperSettings,
                    searchRequests = searchRequests.intValue,
                    changelogRequests = changelogRequests.intValue,
                    onLaunchFrom = ::launchApp, onGoogleSearch = ::openGoogleSearch, onWebSearch = ::openWebSearch,
                    appearance = appearance.state,
                    onAppearanceMode = { cancelAppearanceLocation(); appearance.setMode(it, systemDark()) },
                    onAppearanceManual = { place, lat, lon -> cancelAppearanceLocation(); appearance.setManual(place, lat, lon, systemDark()) },
                    onAppearanceDeviceLocation = ::useAppearanceLocation,
                    onAppearanceClear = { cancelAppearanceLocation(); appearance.clearLocation(systemDark()) },
                    showFirstRun = showFirstRun.value,
                    onFinishFirstRun = ::finishFirstRun,
                    onShadeSetup = ::showShadeSetup)
            } } } }
        }
        FoldRenderExperiment.attach(this)
        if (restoreShadeDialog) window.decorView.post { if (!isFinishing && !isDestroyed) showShadeSetup() }
    }

    override fun onStart() {
        super.onStart(); widgets.host.startListening(); widgets.refreshProviders()
        if (!timeReceiverRegistered) {
            ContextCompat.registerReceiver(this, timeReceiver, IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK); addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED); addAction(Intent.ACTION_DATE_CHANGED)
            }, ContextCompat.RECEIVER_NOT_EXPORTED)
            timeReceiverRegistered = true
        }
        appearance.refresh(systemDark())
        SystemWallpaper.verify(this)
    }
    override fun onStop() {
        if (timeReceiverRegistered) { unregisterReceiver(timeReceiver); timeReceiverRegistered = false }
        widgets.host.stopListening(); super.onStop()
    }
    override fun onDestroy() {
        shadeSetupDialog?.dismiss()
        cancelAppearanceLocation()
        if (isChangingConfigurations) FoldHandoff.leave(onCover())
        super.onDestroy()
    }
    override fun onResume() {
        super.onResume()
        returningFromShadeSettings = false
        model.refresh(); appearance.refresh(systemDark()); updateDefaultHome()
        homeGesturesEnabled = SystemShadeAccessibilityService.isEnabled(this)
        NotificationBadges.refreshAccess(this)
    }

    /**
     * The cover screen stays upright unless the user allows rotation; the inner screen always
     * follows the device.
     */
    private fun applyCoverRotation(allowed: Boolean, cover: Boolean) {
        val wanted = if (cover && !allowed) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        if (requestedOrientation != wanted) requestedOrientation = wanted
    }

    internal fun openSystemShade(panel: ShadePanel) =
        handleGesture(SystemShadeAccessibilityService.open(this, panel), R.string.shade_rejected)

    internal fun lockScreen() =
        handleGesture(SystemShadeAccessibilityService.lockScreen(this), R.string.lock_rejected)

    private fun handleGesture(result: ShadeOpenResult, rejected: Int) {
        when (result) {
            ShadeOpenResult.OPENED -> Unit
            ShadeOpenResult.SERVICE_DISABLED -> showShadeSetup()
            ShadeOpenResult.SERVICE_STARTING -> Toast.makeText(this,
                R.string.shade_starting, Toast.LENGTH_SHORT).show()
            ShadeOpenResult.ACTION_REJECTED -> Toast.makeText(this, rejected, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showShadeSetup() {
        if (shadeSetupDialog?.isShowing == true) return
        shadeSetupDialog = android.app.AlertDialog.Builder(this)
            .setTitle(R.string.shade_setup_title)
            .setMessage(R.string.shade_setup_message)
            .setNegativeButton(R.string.not_now, null)
            .setPositiveButton(R.string.open_settings) { _, _ ->
                try {
                    returningFromShadeSettings = true
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } catch (_: android.content.ActivityNotFoundException) {
                    returningFromShadeSettings = false
                    Toast.makeText(this, R.string.accessibility_unavailable, Toast.LENGTH_LONG).show()
                }
            }
            .also { dialog -> dialog.setOnDismissListener { shadeSetupDialog = null } }
            .show()
    }

    private fun finishFirstRun() {
        setupExperience.finish()
        showFirstRun.value = false
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) setStatusMode(model.state.value.verticalStatus)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        widgets.save(outState)
        outState.putBoolean(SHADE_DIALOG_VISIBLE, shadeSetupDialog?.isShowing == true && !returningFromShadeSettings)
        outState.putBoolean(SHADE_SETTINGS_PENDING, returningFromShadeSettings)
        super.onSaveInstanceState(outState)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        FoldRenderExperiment.onNewIntent(this, intent)
        if (intent.getStringExtra("duo_destination") == "search") searchRequests.intValue++
        else if (intent.getStringExtra("duo_destination") == UpdateNotice.DESTINATION) changelogRequests.intValue++
        else if (intent.hasCategory(Intent.CATEGORY_HOME) || intent.getStringExtra("duo_destination") == "home") homeRequests.intValue++
        intent.removeExtra("duo_destination")
    }

    @Deprecated("Widget configuration uses the platform host request-code API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!widgets.onActivityResult(requestCode, resultCode)) super.onActivityResult(requestCode, resultCode, data)
    }

    private fun launchApp(app: AppEntry, bounds: android.graphics.Rect? = null) {
        try {
            val user = getSystemService(UserManager::class.java).getUserForSerialNumber(app.userSerial)
                ?: throw IllegalStateException("Profile is unavailable")
            getSystemService(LauncherApps::class.java).startMainActivity(app.component, user, screenBounds(bounds), launchOptions(bounds))
            model.noteLaunch(app.id)
        } catch (_: Exception) { Toast.makeText(this, getString(R.string.app_unavailable, app.label), Toast.LENGTH_SHORT).show(); model.refresh() }
    }

    private fun screenBounds(bounds: android.graphics.Rect?): android.graphics.Rect? = bounds?.takeUnless { it.isEmpty }?.let {
        val location = IntArray(2); window.decorView.getLocationOnScreen(location)
        android.graphics.Rect(it).apply { offset(location[0], location[1]) }
    }
    private fun launchOptions(bounds: android.graphics.Rect?): Bundle? = bounds?.takeUnless { it.isEmpty }?.let {
        android.app.ActivityOptions.makeScaleUpAnimation(window.decorView, it.left, it.top, it.width(), it.height()).toBundle()
    }
    private fun openGoogleSearch(bounds: android.graphics.Rect?): Boolean = try {
        startActivity(googleSearchIntent().apply { sourceBounds = screenBounds(bounds) }, launchOptions(bounds))
        true
    } catch (_: android.content.ActivityNotFoundException) { false }
      catch (_: SecurityException) { false }

    /** Google's results for [query]: in the Google app, else in the browser. */
    private fun openWebSearch(query: String) {
        for (intent in webSearchIntents(query)) {
            try { startActivity(intent); return }
            catch (_: android.content.ActivityNotFoundException) { }
            catch (_: SecurityException) { }
        }
    }


    private fun makeDefault() {
        // Samsung may immediately cancel a role request; its Home settings is reliable.
        try { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
        catch (_: android.content.ActivityNotFoundException) {
            val role = getSystemService(RoleManager::class.java)
            if (role.isRoleAvailable(RoleManager.ROLE_HOME)) startActivity(role.createRequestRoleIntent(RoleManager.ROLE_HOME))
            else startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        }
    }

    private fun updateDefaultHome() {
        defaultHome.value = getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)
    }

    /** Whether Home is on the cover screen now; Samsung may move this window between screens. */
    private fun onCover() = resources.displayMetrics.let {
        isCoverWindow(minOf(it.widthPixels, it.heightPixels), it.xdpi, it.density)
    }

    private fun systemDark() = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES

    private fun useAppearanceLocation() {
        cancelAppearanceLocation()
        appearance.locationStatus(getString(R.string.location_waiting))
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            requestAppearanceLocation(keepPending = true)
        else {
            appearancePermissionGeneration = appearanceLocationGeneration
            runCatching { locationPermission.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION) }
                .onFailure { finishAppearanceLocation(getString(R.string.location_request_failed)) }
        }
    }

    private fun requestAppearanceLocation(keepPending: Boolean = false) {
        val generation = ++appearanceLocationGeneration
        val manager = getSystemService(LocationManager::class.java)
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            finishAppearanceLocation(getString(R.string.location_permission_unavailable)); return
        }
        val cached = runCatching { manager.getProviders(true).mapNotNull { manager.getLastKnownLocation(it) }
            .maxByOrNull { it.time }?.takeIf { System.currentTimeMillis() - it.time <= 15 * 60_000 } }.getOrNull()
        if (cached != null) {
            if (generation == appearanceLocationGeneration) appearance.setDeviceLocation(cached.latitude, cached.longitude, systemDark())
            finishAppearanceLocation(null); return
        }
        val provider = runCatching { when {
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            manager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER) -> LocationManager.PASSIVE_PROVIDER
            else -> null
        } }.getOrNull() ?: run { finishAppearanceLocation(getString(R.string.location_no_provider)); return }
        val cancellation = CancellationSignal()
        appearanceLocationCancellation = cancellation
        window.decorView.postDelayed({
            if (generation == appearanceLocationGeneration && appearanceLocationCancellation === cancellation) {
                cancellation.cancel(); finishAppearanceLocation(getString(R.string.location_timeout))
            }
        }, 10_000)
        runCatching { manager.getCurrentLocation(provider, cancellation, ContextCompat.getMainExecutor(this)) { location ->
            if (generation != appearanceLocationGeneration || isDestroyed) return@getCurrentLocation
            if (location != null) appearance.setDeviceLocation(location.latitude, location.longitude, systemDark())
            finishAppearanceLocation(if (location == null) getString(R.string.location_unavailable) else null)
        } }.onFailure { finishAppearanceLocation(getString(R.string.location_unavailable)) }
    }

    private fun cancelAppearanceLocation() {
        appearanceLocationGeneration++
        appearancePermissionGeneration = -1
        appearanceLocationCancellation?.cancel(); appearanceLocationCancellation = null
        if (::appearance.isInitialized) appearance.locationStatus(null)
    }

    private fun finishAppearanceLocation(message: String?) {
        appearanceLocationGeneration++
        appearancePermissionGeneration = -1
        appearanceLocationCancellation = null
        appearance.locationStatus(message)
    }

    private fun setStatusMode(vertical: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (vertical) controller.hide(WindowInsetsCompat.Type.statusBars()) else controller.show(WindowInsetsCompat.Type.statusBars())
    }

    /** Android's own wallpaper picker, including live wallpapers; Home follows whatever it sets. */
    private fun openWallpaperSettings() {
        try {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), getString(R.string.open_wallpaper_settings)))
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(this, R.string.wallpaper_settings_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private fun appInfo(app: AppEntry) {
        try {
            val user = getSystemService(UserManager::class.java).getUserForSerialNumber(app.userSerial)
                ?: throw IllegalStateException("Profile is unavailable")
            getSystemService(LauncherApps::class.java).startAppDetailsActivity(app.component, user, null, null)
        } catch (_: Exception) {
            Toast.makeText(this, getString(R.string.app_unavailable, app.label), Toast.LENGTH_SHORT).show()
            model.refresh()
        }
    }

    private companion object {
        const val SHADE_DIALOG_VISIBLE = "duo.shade.dialog_visible"
        const val SHADE_SETTINGS_PENDING = "duo.shade.settings_pending"
    }
}
