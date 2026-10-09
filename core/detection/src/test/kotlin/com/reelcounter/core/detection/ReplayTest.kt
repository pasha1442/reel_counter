package com.reelcounter.core.detection

import com.reelcounter.core.detection.replay.Replay
import java.io.File
import kotlin.math.abs
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * Replays every recording in src/test/resources/recordings against the shipped rules pack.
 * Target from the plan: 97% or better against the hand count.
 */
class ReplayTest {

    @TestFactory
    fun `every recording matches its hand count`(): List<DynamicTest> {
        val dir = File(requireNotNull(javaClass.getResource("/recordings")) { "recordings folder missing" }.toURI())
        val files = dir.listFiles { f -> f.extension == "jsonl" }.orEmpty().sortedBy { it.name }
        assertTrue(files.isNotEmpty(), "no recordings found")

        val pack = BundledRules.load()
        return files.map { file ->
            DynamicTest.dynamicTest(file.nameWithoutExtension) {
                val recording = Replay.parse(file.useLines { it.toList() }.asSequence())
                val result = Replay.run(recording, pack)
                val hand = recording.header.handCount
                val error = abs(result.counted - hand).toDouble() / maxOf(hand, 1)
                assertTrue(
                    error <= 0.03,
                    "${file.name}: counted ${result.counted}, hand count $hand (${"%.1f".format(error * 100)}% off)",
                )
            }
        }
    }
}
