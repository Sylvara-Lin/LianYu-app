package com.lianyu.ai.uicommon.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import kotlin.math.abs

/**
 * NestedScrollConnection that prevents horizontal scroll events from propagating
 * to the parent when the user is primarily scrolling vertically.
 * Use with LazyColumn to stop accidental side-swipes during chat scrolling.
 *
 * Usage: LazyColumn(modifier = Modifier.nestedScroll(rememberHorizontalSwipeGuard()))
 */
@Composable
fun rememberHorizontalSwipeGuard(): NestedScrollConnection {
    return remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                return if (abs(available.y) > abs(available.x)) {
                    Offset(available.x, 0f) // consume horizontal, pass vertical
                } else {
                    Offset.Zero
                }
            }
        }
    }
}
