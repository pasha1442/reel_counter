package com.reelcounter.feature.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reelcounter.core.data.DeviceGuide
import com.reelcounter.core.design.components.IconTile
import com.reelcounter.core.design.components.RcButton
import com.reelcounter.core.design.components.RcButtonStyle
import com.reelcounter.core.design.components.RcCard
import com.reelcounter.core.design.components.RcIcons
import com.reelcounter.core.design.theme.RcTheme
import com.reelcounter.core.model.CounterHealth

@Composable
internal fun CounterHealthSheet(
    health: CounterHealth,
    onDismiss: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenBattery: () -> Unit,
) {
    val guide = DeviceGuide.guide()
    val extended = RcTheme.extended
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (icon, tint) = when (health) {
                    CounterHealth.HEALTHY -> RcIcons.CheckCircle to extended.successText
                    CounterHealth.NOT_ENABLED -> RcIcons.Accessibility to MaterialTheme.colorScheme.primary
                    CounterHealth.ENABLED_NOT_RUNNING -> RcIcons.Warning to extended.cta
                }
                IconTile(icon, tint, size = 48.dp)
                Spacer(Modifier.width(14.dp))
                Text(
                    text = when (health) {
                        CounterHealth.HEALTHY -> "The counter is running"
                        CounterHealth.NOT_ENABLED -> "The counter is off"
                        CounterHealth.ENABLED_NOT_RUNNING -> "The counter stopped"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = when (health) {
                    CounterHealth.HEALTHY -> "Every Reel and Short you watch is being counted. We check on it every 15 minutes and tell you if it stops."
                    CounterHealth.NOT_ENABLED -> "Turn on Reel Counter in Accessibility settings. Look under ${guide.accessibilityPath}."
                    CounterHealth.ENABLED_NOT_RUNNING -> "Android shows it as on, but your phone stopped it to save battery. Open settings, switch Reel Counter off and on again, then follow the battery steps below so it stays on."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            if (health != CounterHealth.HEALTHY) {
                RcButton(
                    text = "Open Accessibility settings",
                    onClick = onOpenAccessibility,
                    style = RcButtonStyle.Cta,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
            }
            RcCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Text("Keep it running on ${guide.brandName}", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(guide.problem, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                guide.steps.forEachIndexed { index, step ->
                    Text("${index + 1}. $step", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 3.dp))
                }
                Spacer(Modifier.height(12.dp))
                RcButton(
                    text = "Open battery settings",
                    onClick = onOpenBattery,
                    style = RcButtonStyle.Secondary,
                    icon = RcIcons.Battery,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
