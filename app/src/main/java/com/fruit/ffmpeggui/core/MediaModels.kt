package com.fruit.ffmpeggui.core

enum class WorkTab(val label: String) {
    Presets("预设"),
    Format("格式"),
    Custom("命令"),
    History("历史")
}

enum class FormatConversionMode(val label: String, val detail: String) {
    Video("视频", "保留视频工作流，可同时选择视频编码、音频编码和封装容器。"),
    Audio("音频", "只输出音频流，适合音频文件转码，也可从视频中导出音频。"),
    Image("图片", "从媒体中导出单帧图片，输出格式决定图片编码。")
}

enum class PresetCategory(val label: String) {
    All("全部"),
    Video("视频"),
    Audio("音频"),
    Image("图片"),
    Tool("工具")
}

enum class OperationPreset(
    val label: String,
    val description: String,
    val outputExtension: String?,
    val category: PresetCategory,
    val requiresOutput: Boolean = true,
    val minInputs: Int = 1
) {
    Transcode("转码", "转为常用视频封装和编码", "mp4", PresetCategory.Video),
    Compress("压缩", "按 CRF 和分辨率压缩视频", "mp4", PresetCategory.Video),
    ExtractAudio("提取音频", "从视频中导出音频", "mp3", PresetCategory.Audio),
    Trim("裁剪片段", "按开始时间和时长截取", "mp4", PresetCategory.Video),
    Merge("合并", "按顺序拼接多个视频", "mp4", PresetCategory.Video, minInputs = 2),
    Subtitle("烧录字幕", "把字幕渲染进视频画面", "mp4", PresetCategory.Video),
    Watermark("水印", "叠加图片水印", "mp4", PresetCategory.Video),
    Speed("变速", "调整音视频播放速度", "mp4", PresetCategory.Video),
    Gif("GIF", "导出动图", "gif", PresetCategory.Image),
    Frame("截帧", "导出指定时间点画面", "jpg", PresetCategory.Image),
    Probe("探测", "查看媒体格式信息", null, PresetCategory.Tool, requiresOutput = false),
    Remux("重新封装", "不重编码，改换媒体容器", "mp4", PresetCategory.Video),
    MuteVideo("静音视频", "保留画面并把音频静音", "mp4", PresetCategory.Video),
    VideoOnly("仅保留视频", "移除音频和字幕等非视频流", "mp4", PresetCategory.Video),
    RotateFlip("旋转/翻转", "旋转或翻转视频画面", "mp4", PresetCategory.Video),
    ChangeFrameRate("改帧率", "重采样为目标视频帧率", "mp4", PresetCategory.Video),
    Social1080p("社媒 1080p", "生成兼容性优先的 1080p MP4", "mp4", PresetCategory.Video),
    AudioConvert("音频转换", "转为常用音频格式", "mp3", PresetCategory.Audio),
    AudioTrim("音频裁剪", "按开始时间和时长截取音频", "mp3", PresetCategory.Audio),
    VolumeAdjust("音量调整", "按百分比调整音频音量", "mp3", PresetCategory.Audio),
    LoudnessNormalize("响度标准化", "按播客/短视频常用响度标准处理", "mp3", PresetCategory.Audio),
    FastMp4("快速 MP4", "快速生成可边下边播的 MP4", "mp4", PresetCategory.Tool),
    LosslessCopy("无损复制", "复制所有流，不重新编码", "mkv", PresetCategory.Tool),
    FixTimestamps("修复时间戳", "重新生成时间戳并复制媒体流", "mp4", PresetCategory.Tool)
}

enum class OutputFamily {
    Video,
    Audio,
    Image
}

