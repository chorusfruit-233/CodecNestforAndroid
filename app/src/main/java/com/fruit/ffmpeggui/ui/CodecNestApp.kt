package com.fruit.ffmpeggui.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fruit.ffmpeggui.core.AudioChannelMode
import com.fruit.ffmpeggui.core.AudioCodec
import com.fruit.ffmpeggui.core.AudioSampleRate
import com.fruit.ffmpeggui.core.FormatConversionMode
import com.fruit.ffmpeggui.core.JobHistoryItem
import com.fruit.ffmpeggui.core.JobStatus
import com.fruit.ffmpeggui.core.OperationPreset
import com.fruit.ffmpeggui.core.OutputFormat
import com.fruit.ffmpeggui.core.PresetCategory
import com.fruit.ffmpeggui.core.PresetSettings
import com.fruit.ffmpeggui.core.RotationMode
import com.fruit.ffmpeggui.core.ScalePreset
import com.fruit.ffmpeggui.core.VideoCodec
import com.fruit.ffmpeggui.core.WatermarkPosition
import com.fruit.ffmpeggui.core.WorkTab
import com.fruit.ffmpeggui.core.compatibleAudioCodecs
import com.fruit.ffmpeggui.core.compatibleOutputFormats
import com.fruit.ffmpeggui.core.compatibleVideoCodecs
import com.fruit.ffmpeggui.core.normalizedAudioCodec
import com.fruit.ffmpeggui.core.normalizedOutputFormat
import com.fruit.ffmpeggui.core.normalizedVideoCodec
import com.fruit.ffmpeggui.core.usesAudioBitrate
import com.fruit.ffmpeggui.data.SelectedMediaFile
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodecNestApp(viewModel: CodecNestViewModel) {
    val uiState by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val latestOutputName by rememberUpdatedState(uiState.suggestedOutputName)

    val inputLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> viewModel.setInputs(uris) }
    val subtitleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> viewModel.setSubtitle(uri) }
    val watermarkLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> viewModel.setWatermark(uri) }
    val outputLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(uiState.outputMimeType)
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.startJob(uri, latestOutputName)
        }
    }

    LaunchedEffect(uiState.message) {
        val message = uiState.message
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("CodecNest", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Android FFmpeg GUI",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        bottomBar = {
            WorkNavigationBar(uiState.tab, viewModel::setTab)
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusStrip(uiState)
            FilePanel(
                uiState = uiState,
                onPickInputs = { inputLauncher.launch(arrayOf("*/*")) },
                onClearInputs = viewModel::clearInputs
            )
            when (uiState.tab) {
                WorkTab.Presets -> PresetPanel(
                    uiState = uiState,
                    onPresetChange = viewModel::setPreset,
                    onSettingsChange = viewModel::updateSettings,
                    onPickSubtitle = { subtitleLauncher.launch(arrayOf("*/*")) },
                    onPickWatermark = { watermarkLauncher.launch(arrayOf("image/*")) },
                    onClearSubtitle = { viewModel.setSubtitle(null) },
                    onClearWatermark = { viewModel.setWatermark(null) }
                )
                WorkTab.Format -> FormatPanel(
                    uiState = uiState,
                    onSettingsChange = viewModel::updateSettings
                )
                WorkTab.Custom -> CustomPanel(
                    uiState = uiState,
                    onCommandChange = viewModel::updateCustomCommand,
                    onSettingsChange = viewModel::updateSettings
                )
                WorkTab.History -> HistoryPanel(
                    history = uiState.history,
                    onClear = viewModel::clearHistory
                )
            }
            CommandPreview(uiState.commandPreview)
            RunPanel(
                uiState = uiState,
                onRun = {
                    if (uiState.requiresOutput) {
                        outputLauncher.launch(uiState.suggestedOutputName)
                    } else {
                        viewModel.startJob(null, null)
                    }
                },
                onCancel = viewModel::cancelJob
            )
            LogPanel(uiState.logs)
        }
    }
}

