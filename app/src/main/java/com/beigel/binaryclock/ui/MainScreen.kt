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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.beigel.binaryclock.data.SettingsRepository
import com.beigel.binaryclock.wallpaper.BinaryClockWallpaperService
import com.beigel.binaryclock.widget.WidgetUpdater

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(repository: SettingsRepository) {
    val context = LocalContext.current
    val settings by repository.settings.collectAsStateWithLifecycle()

    var canScheduleExact by remember { mutableStateOf(true) }
    var hasWidgets by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        canScheduleExact = WidgetUpdater.canScheduleExact(context)
        hasWidgets = WidgetUpdater.hasWidgets(context)
        onPauseOrDispose { }
    }

    val update: ((ClockSettings) -> ClockSettings) -> Unit = { transform ->
        repository.update(transform)
        WidgetUpdater.updateAll(context)
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
            val previewDescription = stringResource(R.string.cd_preview)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ClockCanvas(
                    settings = settings,
                    modifier = Modifier
                        .heightIn(max = 440.dp)
                        .aspectRatio(9f / 19.5f)
                        .clip(RoundedCornerShape(28.dp))
                        .semantics { contentDescription = previewDescription },
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

            Section(title = stringResource(R.string.section_style)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ClockStyle.entries.forEach { style ->
                        StyleTile(
                            settings = settings,
                            style = style,
                            selected = settings.style == style,
                            onSelect = { update { it.copy(style = style) } },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Section(title = stringResource(R.string.section_color)) {
                AccentPicker(
                    settings = settings,
                    onPick = { color -> update { it.copy(accentOverride = color) } },
                )
            }

            Section(title = stringResource(R.string.section_display)) {
                SwitchRow(
                    title = stringResource(R.string.option_seconds),
                    subtitle = stringResource(R.string.option_seconds_desc),
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
                SwitchRow(
                    title = stringResource(R.string.option_lock_adjust),
                    subtitle = stringResource(R.string.option_lock_adjust_desc),
                    checked = settings.lockScreenAdjust,
                    onChange = { v -> update { it.copy(lockScreenAdjust = v) } },
                )
            }

            if (hasWidgets && !canScheduleExact) {
                ExactAlarmCard(onGrant = { requestExactAlarm(context) })
            }

            Section(title = stringResource(R.string.section_how_to_read)) {
                Text(
                    text = stringResource(
                        if (settings.style.encoding == Encoding.BCD) R.string.how_to_read_bcd
                        else R.string.how_to_read_binary,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
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
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    Column(
        modifier = modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ClockCanvas(
            // Mini-Vorschau: Stil mit Standardfarbe und ohne Datum, damit sie lesbar bleibt.
            settings = settings.copy(style = style, accentOverride = null, showDate = false),
            live = false,
            centerYFraction = 0.5f,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 14f)
                .clip(RoundedCornerShape(14.dp))
                .border(BorderStroke(2.dp, borderColor), RoundedCornerShape(14.dp)),
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
private fun AccentPicker(settings: ClockSettings, onPick: (Int?) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val options: List<Int?> = listOf<Int?>(null) + ClockSettings.ACCENT_PRESETS
        options.forEach { option ->
            val color = option ?: settings.style.defaultAccent
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
    } catch (e: ActivityNotFoundException) {
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
