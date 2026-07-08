package com.fruit.ffmpeggui.core

import java.util.Locale

class MediaCommandBuilder {
    fun buildPreset(
        preset: OperationPreset,
        settings: PresetSettings,
        prepared: PreparedCommandInputs
    ): CommandPlan {
        require(prepared.inputs.size >= preset.minInputs) {
            "${preset.label} requires at least ${preset.minInputs} input file(s)."
        }
        val outputPath = if (preset.requiresOutput) {
            requireNotNull(prepared.outputPath) { "${preset.label} requires an output file." }
        } else {
            null
        }

        val first = prepared.inputs.first()
        val args = when (preset) {
            OperationPreset.Transcode -> buildTranscode(first.path, outputPath!!, settings)
            OperationPreset.Compress -> buildCompress(first.path, outputPath!!, settings)
            OperationPreset.ExtractAudio -> buildAudioEncode(first.path, outputPath!!, settings)
            OperationPreset.Trim -> listOf(
                "-y", "-ss", settings.startTime, "-t", settings.duration, "-i", first.path,
                "-c", "copy", outputPath!!
            )
            OperationPreset.Merge -> buildMerge(prepared.inputs.map { it.path }, outputPath!!, settings)
            OperationPreset.Subtitle -> {
                val subtitle = requireNotNull(prepared.subtitle) {
                    "Subtitle preset requires a subtitle file."
                }
                buildSubtitle(first.path, subtitle.path, outputPath!!, settings)
            }
            OperationPreset.Watermark -> {
                val watermark = requireNotNull(prepared.watermark) {
                    "Watermark preset requires an image file."
                }
                buildWatermark(first.path, watermark.path, outputPath!!, settings)
            }
            OperationPreset.Speed -> buildSpeed(first.path, outputPath!!, settings)
            OperationPreset.Gif -> buildGif(first.path, outputPath!!, settings)
            OperationPreset.Frame -> listOf(
                "-y", "-ss", settings.frameTime, "-i", first.path,
                "-frames:v", "1", outputPath!!
            )
            OperationPreset.Probe -> listOf("-hide_banner", "-show_format", "-show_streams", first.path)
            OperationPreset.Remux -> listOf("-y", "-i", first.path, "-map", "0", "-c", "copy", outputPath!!)
            OperationPreset.MuteVideo -> listOf(
                "-y", "-i", first.path,
                "-map", "0:v:0", "-map", "0:a?", "-c:v", "copy",
                "-af", "volume=0", "-c:a", defaultAudioEncoder(settings),
                outputPath!!
            )
            OperationPreset.VideoOnly -> listOf(
                "-y", "-i", first.path, "-map", "0:v:0", "-c:v", "copy", "-an", outputPath!!
            )
            OperationPreset.RotateFlip -> buildRotateFlip(first.path, outputPath!!, settings)
            OperationPreset.ChangeFrameRate -> buildChangeFrameRate(first.path, outputPath!!, settings)
            OperationPreset.Social1080p -> buildSocial1080p(first.path, outputPath!!, settings)
            OperationPreset.AudioConvert -> buildAudioEncode(first.path, outputPath!!, settings)
            OperationPreset.AudioTrim -> buildAudioTrim(first.path, outputPath!!, settings)
            OperationPreset.VolumeAdjust -> buildVolumeAdjust(first.path, outputPath!!, settings)
            OperationPreset.LoudnessNormalize -> buildLoudnessNormalize(first.path, outputPath!!, settings)
            OperationPreset.FastMp4 -> buildFastMp4(first.path, outputPath!!, settings)
            OperationPreset.LosslessCopy -> listOf("-y", "-i", first.path, "-map", "0", "-c", "copy", outputPath!!)
            OperationPreset.FixTimestamps -> listOf(
                "-y", "-fflags", "+genpts", "-i", first.path,
                "-map", "0", "-c", "copy", "-avoid_negative_ts", "make_zero", outputPath!!
            )
        }

        return CommandPlan(
            arguments = args,
            summary = preset.label,
            outputExtension = resolveOutputExtension(preset, settings),
            requiresOutput = preset.requiresOutput
        )
    }