enum class OutputFormat(
    val label: String,
    val extension: String,
    val mimeType: String,
    val family: OutputFamily,
    val detail: String
) {
    Mp4("MP4", "mp4", "video/mp4", OutputFamily.Video, "兼容性最好的视频封装，推荐 H.264 + AAC。"),
    Mkv("MKV", "mkv", "video/x-matroska", OutputFamily.Video, "容纳能力强，适合多音轨、多字幕和高级编码。"),
    Mov("MOV", "mov", "video/quicktime", OutputFamily.Video, "Apple/QuickTime 常用封装，推荐 H.264 或 H.265 + AAC。"),
    Webm("WebM", "webm", "video/webm", OutputFamily.Video, "网页开放格式，推荐 VP9 + Opus。"),
    Avi("AVI", "avi", "video/x-msvideo", OutputFamily.Video, "老式视频封装，兼容旧软件但不适合新编码特性。"),
    M4v("M4V", "m4v", "video/x-m4v", OutputFamily.Video, "Apple 生态常见 MP4 变体，推荐 H.264 + AAC。"),
    Ts("TS", "ts", "video/mp2t", OutputFamily.Video, "传输流封装，常用于直播、切片和广播。"),
    ThreeGp("3GP", "3gp", "video/3gpp", OutputFamily.Video, "移动设备旧格式，优先使用 H.264 + AAC。"),
    Mp3("MP3", "mp3", "audio/mpeg", OutputFamily.Audio, "最通用的有损音频格式，使用 MP3 编码。"),
    M4a("M4A", "m4a", "audio/mp4", OutputFamily.Audio, "MP4 音频封装，推荐 AAC。"),
    Mp4Audio("MP4", "mp4", "audio/mp4", OutputFamily.Audio, "音频专用 MP4 封装，可承载 AAC、ALAC、AC3、Opus 等音频流。"),
    Aac("AAC", "aac", "audio/aac", OutputFamily.Audio, "裸 AAC 音频流，适合需要 .aac 输出的场景。"),
    Wav("WAV", "wav", "audio/wav", OutputFamily.Audio, "无压缩 PCM 常用封装，文件较大但编辑友好。"),
    Flac("FLAC", "flac", "audio/flac", OutputFamily.Audio, "无损压缩音频格式，保留质量并减小体积。"),
    Ogg("OGG", "ogg", "audio/ogg", OutputFamily.Audio, "开放音频容器，常配 Vorbis 或 Opus。"),
    Opus("OPUS", "opus", "audio/opus", OutputFamily.Audio, "Opus 专用封装，适合语音和低码率音频。"),
    Jpg("JPG", "jpg", "image/jpeg", OutputFamily.Image, "有损图片格式，适合照片和小体积预览。"),
    Png("PNG", "png", "image/png", OutputFamily.Image, "无损图片格式，适合截图和清晰边缘。"),
    Bmp("BMP", "bmp", "image/bmp", OutputFamily.Image, "未压缩位图格式，文件通常较大。"),
    Tiff("TIFF", "tiff", "image/tiff", OutputFamily.Image, "高质量图片封装，适合归档和后期处理。"),
    Gif("GIF", "gif", "image/gif", OutputFamily.Image, "动图格式，适合短循环动画。")
}

enum class VideoCodec(val label: String, val ffmpegName: String, val detail: String) {
    H264("H.264", "libx264", "兼容性最好，适合 MP4/MOV/M4V/3GP 和大多数播放器。"),
    H265("H.265", "libx265", "同画质体积更小，但旧设备兼容性弱于 H.264。"),
    Vp8("VP8", "libvpx", "开放视频编码，常用于 WebM，也可用于 MKV 等开放容器。"),
    Vp9("VP9", "libvpx-vp9", "开放视频编码，主要用于 WebM/MKV。"),
    Av1("AV1", "libaom-av1", "新一代开放视频编码，压缩效率高，但编码慢且依赖 FFmpeg 构建支持。"),
    Copy("复制", "copy", "不重新编码视频，速度最快，但原编码必须被目标封装支持。")
}

enum class AudioCodec(val label: String, val ffmpegName: String, val detail: String) {
    Aac("AAC", "aac", "通用有损音频编码，适合 MP4/M4A/MOV/3GP。"),
    Mp3("MP3", "libmp3lame", "兼容性极强的有损音频编码，适合 .mp3。"),
    Alac("ALAC", "alac", "Apple 无损音频编码，适合 M4A/MP4/MOV。"),
    Ac3("AC3", "ac3", "影院和电视常见环绕声音频编码，适合 MKV/MP4/MOV/TS。"),
    Opus("Opus", "libopus", "低码率质量优秀，适合 WebM/Opus/Ogg。"),
    Vorbis("Vorbis", "libvorbis", "开放有损音频编码，常用于 OGG。"),
    Flac("FLAC", "flac", "无损压缩音频编码，适合保留原始质量。"),
    PcmS16le("PCM", "pcm_s16le", "未压缩 16-bit PCM，常用于 WAV 和编辑流程。"),
    Copy("复制", "copy", "不重新编码音频，速度最快，但原编码必须被目标封装支持。")
}

