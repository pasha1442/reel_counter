package com.reelcounter.feature.friends

import android.content.Intent
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import com.reelcounter.core.design.theme.RcSpacing
import com.reelcounter.core.design.components.ScreenHeader
import com.reelcounter.core.design.components.ListDivider
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.reelcounter.core.data.account.AccountUser
import com.reelcounter.core.data.account.AuthMode
import com.reelcounter.core.data.account.AuthRepository
import com.reelcounter.core.data.repository.FriendsRepository
import com.reelcounter.core.data.settings.SettingsRepository
import kotlinx.coroutines.flow.combine
import com.reelcounter.core.data.repository.FriendsState
import com.reelcounter.core.data.repository.Standing
import com.reelcounter.core.design.brain.Brain
import com.reelcounter.core.design.components.Pill
import com.reelcounter.core.design.components.RcButton
import com.reelcounter.core.design.components.RcButtonStyle
import com.reelcounter.core.design.components.RcCard
import com.reelcounter.core.design.components.RcIconButton
import com.reelcounter.core.design.components.RcIcons
import com.reelcounter.core.design.components.SectionHeader
import com.reelcounter.core.design.components.lookLabel
import com.reelcounter.core.design.components.lookSurface
import com.reelcounter.core.design.theme.RcTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val friends: FriendsRepository,
    auth: AuthRepository,
    settings: SettingsRepository,
) : ViewModel() {
    val state: StateFlow<FriendsState?> = friends.state.map { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val user: StateFlow<AccountUser?> = auth.user

    val setupComplete: StateFlow<Boolean> = combine(auth.user, settings.settings) { user, s ->
        user != null && user.hasName && user.id in s.ageConfirmedAccounts
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Sign-in is offered only when this build has accounts. */
    val accountsAvailable: Boolean = auth.mode != AuthMode.OFF

    fun inviteMessage(): String = friends.inviteMessage()
}

@Composable
fun FriendsRoute(
    contentPadding: PaddingValues,
    onOpenProfile: () -> Unit,
    onSignIn: () -> Unit,
    onFinishSetup: () -> Unit,
    viewModel: FriendsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val user by viewModel.user.collectAsStateWithLifecycle()
    val setupComplete by viewModel.setupComplete.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val invite = {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, viewModel.inviteMessage())
        }
        context.startActivity(Intent.createChooser(send, "Invite a friend"))
    }
    val s = state ?: return
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = RcSpacing.gutter),
    ) {
        ScreenHeader("Friends") {
            RcIconButton(RcIcons.Person, "Profile and settings", onOpenProfile)
        }
        if (s.battlesAvailable) {
            Battle(s, invite)
        } else {
            ComingSoon(
                you = s.standings.first(),
                onInvite = invite,
                user = user,
                setupComplete = setupComplete,
                onSignIn = if (viewModel.accountsAvailable) onSignIn else null,
                onFinishSetup = onFinishSetup,
            )
        }
        Spacer(Modifier.height(RcSpacing.section))
    }
}

