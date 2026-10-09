package com.reelcounter.core.design.components

import com.reelcounter.core.design.theme.RcTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** What to show where data will appear later: an icon, a short line of why, and what happens next. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconTile(icon, tint = tint, size = 48.dp)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** A one-tap starting point for the daily limit, shared by onboarding and the Limits screen. */
val LimitPresets = listOf(20 to "Monk mode", 40 to "Balanced", 60 to "Easing in", 100 to "Baby steps")

@Composable
fun LimitPresetChips(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val sticker = RcTheme.tokens.isSticker
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LimitPresets.forEach { (value, label) ->
            val isSelected = selected == value
            val shape = RoundedCornerShape(12.dp)
            // Midnight marks the choice with a lifted neutral surface and an amber number; Sticker with the yellow.
            val fill = when {
                isSelected && sticker -> scheme.primaryContainer
                isSelected -> scheme.surfaceContainerHighest
                sticker -> scheme.surfaceContainer
                else -> scheme.surfaceContainerHigh
            }
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .lookSurface(shape, fill, thin = !isSelected)
                    .then(if (isSelected && !sticker) Modifier.border(1.5.dp, scheme.primary, shape) else Modifier)
                    .presetClick {
                        if (!isSelected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSelect(value)
                    }
                    .padding(vertical = 8.dp)
                    .semantics {
                        stateDescription = if (isSelected) "Selected" else "Not selected"
                        contentDescription = "$label, $value reels"
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "$value",
                    style = MaterialTheme.typography.titleMedium,
                    color = when {
                        isSelected && sticker -> scheme.onPrimaryContainer
                        isSelected -> scheme.primary
                        else -> scheme.onSurface
                    },
                )
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected && sticker) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun Modifier.presetClick(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(role = androidx.compose.ui.semantics.Role.RadioButton, onClick = onClick))