@Composable
private fun StatusStrip(uiState: CodecNestUiState) {
    FlowLine {
        AssistChip(
            onClick = {},
            leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
            label = {
                Text(if (uiState.engineAvailable) "引擎可用：${uiState.engineName}" else "缺少 FFmpegKitNext AAR")
            }
        )
        AssistChip(
            onClick = {},
            label = { Text("输入 ${uiState.inputs.size}") }
        )
        AssistChip(
            onClick = {},
            leadingIcon = { Icon(Icons.Outlined.Save, contentDescription = null) },
            label = { Text(if (uiState.requiresOutput) uiState.suggestedOutputName else "无输出文件") }
        )
        uiState.status?.let {
            AssistChip(onClick = {}, label = { Text(it.label()) })
        }
    }
}

@Composable
private fun FilePanel(
    uiState: CodecNestUiState,
    onPickInputs: () -> Unit,
    onClearInputs: () -> Unit
) {
    ToolSurface {
        SectionTitle("文件", Icons.Outlined.FolderOpen)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            FilledTonalButton(onClick = onPickInputs, enabled = !uiState.isRunning) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("选择输入")
            }
            OutlinedButton(
                onClick = onClearInputs,
                enabled = uiState.inputs.isNotEmpty() && !uiState.isRunning
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("清空")
            }
        }
        Spacer(Modifier.height(8.dp))
        if (uiState.inputs.isEmpty()) {
            Text("未选择文件", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                uiState.inputs.forEachIndexed { index, file ->
                    MediaFileRow(index + 1, file)
                }
            }
        }
    }
}

