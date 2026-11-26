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
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

        SectionLabel("Библиотека")
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
                .clickable { vm.rescan() }
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Refresh, contentDescription = null, tint = Amber, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Пересканировать библиотеку", style = MaterialTheme.typography.titleMedium, color = Ink)
                Text("Найти новые и удалить пропавшие треки", style = MaterialTheme.typography.bodySmall, color = Muted)
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
