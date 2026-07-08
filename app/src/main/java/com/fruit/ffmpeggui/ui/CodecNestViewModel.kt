package com.fruit.ffmpeggui.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fruit.ffmpeggui.core.CommandPlan
import com.fruit.ffmpeggui.core.CommandTokenizer
import com.fruit.ffmpeggui.core.FfmpegExecutionResult
import com.fruit.ffmpeggui.core.FormatConversionMode
import com.fruit.ffmpeggui.core.JobHistoryItem
import com.fruit.ffmpeggui.core.JobStatus
import com.fruit.ffmpeggui.core.MediaCommandBuilder
import com.fruit.ffmpeggui.core.OperationPreset
import com.fruit.ffmpeggui.core.OutputFamily
import com.fruit.ffmpeggui.core.PreparedCommandInputs
import com.fruit.ffmpeggui.core.PreparedMediaFile
import com.fruit.ffmpeggui.core.PresetSettings
import com.fruit.ffmpeggui.core.WorkTab
import com.fruit.ffmpeggui.core.normalizedAudioCodec
import com.fruit.ffmpeggui.core.normalizedOutputFormat
import com.fruit.ffmpeggui.core.normalizedVideoCodec
import com.fruit.ffmpeggui.data.DocumentFileStore
import com.fruit.ffmpeggui.data.PreparedJobFiles
import com.fruit.ffmpeggui.data.SelectedMediaFile
import com.fruit.ffmpeggui.data.SharedPreferencesJobHistoryRepository
import com.fruit.ffmpeggui.ffmpeg.ReflectiveFfmpegRunner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class CodecNestUiState(
    val tab: WorkTab = WorkTab.Presets,
    val inputs: List<SelectedMediaFile> = emptyList(),
    val subtitle: SelectedMediaFile? = null,
    val watermark: SelectedMediaFile? = null,
    val preset: OperationPreset = OperationPreset.Compress,
    val settings: PresetSettings = PresetSettings(),
    val customCommand: String = "-i {input} -c:v libx264 -preset medium -crf 23 -c:a aac {output}",
    val commandPreview: String = "",
    val outputExtension: String = "mp4",
    val outputMimeType: String = "video/mp4",
    val suggestedOutputName: String = "output.mp4",
    val isRunning: Boolean = false,
    val status: JobStatus? = null,
    val progressTimeMillis: Long? = null,
    val logs: List<String> = emptyList(),
    val history: List<JobHistoryItem> = emptyList(),
    val message: String? = null,
    val engineAvailable: Boolean = false,
    val engineName: String = "FFmpegKitNext"
) {
    val requiresOutput: Boolean
        get() = when (tab) {
            WorkTab.Custom,
            WorkTab.Format -> true
            WorkTab.Presets -> preset.requiresOutput
            WorkTab.History -> true
        }
}

class CodecNestViewModel(application: Application) : AndroidViewModel(application) {
    private val fileStore = DocumentFileStore(application)
    private val historyRepository = SharedPreferencesJobHistoryRepository(application)
    private val runner = ReflectiveFfmpegRunner()
    private val commandBuilder = MediaCommandBuilder()
    private var runningJob: Job? = null

    private val _state = MutableStateFlow(
        CodecNestUiState(
            history = historyRepository.load(),
            engineAvailable = runner.isAvailable,
            engineName = runner.engineName
        )
    )
    val state: StateFlow<CodecNestUiState> = _state.asStateFlow()

    init {
        refreshDerivedState()
    }

    fun setTab(tab: WorkTab) {
        _state.update { it.copy(tab = tab, settings = coerceSettings(tab, it.preset, it.settings), message = null) }
        refreshDerivedState()
    }

    fun setPreset(preset: OperationPreset) {
        _state.update {
            it.copy(
                preset = preset,
                settings = coerceSettings(WorkTab.Presets, preset, it.settings),
                message = null
            )
        }
        refreshDerivedState()
    }

    fun updateSettings(transform: (PresetSettings) -> PresetSettings) {
        _state.update {
            val nextSettings = transform(it.settings)
            it.copy(settings = coerceSettings(it.tab, it.preset, nextSettings), message = null)
        }
        refreshDerivedState()
    }