    fun buildCustom(template: String, prepared: PreparedCommandInputs): CommandPlan {
        val rawTokens = CommandTokenizer.tokenize(template).dropWhile {
            it.equals("ffmpeg", ignoreCase = true)
        }
        require(rawTokens.isNotEmpty()) { "Custom command is empty." }
        require(prepared.inputs.isNotEmpty()) { "Custom command requires an input file." }

        val hasInputPlaceholder = rawTokens.any {
            it.contains("{input}") || it.contains("{inputs}") || Regex("\\{input\\d+}").containsMatchIn(it)
        }
        require(hasInputPlaceholder) {
            "Custom command must reference {input}, {inputs}, or {input2} style placeholders."
        }

        val outputPath = prepared.outputPath
        val expanded = mutableListOf<String>()
        rawTokens.forEach { token ->
            when (token) {
                "{inputs}" -> prepared.inputs.forEach { file ->
                    expanded += "-i"
                    expanded += file.path
                }
                else -> expanded += replacePlaceholders(token, prepared)
            }
        }

        if (outputPath != null && expanded.none { it == outputPath }) {
            expanded += outputPath
        }
        val unresolved = expanded.firstOrNull { Regex("\\{[A-Za-z0-9_]+}").containsMatchIn(it) }
        require(unresolved == null) { "Unresolved placeholder in command: $unresolved" }

        return CommandPlan(
            arguments = expanded,
            summary = "自定义命令",
            outputExtension = outputPath?.substringAfterLast('.', missingDelimiterValue = "mp4"),
            requiresOutput = outputPath != null
        )
    }

    fun buildFormatConversion(
        settings: PresetSettings,
        prepared: PreparedCommandInputs
    ): CommandPlan {
        require(prepared.inputs.isNotEmpty()) { "Format conversion requires an input file." }
        val outputPath = requireNotNull(prepared.outputPath) {
            "Format conversion requires an output file."
        }
        val format = normalizedOutputFormat(settings.formatMode, settings)
        val normalizedSettings = normalizeFormatSettings(settings, format)
        val first = prepared.inputs.first()
        val args = when (normalizedSettings.formatMode) {
            FormatConversionMode.Video -> buildFormatVideo(first.path, outputPath, normalizedSettings)
            FormatConversionMode.Audio -> buildFormatAudio(first.path, outputPath, normalizedSettings)
            FormatConversionMode.Image -> listOf(
                "-y", "-ss", normalizedSettings.frameTime, "-i", first.path,
                "-frames:v", "1", outputPath
            )
        }

        return CommandPlan(
            arguments = args,
            summary = "格式转换",
            outputExtension = format.extension,
            requiresOutput = true
        )
    }

    fun outputExtensionFor(preset: OperationPreset, settings: PresetSettings): String? =
        resolveOutputExtension(preset, settings)

    private fun resolveOutputExtension(
        preset: OperationPreset,
        settings: PresetSettings
    ): String? = when (preset) {
        OperationPreset.Probe -> null
        else -> normalizedOutputFormat(preset, settings)?.extension
    }

    private fun buildTranscode(input: String, output: String, settings: PresetSettings): List<String> {
        val args = mutableListOf("-y", "-i", input)
        addVideoArgs(args, settings, includeScale = true)
        addAudioArgs(args, settings)
        args += output
        return args
    }

    private fun buildCompress(input: String, output: String, settings: PresetSettings): List<String> {
        val args = mutableListOf("-y", "-i", input)
        addVideoArgs(args, settings.copy(videoCodec = VideoCodec.H264), includeScale = true)
        args += listOf("-movflags", "+faststart")
        addAudioArgs(args, settings.copy(audioCodec = AudioCodec.Aac))
        args += output
        return args
    }