enum class ScalePreset(val label: String, val filter: String?) {
    Original("原始", null),
    P1080("1080p", "scale=-2:1080"),
    P720("720p", "scale=-2:720"),
    P480("480p", "scale=-2:480")
}

enum class WatermarkPosition(val label: String, val overlayExpression: String) {
    TopLeft("左上", "24:24"),
    TopRight("右上", "W-w-24:24"),
    BottomLeft("左下", "24:H-h-24"),
    BottomRight("右下", "W-w-24:H-h-24")
}

enum class RotationMode(val label: String, val filter: String) {
    Clockwise90("顺时针 90°", "transpose=1"),
    CounterClockwise90("逆时针 90°", "transpose=2"),
    Rotate180("旋转 180°", "transpose=1,transpose=1"),
    FlipHorizontal("水平翻转", "hflip"),
    FlipVertical("垂直翻转", "vflip")
}

enum class AudioSampleRate(val label: String, val hertz: Int?) {
    Original("原始", null),
    Hz44100("44.1 kHz", 44100),
    Hz48000("48 kHz", 48000)
}

enum class AudioChannelMode(val label: String, val channels: Int?) {
    Original("原始", null),
    Mono("单声道", 1),
    Stereo("立体声", 2)
}

data class PresetSettings(
    val formatMode: FormatConversionMode = FormatConversionMode.Video,
    val outputFormat: OutputFormat = OutputFormat.Mp4,
    val videoCodec: VideoCodec = VideoCodec.H264,
    val audioCodec: AudioCodec = AudioCodec.Aac,
    val crf: Int = 23,
    val audioBitrateKbps: Int = 128,
    val scalePreset: ScalePreset = ScalePreset.Original,
    val startTime: String = "00:00:00",
    val duration: String = "00:00:10",
    val frameTime: String = "00:00:01",
    val speed: Float = 1.0f,
    val gifFps: Int = 12,
    val watermarkPosition: WatermarkPosition = WatermarkPosition.BottomRight,
    val frameRate: Int = 30,
    val rotationMode: RotationMode = RotationMode.Clockwise90,
    val volumePercent: Int = 100,
    val audioSampleRate: AudioSampleRate = AudioSampleRate.Original,
    val audioChannelMode: AudioChannelMode = AudioChannelMode.Original
)

data class PreparedMediaFile(
    val path: String,
    val displayName: String
)

data class PreparedCommandInputs(
    val inputs: List<PreparedMediaFile>,
    val outputPath: String? = null,
    val subtitle: PreparedMediaFile? = null,
    val watermark: PreparedMediaFile? = null
)

data class CommandPlan(
    val arguments: List<String>,
    val summary: String,
    val outputExtension: String?,
    val requiresOutput: Boolean
) {
    val preview: String = CommandTokenizer.quote(arguments)
}

data class JobHistoryItem(
    val id: String,
    val title: String,
    val commandPreview: String,
    val status: JobStatus,
    val startedAtMillis: Long,
    val finishedAtMillis: Long,
    val outputName: String?,
    val logTail: String
)

enum class JobStatus {
    Queued,
    Running,
    Success,
    Failed,
    Cancelled
}

data class FfmpegExecutionResult(
    val status: JobStatus,
    val returnCode: String?,
    val logs: String,
    val failureMessage: String? = null
) {
    val succeeded: Boolean get() = status == JobStatus.Success
}

fun compatibleOutputFormats(preset: OperationPreset): List<OutputFormat> = when (preset) {
    OperationPreset.Probe -> emptyList()
    OperationPreset.Gif -> listOf(OutputFormat.Gif)
    OperationPreset.Frame -> OutputFormat.entries.filter { it.family == OutputFamily.Image && it != OutputFormat.Gif }
    OperationPreset.ExtractAudio,
    OperationPreset.AudioConvert,
    OperationPreset.AudioTrim,
    OperationPreset.VolumeAdjust,
    OperationPreset.LoudnessNormalize -> OutputFormat.entries.filter { it.family == OutputFamily.Audio }
    OperationPreset.Social1080p,
    OperationPreset.FastMp4 -> listOf(OutputFormat.Mp4)
    else -> OutputFormat.entries.filter { it.family == OutputFamily.Video }
}

fun normalizedOutputFormat(preset: OperationPreset, settings: PresetSettings): OutputFormat? {
    val formats = compatibleOutputFormats(preset)
    if (formats.isEmpty()) return null
    return settings.outputFormat.takeIf { it in formats } ?: formats.first()
}