    fun updateCustomCommand(command: String) {
        _state.update { it.copy(customCommand = command, commandPreview = command, message = null) }
    }

    fun setInputs(uris: List<Uri>) {
        val files = uris.distinct().map { uri ->
            fileStore.persistReadPermission(uri)
            fileStore.describe(uri)
        }
        _state.update { it.copy(inputs = files, message = null) }
        refreshDerivedState()
    }

    fun setSubtitle(uri: Uri?) {
        val file = uri?.let {
            fileStore.persistReadPermission(it)
            fileStore.describe(it)
        }
        _state.update { it.copy(subtitle = file, message = null) }
        refreshDerivedState()
    }

    fun setWatermark(uri: Uri?) {
        val file = uri?.let {
            fileStore.persistReadPermission(it)
            fileStore.describe(it)
        }
        _state.update { it.copy(watermark = file, message = null) }
        refreshDerivedState()
    }

    fun clearInputs() {
        _state.update { it.copy(inputs = emptyList(), message = null) }
        refreshDerivedState()
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }

    fun clearHistory() {
        historyRepository.clear()
        _state.update { it.copy(history = emptyList(), message = null) }
    }

    fun startJob(outputUri: Uri?, outputName: String?) {
        val snapshot = state.value
        if (snapshot.isRunning) return
        if (!snapshot.engineAvailable) {
            _state.update {
                it.copy(message = "缺少 FFmpegKitNext 引擎：请把 ffmpeg-kit-next.aar 放到 app/libs/。")
            }
            return
        }
        if (snapshot.inputs.size < requiredInputCount(snapshot)) {
            _state.update { it.copy(message = "当前预设需要至少 ${requiredInputCount(snapshot)} 个输入文件。") }
            return
        }
        if (snapshot.requiresOutput && outputUri == null) {
            _state.update { it.copy(message = "请先选择输出文件。") }
            return
        }
        if (snapshot.preset == OperationPreset.Subtitle && snapshot.subtitle == null && snapshot.tab == WorkTab.Presets) {
            _state.update { it.copy(message = "烧录字幕需要选择字幕文件。") }
            return
        }
        if (snapshot.preset == OperationPreset.Watermark && snapshot.watermark == null && snapshot.tab == WorkTab.Presets) {
            _state.update { it.copy(message = "水印预设需要选择图片文件。") }
            return
        }
        outputUri?.let(fileStore::persistWritePermission)

        runningJob = viewModelScope.launch {
            runJob(snapshot, outputUri, outputName)
        }
    }

    fun cancelJob() {
        runner.cancel()
        runningJob?.cancel()
    }

