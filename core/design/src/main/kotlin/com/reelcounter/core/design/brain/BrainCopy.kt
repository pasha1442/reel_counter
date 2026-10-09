package com.reelcounter.core.design.brain

import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.reelcounter.core.design.R
import com.reelcounter.core.model.BrainStage
import kotlin.math.roundToInt

@StringRes
fun BrainStage.nameRes(): Int = when (this) {
    BrainStage.FRESH -> R.string.stage_fresh
    BrainStage.FOGGY -> R.string.stage_foggy
    BrainStage.CRACKING -> R.string.stage_cracking
    BrainStage.MELTING -> R.string.stage_melting
    BrainStage.ROTTEN -> R.string.stage_rotten
}

@StringRes
fun BrainStage.taglineRes(): Int = when (this) {
    BrainStage.FRESH -> R.string.tagline_fresh
    BrainStage.FOGGY -> R.string.tagline_foggy
    BrainStage.CRACKING -> R.string.tagline_cracking
    BrainStage.MELTING -> R.string.tagline_melting
    BrainStage.ROTTEN -> R.string.tagline_rotten
}

@ArrayRes
fun BrainStage.pokeQuipsRes(): Int = when (this) {
    BrainStage.FRESH -> R.array.poke_fresh
    BrainStage.FOGGY -> R.array.poke_foggy
    BrainStage.CRACKING -> R.array.poke_cracking
    BrainStage.MELTING -> R.array.poke_melting
    BrainStage.ROTTEN -> R.array.poke_rotten
}

@Composable
fun brainDescription(rot: Float): String {
    val stage = BrainStage.fromRot(rot)
    return stringResource(R.string.brain_description, stringResource(stage.nameRes()), rot.roundToInt())
}
