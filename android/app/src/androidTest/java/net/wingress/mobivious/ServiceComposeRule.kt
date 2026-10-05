package net.wingress.mobivious

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher

/** Queue asynchronous service effects instead of resuming them inside a Compose layout pass. */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
internal fun createServiceComposeRule() = createEmptyComposeRule(effectContext = StandardTestDispatcher())
