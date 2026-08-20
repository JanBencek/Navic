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
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Row with a horizontal-swipe action layer. Tap and long-press are NOT
 * handled here - they belong to the child's own clickable (ListItem), which
 * works natively and reliably. This layer only claims a gesture once the
 * finger moves horizontally past [swipeStart] while out-pacing vertical
 * drift, so holds and taps (which stay within touch slop) are left to the
 * child untouched.
 *
 * - horizontal drag past [swipeStart] that outpaces vertical drift -> the
 *   content follows the finger; releasing beyond [actionThreshold] fires
 *   [onSwipeRight]/[onSwipeLeft] and the row settles back.
 * - taps / long-presses / vertical scrolls pass through to the child and
 *   the parent LazyColumn, which consume their own events.
 */
@Composable
fun SwipeActionRow(
	modifier: Modifier = Modifier,
	onSwipeRight: () -> Unit,
	onSwipeLeft: () -> Unit,
	rightBackground: @Composable BoxScope.() -> Unit,
	leftBackground: @Composable BoxScope.() -> Unit,
	content: @Composable BoxScope.() -> Unit
) {
	val scope = rememberCoroutineScope()
	val offsetX = remember { Animatable(0f) }
	val density = LocalDensity.current
	val swipeStartPx = with(density) { 24.dp.toPx() }
	val actionThresholdPx = with(density) { 100.dp.toPx() }

	val currentOnSwipeRight by rememberUpdatedState(onSwipeRight)
	val currentOnSwipeLeft by rememberUpdatedState(onSwipeLeft)

	Box(
		modifier = modifier.pointerInput(Unit) {
			awaitEachGesture {
				// The child clickable CONSUMES the down the moment it claims a
				// press, so requireUnconsumed must be false here - otherwise we
				// wait forever for a down that never arrives and swipes are dead.
				val down = awaitFirstDown(requireUnconsumed = false)
				val startX = down.position.x
				val startY = down.position.y
				var swiping = false

				while (true) {
					val event = awaitPointerEvent()
					val change = event.changes.firstOrNull { it.id == down.id }
						?: break

					if (change.changedToUp()) {
						if (swiping) {
							if (abs(offsetX.value) >= actionThresholdPx) {
								if (offsetX.value > 0) currentOnSwipeRight()
								else currentOnSwipeLeft()
							}
							scope.launch { offsetX.animateTo(0f, tween(220)) }
						}
						break
					}

					if (!swiping) {
						val dx = change.position.x - startX
						val dy = change.position.y - startY
						if (abs(dx) > swipeStartPx && abs(dx) > abs(dy)) {
							swiping = true
							scope.launch { offsetX.snapTo(dx) }
							change.consume()
						}
					} else {
						val dx = change.position.x - startX
						scope.launch { offsetX.snapTo(dx.coerceIn(-1200f, 1200f)) }
						change.consume()
					}
				}

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