    private suspend fun runJob(snapshot: CodecNestUiState, outputUri: Uri?, outputName: String?) {
        val started = System.currentTimeMillis()
        var prepared: PreparedJobFiles? = null
        var commandPlan: CommandPlan? = null
        var result = FfmpegExecutionResult(JobStatus.Failed, null, "", null)

        _state.update {
            it.copy(
                isRunning = true,
                status = JobStatus.Running,
                logs = emptyList(),
                progressTimeMillis = null,
                message = null
            )
        }

        try {
            appendLog("Preparing workspace...")
            val extension = activeOutputExtension(snapshot)
            prepared = withContext(Dispatchers.IO) {
                if (snapshot.tab == WorkTab.Presets && snapshot.preset == OperationPreset.Probe) {
                    fileStore.prepareProbeJob(snapshot.inputs)
                } else {
                    fileStore.prepareJob(
                        inputs = snapshot.inputs,
                        outputExtension = if (snapshot.requiresOutput) extension else null,
                        subtitle = snapshot.subtitle,
                        watermark = snapshot.watermark
                    )
                }
            }
            commandPlan = when (snapshot.tab) {
                WorkTab.Custom -> commandBuilder.buildCustom(snapshot.customCommand, prepared.commandInputs)
                WorkTab.Format -> commandBuilder.buildFormatConversion(snapshot.settings, prepared.commandInputs)
                WorkTab.Presets,
                WorkTab.History -> commandBuilder.buildPreset(snapshot.preset, snapshot.settings, prepared.commandInputs)
            }
            _state.update { it.copy(commandPreview = commandPlan.preview) }
            appendLog(commandPlan.preview)

            result = if (snapshot.tab == WorkTab.Presets && snapshot.preset == OperationPreset.Probe) {
                runner.probe(commandPlan.arguments, ::appendLog)
            } else {
                runner.execute(
                    arguments = commandPlan.arguments,
                    onLog = ::appendLog,
                    onProgressTime = { time -> _state.update { it.copy(progressTimeMillis = time) } }
                )
            }

            if (result.succeeded && snapshot.requiresOutput && outputUri != null) {
                val outputFile = requireNotNull(prepared.outputFile)
                appendLog("Writing output document...")
                withContext(Dispatchers.IO) {
                    fileStore.copyOutputToUri(outputFile, outputUri)
                }
            }

            val finalStatus = if (result.succeeded) JobStatus.Success else result.status
            _state.update {
                it.copy(
                    isRunning = false,
                    status = finalStatus,
                    message = finalStatus.userMessage(result.failureMessage)
                )
            }
        } catch (cancellation: CancellationException) {
            result = result.copy(status = JobStatus.Cancelled, failureMessage = "Cancelled")
            _state.update {
                it.copy(isRunning = false, status = JobStatus.Cancelled, message = "任务已取消。")
            }
        } catch (throwable: Throwable) {
            result = FfmpegExecutionResult(
                status = JobStatus.Failed,
                returnCode = null,
                logs = state.value.logs.joinToString("\n"),
                failureMessage = throwable.message
            )
            appendLog("Failed: ${throwable.message ?: throwable::class.java.simpleName}")
            _state.update {
                it.copy(isRunning = false, status = JobStatus.Failed, message = "任务失败：${throwable.message}")
            }
        } finally {
            prepared?.let { withContext(Dispatchers.IO) { fileStore.clean(it.workingDir) } }
            val finished = System.currentTimeMillis()
            val plan = commandPlan
            if (plan != null) {
                val item = JobHistoryItem(
                    id = UUID.randomUUID().toString(),
                    title = plan.summary,
                    commandPreview = plan.preview,
                    status = result.status,
                    startedAtMillis = started,
                    finishedAtMillis = finished,
                    outputName = outputName,
                    logTail = state.value.logs.takeLast(24).joinToString("\n")
                )
                historyRepository.add(item)
                _state.update { it.copy(history = historyRepository.load()) }
            }
        }
    }

    private fun refreshDerivedState() {
        val current = state.value
        val extension = activeOutputExtension(current)
        val mimeType = activeOutputMimeType(current)
        val suggestedName = suggestedOutputName(current, extension)
        val preview = buildPreview(current, extension)
        _state.update {
            it.copy(
                outputExtension = extension,
                outputMimeType = mimeType,
                suggestedOutputName = suggestedName,
                commandPreview = preview
            )
        }
    }

    private fun activeOutputExtension(snapshot: CodecNestUiState): String {
        when (snapshot.tab) {
            WorkTab.Custom -> return snapshot.settings.outputFormat.extension
            WorkTab.Format -> return normalizedOutputFormat(snapshot.settings.formatMode, snapshot.settings).extension
            WorkTab.Presets,
            WorkTab.History -> Unit
        }
        return commandBuilder.outputExtensionFor(snapshot.preset, snapshot.settings)
            ?: snapshot.settings.outputFormat.extension
    }

    private fun activeOutputMimeType(snapshot: CodecNestUiState): String {
        when (snapshot.tab) {
            WorkTab.Custom -> return snapshot.settings.outputFormat.mimeType
            WorkTab.Format -> return normalizedOutputFormat(snapshot.settings.formatMode, snapshot.settings).mimeType
            WorkTab.Presets,
            WorkTab.History -> Unit
        }
        return normalizedOutputFormat(snapshot.preset, snapshot.settings)?.mimeType
            ?: snapshot.settings.outputFormat.mimeType
    }

