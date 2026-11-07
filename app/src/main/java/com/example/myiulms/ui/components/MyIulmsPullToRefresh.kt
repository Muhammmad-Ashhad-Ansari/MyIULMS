package com.example.myiulms.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.myiulms.R
import kotlinx.coroutines.flow.distinctUntilChanged

/*
 * The app's pull-to-refresh: Material's PullToRefreshBox (gesture, nested
 * scroll, thresholds) with a branded indicator and one haptic tick.
 *
 * Indicator: the full MyIULMS building logo (the launcher artwork) in a small
 * round chip. It starts as a light-grey silhouette; while pulling, solid brand
 * colour fills it from the bottom up in step with the pull, so the fill level
 * is the pull progress. Full = release to refresh. While refreshing, the
 * filled logo breathes gently.
 *
 * Every animated value is read inside graphicsLayer / drawWithContent, so a
 * pull or the breathing loop redraws the chip without recomposing anything.
 */

private val ChipSize = 44.dp
/** Logo width inside the chip; height follows the artwork's aspect ratio. */
private val LogoWidth = 28.dp

/**
 * The launcher's building artwork, decoded once per process: downsampled 4x
 * (1024px → 256px, plenty for a 28dp logo) and without density scaling, then
 * cropped to the opaque building so the fill maps onto the logo itself rather
 * than the adaptive-icon padding around it.
 */
private object RefreshLogo {
    @Volatile
    private var cached: Pair<ImageBitmap, IntRect>? = null

    data class IntRect(val left: Int, val top: Int, val width: Int, val height: Int)

    fun get(resources: Resources): Pair<ImageBitmap, IntRect> =
        cached ?: synchronized(this) {
            cached ?: decode(resources).also { cached = it }
        }

    private fun decode(resources: Resources): Pair<ImageBitmap, IntRect> {
        val bitmap = BitmapFactory.decodeResource(
            resources,
            R.drawable.ic_launcher_foreground,
            BitmapFactory.Options().apply { inScaled = false; inSampleSize = 4 }
        )
        return bitmap.asImageBitmap() to opaqueBounds(bitmap)
    }

    private fun opaqueBounds(bitmap: Bitmap): IntRect {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h).also { bitmap.getPixels(it, 0, w, 0, 0, w, h) }
        var left = w; var top = h; var right = -1; var bottom = -1
        for (y in 0 until h) for (x in 0 until w) {
            if ((pixels[y * w + x] ushr 24) > 40) {
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        return if (right < 0) IntRect(0, 0, w, h) else IntRect(left, top, right - left + 1, bottom - top + 1)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyIulmsPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val state = rememberPullToRefreshState()
    val haptics = rememberAppHaptics()
    val refreshing by rememberUpdatedState(isRefreshing)

    // One tick at the moment the pull passes the trigger point, i.e. when
    // letting go would refresh. Only for a finger drag: the indicator also
    // reaches the threshold by animation when a refresh starts or ends, and
    // that must stay silent.
    LaunchedEffect(state) {
        snapshotFlow { state.distanceFraction >= 1f }
            .distinctUntilChanged()
            .collect { pastTrigger ->
                if (pastTrigger && !state.isAnimating && !refreshing) {
                    haptics.confirm()
                }
            }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = {
            BuildingRefreshIndicator(
                state = state,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        },
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BuildingRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier
) {
    val thresholdPx = with(LocalDensity.current) { PullToRefreshDefaults.PositionalThreshold.toPx() }
    val chipPx = with(LocalDensity.current) { ChipSize.toPx() }

    // Breathing loop, composed only while a refresh runs.
    val pulse = if (isRefreshing) {
        val transition = rememberInfiniteTransition(label = "refreshPulse")
        transition.animateFloat(
            initialValue = 0.55f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(650, easing = LinearEasing), RepeatMode.Reverse),
            label = "refreshPulseAlpha"
        )
    } else {
        null
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                val fraction = state.distanceFraction
                // Slides down with the pull, from fully hidden above the list.
                translationY = fraction * thresholdPx - chipPx
                // Fully opaque after the first third of the pull, so the
                // fill is always seen at full contrast, never washed out.
                alpha = if (isRefreshing) 1f else (fraction * 3f).coerceIn(0f, 1f)
                val scale = 0.8f + 0.2f * fraction.coerceIn(0f, 1f)
                scaleX = scale
                scaleY = scale
            }
            .then(if (isRefreshing) Modifier.semantics { contentDescription = "Refreshing" } else Modifier)
    ) {
        Surface(
            modifier = Modifier.size(ChipSize),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 6.dp,
            tonalElevation = 0.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                val resources = LocalContext.current.resources
                val (bitmap, crop) = remember(resources) { RefreshLogo.get(resources) }
                val logo = remember(bitmap, crop) {
                    BitmapPainter(bitmap, IntOffset(crop.left, crop.top), IntSize(crop.width, crop.height))
                }
                val logoModifier = Modifier
                    .width(LogoWidth)
                    .aspectRatio(crop.width.toFloat() / crop.height)
                // Light-grey silhouette: the empty logo...
                Image(
                    painter = logo,
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)),
                    modifier = logoModifier
                )
                // ...filled with solid brand colour from the bottom up by the
                // pull, fully filled (and breathing) while refreshing.
                Image(
                    painter = logo,
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                    modifier = logoModifier
                        .graphicsLayer { alpha = pulse?.value ?: 1f }
                        .drawWithContent {
                            val fill = if (isRefreshing) 1f else state.distanceFraction.coerceIn(0f, 1f)
                            clipRect(top = size.height * (1f - fill)) {
                                this@drawWithContent.drawContent()
                            }
                        }
                )
            }
        }
    }
}
