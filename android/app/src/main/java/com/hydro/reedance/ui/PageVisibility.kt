package com.hydro.reedance.ui

import androidx.compose.runtime.staticCompositionLocalOf

/** Retained destinations use this to pause work while they are not visible. */
internal val LocalPageActive = staticCompositionLocalOf { true }
