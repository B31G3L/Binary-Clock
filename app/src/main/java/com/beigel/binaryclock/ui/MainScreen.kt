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
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.material3.Slider
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
import androidx.compose.ui.input.pointer.pointerInput
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
import com.beigel.binaryclock.clock.Placement
import com.beigel.binaryclock.clock.ThemeMode
import com.beigel.binaryclock.data.SettingsRepository
import com.beigel.binaryclock.wallpaper.BinaryClockWallpaperService
import com.beigel.binaryclock.widget.WidgetUpdater
import kotlin.math.roundToInt

private enum class EditTarget { HOME, LOCK }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(repository: SettingsRepository) {
    val context = LocalContext.current
    val settings by repository.settings.collectAsStateWithLifecycle()
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

    var targetName by rememberSaveable { mutableStateOf(EditTarget.HOME.name) }
    val target = if (settings.lockScreenAdjust) EditTarget.valueOf(targetName) else EditTarget.HOME
    val placement = if (target == EditTarget.LOCK) settings.lock else settings.home

    val update: ((ClockSettings) -> ClockSettings) -> Unit = { transform ->
        val before = repository.settings.value
        repository.update(transform)
        val after = repository.settings.value
        // Position/Größe betreffen nur das Wallpaper – das Widget nicht unnötig neu rendern.
        if (before.copy(home = after.home, lock = after.lock) != after) WidgetUpdater.updateAll(context)
    }
    val updatePlacement: ((Placement) -> Placement) -> Unit = { transform ->
        update { s ->
            if (target == EditTarget.LOCK) s.copy(lock = transform(s.lock).clamped())
            else s.copy(home = transform(s.home).clamped())
        }
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
            // ---------------------------------------------------------- Vorschau
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val previewDescription = stringResource(R.string.cd_preview)
                val ghost = stringResource(R.string.ghost_system_clock)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ClockCanvas(
                        settings = if (target == EditTarget.LOCK) settings.copy(showDate = false) else settings,
                        dark = dark,
                        placement = placement,
                        systemClockGhost = if (target == EditTarget.LOCK) ghost else null,
                        modifier = Modifier
                            .heightIn(max = 460.dp)
                            .aspectRatio(9f / 19.5f)
                            .clip(RoundedCornerShape(28.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(28.dp))
                            .semantics { contentDescription = previewDescription }
                            .pointerInput(target) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    updatePlacement { p ->
                                        p.copy(
                                            x = p.x + pan.x / size.width,
                                            y = p.y + pan.y / size.height,
                                            size = p.size * zoom,
                                        )
                                    }
                                }
                            },
                    )
                }

                if (settings.lockScreenAdjust) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        EditTarget.entries.forEachIndexed { i, t ->
                            SegmentedButton(
                                selected = target == t,
                                onClick = { targetName = t.name },
                                shape = SegmentedButtonDefaults.itemShape(i, EditTarget.entries.size),
                            ) {
                                Text(stringResource(if (t == EditTarget.HOME) R.string.target_home else R.string.target_lock))
                            }
                        }
                    }
                }
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

            // ------------------------------------------------- Position & Größe
            Section(
                title = stringResource(R.string.section_position) + if (settings.lockScreenAdjust) {
                    " · " + stringResource(if (target == EditTarget.LOCK) R.string.target_lock else R.string.target_home)
                } else {
                    ""
                },
            ) {
                Text(
                    text = stringResource(R.string.position_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LabeledSlider(
                    label = stringResource(R.string.slider_horizontal),
                    value = placement.x,
                    range = 0f..1f,
                    display = "${(placement.x * 100).roundToInt()} %",
                    onChange = { v -> updatePlacement { it.copy(x = v) } },
                )
                LabeledSlider(
                    label = stringResource(R.string.slider_vertical),
                    value = placement.y,
                    range = 0f..1f,
                    display = "${(placement.y * 100).roundToInt()} %",
                    onChange = { v -> updatePlacement { it.copy(y = v) } },
                )
                LabeledSlider(
                    label = stringResource(R.string.slider_size),
                    value = placement.size,
                    range = Placement.MIN_SIZE..Placement.MAX_SIZE,
                    display = "${(placement.size * 100).roundToInt()} %",
                    onChange = { v -> updatePlacement { it.copy(size = v) } },
                )
                TextButton(
                    onClick = {
                        updatePlacement {
                            if (target == EditTarget.LOCK) Placement.LOCK_DEFAULT else Placement.HOME_DEFAULT
                        }
                    },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.action_reset))
                }
            }

            // ------------------------------------------------------------ Anzeige
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
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(display, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.semantics { contentDescription = label },
        )
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
