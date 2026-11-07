package com.example.myiulms.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

/**
 * A progress value that fills from 0 to [target], then follows later changes
 * of [target] smoothly (e.g. one more evaluation done).
 *
 * When it replays is decided by where it is called and by [replayKey]:
 * - Call it at SCREEN level (outside LazyColumn items). The value then lives
 *   as long as the screen, so scrolling a card out of view and back never
 *   replays it, while leaving the tab disposes the screen, so coming back
 *   replays it.
 * - Change [replayKey] to replay on demand, e.g. a counter bumped when a
 *   refresh finishes.
 *
 * Read the result only from a draw-phase lambda (e.g. `progress = { value() }`),
 * so each frame redraws the bar without recomposing.
 */
@Composable
fun rememberRevealedProgress(target: Float, replayKey: Any? = Unit): () -> Float {
    val animatable = remember(replayKey) { Animatable(0f) }
    LaunchedEffect(replayKey, target) {
        animatable.animateTo(
            targetValue = target,
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessVeryLow)
        )
    }
    return { animatable.value }
}
