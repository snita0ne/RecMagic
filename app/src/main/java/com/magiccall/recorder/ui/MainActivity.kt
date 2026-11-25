package com.magiccall.recorder.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.magiccall.recorder.data.AudioSourceMode
import com.magiccall.recorder.data.CallRecordingItem
import com.magiccall.recorder.data.RecordingPrefs
import com.magiccall.recorder.data.RecordingRepository
import com.magiccall.recorder.service.CallRecordAccessibilityService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MagicCallRecorderApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MagicCallRecorderApp() {
    val context = LocalContext.current
    val prefs = remember { RecordingPrefs(context) }
    val repository = remember { RecordingRepository(context) }

    var selectedTab by remember { mutableIntStateOf(0) }
    var recordings by remember { mutableStateOf(repository.listRecordings()) }
    var playingUri by remember { mutableStateOf<Uri?>(null) }
    val player = remember { MediaPlayer() }

    DisposableEffect(Unit) {
        onDispose {
            player.release()
        }
    }

    val requiredPermissions = remember {
        buildList {
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.READ_PHONE_STATE)
            add(Manifest.permission.READ_CALL_LOG)
            add(Manifest.permission.READ_CONTACTS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
                add(Manifest.permission.READ_MEDIA_AUDIO)
            }
        }.toTypedArray()
    }

    var permissionsTick by remember { mutableIntStateOf(0) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionsTick++
        recordings = repository.listRecordings()
    }

    val allPermissionsGranted = remember(permissionsTick) {
        requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Автозапись звонков") }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = {
                        selectedTab = 0
                        recordings = repository.listRecordings()
                    },
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                    label = { Text("Записи") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Настройки") }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            if (selectedTab == 0) {
                SetupStatusCard(
                    allPermissionsGranted = allPermissionsGranted,
                    onRequestPermissions = { permissionLauncher.launch(requiredPermissions) }
                )
                Spacer(modifier = Modifier.height(12.dp))
                RecordingsList(
                    recordings = recordings,
                    playingUri = playingUri,
                    onTogglePlay = { item ->
                        if (playingUri == item.uri) {
                            player.stop()
                            player.reset()
                            playingUri = null
                        } else {
                            try {
                                player.reset()
                                player.setDataSource(context, item.uri)
                                player.prepare()
                                player.setOnCompletionListener {
                                    playingUri = null
                                }
                                player.start()
                                playingUri = item.uri
                            } catch (_: Exception) {
                                playingUri = null
                            }
                        }
                    },
                    onShare = { item ->
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "audio/mp4"
                            putExtra(Intent.EXTRA_STREAM, item.uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Поделиться записью"))
                    },
                    onDelete = { item ->
                        if (playingUri == item.uri) {
                            player.stop()
                            player.reset()
                            playingUri = null
                        }
                        if (repository.deleteRecording(item.uri)) {
                            recordings = repository.listRecordings()
                        }
                    }
                )
            } else {
                SettingsTab(prefs = prefs)
            }
        }
    }
}

@Composable
private fun SetupStatusCard(
    allPermissionsGranted: Boolean,
    onRequestPermissions: () -> Unit
) {
    val context = LocalContext.current
    val isAccessibilityOn = CallRecordAccessibilityService.isConnected
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val isIgnoringBattery = pm.isIgnoringBatteryOptimizations(context.packageName)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Готовность MagicOS 10", fontWeight = FontWeight.SemiBold)

            StatusRow(
                label = "Разрешения (микрофон, телефон, контакты)",
                ok = allPermissionsGranted,
                actionText = "Выдать",
                onAction = onRequestPermissions
            )

            StatusRow(
                label = "Спецвозможности (фоновая запись)",
                ok = isAccessibilityOn,
                actionText = "Открыть",
                onAction = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            )

            StatusRow(
                label = "Работа в фоне (без экономии батареи)",
                ok = isIgnoringBattery,
                actionText = "Отключить",
                onAction = {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
            )
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    ok: Boolean,
    actionText: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (ok) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
        if (!ok) {
            OutlinedButton(onClick = onAction) {
                Text(actionText)
            }
        }
    }
}

@Composable
private fun RecordingsList(
    recordings: List<CallRecordingItem>,
    playingUri: Uri?,
    onTogglePlay: (CallRecordingItem) -> Unit,
    onShare: (CallRecordingItem) -> Unit,
    onDelete: (CallRecordingItem) -> Unit
) {
    if (recordings.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Записей пока нет",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Файлы сохраняются в Music/MagicCallRecorder",
                style = MaterialTheme.typography.bodySmall
            )
        }
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(recordings, key = { it.id }) { item ->
            val dateText = remember(item.dateAddedSec) {
                SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                    .format(Date(item.dateAddedSec * 1000))
            }
            val durationSec = item.durationMs / 1000
            val durText = String.format(Locale.getDefault(), "%02d:%02d", durationSec / 60, durationSec % 60)

            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onTogglePlay(item) }) {
                        Icon(
                            imageVector = if (playingUri == item.uri) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.displayName, fontWeight = FontWeight.Medium)
                        Text(
                            "$dateText • $durText",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    IconButton(onClick = { onShare(item) }) {
                        Icon(Icons.Default.Share, contentDescription = "Поделиться")
                    }
                    IconButton(onClick = { onDelete(item) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Удалить")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsTab(prefs: RecordingPrefs) {
    var autoRecord by remember { mutableStateOf(prefs.autoRecordEnabled) }
    var selectedSource by remember { mutableStateOf(prefs.audioSourceMode) }
    var excluded by remember { mutableStateOf(prefs.excludedNumbers.toList().sorted()) }
    var newNumber by remember { mutableStateOf("") }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Автозапись всех звонков", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Запускается автоматически при ответе на вызов",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = autoRecord,
                        onCheckedChange = {
                            autoRecord = it
                            prefs.autoRecordEnabled = it
                        }
                    )
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Источник звука", fontWeight = FontWeight.SemiBold)
                    AudioSourceMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedSource == mode,
                                onClick = {
                                    selectedSource = mode
                                    prefs.audioSourceMode = mode
                                }
                            )
                            Column {
                                Text(mode.title, style = MaterialTheme.typography.bodyMedium)
                                Text(mode.subtitle, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Исключения (не записывать)", fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newNumber,
                            onValueChange = { newNumber = it },
                            label = { Text("Номер телефона") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Button(onClick = {
                            prefs.addExcludedNumber(newNumber)
                            excluded = prefs.excludedNumbers.toList().sorted()
                            newNumber = ""
                        }) {
                            Text("Добавить")
                        }
                    }
                    excluded.forEach { num ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(num)
                            IconButton(onClick = {
                                prefs.removeExcludedNumber(num)
                                excluded = prefs.excludedNumbers.toList().sorted()
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Удалить")
                            }
                        }
                    }
                }
            }
        }
    }
}
