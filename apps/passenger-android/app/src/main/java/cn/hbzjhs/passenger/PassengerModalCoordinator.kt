package cn.hbzjhs.passenger

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember

/**
 * UI-only bookkeeping of which modal surfaces are open, so app-wide popups (order confirmations,
 * announcements) wait instead of stacking on a dialog or full page the passenger is already using.
 * It never decides business state; it only tells a popup to stay hidden for now.
 *
 * Ranks: dialogs/pages the passenger opened ([RANK_PAGE]) outrank an announcement, which outranks an
 * order confirmation. A popup is visible only when nothing of a better rank is open, so the order
 * is acyclic and a popup can never be hidden by its own slot.
 */
object PassengerModalCoordinator {
  const val RANK_PAGE = 0
  const val RANK_ANNOUNCEMENT = 1
  const val RANK_PROMPT = 2

  private val open = mutableStateMapOf<Any, Int>()

  /** True while any modal other than [self] is open. Reading it inside composition subscribes to changes. */
  fun othersOpen(self: Any? = null): Boolean = open.keys.any { it !== self }

  /** True while another modal ranked before [rank] is open (a popup of [rank] must stay hidden). */
  fun outranked(self: Any?, rank: Int): Boolean = open.any { (token, r) -> token !== self && r < rank }

  /**
   * A new announcement claim may start only with nothing else open and nothing claimed or held, so
   * a claimed announcement is never re-claimed and the claim is not spent under another modal.
   */
  fun mayClaimAnnouncement(ready: Boolean, claiming: Boolean, held: Boolean, failed: Boolean, paused: Boolean, othersOpen: Boolean): Boolean =
    ready && !claiming && !held && !failed && !paused && !othersOpen

  internal fun mark(token: Any, active: Boolean, rank: Int = RANK_PAGE) {
    if (active) open[token] = rank else open.remove(token)
  }
}

/**
 * Registers the calling surface as open while [active]; returns its token. [active] must not be
 * derived from the coordinator itself, so registering never feeds back into composition.
 */
@Composable
fun PassengerModalSlot(active: Boolean = true, rank: Int = PassengerModalCoordinator.RANK_PAGE): Any {
  val token = remember { Any() }
  DisposableEffect(token, active, rank) {
    PassengerModalCoordinator.mark(token, active, rank)
    onDispose { PassengerModalCoordinator.mark(token, false) }
  }
  return token
}