@Composable
private fun Battle(state: FriendsState, onInvite: () -> Unit) {
    val extended = RcTheme.extended
    val scheme = MaterialTheme.colorScheme
    Text(
        "Lowest share of your own limit wins, so a limit of 20 and a limit of 40 compete fairly. Ends at midnight.",
        style = MaterialTheme.typography.bodyMedium,
        color = scheme.onSurfaceVariant,
    )
    if (state.demo) {
        Spacer(Modifier.height(12.dp))
        Pill("Sample battle · debug build", color = extended.tintSky, contentColor = extended.onTintSky)
    }
    SectionHeader("Today's battle")
    RcCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 6.dp)) {
        var rank = 0
        state.standings.forEachIndexed { i, standing ->
            if (i > 0) ListDivider()
            val ranked = standing.trackingOffHours == null
            if (ranked) rank++
            StandingRow(standing, if (ranked) rank else null, leading = ranked && rank == 1, colorIndex = i)
        }
    }
    Spacer(Modifier.height(RcSpacing.item))
    RcButton("Add a friend", onInvite, style = RcButtonStyle.Secondary, icon = RcIcons.PersonAdd, modifier = Modifier.fillMaxWidth())
    if (state.past.isNotEmpty()) {
        SectionHeader("Past results")
        RcCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 6.dp)) {
            state.past.forEachIndexed { i, past ->
                if (i > 0) ListDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = RcSpacing.card, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (past.youWon) RcIcons.Trophy else RcIcons.Friends,
                        contentDescription = null,
                        tint = if (past.youWon) extended.successText else scheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (past.youWon) "You won" else "${past.winner} won",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        )
                        Text(past.date.format(DateTimeFormatter.ofPattern("EEEE d MMM")), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                    Text("You ${past.yourPercent}%", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun StandingRow(standing: Standing, rank: Int?, leading: Boolean, colorIndex: Int = 0) {
    val extended = RcTheme.extended
    val scheme = MaterialTheme.colorScheme
    val over = standing.reels > standing.limit
    val off = standing.trackingOffHours != null
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (off) 0.6f else 1f)
            .padding(horizontal = RcSpacing.card, vertical = 12.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append(if (standing.isYou) "You" else standing.name)
                    if (rank != null) append(", rank $rank") else if (off) append(", not ranked, counting was off")
                    append(", ${standing.reels} of ${standing.limit} reels, ${standing.sharePercent} percent")
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Ranks only mean something in a battle; a lone "your score" row has none.
        if (rank != null || off) {
            Text(
                rank?.toString() ?: "–",
                style = MaterialTheme.typography.titleMedium,
                color = if (leading) scheme.onSurface else scheme.onSurfaceVariant,
                modifier = Modifier.width(24.dp),
            )
        }
        Avatar(standing, colorIndex)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(standing.name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                if (leading) {
                    Spacer(Modifier.width(8.dp))
                    Icon(RcIcons.Trophy, contentDescription = null, tint = if (RcTheme.tokens.isSticker) scheme.onSurface else extended.cta, modifier = Modifier.size(16.dp))
                }
            }
            Text(
                if (off) "Counting was off for ${standing.trackingOffHours} hours" else "${standing.reels} of ${standing.limit} reels",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
        Text(
            if (off) "Not ranked" else "${standing.sharePercent}%",
            style = if (off) MaterialTheme.typography.bodySmall else MaterialTheme.typography.titleMedium,
            color = when {
                off -> scheme.onSurfaceVariant
                over -> extended.warningText
                else -> scheme.onSurface
            },
        )
    }
}

@Composable
private fun Avatar(standing: Standing, colorIndex: Int) {
    val extended = RcTheme.extended
    val sticker = RcTheme.tokens.isSticker
    val (fill, content) = when {
        standing.isYou -> extended.cta to extended.onCta
        else -> listOf(
            extended.tintSky to extended.onTintSky,
            extended.tintMint to extended.onTintMint,
            extended.tintWarm to extended.onTintWarm,
        )[colorIndex.mod(3)]
    }
    Box(
        Modifier
            .size(36.dp)
            .lookSurface(CircleShape, fill, thin = true),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (standing.isYou) "You" else standing.initial,
            style = if (standing.isYou) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
            color = if (sticker) Color(0xFF111111) else content,
        )
    }
}

/** Before accounts exist: explain battles honestly, show the user's own score, and let them invite. */
@Composable
private fun ComingSoon(
    you: Standing,
    onInvite: () -> Unit,
    user: AccountUser?,
    setupComplete: Boolean,
    onSignIn: (() -> Unit)?,
    onFinishSetup: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Brain(rot = 0f, celebrateKey = Unit, modifier = Modifier.size(136.dp))
        Spacer(Modifier.height(16.dp))
        Text("Battle your friends", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Each day, whoever uses the smallest share of their own limit wins. Battles arrive with free accounts in the next update.",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        RcButton("Invite a friend", onInvite, icon = RcIcons.PersonAdd, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Text(
            "They'll have a few days of counts ready when battles open.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (user != null && setupComplete) {
            Spacer(Modifier.height(16.dp))
            Pill("Signed in as ${user.name ?: user.email.orEmpty()} · you're ready", icon = RcIcons.Check)
        } else if (user != null) {
            Spacer(Modifier.height(12.dp))
            RcButton("Finish your account", onFinishSetup, style = RcButtonStyle.Secondary, icon = RcIcons.Person, modifier = Modifier.fillMaxWidth())
        } else if (onSignIn != null) {
            Spacer(Modifier.height(12.dp))
            RcButton("Sign in to get ready", onSignIn, style = RcButtonStyle.Secondary, icon = RcIcons.Login, modifier = Modifier.fillMaxWidth())
        }
    }
    SectionHeader("Your score today")
    RcCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 6.dp)) {
        StandingRow(you, rank = null, leading = false)
    }
    Spacer(Modifier.height(10.dp))
    Text(
        "${you.sharePercent}% of your limit used. Lower is better.",
        style = MaterialTheme.typography.bodySmall,
        color = scheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}
