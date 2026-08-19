package paige.navic.ui.components.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Row with tap / long-press / horizontal-swipe arbitration.
 *
 * The default Material3 SwipeToDismissBox drag detector claims the gesture as
 * soon as the finger moves past touch slop, which starves the child's
 * long-press (a hold with a few px of drift becomes a swipe). This composable
 * owns all three gestures so they can coexist:
 *
 * - quick tap (no meaningful movement) -> [onTap]
 * - hold ~400ms with up to [holdTolerance] drift -> [onLongPress]
 * - horizontal drag past [swipeStart] that outpaces vertical drift -> swipe:
 *   the content follows the finger; releasing beyond [actionThreshold] fires
 *   [onSwipeRight]/[onSwipeLeft] and the row settles back.
 * - vertical movement cancels holds/taps so the parent LazyColumn scrolls.
 */
@Composable
fun SwipeActionRow(
	modifier: Modifier = Modifier,
	onTap: () -> Unit,
	onLongPress: () -> Unit,
	onSwipeRight: () -> Unit,
	onSwipeLeft: () -> Unit,
	rightBackground: @Composable BoxScope.() -> Unit,
	leftBackground: @Composable BoxScope.() -> Unit,
	content: @Composable BoxScope.() -> Unit
) {
	val scope = rememberCoroutineScope()
	val offsetX = remember { Animatable(0f) }
	val density = LocalDensity.current
	val holdTolerancePx = with(density) { 24.dp.toPx() }
	val swipeStartPx = with(density) { 24.dp.toPx() }
	val actionThresholdPx = with(density) { 100.dp.toPx() }

	val currentOnTap by rememberUpdatedState(onTap)
	val currentOnLongPress by rememberUpdatedState(onLongPress)
	val currentOnSwipeRight by rememberUpdatedState(onSwipeRight)
	val currentOnSwipeLeft by rememberUpdatedState(onSwipeLeft)

	Box(
		modifier = modifier.pointerInput(Unit) {
			awaitEachGesture {
				val down = awaitFirstDown()
				val startX = down.position.x
				val startY = down.position.y
				var longPressFired = false
				var swiping = false
				var moved = false

				val longPressJob = launch {
					delay(400)
					longPressFired = true
					currentOnLongPress()
				}

				while (true) {
					val event = awaitPointerEvent()
					val change = event.changes.firstOrNull { it.id == down.id }
						?: break
					if (change.isConsumed) {
						// Another handler took the pointer (e.g. the LazyColumn
						// scrolling) - this was movement, not a tap/hold.
						moved = true
						longPressJob.cancel()
						continue
					}

					if (change.changedToUp()) {
						if (!swiping) {
							longPressJob.cancel()
							if (!longPressFired && !moved) currentOnTap()
						} else {
							if (abs(offsetX.value) >= actionThresholdPx) {
								if (offsetX.value > 0) currentOnSwipeRight()
								else currentOnSwipeLeft()
							}
							scope.launch { offsetX.animateTo(0f, tween(220)) }
						}
						break
					}

					val dx = change.position.x - startX
					val dy = change.position.y - startY
					val dist = abs(dx)

					if (!swiping) {
						if (dist > swipeStartPx && dist > abs(dy)) {
							swiping = true
							longPressJob.cancel()
							offsetX.snapTo(dx)
							change.consume()
						} else if (dist > holdTolerancePx || abs(dy) > holdTolerancePx) {
							moved = true
							longPressJob.cancel()
						}
					} else {
						offsetX.snapTo(dx.coerceIn(-1200f, 1200f))
						change.consume()
					}
				}

				longPressJob.cancel()
				if (swiping && offsetX.value != 0f) {
					scope.launch { offsetX.animateTo(0f, tween(220)) }
				}
			}
		}
	) {
		Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
			rightBackground()
		}
		Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
			leftBackground()
		}
		Box(
			Modifier
				.fillMaxSize()
				.offset { IntOffset(offsetX.value.roundToInt(), 0) }
		) {
			content()
		}
	}
}
