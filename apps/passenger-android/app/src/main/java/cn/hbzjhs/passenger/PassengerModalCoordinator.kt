package cn.hbzjhs.passenger

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember

/**
 * UI-only bookkeeping of which modal surfaces are open, so app-wide popups (order confirmations,
 * announcements) wait instead of stacking on a dialog or full page the passenger is already using.
 * It never decides business state; it only tells a popup to stay hidden for now.
 */
object PassengerModalCoordinator {
  private val open = mutableStateMapOf<Any, Unit>()

  /** True while any modal other than [self] is open. Reading it inside composition subscribes to changes. */
  fun othersOpen(self: Any? = null): Boolean = open.keys.any { it !== self }

  internal fun mark(token: Any, active: Boolean) {
    if (active) open[token] = Unit else open.remove(token)
  }
}

/** Registers the calling surface as open while [active]; returns its token for [PassengerModalCoordinator.othersOpen]. */
@Composable
fun PassengerModalSlot(active: Boolean = true): Any {
  val token = remember { Any() }
  DisposableEffect(token, active) {
    PassengerModalCoordinator.mark(token, active)
    onDispose { PassengerModalCoordinator.mark(token, false) }
  }
  return token
}
