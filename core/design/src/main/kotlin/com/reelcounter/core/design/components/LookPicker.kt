package com.reelcounter.core.design.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.reelcounter.core.design.brain.Brain
import com.reelcounter.core.design.theme.RcTheme
import com.reelcounter.core.model.AppLook

/**
 * Two live previews side by side, each drawn in its own look, so people pick by seeing, not reading.
 * Used in onboarding and in Settings.
 */
@Composable
fun LookPicker(
    selected: AppLook,
    onSelect: (AppLook) -> Unit,
    modifier: Modifier = Modifier,
) {
    val outerScheme = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AppLook.entries.forEach { look ->
            val on = look == selected
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .border(if (on) 2.dp else 1.dp, if (on) outerScheme.onSurface else outerScheme.outline, RoundedCornerShape(22.dp))
                    .clickable(role = Role.RadioButton) { onSelect(look) }
                    .semantics {
                        this.selected = on
                        contentDescription = "${look.displayName} look. ${look.tagline}"
                    },
            ) {
                RcTheme(look = look) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .lookSurface(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), MaterialTheme.colorScheme.background, outlined = false)
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Brain(rot = 8f, modifier = Modifier.size(92.dp))
                        Text("13", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text("of 20 today", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .lookSurface(
                                    if (RcTheme.tokens.isSticker) RoundedCornerShape(9.dp) else CircleShape,
                                    MaterialTheme.colorScheme.primaryContainer,
                                    raised = true,
                                    shadowSize = 2.dp,
                                    thin = true,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Let's go", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(look.displayName, style = MaterialTheme.typography.titleSmall)
                        Text(look.tagline, style = MaterialTheme.typography.bodySmall, color = outerScheme.onSurfaceVariant, maxLines = 2)
                    }
                    if (on) {
                        Box(Modifier.size(24.dp).clip(CircleShape).lookSurface(CircleShape, outerScheme.onSurface, outlined = false), contentAlignment = Alignment.Center) {
                            Icon(RcIcons.Check, contentDescription = null, tint = outerScheme.surface, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
