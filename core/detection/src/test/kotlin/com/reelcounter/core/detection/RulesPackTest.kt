package com.reelcounter.core.detection

import com.reelcounter.core.model.ReelApp
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RulesPackTest {

    private val pack = RulesPack(
        version = 3,
        apps = listOf(
            AppRules(IG, minVersionCode = 0, maxVersionCode = 99, surfaceViewIds = listOf("old")),
            AppRules(IG, minVersionCode = 100, maxVersionCode = 199, broken = true),
            AppRules(IG, minVersionCode = 200, surfaceViewIds = listOf("new")),
        ),
    )

    @Test
    fun `resolves the rule for the installed version`() {
        assertEquals(listOf("old"), (pack.resolve(IG, 50) as ResolvedRules.Specific).rules.surfaceViewIds)
        assertEquals(listOf("new"), (pack.resolve(IG, 250) as ResolvedRules.Specific).rules.surfaceViewIds)
    }

    @Test
    fun `a broken version range pauses the app`() {
        assertEquals(ResolvedRules.Paused, pack.resolve(IG, 150))
    }

    @Test
    fun `apps without rules use the generic fallback`() {
        assertEquals(ResolvedRules.Generic, pack.resolve("com.google.android.youtube", 1))
    }

    @Test
    fun `unknown installed version uses the newest working rule`() {
        assertEquals(listOf("new"), (pack.resolve(IG, null) as ResolvedRules.Specific).rules.surfaceViewIds)
    }

    @Test
    fun `the bundled pack covers every supported app`() {
        val bundled = BundledRules.load()
        ReelApp.entries.forEach { app ->
            assertTrue(bundled.resolve(app.packageName, 1) is ResolvedRules.Specific, "no rules for $app")
        }
    }

    @Test
    fun `parser ignores fields it does not know`() {
        val parsed = RulesPackParser.parse("""{"version":7,"future":true,"apps":[{"packageName":"$IG","extra":1}]}""")
        assertEquals(7, parsed.version)
        assertEquals(IG, parsed.apps.single().packageName)
    }

    @Test
    fun `verifier accepts our signature and rejects tampering`() {
        val keys = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val body = """{"version":9,"apps":[]}""".toByteArray()
        val signature = Signature.getInstance(RulesPackVerifier.ALGORITHM).run {
            initSign(keys.private)
            update(body)
            Base64.getEncoder().encodeToString(sign())
        }
        val verifier = RulesPackVerifier(Base64.getEncoder().encodeToString(keys.public.encoded))

        assertTrue(verifier.verify(body, signature))
        assertFalse(verifier.verify("""{"version":9,"apps":[{}]}""".toByteArray(), signature))
        assertFalse(verifier.verify(body, "not-base64!"))
    }
}