    private fun buildMerge(inputs: List<String>, output: String, settings: PresetSettings): List<String> {
        val args = mutableListOf("-y")
        inputs.forEach { path ->
            args += "-i"
            args += path
        }
        args += listOf(
            "-filter_complex",
            "concat=n=${inputs.size}:v=1:a=1[outv][outa]",
            "-map",
            "[outv]",
            "-map",
            "[outa]",
            "-c:v",
            defaultVideoEncoder(settings),
            "-c:a",
            defaultAudioEncoder(settings),
            output
        )
        return args
    }

    private fun buildSubtitle(
        input: String,
        subtitle: String,
        output: String,
        settings: PresetSettings
    ): List<String> {
        val filters = mutableListOf<String>()
        settings.scalePreset.filter?.let(filters::add)
        filters += "subtitles=${escapeFilterPath(subtitle)}"
        return listOf("-y", "-i", input, "-vf", filters.joinToString(",")) +
            listOf("-c:v", defaultVideoEncoder(settings), "-crf", settings.crf.toString(), "-c:a", "copy", output)
    }

    private fun buildWatermark(
        input: String,
        watermark: String,
        output: String,
        settings: PresetSettings
    ): List<String> = listOf(
        "-y", "-i", input, "-i", watermark,
        "-filter_complex", "overlay=${settings.watermarkPosition.overlayExpression}",
        "-c:v", defaultVideoEncoder(settings), "-crf", settings.crf.toString(),
        "-c:a", "copy", output
    )

    private fun buildSpeed(input: String, output: String, settings: PresetSettings): List<String> {
        val speed = settings.speed.coerceIn(0.25f, 4.0f)
        val audioFilter = buildAtempoFilter(speed)
        return listOf(
            "-y", "-i", input,
            "-filter_complex", "[0:v]setpts=PTS/$speed[v];[0:a]$audioFilter[a]",
            "-map", "[v]", "-map", "[a]",
            "-c:v", defaultVideoEncoder(settings), "-crf", settings.crf.toString(),
            "-c:a", defaultAudioEncoder(settings), output
        )
    }

    private fun buildGif(input: String, output: String, settings: PresetSettings): List<String> {
        val scale = settings.scalePreset.filter ?: "scale=640:-1"
        return listOf(
            "-y", "-i", input,
            "-vf", "fps=${settings.gifFps},$scale:flags=lanczos",
            "-loop", "0", output
        )
    }

    private fun buildRotateFlip(input: String, output: String, settings: PresetSettings): List<String> =
        listOf(
            "-y", "-i", input,
            "-vf", settings.rotationMode.filter,
            "-c:v", defaultVideoEncoder(settings),
            "-crf", settings.crf.toString(),
            "-c:a", "copy",
            output
        )

    private fun buildChangeFrameRate(input: String, output: String, settings: PresetSettings): List<String> =
        listOf(
            "-y", "-i", input,
            "-r", settings.frameRate.coerceIn(1, 120).toString(),
            "-c:v", defaultVideoEncoder(settings),
            "-crf", settings.crf.toString(),
            "-c:a", "copy",
            output
        )

    private fun buildSocial1080p(input: String, output: String, settings: PresetSettings): List<String> =
        listOf(
            "-y", "-i", input,
            "-vf", "scale=-2:1080,format=yuv420p",
            "-r", settings.frameRate.coerceIn(1, 120).toString(),
            "-c:v", "libx264",
            "-preset", "medium",
            "-crf", settings.crf.toString(),
            "-c:a", "aac",
            "-b:a", "${settings.audioBitrateKbps}k",
            "-movflags", "+faststart",
            output
        )

    private fun buildFastMp4(input: String, output: String, settings: PresetSettings): List<String> =
        listOf(
            "-y", "-i", input,
            "-c:v", "libx264",
            "-preset", "veryfast",
            "-crf", settings.crf.toString(),
            "-c:a", "aac",
            "-b:a", "${settings.audioBitrateKbps}k",
            "-movflags", "+faststart",
            output
        )

    private fun buildAudioEncode(input: String, output: String, settings: PresetSettings): List<String> {
        val args = mutableListOf("-y", "-i", input, "-vn")
        addMappedAudioEncodeArgs(args, settings)
        args += output
        return args
    }