@Composable
private fun MediaFileRow(index: Int, file: SelectedMediaFile) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "#$index",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(34.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(file.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(file.mimeType, file.sizeBytes?.let(::formatBytes)).joinToString("  "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun WorkNavigationBar(selected: WorkTab, onSelect: (WorkTab) -> Unit) {
    NavigationBar {
        WorkTab.entries.forEach { tab ->
            val icon = when (tab) {
                WorkTab.Presets -> Icons.Outlined.Tune
                WorkTab.Format -> Icons.Outlined.SwapHoriz
                WorkTab.Custom -> Icons.Outlined.Terminal
                WorkTab.History -> Icons.Outlined.History
            }
            NavigationBarItem(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                icon = { Icon(icon, contentDescription = null) },
                label = { Text(tab.label) }
            )
        }
    }
}

@Composable
private fun PresetPanel(
    uiState: CodecNestUiState,
    onPresetChange: (OperationPreset) -> Unit,
    onSettingsChange: ((PresetSettings) -> PresetSettings) -> Unit,
    onPickSubtitle: () -> Unit,
    onPickWatermark: () -> Unit,
    onClearSubtitle: () -> Unit,
    onClearWatermark: () -> Unit
) {
    var category by remember { mutableStateOf(PresetCategory.All) }
    val presets = OperationPreset.entries
        .filterNot { it == OperationPreset.Transcode || it == OperationPreset.AudioConvert }
        .filter { preset ->
            category == PresetCategory.All || preset.category == category
        }
    ToolSurface {
        SectionTitle("预设", Icons.Outlined.Tune)
        OptionRow(
            label = "分类",
            values = PresetCategory.entries,
            selected = category,
            enabled = !uiState.isRunning,
            name = { it.label },
            onSelected = { category = it }
        )
        FlowLine {
            presets.forEach { preset ->
                FilterChip(
                    selected = uiState.preset == preset,
                    onClick = { onPresetChange(preset) },
                    enabled = !uiState.isRunning,
                    label = { Text(preset.label) }
                )
            }
        }
        Text(
            uiState.preset.description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(6.dp))
        PresetSettingsPanel(
            preset = uiState.preset,
            settings = uiState.settings,
            subtitle = uiState.subtitle,
            watermark = uiState.watermark,
            enabled = !uiState.isRunning,
            onSettingsChange = onSettingsChange,
            onPickSubtitle = onPickSubtitle,
            onPickWatermark = onPickWatermark,
            onClearSubtitle = onClearSubtitle,
            onClearWatermark = onClearWatermark
        )
    }
}

@Composable
private fun PresetSettingsPanel(
    preset: OperationPreset,
    settings: PresetSettings,
    subtitle: SelectedMediaFile?,
    watermark: SelectedMediaFile?,
    enabled: Boolean,
    onSettingsChange: ((PresetSettings) -> PresetSettings) -> Unit,
    onPickSubtitle: () -> Unit,
    onPickWatermark: () -> Unit,
    onClearSubtitle: () -> Unit,
    onClearWatermark: () -> Unit
) {
    val outputFormats = compatibleOutputFormats(preset)
    val selectedOutputFormat = normalizedOutputFormat(preset, settings)
    if (outputFormats.isNotEmpty() && selectedOutputFormat != null) {
        OptionRow(
            label = "输出",
            values = outputFormats,
            selected = selectedOutputFormat,
            enabled = enabled,
            name = { it.label },
            onSelected = { onSettingsChange { state -> state.copy(outputFormat = it) } }
        )
    }

    if (preset in listOf(OperationPreset.Transcode, OperationPreset.Compress)) {
        val videoCodecs = selectedOutputFormat?.let { compatibleVideoCodecs(it) } ?: VideoCodec.entries
        val selectedVideoCodec = selectedOutputFormat?.let { normalizedVideoCodec(it, settings) }
            ?: settings.videoCodec
        OptionRow(
            label = "视频",
            values = videoCodecs,
            selected = selectedVideoCodec,
            enabled = enabled,
            name = { it.label },
            onSelected = { onSettingsChange { state -> state.copy(videoCodec = it) } }
        )
    }

    if (preset in listOf(OperationPreset.Transcode, OperationPreset.Compress)) {
        val audioCodecs = selectedOutputFormat?.let { compatibleAudioCodecs(it) } ?: AudioCodec.entries
        val selectedAudioCodec = selectedOutputFormat?.let { normalizedAudioCodec(it, settings) }
            ?: settings.audioCodec
        OptionRow(
            label = "音频",
            values = audioCodecs,
            selected = selectedAudioCodec,
            enabled = enabled,
            name = { it.label },
            onSelected = { onSettingsChange { state -> state.copy(audioCodec = it) } }
        )
    }

    if (preset in listOf(OperationPreset.Transcode, OperationPreset.Compress, OperationPreset.Subtitle, OperationPreset.Gif, OperationPreset.Social1080p)) {
        OptionRow(
            label = "尺寸",
            values = ScalePreset.entries,
            selected = settings.scalePreset,
            enabled = enabled,
            name = { it.label },
            onSelected = { onSettingsChange { state -> state.copy(scalePreset = it) } }
        )
    }

    if (preset in listOf(
            OperationPreset.Transcode,
            OperationPreset.Compress,
            OperationPreset.Subtitle,
            OperationPreset.Watermark,
            OperationPreset.Speed,
            OperationPreset.RotateFlip,
            OperationPreset.ChangeFrameRate,
            OperationPreset.Social1080p,
            OperationPreset.FastMp4
        )
    ) {
        ValueSlider(
            label = "CRF",
            value = settings.crf.toFloat(),
            valueRange = 16f..35f,
            enabled = enabled,
            display = settings.crf.toString(),
            onValue = { onSettingsChange { state -> state.copy(crf = it.roundToInt()) } }
        )
    }

    if (preset in listOf(
            OperationPreset.Transcode,
            OperationPreset.Compress,
            OperationPreset.ExtractAudio,
            OperationPreset.AudioConvert,
            OperationPreset.AudioTrim,
            OperationPreset.VolumeAdjust,
            OperationPreset.LoudnessNormalize,
            OperationPreset.Social1080p,
            OperationPreset.FastMp4
        )
    ) {
        ValueSlider(
            label = "音频码率",
            value = settings.audioBitrateKbps.toFloat(),
            valueRange = 64f..320f,
            enabled = enabled,
            display = "${settings.audioBitrateKbps}k",
            onValue = { onSettingsChange { state -> state.copy(audioBitrateKbps = it.roundToInt()) } }
        )
    }

    if (preset == OperationPreset.Trim || preset == OperationPreset.AudioTrim) {
        TimeFields(
            start = settings.startTime,
            duration = settings.duration,
            enabled = enabled,
            onStart = { onSettingsChange { state -> state.copy(startTime = it) } },
            onDuration = { onSettingsChange { state -> state.copy(duration = it) } }
        )
    }

    if (preset == OperationPreset.Speed) {
        ValueSlider(
            label = "速度",
            value = settings.speed,
            valueRange = 0.25f..4.0f,
            enabled = enabled,
            display = "${"%.2f".format(settings.speed)}x",
            onValue = { onSettingsChange { state -> state.copy(speed = it) } }
        )
    }

    if (preset == OperationPreset.ChangeFrameRate || preset == OperationPreset.Social1080p) {
        ValueSlider(
            label = "帧率",
            value = settings.frameRate.toFloat(),
            valueRange = 1f..60f,
            enabled = enabled,
            display = "${settings.frameRate} fps",
            onValue = { onSettingsChange { state -> state.copy(frameRate = it.roundToInt()) } }
        )
    }

    if (preset == OperationPreset.RotateFlip) {
        OptionRow(
            label = "方向",
            values = RotationMode.entries,
            selected = settings.rotationMode,
            enabled = enabled,
            name = { it.label },
            onSelected = { onSettingsChange { state -> state.copy(rotationMode = it) } }
        )
    }

    if (preset == OperationPreset.VolumeAdjust) {
        ValueSlider(
            label = "音量",
            value = settings.volumePercent.toFloat(),
            valueRange = 0f..300f,
            enabled = enabled,
            display = "${settings.volumePercent}%",
            onValue = { onSettingsChange { state -> state.copy(volumePercent = it.roundToInt()) } }
        )
    }

    if (preset in listOf(
            OperationPreset.ExtractAudio,
            OperationPreset.AudioConvert,
            OperationPreset.AudioTrim,
            OperationPreset.VolumeAdjust,
            OperationPreset.LoudnessNormalize
        )
    ) {
        OptionRow(
            label = "采样率",
            values = AudioSampleRate.entries,
            selected = settings.audioSampleRate,
            enabled = enabled,
            name = { it.label },
            onSelected = { onSettingsChange { state -> state.copy(audioSampleRate = it) } }
        )
        OptionRow(
            label = "声道",
            values = AudioChannelMode.entries,
            selected = settings.audioChannelMode,
            enabled = enabled,
            name = { it.label },
            onSelected = { onSettingsChange { state -> state.copy(audioChannelMode = it) } }
        )
    }

    if (preset == OperationPreset.Gif) {
        ValueSlider(
            label = "帧率",
            value = settings.gifFps.toFloat(),
            valueRange = 6f..24f,
            enabled = enabled,
            display = "${settings.gifFps} fps",
            onValue = { onSettingsChange { state -> state.copy(gifFps = it.roundToInt()) } }
        )
    }

    if (preset == OperationPreset.Frame) {
        OutlinedTextField(
            value = settings.frameTime,
            onValueChange = { onSettingsChange { state -> state.copy(frameTime = it) } },
            enabled = enabled,
            singleLine = true,
            label = { Text("时间点") },
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (preset == OperationPreset.Subtitle) {
        AuxiliaryFilePicker(
            label = "字幕",
            file = subtitle,
            icon = Icons.Outlined.Subtitles,
            enabled = enabled,
            onPick = onPickSubtitle,
            onClear = onClearSubtitle
        )
    }

    if (preset == OperationPreset.Watermark) {
        AuxiliaryFilePicker(
            label = "水印",
            file = watermark,
            icon = Icons.Outlined.Image,
            enabled = enabled,
            onPick = onPickWatermark,
            onClear = onClearWatermark
        )
        OptionRow(
            label = "位置",
            values = WatermarkPosition.entries,
            selected = settings.watermarkPosition,
            enabled = enabled,
            name = { it.label },
            onSelected = { onSettingsChange { state -> state.copy(watermarkPosition = it) } }
        )
    }
}

@Composable
private fun FormatPanel(
    uiState: CodecNestUiState,
    onSettingsChange: ((PresetSettings) -> PresetSettings) -> Unit
) {
    val settings = uiState.settings
    val enabled = !uiState.isRunning
    val outputFormat = normalizedOutputFormat(settings.formatMode, settings)
    ToolSurface {
        SectionTitle("格式转换", Icons.Outlined.SwapHoriz)
        DetailedOptionRow(
            label = "转换类型",
            values = FormatConversionMode.entries,
            selected = settings.formatMode,
            enabled = enabled,
            name = { it.label },
            detail = { it.detail },
            onSelected = { mode -> onSettingsChange { state -> state.copy(formatMode = mode) } }
        )
        DetailedOptionRow(
            label = "封装格式",
            values = compatibleOutputFormats(settings.formatMode),
            selected = outputFormat,
            enabled = enabled,
            name = { "${it.label} .${it.extension}" },
            detail = { it.detail },
            onSelected = { format -> onSettingsChange { state -> state.copy(outputFormat = format) } }
        )

        when (settings.formatMode) {
            FormatConversionMode.Video -> VideoFormatOptions(
                settings = settings,
                outputFormat = outputFormat,
                enabled = enabled,
                onSettingsChange = onSettingsChange
            )
            FormatConversionMode.Audio -> AudioFormatOptions(
                settings = settings,
                outputFormat = outputFormat,
                enabled = enabled,
                onSettingsChange = onSettingsChange
            )
            FormatConversionMode.Image -> ImageFormatOptions(
                settings = settings,
                outputFormat = outputFormat,
                enabled = enabled,
                onSettingsChange = onSettingsChange
            )
        }
    }
}

@Composable
private fun VideoFormatOptions(
    settings: PresetSettings,
    outputFormat: OutputFormat,
    enabled: Boolean,
    onSettingsChange: ((PresetSettings) -> PresetSettings) -> Unit
) {
    val videoCodec = normalizedVideoCodec(outputFormat, settings)
    val audioCodec = normalizedAudioCodec(outputFormat, settings)
    DetailedOptionRow(
        label = "视频编码",
        values = compatibleVideoCodecs(outputFormat),
        selected = videoCodec,
        enabled = enabled,
        name = { "${it.label} (${it.ffmpegName})" },
        detail = { it.detail },
        onSelected = { codec -> onSettingsChange { state -> state.copy(videoCodec = codec) } }
    )
    DetailedOptionRow(
        label = "音频编码",
        values = compatibleAudioCodecs(outputFormat),
        selected = audioCodec,
        enabled = enabled,
        name = { "${it.label} (${it.ffmpegName})" },
        detail = { it.detail },
        onSelected = { codec -> onSettingsChange { state -> state.copy(audioCodec = codec) } }
    )
    if (videoCodec != VideoCodec.Copy) {
        ValueSlider(
            label = "CRF",
            value = settings.crf.toFloat(),
            valueRange = 16f..35f,
            enabled = enabled,
            display = settings.crf.toString(),
            onValue = { onSettingsChange { state -> state.copy(crf = it.roundToInt()) } }
        )
        OptionRow(
            label = "尺寸",
            values = ScalePreset.entries,
            selected = settings.scalePreset,
            enabled = enabled,
            name = { it.label },
            onSelected = { onSettingsChange { state -> state.copy(scalePreset = it) } }
        )
    }
    AudioShapeOptions(
        settings = settings,
        audioCodec = audioCodec,
        enabled = enabled,
        onSettingsChange = onSettingsChange
    )
}

@Composable
private fun AudioFormatOptions(
    settings: PresetSettings,
    outputFormat: OutputFormat,
    enabled: Boolean,
    onSettingsChange: ((PresetSettings) -> PresetSettings) -> Unit
) {
    val audioCodec = normalizedAudioCodec(outputFormat, settings)
    DetailedOptionRow(
        label = "音频编码",
        values = compatibleAudioCodecs(outputFormat),
        selected = audioCodec,
        enabled = enabled,
        name = { "${it.label} (${it.ffmpegName})" },
        detail = { it.detail },
        onSelected = { codec -> onSettingsChange { state -> state.copy(audioCodec = codec) } }
    )
    AudioShapeOptions(
        settings = settings,
        audioCodec = audioCodec,
        enabled = enabled,
        onSettingsChange = onSettingsChange
    )
}

@Composable
private fun ImageFormatOptions(
    settings: PresetSettings,
    outputFormat: OutputFormat,
    enabled: Boolean,
    onSettingsChange: ((PresetSettings) -> PresetSettings) -> Unit
) {
    Text(
        "图片编码：${outputFormat.label} 编码由输出格式决定，文件扩展名为 .${outputFormat.extension}。",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall
    )
    OutlinedTextField(
        value = settings.frameTime,
        onValueChange = { onSettingsChange { state -> state.copy(frameTime = it) } },
        enabled = enabled,
        singleLine = true,
        label = { Text("时间点") },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun AudioShapeOptions(
    settings: PresetSettings,
    audioCodec: AudioCodec,
    enabled: Boolean,
    onSettingsChange: ((PresetSettings) -> PresetSettings) -> Unit
) {
    if (usesAudioBitrate(audioCodec)) {
        ValueSlider(
            label = "音频码率",
            value = settings.audioBitrateKbps.toFloat(),
            valueRange = 64f..320f,
            enabled = enabled,
            display = "${settings.audioBitrateKbps}k",
            onValue = { onSettingsChange { state -> state.copy(audioBitrateKbps = it.roundToInt()) } }
        )
    }
    if (audioCodec != AudioCodec.Copy) {
        OptionRow(
            label = "采样率",
            values = AudioSampleRate.entries,
            selected = settings.audioSampleRate,
            enabled = enabled,
            name = { it.label },
            onSelected = { onSettingsChange { state -> state.copy(audioSampleRate = it) } }
        )
        OptionRow(
            label = "声道",
            values = AudioChannelMode.entries,
            selected = settings.audioChannelMode,
            enabled = enabled,
            name = { it.label },
            onSelected = { onSettingsChange { state -> state.copy(audioChannelMode = it) } }
        )
    }
}

@Composable
private fun CustomPanel(
    uiState: CodecNestUiState,
    onCommandChange: (String) -> Unit,
    onSettingsChange: ((PresetSettings) -> PresetSettings) -> Unit
) {
    ToolSurface {
        SectionTitle("命令", Icons.Outlined.Terminal)
        OptionRow(
            label = "输出",
            values = OutputFormat.entries,
            selected = uiState.settings.outputFormat,
            enabled = !uiState.isRunning,
            name = { it.label },
            onSelected = { onSettingsChange { state -> state.copy(outputFormat = it) } }
        )
        OutlinedTextField(
            value = uiState.customCommand,
            onValueChange = onCommandChange,
            enabled = !uiState.isRunning,
            minLines = 5,
            label = { Text("FFmpeg 参数") },
            placeholder = { Text("-i {input} -c:v libx264 -c:a aac {output}") },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CommandPreview(preview: String) {
    ToolSurface {
        SectionTitle("预览", Icons.Outlined.Terminal)
        SelectionContainer {
            Text(
                preview.ifBlank { "ffmpeg" },
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            )
        }
    }
}

@Composable
private fun RunPanel(
    uiState: CodecNestUiState,
    onRun: () -> Unit,
    onCancel: () -> Unit
) {
    ToolSurface {
        SectionTitle("执行", Icons.Outlined.PlayArrow)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (uiState.isRunning) {
                Button(onClick = onCancel) {
                    Icon(Icons.Outlined.Stop, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("取消")
                }
            } else {
                Button(
                    onClick = onRun,
                    enabled = uiState.engineAvailable && uiState.inputs.isNotEmpty()
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("开始")
                }
            }
            Text(
                uiState.status?.label() ?: "就绪",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (uiState.isRunning) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            uiState.progressTimeMillis?.let {
                Text(
                    "媒体时间 ${formatMediaTime(it)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LogPanel(logs: List<String>) {
    ToolSurface {
        SectionTitle("日志", Icons.Outlined.Info)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp, max = 320.dp)
                .background(Color(0xFF111827), RoundedCornerShape(6.dp))
                .padding(10.dp)
        ) {
            SelectionContainer {
                Text(
                    if (logs.isEmpty()) "No logs yet." else logs.joinToString("\n"),
                    color = Color(0xFFE5E7EB),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                )
            }
        }
    }
}

@Composable
private fun HistoryPanel(history: List<JobHistoryItem>, onClear: () -> Unit) {
    ToolSurface {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SectionTitle("历史", Icons.Outlined.History)
            OutlinedButton(onClick = onClear, enabled = history.isNotEmpty()) {
                Icon(Icons.Outlined.Delete, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("清空")
            }
        }
        if (history.isEmpty()) {
            Text("暂无历史", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                history.forEach { item -> HistoryRow(item) }
            }
        }
    }
}

@Composable
private fun HistoryRow(item: JobHistoryItem) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(item.title, fontWeight = FontWeight.SemiBold)
                Text(item.status.label(), color = item.status.statusColor())
            }
            Text(
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(Date(item.startedAtMillis)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                item.commandPreview,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun TimeFields(
    start: String,
    duration: String,
    enabled: Boolean,
    onStart: (String) -> Unit,
    onDuration: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = start,
            onValueChange = onStart,
            enabled = enabled,
            singleLine = true,
            label = { Text("开始") },
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = duration,
            onValueChange = onDuration,
            enabled = enabled,
            singleLine = true,
            label = { Text("时长") },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun AuxiliaryFilePicker(
    label: String,
    file: SelectedMediaFile?,
    icon: ImageVector,
    enabled: Boolean,
    onPick: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalButton(onClick = onPick, enabled = enabled) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(label)
        }
        Text(
            file?.displayName ?: "未选择",
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(onClick = onClear, enabled = enabled && file != null) {
            Icon(Icons.Outlined.Delete, contentDescription = null)
        }
    }
}

@Composable
private fun <T> OptionRow(
    label: String,
    values: List<T>,
    selected: T,
    enabled: Boolean,
    name: (T) -> String,
    onSelected: (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        FlowLine {
            values.forEach { value ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelected(value) },
                    enabled = enabled,
                    label = { Text(name(value)) }
                )
            }
        }
    }
}

@Composable
private fun <T> DetailedOptionRow(
    label: String,
    values: List<T>,
    selected: T,
    enabled: Boolean,
    name: (T) -> String,
    detail: (T) -> String,
    onSelected: (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        FlowLine {
            values.forEach { value ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelected(value) },
                    enabled = enabled,
                    label = { Text(name(value)) }
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            values.forEach { value ->
                Text(
                    "${name(value)}：${detail(value)}",
                    color = if (selected == value) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun ValueSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    display: String,
    onValue: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(display, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value,
            onValueChange = onValue,
            valueRange = valueRange,
            enabled = enabled
        )
    }
}

@Composable
private fun ToolSurface(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun SectionTitle(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowLine(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        content()
    }
}

private fun JobStatus.label(): String = when (this) {
    JobStatus.Queued -> "排队"
    JobStatus.Running -> "运行中"
    JobStatus.Success -> "成功"
    JobStatus.Failed -> "失败"
    JobStatus.Cancelled -> "已取消"
}

@Composable
private fun JobStatus.statusColor(): Color = when (this) {
    JobStatus.Success -> Color(0xFF15803D)
    JobStatus.Failed -> MaterialTheme.colorScheme.error
    JobStatus.Cancelled -> MaterialTheme.colorScheme.tertiary
    JobStatus.Queued,
    JobStatus.Running -> MaterialTheme.colorScheme.primary
}

private fun formatBytes(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return if (unitIndex == 0) {
        "${bytes}B"
    } else {
        "%.1f%s".format(value, units[unitIndex])
    }
}

private fun formatMediaTime(timeMillis: Long): String {
    val totalSeconds = timeMillis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}
