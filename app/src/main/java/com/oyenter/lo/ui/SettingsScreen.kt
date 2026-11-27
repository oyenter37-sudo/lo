package com.oyenter.lo.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.oyenter.lo.BuildConfig
import com.oyenter.lo.data.Settings
import com.oyenter.lo.ui.components.SectionLabel
import com.oyenter.lo.ui.theme.Amber
import com.oyenter.lo.ui.theme.Bg
import com.oyenter.lo.ui.theme.Faint
import com.oyenter.lo.ui.theme.Ink
import com.oyenter.lo.ui.theme.Muted
import com.oyenter.lo.ui.theme.Surface1
import com.oyenter.lo.ui.theme.Surface2

@Composable
fun SettingsScreen(vm: AppVm, settings: Settings, onBack: () -> Unit) {
    val ctx = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBackIosNew, contentDescription = "Назад", tint = Ink, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(4.dp))
            Text("Настройки", style = MaterialTheme.typography.displayMedium, color = Ink)
        }

        SectionLabel("Вид")
        Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
            var uiScale by androidx.compose.runtime.remember(settings.uiScale) {
                androidx.compose.runtime.mutableFloatStateOf(settings.uiScale)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Размер интерфейса", style = MaterialTheme.typography.titleMedium, color = Ink, modifier = Modifier.weight(1f))
                Text("${(uiScale * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = Amber)
            }
            Slider(
                value = uiScale,
                onValueChange = { uiScale = it },
                onValueChangeFinished = { vm.setUiScale(uiScale) },
                valueRange = 0.8f..1.35f,
                colors = SliderDefaults.colors(
                    thumbColor = Amber,
                    activeTrackColor = Amber,
                    inactiveTrackColor = Surface2,
                ),
            )
            Row {
                Text("Компактно", style = MaterialTheme.typography.bodySmall, color = Faint, modifier = Modifier.weight(1f))
                Text("Крупнее", style = MaterialTheme.typography.bodySmall, color = Faint)
            }
        }

        SectionLabel("Библиотека")
        SettingRow(
            title = "Обновлять автоматически",
            subtitle = "Ловить новые и удалённые треки в фоне",
            checked = settings.autoUpdate,
            onChange = vm::setAutoUpdate,
        )
        SettingRow(
            title = "Скрывать системные папки",
            subtitle = "Рингтоны, уведомления, записи, мессенджеры",
            checked = settings.excludeSystem,
            onChange = vm::setExcludeSystem,
        )
        SettingRow(
            title = "Исключать короткие файлы",
            subtitle = "Минимум ${settings.minDurationSec} секунд",
            checked = settings.minDurationSec > 0,
            onChange = { on -> vm.setMinDuration(if (on) 20 else 0) },
        )
        SettingRow(
            title = "Показывать скрытые",
            subtitle = "Треки, убранные из меню «Скрыть»",
            checked = settings.showHidden,
            onChange = vm::setShowHidden,
        )
        SettingRow(
            title = "Акцент из обложки",
            subtitle = "Цвет интерфейса подбирается под трек",
            checked = settings.accentFromCover,
            onChange = vm::setAccentFromCover,
        )
        SettingRow(
            title = "Запоминать позицию",
            subtitle = "Для треков дольше 10 минут",
            checked = settings.resumeLongTracks,
            onChange = vm::setResumeLong,
        )

        SectionLabel("Воспроизведение")
        SettingRow(
            title = "Pitch Lock",
            subtitle = "При ускорении голос не писклявит",
            checked = settings.pitchLock,
            onChange = vm::setPitchLock,
        )
        Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
            var extraVolume by androidx.compose.runtime.remember(settings.extraVolume) {
                androidx.compose.runtime.mutableFloatStateOf(settings.extraVolume)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Доп. громкость", style = MaterialTheme.typography.titleMedium, color = Ink, modifier = Modifier.weight(1f))
                Text("${(extraVolume * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = Amber)
            }
            Slider(
                value = extraVolume,
                onValueChange = {
                    extraVolume = it
                    vm.previewExtraVolume(it)
                },
                onValueChangeFinished = { vm.setExtraVolume(extraVolume) },
                valueRange = 0f..2f,
                colors = SliderDefaults.colors(
                    thumbColor = Amber,
                    activeTrackColor = Amber,
                    inactiveTrackColor = Surface2,
                ),
            )
            Text(
                "0–100% тише, 100–200% громче (с лимитером)",
                style = MaterialTheme.typography.bodySmall,
                color = Faint,
            )
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
            var speed by androidx.compose.runtime.remember(settings.speed) {
                androidx.compose.runtime.mutableFloatStateOf(settings.speed)
            }
            Text(
                "Скорость по умолчанию: ${"%.2f".format(speed)}×",
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
            )
            Slider(
                value = speed,
                onValueChange = {
                    speed = it
                    vm.previewSpeed(it)
                },
                onValueChangeFinished = { vm.setSpeed(speed) },
                valueRange = 0.5f..3f,
                steps = 49,
                colors = SliderDefaults.colors(
                    thumbColor = Amber,
                    activeTrackColor = Amber,
                    inactiveTrackColor = Surface2,
                ),
            )
        }

        SectionLabel("Данные")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    com.oyenter.lo.data.CoverStore.clear(ctx)
                    vm.flash("Обложки перечитаются")
                }
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Refresh, contentDescription = null, tint = Amber, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Обновить обложки", style = MaterialTheme.typography.titleMedium, color = Ink)
                Text("Если показываются чужие картинки", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }
        val syncing by vm.syncing.collectAsStateWithLifecycle()
        val summary by vm.lastSync.collectAsStateWithLifecycle()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !syncing) { vm.rescan() }
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (syncing) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Amber,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(Icons.Rounded.Refresh, contentDescription = null, tint = Amber, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (syncing) "Сканирую…" else "Пересканировать библиотеку",
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink,
                )
                Text(
                    if (syncing) {
                        "Смотрю папки телефона"
                    } else {
                        summary?.let {
                            "Треков: ${it.total} · изменено: ${it.changed} · ${it.durationMs} мс"
                        } ?: "Найти новые и удалить пропавшие треки"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
            }
        }

        SectionLabel("Обновления")
        val update by vm.update.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) {
            if (!update.checked) vm.checkForUpdate()
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !update.checking) { vm.checkForUpdate() }
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (update.checking) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Amber,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(Icons.Rounded.SystemUpdate, contentDescription = null, tint = Amber, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (update.checking) "Проверяю обновления…" else "Проверить обновления",
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink,
                )
                Text(
                    when {
                        update.checking -> "GitHub Releases"
                        update.error -> "Не удалось связаться с GitHub"
                        update.info != null -> "Доступна версия ${update.info!!.version} · ${update.info!!.sizeMb} МБ"
                        update.checked -> "Версия ${BuildConfig.VERSION_NAME} — свежая"
                        else -> "GitHub Releases"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
            }
            update.info?.let { info ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        vm.downloadUpdate(ctx)
                        vm.flash("Скачиваю Lo ${info.version}…")
                    },
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, tint = Amber, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Скачать", style = MaterialTheme.typography.titleSmall, color = Amber)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { vm.hardResetPlayer() }
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.RestartAlt, contentDescription = null, tint = Amber, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Перезапустить плеер", style = MaterialTheme.typography.titleMedium, color = Ink)
                Text("Если кнопки паузы и переключения не реагируют", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }

        SectionLabel("О приложении")
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("Lo · ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium, color = Ink)
            Spacer(Modifier.height(4.dp))
            Text(
                "Треки не покидают телефон. Сканирование, обложки и статистика — всё локально.",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable {
                    runCatching {
                        ctx.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/oyenter37-sudo/lo")),
                        )
                    }
                },
            ) {
                Text("github.com/oyenter37-sudo/lo", style = MaterialTheme.typography.bodySmall, color = Amber)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Rounded.OpenInNew, contentDescription = null, tint = Amber, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Эквалайзер работает через системный аудиоэффект. Если на устройстве он недоступен, ползунки не изменят звук.",
                style = MaterialTheme.typography.bodySmall,
                color = Faint,
            )
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Bg,
                checkedTrackColor = Amber,
                uncheckedThumbColor = Muted,
                uncheckedTrackColor = Surface1,
            ),
        )
    }
}
