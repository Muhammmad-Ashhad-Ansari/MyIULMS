package com.example.myiulms.data

import android.content.Context
import com.example.myiulms.IulmsClient
import com.example.myiulms.IulmsSession
import com.example.myiulms.WeeklySchedule
import com.example.myiulms.parseWeeklySchedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/** A value plus when it was fetched, and whether it came from the saved copy. */
data class Timestamped<T>(val value: T, val fetchedAt: Long, val fromCache: Boolean)

/**
 * Bridge between the portal and the on-disk cache. Only the weekly schedule is
 * cached for now; it is what the home-screen widget needs.
 *
 * Writes only happen for an *active owner*, set at sign-in. The owner is null
 * when Remember me is off (README: nothing written to disk then) and after
 * sign-out, so a fetch still in flight at sign-out can never re-create the cache.
 */
class PortalRepository(
    context: Context,
    clientProvider: () -> IulmsClient = { IulmsSession.client }
) {
    private val cache = PortalCache(context)
    // Lazy: the widget only reads, and should not build an HTTP client to do so.
    private val client by lazy(clientProvider)
    private val ownerLock = Any()

    @Volatile
    private var activeOwnerHash: String? = null

    /**
     * Called on successful sign-in, before the UI or widget see the new account.
     * Discards cached data belonging to anyone else, and everything when
     * [persist] is false. Blocking: call off the main thread.
     */
    fun setActiveOwner(username: String, persist: Boolean) = synchronized(ownerLock) {
        val owner = if (persist) ownerHashOf(username) else null
        activeOwnerHash = owner
        val cachedOwner = cache.read(WEEKLY_SCHEDULE)
            ?.let { decodeEnvelope(WeeklySchedule.serializer(), it) }
            ?.ownerHash
        if (owner == null || (cachedOwner != null && cachedOwner != owner)) cache.wipe()
    }

    /** Sign-out: stop all writes and delete everything cached. */
    fun clear() = synchronized(ownerLock) {
        activeOwnerHash = null
        cache.wipe()
    }

    /**
     * The last saved weekly schedule, or null. The widget reads without an
     * owner check; it is gated by the sign-in state and the wipe rules above.
     */
    suspend fun cachedWeeklySchedule(): CacheEnvelope<WeeklySchedule>? = withContext(Dispatchers.IO) {
        cache.read(WEEKLY_SCHEDULE)?.let { decodeEnvelope(WeeklySchedule.serializer(), it) }
    }

    /** The saved schedule, but only if it belongs to [username]. */
    suspend fun cachedWeeklyScheduleFor(username: String): CacheEnvelope<WeeklySchedule>? =
        cachedWeeklySchedule()?.takeIf { it.ownerHash == ownerHashOf(username) }

    /**
     * Cache-then-network: emits [username]'s saved schedule first (if any), so a
     * screen can draw instantly, then fetches, saves and emits the portal copy.
     * A network failure is thrown after the cached emission, so the caller keeps
     * the saved copy on screen and reports the error beside it.
     */
    fun weeklyScheduleUpdates(username: String?): Flow<Timestamped<WeeklySchedule>> = flow {
        if (username != null) {
            cachedWeeklyScheduleFor(username)?.let { emit(Timestamped(it.payload, it.fetchedAt, fromCache = true)) }
        }
        val fresh = refreshWeeklySchedule()
        emit(Timestamped(fresh, System.currentTimeMillis(), fromCache = false))
    }.flowOn(Dispatchers.IO)

    /** Fetches and parses the schedule, then saves it if an owner is active. */
    suspend fun refreshWeeklySchedule(): WeeklySchedule = withContext(Dispatchers.IO) {
        val owner = activeOwnerHash
        val schedule = parseWeeklySchedule(client.getHtml("/sic/Schedule.php"))
        if (owner != null) {
            val bytes = encodeEnvelope(
                WeeklySchedule.serializer(),
                CacheEnvelope(CACHE_SCHEMA_VERSION, owner, System.currentTimeMillis(), schedule)
            )
            // Re-checked under the lock: a sign-out or account switch during the
            // fetch must win over this write.
            synchronized(ownerLock) {
                if (activeOwnerHash == owner) cache.write(WEEKLY_SCHEDULE, bytes)
            }
        }
        schedule
    }

    private companion object {
        const val WEEKLY_SCHEDULE = "weekly_schedule.bin"
    }
}