    private fun buildAudioTrim(input: String, output: String, settings: PresetSettings): List<String> {
        val args = mutableListOf(
            "-y", "-ss", settings.startTime, "-t", settings.duration, "-i", input, "-vn"
        )
        addMappedAudioEncodeArgs(args, settings)
        args += output
        return args
    }

    private fun buildVolumeAdjust(input: String, output: String, settings: PresetSettings): List<String> {
        val volume = settings.volumePercent.coerceIn(0, 300) / 100.0f
        val args = mutableListOf(
            "-y", "-i", input, "-vn",
            "-af", "volume=${"%.2f".format(Locale.US, volume)}"
        )
        addMappedAudioEncodeArgs(args, settings)
        args += output
        return args
    }

    private fun buildLoudnessNormalize(input: String, output: String, settings: PresetSettings): List<String> {
        val args = mutableListOf(
            "-y", "-i", input, "-vn",
            "-af", "loudnorm=I=-16:TP=-1.5:LRA=11"
        )
        addMappedAudioEncodeArgs(args, settings)
        args += output
        return args
    }

    private fun buildFormatVideo(input: String, output: String, settings: PresetSettings): List<String> {
        val args = mutableListOf("-y", "-i", input)
        addVideoArgs(args, settings, includeScale = true)
        addAudioArgs(args, settings)
        args += output
        return args
    }

    private fun buildFormatAudio(input: String, output: String, settings: PresetSettings): List<String> {
        val args = mutableListOf("-y", "-i", input, "-vn")
        addSelectedAudioEncodeArgs(args, settings)
        args += output
        return args
    }

    private fun addVideoArgs(
        args: MutableList<String>,
        settings: PresetSettings,
        includeScale: Boolean
    ) {
        if (includeScale) {
            settings.scalePreset.filter?.let {
                args += "-vf"
                args += it
            }
        }
        if (settings.videoCodec == VideoCodec.Copy) {
            args += listOf("-c:v", "copy")
            return
        }
        val codec = effectiveVideoCodec(settings)
        args += listOf("-c:v", codec.ffmpegName)
        when (codec) {
            VideoCodec.H264,
            VideoCodec.H265 -> args += listOf("-preset", "medium", "-crf", settings.crf.toString())
            VideoCodec.Vp8 -> args += listOf("-b:v", "1M", "-crf", settings.crf.toString())
            VideoCodec.Vp9 -> args += listOf("-b:v", "0", "-crf", settings.crf.toString())
            VideoCodec.Av1 -> args += listOf("-b:v", "0", "-crf", settings.crf.toString(), "-cpu-used", "4")
            VideoCodec.Copy -> Unit
        }
    }

    private fun addAudioArgs(args: MutableList<String>, settings: PresetSettings) {
        if (settings.audioCodec == AudioCodec.Copy) {
            args += listOf("-c:a", "copy")
        } else {
            val codec = effectiveAudioCodec(settings)
            args += listOf("-c:a", codec.ffmpegName)
            if (usesAudioBitrate(codec)) {
                args += listOf("-b:a", "${settings.audioBitrateKbps}k")
            }
            addAudioShapeArgs(args, settings)
        }
    }

    private fun addMappedAudioEncodeArgs(args: MutableList<String>, settings: PresetSettings) {
        val outputFormat = normalizedOutputFormat(OperationPreset.AudioConvert, settings) ?: OutputFormat.Mp3
        val codec = defaultAudioCodecFor(outputFormat)
        args += listOf("-c:a", codec.ffmpegName)
        if (usesAudioBitrate(codec)) {
            args += listOf("-b:a", "${settings.audioBitrateKbps}k")
        }
        addAudioShapeArgs(args, settings)
    }

    private fun addSelectedAudioEncodeArgs(args: MutableList<String>, settings: PresetSettings) {
        if (settings.audioCodec == AudioCodec.Copy) {
            args += listOf("-c:a", "copy")
            return
        }
        args += listOf("-c:a", settings.audioCodec.ffmpegName)
        if (usesAudioBitrate(settings.audioCodec)) {
            args += listOf("-b:a", "${settings.audioBitrateKbps}k")
        }
        addAudioShapeArgs(args, settings)
    }

