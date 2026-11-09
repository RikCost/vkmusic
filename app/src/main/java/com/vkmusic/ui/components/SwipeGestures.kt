package com.vkmusic.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class SwipeDirection { Left, Right, Up, Down }

/** Сдвиг под пальцем. Где его показывать, решает экран: обложка едет вбок, а весь плеер — вниз. */
@Stable
class SwipeState {
    val offsetX = Animatable(0f)
    val offsetY = Animatable(0f)
}

@Composable
fun rememberSwipeState(): SwipeState = remember { SwipeState() }

/**
 * Свайпы по треку. Направление выбирается по первому движению пальца и срабатывает, если палец ушёл
 * дальше порога или смахнул быстро. Влево и вправо отрабатываются здесь же как «карусель»: содержимое
 * уезжает, трек меняется ([onSwipe]), новое въезжает с другой стороны. Вверх и вниз [onSwipe] анимирует
 * сам (например, уводит экран вниз), после чего сдвиг возвращается на место.
 *
 * Направление, для которого [canSwipe] вернул false (нет следующего трека), тянется туго и пружинит обратно.
 * Перемотка ползунком и прокрутка списков не страдают: их жесты забирают дочерние элементы раньше.
 */
@Composable
fun Modifier.trackSwipes(
    state: SwipeState,
    canSwipe: (SwipeDirection) -> Boolean,
    onSwipe: suspend (SwipeDirection) -> Unit,
): Modifier {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val allowed by rememberUpdatedState(canSwipe)
    val action by rememberUpdatedState(onSwipe)
    return pointerInput(state) {
        val threshold = SWIPE_DISTANCE.toPx()
        val flingVelocity = SWIPE_VELOCITY.toPx()
        val tracker = VelocityTracker()
        var horizontal = true
        var axisChosen = false

        fun direction(offset: Float, velocity: Float): SwipeDirection? {
            val far = abs(offset) > threshold || (abs(velocity) > flingVelocity && offset * velocity > 0)
            if (!far || offset == 0f) return null
            return when {
                horizontal && offset < 0 -> SwipeDirection.Left
                horizontal -> SwipeDirection.Right
                offset < 0 -> SwipeDirection.Up
                else -> SwipeDirection.Down
            }.takeIf(allowed)
        }

        detectDragGestures(
            onDragStart = {
                tracker.resetTracking()
                axisChosen = false
            },
            onDragCancel = {
                scope.launch { state.offsetX.animateTo(0f, settle) }
                scope.launch { state.offsetY.animateTo(0f, settle) }
            },
            onDragEnd = {
                val velocity = tracker.calculateVelocity()
                val target = if (horizontal) state.offsetX else state.offsetY
                val swipe = direction(target.value, if (horizontal) velocity.x else velocity.y)
                scope.launch {
                    if (swipe == null) {
                        target.animateTo(0f, settle)
                        return@launch
                    }
                    haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                    if (horizontal) {
                        val width = size.width.toFloat()
                        val side = if (swipe == SwipeDirection.Left) -1f else 1f
                        target.animateTo(side * width, tween(SLIDE_OUT_MS))
                        action(swipe)
                        target.snapTo(-side * width / 2)
                    } else {
                        action(swipe)
                    }
                    target.animateTo(0f, settle)
                }
            },
        ) { change, drag ->
            if (!axisChosen) {
                horizontal = abs(drag.x) >= abs(drag.y)
                axisChosen = true
            }
            change.consume()
            tracker.addPosition(change.uptimeMillis, change.position)
            val target = if (horizontal) state.offsetX else state.offsetY
            val delta = if (horizontal) drag.x else drag.y
            val next = target.value + delta
            val toward = when {
                horizontal && next < 0 -> SwipeDirection.Left
                horizontal -> SwipeDirection.Right
                next < 0 -> SwipeDirection.Up
                else -> SwipeDirection.Down
            }
            // Туда, где ничего не произойдёт, палец тянет туго — видно, что жест есть, но там пусто.
            val resisted = if (allowed(toward)) delta else delta * RESISTANCE
            scope.launch { target.snapTo(target.value + resisted) }
        }
    }
}

/** Горизонтальный сдвиг для «карусели»: содержимое едет за пальцем и бледнеет к краю. */
fun Modifier.swipeOffsetX(state: SwipeState): Modifier = graphicsLayer {
    val offset = state.offsetX.value
    translationX = offset
    alpha = if (size.width > 0f) 1f - (abs(offset) / size.width).coerceIn(0f, MAX_FADE) else 1f
}

private val settle = spring<Float>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)

private val SWIPE_DISTANCE = 72.dp
private val SWIPE_VELOCITY = 800.dp
private const val SLIDE_OUT_MS = 140
private const val RESISTANCE = 0.25f
private const val MAX_FADE = 0.7f
