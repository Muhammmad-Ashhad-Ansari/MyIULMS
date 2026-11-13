package com.example.myiulms

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import com.example.myiulms.alarms.ClassAlertWork
import com.example.myiulms.data.PortalRepository

/**
 * The app's shared dependencies, one instance each per process.
 *
 * Every property is lazy, so Application start does no work (cold start is
 * unaffected) and each object is built on first use, on whichever thread asks.
 * `by lazy` is synchronized, so a ViewModel on the main thread and the widget or
 * alarm receiver on a background thread always get the same instance.
 */
class AppContainer(app: Application) {
    init {
        // Cloudflare challenge ka WebView solver + ek hi User-Agent dono taraf
        // (OkHttp + WebView) — client ke saath ek hi jagah se attach hota hai.
        PortalChallengeSolver.attach(client, app)
    }

    /** The logged-in OkHttp client (cookie jar + credentials for auto re-login). */
    val client: IulmsClient get() = IulmsSession.client

    /** Remembered credentials (EncryptedSharedPreferences). Open it off the main thread. */
    val secureStore: SecureStore by lazy { SecureStore(app) }

    /**
     * Encrypted schedule cache. One instance, so its owner check and its file
     * lock are shared by the app, the widget and the alarm receiver.
     */
    val portal: PortalRepository by lazy { PortalRepository(app) { client } }
}

class MyIulmsApp : Application(), Configuration.Provider {
    val container: AppContainer by lazy { AppContainer(this) }

    // WorkManager's startup initializer is removed in the manifest; this builds
    // it on demand instead, when ClassAlertWork.sync first runs, so its
    // database is never opened on the main thread.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        // Re-register the reminder safety net after a reboot or process start;
        // the periodic work itself is only kept while reminders are on.
        ClassAlertWork.sync(this)
    }
}

/** Shared dependencies from any Context in this process. */
val Context.appContainer: AppContainer
    get() = (applicationContext as MyIulmsApp).container
