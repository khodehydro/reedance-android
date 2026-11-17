package com.hydro.reedance.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide entitlement state. The purchase verifier must be the only caller
 * of updateFromVerifiedStore; no user-facing/debug switch may grant Premium.
 * A store adapter can refresh this after purchase and on app startup.
 */
object PremiumEntitlement {
    private val mutableActive = MutableStateFlow(false)
    val active: StateFlow<Boolean> = mutableActive.asStateFlow()

    @Synchronized
    fun updateFromVerifiedStore(isActive: Boolean) {
        mutableActive.value = isActive
    }
}