fun compatibleOutputFormats(mode: FormatConversionMode): List<OutputFormat> = when (mode) {
    FormatConversionMode.Video -> OutputFormat.entries.filter { it.family == OutputFamily.Video }
    FormatConversionMode.Audio -> OutputFormat.entries.filter { it.family == OutputFamily.Audio }
    FormatConversionMode.Image -> OutputFormat.entries.filter { it.family == OutputFamily.Image && it != OutputFormat.Gif }
}

fun normalizedOutputFormat(mode: FormatConversionMode, settings: PresetSettings): OutputFormat {
    val formats = compatibleOutputFormats(mode)
    return settings.outputFormat.takeIf { it in formats } ?: formats.first()
}

fun compatibleVideoCodecs(format: OutputFormat): List<VideoCodec> = when (format) {
    OutputFormat.Webm -> listOf(VideoCodec.Vp9, VideoCodec.Vp8, VideoCodec.Av1, VideoCodec.Copy)
    OutputFormat.Mp4,
    OutputFormat.Mkv,
    OutputFormat.Mov,
    OutputFormat.Avi,
    OutputFormat.M4v,
    OutputFormat.Ts,
    OutputFormat.ThreeGp -> listOf(
        VideoCodec.H264,
        VideoCodec.H265,
        VideoCodec.Vp8,
        VideoCodec.Vp9,
        VideoCodec.Av1,
        VideoCodec.Copy
    )
    else -> emptyList()
}

fun compatibleAudioCodecs(format: OutputFormat): List<AudioCodec> = when (format) {
    OutputFormat.Webm -> listOf(AudioCodec.Opus, AudioCodec.Vorbis, AudioCodec.Copy)
    OutputFormat.Mp4,
    OutputFormat.Mov,
    OutputFormat.M4v -> listOf(
        AudioCodec.Aac,
        AudioCodec.Mp3,
        AudioCodec.Alac,
        AudioCodec.Ac3,
        AudioCodec.Opus,
        AudioCodec.Copy
    )
    OutputFormat.Mkv -> listOf(
        AudioCodec.Aac,
        AudioCodec.Mp3,
        AudioCodec.Alac,
        AudioCodec.Flac,
        AudioCodec.Ac3,
        AudioCodec.Opus,
        AudioCodec.Vorbis,
        AudioCodec.PcmS16le,
        AudioCodec.Copy
    )
    OutputFormat.Avi -> listOf(AudioCodec.Mp3, AudioCodec.Aac, AudioCodec.Ac3, AudioCodec.PcmS16le, AudioCodec.Copy)
    OutputFormat.Ts -> listOf(AudioCodec.Aac, AudioCodec.Mp3, AudioCodec.Ac3, AudioCodec.Copy)
    OutputFormat.ThreeGp -> listOf(AudioCodec.Aac, AudioCodec.Mp3, AudioCodec.Copy)
    OutputFormat.Mp3 -> listOf(AudioCodec.Mp3)
    OutputFormat.M4a,
    OutputFormat.Mp4Audio -> listOf(
        AudioCodec.Aac,
        AudioCodec.Mp3,
        AudioCodec.Alac,
        AudioCodec.Ac3,
        AudioCodec.Opus
    )
    OutputFormat.Aac -> listOf(AudioCodec.Aac)
    OutputFormat.Wav -> listOf(AudioCodec.PcmS16le)
    OutputFormat.Flac -> listOf(AudioCodec.Flac)
    OutputFormat.Ogg -> listOf(AudioCodec.Vorbis, AudioCodec.Opus, AudioCodec.Flac)
    OutputFormat.Opus -> listOf(AudioCodec.Opus)
    else -> emptyList()
}

fun normalizedVideoCodec(format: OutputFormat, settings: PresetSettings): VideoCodec {
    val codecs = compatibleVideoCodecs(format)
    return settings.videoCodec.takeIf { it in codecs } ?: codecs.first()
}

fun normalizedAudioCodec(format: OutputFormat, settings: PresetSettings): AudioCodec {
    val codecs = compatibleAudioCodecs(format)
    return settings.audioCodec.takeIf { it in codecs } ?: codecs.first()
}

fun usesAudioBitrate(codec: AudioCodec): Boolean =
    codec !in listOf(AudioCodec.Copy, AudioCodec.Alac, AudioCodec.Flac, AudioCodec.PcmS16le)
