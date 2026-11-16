package com.example.myiulms.ui.dashboard

/**
 * What a weekly row should show today.
 *
 * Only today's group can be past or live: every other day is a recurring
 * template for a future date, so it never carries a temporal state.
 */
enum class ScheduleRowState { NotToday, Past, Live, Upcoming, Unknown }

/**
 * Derives a row's state from facts the screen already has.
 *
 * [rowKey] is `entryKey` for this entry and [liveKey] is the key of the entry
 * [resolveScheduleFocus] reports as live. Comparing keys instead of re-testing
 * the interval is what guarantees this row and the focus card can never
 * disagree about which class is live.
 *
 * Liveness is checked before past because the resolver owns that answer; with
 * consistent inputs the two are mutually exclusive anyway, since the live
 * window is start-inclusive/end-exclusive and the past rule (`end <= now`) is
 * its exact complement.
 *
 * [Unknown] means the row's time could not be parsed: it gets no dimming and no
 * check rather than a guessed state.
 */
fun scheduleRowState(
    isToday: Boolean,
    rowKey: String,
    interval: ScheduleInterval?,
    nowMinuteOfDay: Int,
    liveKey: String?
): ScheduleRowState {
    if (!isToday) return ScheduleRowState.NotToday
    if (interval == null) return ScheduleRowState.Unknown
    if (rowKey == liveKey) return ScheduleRowState.Live
    if (interval.endMinuteOfDay <= nowMinuteOfDay) return ScheduleRowState.Past
    return ScheduleRowState.Upcoming
}

/**
 * Fraction of [interval] already elapsed at [nowMinuteOfDay], clamped to 0..1.
 *
 * Spans past midnight are already flattened by the parser (`endMinuteOfDay` may
 * exceed 1439), so the arithmetic needs no wrap-around case. A non-positive
 * span and a clock jump in either direction both degrade to a valid fraction
 * instead of a negative or oversized one.
 */
fun classProgressFraction(interval: ScheduleInterval, nowMinuteOfDay: Int): Float {
    val span = interval.endMinuteOfDay - interval.startMinuteOfDay
    if (span <= 0) return 0f
    return ((nowMinuteOfDay - interval.startMinuteOfDay).toFloat() / span.toFloat())
        .coerceIn(0f, 1f)
}
