package com.beigel.binaryclock.ui

import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beigel.binaryclock.R
import com.beigel.binaryclock.clock.ClockSettings
import com.beigel.binaryclock.clock.ClockStyle
import com.beigel.binaryclock.clock.Encoding
import com.beigel.binaryclock.clock.Screen
import com.beigel.binaryclock.clock.ThemeMode
import com.beigel.binaryclock.data.SettingsRepository
import com.beigel.binaryclock.wallpaper.BinaryClockWallpaperService
import com.beigel.binaryclock.widget.WidgetUpdater

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(repository: SettingsRepository) {
    val context = LocalContext.current
    val all by repository.settings.collectAsStateWithLifecycle()

    var screenName by rememberSaveable { mutableStateOf(Screen.HOME.name) }
    val screen = Screen.valueOf(screenName)
    val settings = all[screen]

    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    var canScheduleExact by remember { mutableStateOf(true) }
    var hasWidgets by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        canScheduleExact = WidgetUpdater.canScheduleExact(context)
        hasWidgets = WidgetUpdater.hasWidgets(context)
        onPauseOrDispose { }
    }

    val update: ((ClockSettings) -> ClockSettings) -> Unit = { transform ->
        val before = repository.settings.value.home
        repository.update(screen, transform)
        val after = repository.settings.value.home
        // Das Widget nutzt die Homescreen-Einstellungen – Position/Größe spielen dort keine Rolle.
        if (before.copy(placement = after.placement) != after) WidgetUpdater.updateAll(context)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // ------------------------------------------- Bildschirm + Vorschau
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Screen.entries.forEachIndexed { i, s ->
                        SegmentedButton(
                            selected = screen == s,
                            onClick = { screenName = s.name },
                            shape = SegmentedButtonDefaults.itemShape(i, Screen.entries.size),
                        ) {
                            Text(stringResource(if (s == Screen.HOME) R.string.target_home else R.string.target_lock))
                        }
                    }
                }

                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(R.string.placement_hint_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(stringResource(R.string.placement_hint), style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ClockEditor(
                        settings = settings,
                        dark = dark,
                        screen = screen,
                        ghostLabel = stringResource(R.string.ghost_system_clock),
                        description = stringResource(R.string.cd_preview),
                        onPlacementChange = { p -> repository.update(screen) { it.copy(placement = p) } },
                    )
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    TextButton(onClick = {
                        val default = if (screen == Screen.HOME) ClockSettings.HOME_DEFAULT else ClockSettings.LOCK_DEFAULT
                        repository.update(screen) { it.copy(placement = default.placement) }
                    }) {
                        Text(stringResource(R.string.action_reset_position))
                    }
                    if (screen == Screen.LOCK) {
                        TextButton(onClick = { repository.copyHomeToLock() }) {
                            Text(stringResource(R.string.action_copy_from_home))
                        }
                    }
                }

                Text(
                    text = stringResource(if (screen == Screen.HOME) R.string.scope_home else R.string.scope_lock),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Button(
                onClick = { openWallpaperPicker(context) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(stringResource(R.string.action_set_wallpaper))
            }

            // -------------------------------------------------------------- Stil
            Section(title = stringResource(R.string.section_style)) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ClockStyle.entries.forEach { style ->
                        StyleTile(
                            settings = settings,
                            style = style,
                            dark = dark,
                            selected = settings.style == style,
                            onSelect = { update { it.copy(style = style) } },
                            modifier = Modifier.width(78.dp),
                        )
                    }
                }
            }

            // -------------------------------------------------------- Darstellung
            Section(title = stringResource(R.string.section_theme)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { i, mode ->
                        SegmentedButton(
                            selected = settings.themeMode == mode,
                            onClick = { update { it.copy(themeMode = mode) } },
                            shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                        ) {
                            Text(stringResource(mode.label))
                        }
                    }
                }
            }

            Section(title = stringResource(R.string.section_color)) {
                AccentPicker(
                    settings = settings,
                    dark = dark,
                    onPick = { color -> update { it.copy(accentOverride = color) } },
                )
            }

            // ------------------------------------------------------------ Anzeige
            Section(title = stringResource(R.string.section_display)) {
                SwitchRow(
                    title = stringResource(R.string.option_seconds),
                    subtitle = if (screen == Screen.HOME) stringResource(R.string.option_seconds_desc) else null,
                    checked = settings.showSeconds,
                    onChange = { v -> update { it.copy(showSeconds = v) } },
                )
                SwitchRow(
                    title = stringResource(R.string.option_hints),
                    subtitle = stringResource(R.string.option_hints_desc),
                    checked = settings.showHints,
                    onChange = { v -> update { it.copy(showHints = v) } },
                )
                SwitchRow(
                    title = stringResource(R.string.option_date),
                    subtitle = null,
                    checked = settings.showDate,
                    onChange = { v -> update { it.copy(showDate = v) } },
                )
            }

            if (hasWidgets && !canScheduleExact) {
                ExactAlarmCard(onGrant = { requestExactAlarm(context) })
            }

            Section(title = stringResource(R.string.section_how_to_read)) {
                Text(
                    text = stringResource(
                        when {
                            settings.style == ClockStyle.RINGS -> R.string.how_to_read_rings
                            settings.style.encoding == Encoding.BCD -> R.string.how_to_read_bcd
                            else -> R.string.how_to_read_binary
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.widget_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        content()
    }
}

@Composable
private fun StyleTile(
    settings: ClockSettings,
    style: ClockStyle,
    dark: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ClockCanvas(
            // Mini-Vorschau: Stil mit Standardfarbe und ohne Datum, damit sie lesbar bleibt.
            settings = settings.copy(style = style, accentOverride = null, showDate = false),
            dark = dark,
            live = false,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 14f)
                .clip(RoundedCornerShape(14.dp))
                .border(BorderStroke(if (selected) 2.dp else 1.dp, borderColor), RoundedCornerShape(14.dp)),
        )
        Text(
            text = stringResource(style.label),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentPicker(settings: ClockSettings, dark: Boolean, onPick: (Int?) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val options: List<Int?> = listOf<Int?>(null) + ClockSettings.ACCENT_PRESETS
        options.forEach { option ->
            val color = option ?: settings.style.palette(dark).accent
            val selected = settings.accentOverride == option
            val label = if (option == null) {
                stringResource(R.string.color_default)
            } else {
                stringResource(R.string.cd_color_swatch, "#%06X".format(option and 0xFFFFFF))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { onPick(option) })
                        .semantics { contentDescription = label }
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape,
                        )
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(Color(color)),
                )
                if (option == null) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ExactAlarmCard(onGrant: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.exact_alarm_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.exact_alarm_text), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onGrant, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.exact_alarm_action))
            }
        }
    }
}

private fun openWallpaperPicker(context: Context) {
    val component = ComponentName(context, BinaryClockWallpaperService::class.java)
    val direct = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
        .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
    try {
        context.startActivity(direct)
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
        } catch (_: ActivityNotFoundException) {
            // Kein Wallpaper-Picker vorhanden (sehr selten) – nichts zu tun.
        }
    }
}

private fun requestExactAlarm(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()),
        )
    }
}
