package io.github.ewoc2026.visualroutines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

// Width uses the card's inner content; start offset adjusts alignment independently.
private const val APPEARANCE_DIVIDER_WIDTH_FRACTION = 0.60f
// The visible radio circle starts 2.dp inside its layout box (its center is at 12.dp).
private val APPEARANCE_DIVIDER_START_OFFSET = 2.dp

/** Keeps both independent choice groups in one content-sized, scrollable settings section. */
@Composable
internal fun AppearanceSettings(state: VisualRoutinesState) {
    val settings = state.homeSettings
    val styleEnabled = settings.appearanceMode != AppearanceMode.Light
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(
                stringResource(R.string.appearance_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
            Column(Modifier.selectableGroup().padding(top = 8.dp)) {
                AppearanceOption(
                    stringResource(R.string.appearance_system),
                    selected = settings.appearanceMode == AppearanceMode.System,
                    onClick = { state.updateAppearanceMode(AppearanceMode.System) },
                    supportingText = stringResource(R.string.appearance_system_description),
                    tag = "appearance-system",
                )
                AppearanceOption(
                    stringResource(R.string.appearance_light),
                    selected = settings.appearanceMode == AppearanceMode.Light,
                    onClick = { state.updateAppearanceMode(AppearanceMode.Light) },
                    tag = "appearance-light",
                )
                AppearanceOption(
                    stringResource(R.string.appearance_dark),
                    selected = settings.appearanceMode == AppearanceMode.Dark,
                    onClick = { state.updateAppearanceMode(AppearanceMode.Dark) },
                    tag = "appearance-dark",
                )
            }
            HorizontalDivider(
                modifier = Modifier
                    .padding(vertical = 16.dp)
                    .fillMaxWidth(APPEARANCE_DIVIDER_WIDTH_FRACTION)
                    .align(Alignment.Start)
                    .offset(x = APPEARANCE_DIVIDER_START_OFFSET),
            )
            Text(
                stringResource(R.string.dark_theme_style_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
            Column(Modifier.selectableGroup().padding(top = 8.dp)) {
                AppearanceOption(
                    stringResource(R.string.dark_theme_standard),
                    selected = settings.darkThemeStyle == DarkThemeStyle.Standard,
                    onClick = { state.updateDarkThemeStyle(DarkThemeStyle.Standard) },
                    enabled = styleEnabled,
                    tag = "dark-style-standard",
                )
                AppearanceOption(
                    stringResource(R.string.dark_theme_black),
                    selected = settings.darkThemeStyle == DarkThemeStyle.Black,
                    onClick = { state.updateDarkThemeStyle(DarkThemeStyle.Black) },
                    enabled = styleEnabled,
                    tag = "dark-style-black",
                )
            }
            Text(
                stringResource(if (styleEnabled) R.string.dark_theme_style_description else R.string.dark_theme_style_disabled),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun AppearanceOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String,
    enabled: Boolean = true,
    supportingText: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .sizeIn(minHeight = 48.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // The whole row owns the action and selection announcement.
        RadioButton(selected, onClick = null, enabled = enabled, modifier = Modifier.clearAndSetSemantics {})
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f),
            )
            supportingText?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