    private fun coerceSettings(
        tab: WorkTab,
        preset: OperationPreset,
        settings: PresetSettings
    ): PresetSettings = when (tab) {
        WorkTab.Custom,
        WorkTab.History -> settings
        WorkTab.Presets -> {
            val outputFormat = normalizedOutputFormat(preset, settings) ?: settings.outputFormat
            settings.copy(
                outputFormat = outputFormat,
                videoCodec = if (outputFormat.family == OutputFamily.Video) {
                    normalizedVideoCodec(outputFormat, settings)
                } else {
                    settings.videoCodec
                },
                audioCodec = if (outputFormat.family != OutputFamily.Image) {
                    normalizedAudioCodec(outputFormat, settings)
                } else {
                    settings.audioCodec
                }
            )
        }
        WorkTab.Format -> {
            val outputFormat = normalizedOutputFormat(settings.formatMode, settings)
            settings.copy(
                outputFormat = outputFormat,
                videoCodec = if (settings.formatMode == FormatConversionMode.Video) {
                    normalizedVideoCodec(outputFormat, settings)
                } else {
                    settings.videoCodec
                },
                audioCodec = if (settings.formatMode != FormatConversionMode.Image) {
                    normalizedAudioCodec(outputFormat, settings)
                } else {
                    settings.audioCodec
                }
            )
        }
    }

    private fun requiredInputCount(snapshot: CodecNestUiState): Int =
        if (snapshot.tab == WorkTab.Presets) snapshot.preset.minInputs else 1

    private fun buildPreview(snapshot: CodecNestUiState, extension: String): String {
        if (snapshot.tab == WorkTab.Custom) {
            return snapshot.customCommand
        }
        return runCatching {
            val inputCount = if (snapshot.tab == WorkTab.Presets) {
                maxOf(snapshot.inputs.size, snapshot.preset.minInputs)
            } else {
                maxOf(snapshot.inputs.size, 1)
            }
            val prepared = PreparedCommandInputs(
                inputs = (1..inputCount).map { index ->
                    PreparedMediaFile(path = "{input$index}", displayName = "input$index")
                },
                outputPath = if (snapshot.requiresOutput) "{output}.$extension" else null,
                subtitle = snapshot.subtitle?.let { PreparedMediaFile("{subtitle}", it.displayName) }
                    ?: PreparedMediaFile("{subtitle}", "subtitle"),
                watermark = snapshot.watermark?.let { PreparedMediaFile("{watermark}", it.displayName) }
                    ?: PreparedMediaFile("{watermark}", "watermark")
            )
            if (snapshot.tab == WorkTab.Format) {
                commandBuilder.buildFormatConversion(snapshot.settings, prepared).preview
            } else {
                commandBuilder.buildPreset(snapshot.preset, snapshot.settings, prepared).preview
            }
        }.getOrElse {
            CommandTokenizer.quote(listOf("ffmpeg", if (snapshot.tab == WorkTab.Format) "格式转换" else snapshot.preset.label))
        }
    }

    private fun suggestedOutputName(snapshot: CodecNestUiState, extension: String): String {
        val base = snapshot.inputs.firstOrNull()?.displayName
            ?.substringBeforeLast('.', missingDelimiterValue = snapshot.inputs.first().displayName)
            ?.replace(Regex("[^A-Za-z0-9._-]"), "_")
            ?.take(48)
            ?.ifBlank { "output" }
            ?: "output"
        val suffix = when {
            snapshot.tab == WorkTab.Custom -> "custom"
            snapshot.tab == WorkTab.Format -> "format"
            snapshot.preset == OperationPreset.Probe -> "probe"
            else -> snapshot.preset.name.lowercase()
        }
        return "$base-$suffix.$extension"
    }

    private fun appendLog(message: String) {
        val lines = message.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return
        _state.update {
            it.copy(logs = (it.logs + lines).takeLast(MAX_LOG_LINES))
        }
    }

    private fun JobStatus.userMessage(failureMessage: String?): String = when (this) {
        JobStatus.Success -> "任务完成。"
        JobStatus.Cancelled -> "任务已取消。"
        JobStatus.Failed -> "任务失败：${failureMessage ?: "FFmpeg 返回失败状态。"}"
        JobStatus.Queued,
        JobStatus.Running -> "任务运行中。"
    }

    private companion object {
        const val MAX_LOG_LINES = 600
    }
}
