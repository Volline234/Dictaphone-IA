package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.CustomTemplateScreen
import com.example.ui.screens.FaqHelpScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.PaywallScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MainViewModel
import android.content.Context
import androidx.activity.SystemBarStyle
import androidx.annotation.StringRes
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import com.example.settings.LocaleHelper
import com.example.ui.components.isAppInDarkTheme

enum class NavigationDestination(
    @StringRes val titleRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME(R.string.nav_home, Icons.Filled.Mic, Icons.Outlined.Mic),
    HISTORY(R.string.nav_history, Icons.Filled.History, Icons.Outlined.History),
    TEMPLATES(R.string.nav_templates, Icons.Filled.Tune, Icons.Outlined.Tune),
    FAQ(R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings)
}

class MainActivity : ComponentActivity() {

    /** Applies the interface language chosen in the app (French / English / device). */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel()
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val darkTheme = isAppInDarkTheme(themeMode)

            // Keep status/navigation bar icons readable when the user forces light or dark mode
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    ) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(
                        LightNavigationScrim,
                        DarkNavigationScrim
                    ) { darkTheme }
                )
                onDispose {}
            }

            MyApplicationTheme(darkTheme = darkTheme) {
                DictaphoneApp(viewModel = viewModel)
            }
        }
    }
}

private val LightNavigationScrim = android.graphics.Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DarkNavigationScrim = android.graphics.Color.argb(0x80, 0x1b, 0x1b, 0x1b)

@Composable
fun DictaphoneApp(viewModel: MainViewModel = viewModel()) {
    val hasCompletedOnboarding by viewModel.hasCompletedOnboarding.collectAsStateWithLifecycle()
    val showPaywall by viewModel.showPaywall.collectAsStateWithLifecycle()

    // Saveable so the current tab survives the Activity recreation after a language change
    var currentDestination by rememberSaveable { mutableStateOf(NavigationDestination.HOME) }

    if (!hasCompletedOnboarding) {
        OnboardingScreen(
            viewModel = viewModel,
            onFinish = { viewModel.completeOnboarding() },
            modifier = Modifier.fillMaxSize()
        )
    } else {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    tonalElevation = 0.dp,
                    modifier = Modifier.testTag("bottom_navigation_bar")
                ) {
                    NavigationDestination.entries.forEach { destination ->
                        val isSelected = currentDestination == destination
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { currentDestination = destination },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                                    contentDescription = stringResource(destination.titleRes)
                                )
                            },
                            label = { Text(stringResource(destination.titleRes), maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.testTag("nav_item_${destination.name.lowercase()}")
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Handle Android back button/gesture:
                // - If paywall is showing → close it
                // - If on a non-HOME tab → go back to HOME
                // - If on HOME → default behavior (exit app)
                BackHandler(enabled = showPaywall || currentDestination != NavigationDestination.HOME) {
                    when {
                        showPaywall -> viewModel.setShowPaywall(false)
                        else -> currentDestination = NavigationDestination.HOME
                    }
                }

                when (currentDestination) {
                    NavigationDestination.HOME -> {
                        HomeScreen(
                            viewModel = viewModel,
                            onNavigateToHistory = { currentDestination = NavigationDestination.HISTORY },
                            onNavigateToTemplates = { currentDestination = NavigationDestination.TEMPLATES },
                            onNavigateToPaywall = { viewModel.setShowPaywall(true) }
                        )
                    }

                    NavigationDestination.HISTORY -> {
                        HistoryScreen(
                            viewModel = viewModel,
                            onSelectNote = { note ->
                                viewModel.loadNoteIntoActive(note)
                                currentDestination = NavigationDestination.HOME
                            }
                        )
                    }

                    NavigationDestination.TEMPLATES -> {
                        CustomTemplateScreen(
                            viewModel = viewModel,
                            onTemplateChosen = { currentDestination = NavigationDestination.HOME }
                        )
                    }

                    NavigationDestination.FAQ -> {
                        FaqHelpScreen(viewModel = viewModel)
                    }
                }

                // Paywall Modal / Overlay (F-30, F-31, F-32, F-33)
                AnimatedVisibility(
                    visible = showPaywall,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    PaywallScreen(
                        viewModel = viewModel,
                        onClose = { viewModel.setShowPaywall(false) }
                    )
                }
            }
        }
    }
}
