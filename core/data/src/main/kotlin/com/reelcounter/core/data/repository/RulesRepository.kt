package com.reelcounter.core.data.repository

import com.reelcounter.core.detection.BundledRules
import com.reelcounter.core.detection.RulesPack
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * The detection rules in use. Starts from the pack bundled in the APK.
 * Next step: fetch newer packs (version in Remote Config, file in Cloud Storage), check them with
 * RulesPackVerifier, persist the last good one, and publish it here.
 */
@Singleton
class RulesRepository @Inject constructor() {
    private val current = MutableStateFlow<RulesPack?>(null)
    val pack: StateFlow<RulesPack?> = current.asStateFlow()

    suspend fun load(): RulesPack = current.value ?: withContext(Dispatchers.IO) {
        BundledRules.load().also { current.value = it }
    }
}
