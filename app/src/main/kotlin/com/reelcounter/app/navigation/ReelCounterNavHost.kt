package com.reelcounter.app.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.reelcounter.core.data.DeepLinks
import com.reelcounter.core.design.theme.LocalReducedMotion
import com.reelcounter.core.design.theme.RcMotion
import com.reelcounter.feature.account.AccountRoute
import com.reelcounter.feature.account.EmailAuthRoute
import com.reelcounter.feature.account.ProfileSetupRoute
import com.reelcounter.feature.account.SignInRoute
import com.reelcounter.feature.friends.FriendsRoute
import com.reelcounter.feature.history.DayDetailRoute
import com.reelcounter.feature.history.ProgressRoute
import com.reelcounter.feature.history.ShareWeekRoute
import com.reelcounter.feature.home.BrainRoute
import com.reelcounter.feature.home.HomeRoute
import com.reelcounter.feature.limits.LimitsRoute
import com.reelcounter.feature.onboarding.OnboardingRoute
import com.reelcounter.feature.settings.LegalRoute
import com.reelcounter.feature.settings.PaywallRoute
import com.reelcounter.feature.settings.SettingsRoute

@Composable
fun ReelCounterNavHost(
    onContentReady: () -> Unit,
    onboardingComplete: Boolean,
    pendingDestination: String?,
    onDestinationHandled: () -> Unit,
    healthRequested: Boolean,
    onHealthRequestHandled: () -> Unit,
    onAddWidget: () -> Unit,
) {
    val nav = rememberNavController()
    // Decided once: finishing onboarding must not rebuild the graph mid-navigation.
    val startDestination: Dest = remember { if (onboardingComplete) Dest.Home else Dest.Onboarding() }
    val entry by nav.currentBackStackEntryAsState()
    val currentTab = TopLevel.entries.firstOrNull { tab -> entry?.destination?.hasRoute(tab.dest::class) == true }
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val contentPadding = PaddingValues(bottom = NavBarHeight + navInset + 4.dp)
    val reducedMotion = LocalReducedMotion.current
    // Onboarding has everything it needs straight away; Today says when its data has arrived.
    LaunchedEffect(Unit) { if (startDestination != Dest.Home) onContentReady() }

    // Tabs are siblings: a quick fade-through. Screens opened on top rise from below and sink back
    // when closed, so direction matches depth. Exits run shorter than enters.
    val tabEnter: EnterTransition = fadeIn(RcMotion.enter(RcMotion.SHORT + 60)) +
        if (reducedMotion) EnterTransition.None else scaleIn(RcMotion.enter(RcMotion.SHORT + 60), initialScale = 0.98f)
    val tabExit: ExitTransition = fadeOut(RcMotion.exit(RcMotion.SHORT))
    val riseIn: EnterTransition = fadeIn(RcMotion.enter()) +
        if (reducedMotion) EnterTransition.None else slideInVertically(RcMotion.enter()) { it / 5 }
    val sinkOut: ExitTransition = fadeOut(RcMotion.exit()) +
        if (reducedMotion) ExitTransition.None else slideOutVertically(RcMotion.exit()) { it / 5 }

    // Settings > "Replay onboarding" flips this back to false while the app is open.
    LaunchedEffect(onboardingComplete) {
        if (!onboardingComplete && entry != null && entry?.destination?.hasRoute(Dest.Onboarding::class) != true) {
            nav.navigate(Dest.Onboarding()) { popUpTo(0) }
        }
    }

    LaunchedEffect(pendingDestination, onboardingComplete) {
        if (pendingDestination == null || !onboardingComplete) return@LaunchedEffect
        when (pendingDestination) {
            DeepLinks.PAYWALL -> nav.navigate(Dest.Paywall())
            DeepLinks.HEALTH -> nav.navigateToTab(TopLevel.HOME)
        }
        onDestinationHandled()
    }

    val back: () -> Unit = { if (!nav.popBackStack()) nav.navigate(Dest.Home) { popUpTo(0) } }
    val openPaywall: () -> Unit = { nav.navigate(Dest.Paywall()) }
    val openProfile: () -> Unit = { nav.navigate(Dest.Settings) { launchSingleTop = true } }
    val openLegal: (String) -> Unit = { page -> nav.navigate(Dest.Legal(page)) }
    val openSignIn: () -> Unit = { nav.navigate(Dest.SignIn) { launchSingleTop = true } }
    // After signing in: finish a new account, or go back to wherever sign-in was opened from.
    val afterSignIn: (Boolean) -> Unit = { needsSetup ->
        if (needsSetup) {
            nav.navigate(Dest.ProfileSetup) { popUpTo<Dest.SignIn> { inclusive = true } }
        } else if (!nav.popBackStack<Dest.SignIn>(inclusive = true)) {
            back()
        }
    }

    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = nav,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { tabEnter },
            exitTransition = { tabExit },
            popEnterTransition = { tabEnter },
            popExitTransition = { tabExit },
        ) {
            composable<Dest.Onboarding>(
                enterTransition = { riseIn },
                popExitTransition = { sinkOut },
            ) { backStackEntry ->
                val args = backStackEntry.toRoute<Dest.Onboarding>()
                OnboardingRoute(
                    onFinished = {
                        if (args.startAtDisclosure) {
                            if (!nav.popBackStack()) nav.navigate(Dest.Home) { popUpTo(0) }
                        } else {
                            nav.navigate(Dest.Paywall(fromOnboarding = true)) { popUpTo(0) }
                        }
                    },
                    onDeclined = {
                        if (!args.startAtDisclosure || !nav.popBackStack()) nav.navigate(Dest.Home) { popUpTo(0) }
                    },
                )
            }
            composable<Dest.Home> {
                HomeRoute(
                    onReady = onContentReady,
                    contentPadding = contentPadding,
                    onOpenPaywall = openPaywall,
                    onSetUpCounting = { nav.navigate(Dest.Onboarding(startAtDisclosure = true)) },
                    onOpenBrain = { nav.navigate(Dest.Brain) },
                    onOpenProfile = openProfile,
                    showHealthRequest = healthRequested,
                    onHealthRequestHandled = onHealthRequestHandled,
                )
            }
            composable<Dest.Progress> {
                ProgressRoute(
                    contentPadding = contentPadding,
                    onOpenPaywall = openPaywall,
                    onOpenDay = { nav.navigate(Dest.DayDetail(it.toEpochDay())) },
                    onShareWeek = { nav.navigate(Dest.ShareWeek(it.toEpochDay())) },
                )
            }
            composable<Dest.Limits> {
                LimitsRoute(contentPadding = contentPadding, onOpenPaywall = openPaywall, onOpenProfile = openProfile)
            }
            composable<Dest.Friends> {
                FriendsRoute(
                    contentPadding = contentPadding,
                    onOpenProfile = openProfile,
                    onSignIn = openSignIn,
                    onFinishSetup = { nav.navigate(Dest.ProfileSetup) { launchSingleTop = true } },
                )
            }
            composable<Dest.Settings>(enterTransition = { riseIn }, popExitTransition = { sinkOut }) {
                SettingsRoute(
                    onBack = back,
                    onOpenPaywall = openPaywall,
                    onAddWidget = onAddWidget,
                    onSignIn = openSignIn,
                    onOpenAccount = { nav.navigate(Dest.Account) { launchSingleTop = true } },
                    onOpenLegal = openLegal,
                )
            }
            composable<Dest.SignIn>(enterTransition = { riseIn }, popExitTransition = { sinkOut }) {
                SignInRoute(
                    onBack = back,
                    onEmail = { nav.navigate(Dest.EmailAuth()) },
                    onSignedIn = afterSignIn,
                    onOpenLegal = openLegal,
                )
            }
            composable<Dest.EmailAuth>(enterTransition = { riseIn }, popExitTransition = { sinkOut }) { backStackEntry ->
                EmailAuthRoute(
                    startCreating = backStackEntry.toRoute<Dest.EmailAuth>().creating,
                    onBack = back,
                    onSignedIn = afterSignIn,
                    onOpenLegal = openLegal,
                )
            }
            composable<Dest.ProfileSetup>(enterTransition = { riseIn }, popExitTransition = { sinkOut }) {
                ProfileSetupRoute(onDone = back)
            }
            composable<Dest.Account>(enterTransition = { riseIn }, popExitTransition = { sinkOut }) {
                AccountRoute(
                    onBack = back,
                    onOpenPaywall = openPaywall,
                    onOpenLegal = openLegal,
                    onFinishSetup = { nav.navigate(Dest.ProfileSetup) },
                )
            }
            composable<Dest.Legal>(enterTransition = { riseIn }, popExitTransition = { sinkOut }) { backStackEntry ->
                LegalRoute(page = backStackEntry.toRoute<Dest.Legal>().page, onBack = back)
            }
            composable<Dest.Brain>(enterTransition = { riseIn }, popExitTransition = { sinkOut }) {
                BrainRoute(onBack = back)
            }
            composable<Dest.DayDetail>(enterTransition = { riseIn }, popExitTransition = { sinkOut }) {
                DayDetailRoute(onBack = back, onOpenPaywall = openPaywall)
            }
            composable<Dest.ShareWeek>(enterTransition = { riseIn }, popExitTransition = { sinkOut }) {
                ShareWeekRoute(onBack = back)
            }
            composable<Dest.Paywall>(
                enterTransition = { riseIn },
                popExitTransition = { sinkOut },
            ) { backStackEntry ->
                val args = backStackEntry.toRoute<Dest.Paywall>()
                PaywallRoute(
                    fromOnboarding = args.fromOnboarding,
                    onClose = {
                        if (args.fromOnboarding || !nav.popBackStack()) nav.navigate(Dest.Home) { popUpTo(0) }
                    },
                    onOpenLegal = openLegal,
                )
            }
        }

        // Screens scroll edge to edge; this keeps their content from running under the clock.
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f)),
        )

        AnimatedVisibility(
            visible = currentTab != null,
            enter = slideInVertically(RcMotion.enter()) { it } + fadeIn(RcMotion.enter()),
            exit = slideOutVertically(RcMotion.exit()) { it } + fadeOut(RcMotion.exit()),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            AppNavBar(current = currentTab, onSelect = { nav.navigateToTab(it) })
        }
    }
}

private fun NavHostController.navigateToTab(tab: TopLevel) {
    navigate(tab.dest) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