    private fun addAudioShapeArgs(args: MutableList<String>, settings: PresetSettings) {
        settings.audioSampleRate.hertz?.let {
            args += "-ar"
            args += it.toString()
        }
        settings.audioChannelMode.channels?.let {
            args += "-ac"
            args += it.toString()
        }
    }

    private fun effectiveVideoCodec(settings: PresetSettings): VideoCodec {
        val format = normalizedOutputFormat(OperationPreset.Transcode, settings) ?: OutputFormat.Mp4
        return normalizedVideoCodec(format, settings)
    }

    private fun effectiveAudioCodec(settings: PresetSettings): AudioCodec {
        val format = normalizedOutputFormat(OperationPreset.Transcode, settings) ?: OutputFormat.Mp4
        return normalizedAudioCodec(format, settings)
    }

    private fun defaultVideoEncoder(settings: PresetSettings): String =
        if (effectiveVideoCodec(settings) == VideoCodec.Copy) {
            if (settings.outputFormat == OutputFormat.Webm) VideoCodec.Vp9.ffmpegName else VideoCodec.H264.ffmpegName
        } else {
            effectiveVideoCodec(settings).ffmpegName
        }

    private fun defaultAudioEncoder(settings: PresetSettings): String =
        if (effectiveAudioCodec(settings) == AudioCodec.Copy) {
            if (settings.outputFormat == OutputFormat.Webm) AudioCodec.Opus.ffmpegName else AudioCodec.Aac.ffmpegName
        } else {
            effectiveAudioCodec(settings).ffmpegName
        }

    private fun defaultAudioCodecFor(format: OutputFormat): AudioCodec = when (format) {
        OutputFormat.Mp3 -> AudioCodec.Mp3
        OutputFormat.M4a,
        OutputFormat.Mp4Audio,
        OutputFormat.Aac -> AudioCodec.Aac
        OutputFormat.Wav -> AudioCodec.PcmS16le
        OutputFormat.Flac -> AudioCodec.Flac
        OutputFormat.Ogg -> AudioCodec.Vorbis
        OutputFormat.Opus -> AudioCodec.Opus
        else -> AudioCodec.Aac
    }

    private fun normalizeFormatSettings(settings: PresetSettings, format: OutputFormat): PresetSettings =
        settings.copy(
            outputFormat = format,
            videoCodec = if (settings.formatMode == FormatConversionMode.Video) {
                normalizedVideoCodec(format, settings)
            } else {
                settings.videoCodec
            },
            audioCodec = if (settings.formatMode != FormatConversionMode.Image) {
                normalizedAudioCodec(format, settings)
            } else {
                settings.audioCodec
            }
        )

    private fun replacePlaceholders(token: String, prepared: PreparedCommandInputs): String {
        var replaced = token
        prepared.inputs.forEachIndexed { index, file ->
            replaced = replaced.replace("{input${index + 1}}", file.path)
        }
        replaced = replaced.replace("{input}", prepared.inputs.first().path)
        prepared.outputPath?.let { replaced = replaced.replace("{output}", it) }
        prepared.subtitle?.let { replaced = replaced.replace("{subtitle}", it.path) }
        prepared.watermark?.let { replaced = replaced.replace("{watermark}", it.path) }
        return replaced
    }

    private fun buildAtempoFilter(speed: Float): String {
        var remaining = speed.coerceIn(0.25f, 4.0f)
        val filters = mutableListOf<String>()
        while (remaining > 2.0f) {
            filters += "atempo=2.0"
            remaining /= 2.0f
        }
        while (remaining < 0.5f) {
            filters += "atempo=0.5"
            remaining /= 0.5f
        }
        filters += "atempo=${"%.3f".format(Locale.US, remaining)}"
        return filters.joinToString(",")
    }

    private fun escapeFilterPath(path: String): String =
        path.replace("\\", "\\\\")
            .replace(":", "\\:")
            .replace("'", "\\'")
}
