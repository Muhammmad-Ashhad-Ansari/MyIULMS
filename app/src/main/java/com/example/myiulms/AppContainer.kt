package com.example.myiulms

import android.app.Application
import android.content.Context
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

class MyIulmsApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

/** Shared dependencies from any Context in this process. */
val Context.appContainer: AppContainer
    get() = (applicationContext as MyIulmsApp).container
