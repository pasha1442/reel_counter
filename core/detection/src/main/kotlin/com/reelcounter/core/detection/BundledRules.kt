package com.reelcounter.core.detection

/** The rules pack shipped inside the app; used until a newer signed pack is downloaded. */
object BundledRules {
    private const val PATH = "/rules/default_rules_pack.json"

    fun load(): RulesPack {
        val text = BundledRules::class.java.getResourceAsStream(PATH)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("Missing bundled rules pack at $PATH")
        return RulesPackParser.parse(text)
    }
}
