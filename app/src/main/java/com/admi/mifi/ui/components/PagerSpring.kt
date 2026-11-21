// Page navigation spring from compose-miuix-ui (Apache-2.0), used by the
// KernelSU Style UI Kit template. Adjacent tab switches use the template spec;
// jumps over more than one page use a faster spring so the page keeps up with
// the bottom-bar indicator.
package com.admi.mifi.ui.components

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

/** Identical to Miuix `PagerNavigationSpringSpec`. */
val PagerNavigationSpringSpec: SpringSpec<Float> =
    spring(
        stiffness = 322.2f,
        dampingRatio = 32.31f / (2f * sqrt(322.2f)),
        visibilityThreshold = 0.5f,
    )

/** Faster spring for multi-page jumps (e.g. Home -> Settings). */
val FastPagerSpringSpec: SpringSpec<Float> =
    spring(
        stiffness = 700f,
        dampingRatio = 0.9f,
        visibilityThreshold = 1f,
    )

class MifiMainPagerState(
    val pagerState: PagerState,
    private val coroutineScope: CoroutineScope,
) {
    // Expose the backing state explicitly so composition always observes the
    // current value, even through this plain (non-@Stable) holder class.
    val selectedPageState = mutableIntStateOf(pagerState.currentPage)
    val selectedPage: Int get() = selectedPageState.value

    var isNavigating by mutableStateOf(false)
        private set

    private var navJob: Job? = null
    private var pendingTarget: Int? = null

    fun animateToPage(targetIndex: Int) {
        // The floating bar emits onSelected both from its own drag state and from
        // our selected state. Ignore the duplicate while a switch is in flight.
        if (targetIndex == pendingTarget || targetIndex == selectedPage) return

        navJob?.cancel()
        pendingTarget = targetIndex
        selectedPageState.value = targetIndex
        isNavigating = true

        navJob =
            coroutineScope.launch {
                val myJob = coroutineContext.job
                var completed = false
                try {
                    val spec =
                        if (abs(targetIndex - pagerState.currentPage) > 1) {
                            FastPagerSpringSpec
                        } else {
                            PagerNavigationSpringSpec
                        }
                    // Use the pager's own animation so currentPage and scroll offset
                    // can never disagree. The old manual updateCurrentPage approach
                    // could leave the bar on Home while Settings was displayed.
                    pagerState.animateScrollToPage(
                        page = targetIndex,
                        animationSpec = spec,
                    )
                    completed = true
                } catch (_: CancellationException) {
                    // A newer tab tap cancelled us; the newer job owns the state.
                } finally {
                    if (navJob == myJob) {
                        isNavigating = false
                        pendingTarget = null
                        if (pagerState.currentPage != targetIndex) {
                            selectedPageState.value = pagerState.currentPage
                        }
                    }
                }
                if (completed && pagerState.currentPage != targetIndex) {
                    pagerState.scrollToPage(targetIndex)
                }
            }
    }

    fun syncPage() {
        if (!isNavigating && selectedPage != pagerState.currentPage) {
            selectedPageState.value = pagerState.currentPage
        }
    }
}

@Composable
fun rememberMifiMainPagerState(
    pagerState: PagerState,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
): MifiMainPagerState =
    remember(pagerState, coroutineScope) {
        MifiMainPagerState(pagerState, coroutineScope)
    }
