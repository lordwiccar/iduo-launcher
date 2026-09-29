package media.whitewhale.iduo

import android.app.Application
import android.content.Context

class DuoApplication : Application() {
    override fun attachBaseContext(newBase: Context) { super.attachBaseContext(AppLanguage.wrap(newBase)) }

    override fun onCreate() {
        super.onCreate()
        SystemWallpaper.initialize(this)
    }
}
