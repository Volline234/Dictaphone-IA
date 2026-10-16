package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.settings.AppLanguage
import com.example.settings.ThemeMode
import com.example.ui.viewmodel.MainViewModel

/** Walks up the ContextWrapper chain to find the hosting Activity. */
fun Context.findHostActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * Saves the interface language and reloads the screen so every text is updated.
 */
fun applyAppLanguage(viewModel: MainViewModel, language: AppLanguage, context: Context) {
    if (viewModel.setAppLanguage(language)) {
        context.findHostActivity()?.recreate()
    }
}

/** Returns true when the app is currently displayed in dark mode. */
@Composable
fun isAppInDarkTheme(themeMode: ThemeMode): Boolean = when (themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/** Generic pill-style single choice row, consistent with the FR / EN switch on Home. */
@Composable
private fun PillOptionRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                RoundedCornerShape(24.dp)
            )
            .padding(4.dp),
        content = content
    )
}

@Composable
private fun RowScope.PillOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    icon: ImageVector? = null
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)
        ) {
            val contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

/** System / Light / Dark selector. */
@Composable
fun ThemeModeSelector(
    selected: ThemeMode,
    onSelected: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    PillOptionRow(modifier = modifier.testTag("theme_mode_selector")) {
        PillOption(
            label = stringResource(R.string.theme_system),
            selected = selected == ThemeMode.SYSTEM,
            onClick = { onSelected(ThemeMode.SYSTEM) },
            testTag = "theme_system_option",
            icon = Icons.Filled.SettingsBrightness
        )
        PillOption(
            label = stringResource(R.string.theme_light),
            selected = selected == ThemeMode.LIGHT,
            onClick = { onSelected(ThemeMode.LIGHT) },
            testTag = "theme_light_option",
            icon = Icons.Filled.LightMode
        )
        PillOption(
            label = stringResource(R.string.theme_dark),
            selected = selected == ThemeMode.DARK,
            onClick = { onSelected(ThemeMode.DARK) },
            testTag = "theme_dark_option",
            icon = Icons.Filled.DarkMode
        )
    }
}

/** Device / Français / English selector. */
@Composable
fun AppLanguageSelector(
    selected: AppLanguage,
    onSelected: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
    showSystemOption: Boolean = true
) {
    PillOptionRow(modifier = modifier.testTag("app_language_selector")) {
        if (showSystemOption) {
            PillOption(
                label = stringResource(R.string.language_system),
                selected = selected == AppLanguage.SYSTEM,
                onClick = { onSelected(AppLanguage.SYSTEM) },
                testTag = "language_system_option"
            )
        }
        PillOption(
            label = stringResource(R.string.language_french),
            selected = selected == AppLanguage.FRENCH,
            onClick = { onSelected(AppLanguage.FRENCH) },
            testTag = "language_fr_option"
        )
        PillOption(
            label = stringResource(R.string.language_english),
            selected = selected == AppLanguage.ENGLISH,
            onClick = { onSelected(AppLanguage.ENGLISH) },
            testTag = "language_en_option"
        )
    }
}

/**
 * Compact sun / moon button that flips between light and dark mode.
 * Place it in screen headers for one-tap access.
 */
@Composable
fun ThemeQuickToggleButton(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val isDark = isAppInDarkTheme(themeMode)
    IconButton(
        onClick = { viewModel.setThemeMode(if (isDark) ThemeMode.LIGHT else ThemeMode.DARK) },
        modifier = modifier.testTag("theme_quick_toggle")
    ) {
        Icon(
            imageVector = if (isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
            contentDescription = stringResource(
                if (isDark) R.string.theme_toggle_to_light else R.string.theme_toggle_to_dark
            ),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Compact language switch (FR / EN) that changes the interface language immediately.
 * Used on the onboarding screen so new users can pick their language right away.
 */
@Composable
fun AppLanguageQuickSwitch(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    // When following the device, highlight the language actually displayed
    val displayedIsFrench = context.resources.configuration.locales[0]?.language == "fr"
    val effective = when (appLanguage) {
        AppLanguage.SYSTEM -> if (displayedIsFrench) AppLanguage.FRENCH else AppLanguage.ENGLISH
        else -> appLanguage
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .testTag("app_language_quick_switch")
    ) {
        listOf(AppLanguage.FRENCH to "FR", AppLanguage.ENGLISH to "EN").forEach { (lang, code) ->
            val selected = effective == lang
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { applyAppLanguage(viewModel, lang, context) }
                    .testTag("app_language_quick_${code.lowercase()}")
            ) {
                Text(
                    text = code,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
