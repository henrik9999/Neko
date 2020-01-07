package eu.kanade.tachiyomi

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.multidex.MultiDex
import com.evernote.android.job.JobManager
import eu.kanade.tachiyomi.data.backup.BackupCreatorJob
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.data.updater.UpdaterJob
import eu.kanade.tachiyomi.extension.ExtensionUpdateJob
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.LocaleHelper
import org.acra.ACRA
import org.acra.annotation.ReportsCrashes
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektScope
import uy.kohesive.injekt.injectLazy
import uy.kohesive.injekt.registry.default.DefaultRegistrar

@ReportsCrashes(
        formUri = "https://collector.tracepot.com/e90773ff",
        reportType = org.acra.sender.HttpSender.Type.JSON,
        httpMethod = org.acra.sender.HttpSender.Method.PUT,
        buildConfigClass = BuildConfig::class,
        excludeMatchingSharedPreferencesKeys = arrayOf(".*username.*", ".*password.*", ".*token.*")
)
open class App : Application(), LifecycleObserver {

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())

        Injekt = InjektScope(DefaultRegistrar())
        Injekt.importModule(AppModule(this))

        setupAcra()
        setupJobManager()
        setupNotificationChannels()

        LocaleHelper.updateConfiguration(this, resources.configuration)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onAppBackgrounded() {
        //App in background
        val preferences: PreferencesHelper by injectLazy()
        if (preferences.lockAfter().getOrDefault() >= 0) {
            MainActivity.unlocked = false
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        LocaleHelper.updateConfiguration(this, newConfig, true)
    }

    protected open fun setupAcra() {
        ACRA.init(this)
    }

    protected open fun setupJobManager() {
        try {
            JobManager.create(this).addJobCreator { tag ->
                when (tag) {
                    LibraryUpdateJob.TAG -> LibraryUpdateJob()
                    UpdaterJob.TAG -> UpdaterJob()
                    BackupCreatorJob.TAG -> BackupCreatorJob()
                    ExtensionUpdateJob.TAG -> ExtensionUpdateJob()
                    else -> null
                }
            }
        } catch (e: Exception) {
            Timber.w("Can't initialize job manager")
        }
    }

    protected open fun setupNotificationChannels() {
        Notifications.createChannels(this)
    }

}
