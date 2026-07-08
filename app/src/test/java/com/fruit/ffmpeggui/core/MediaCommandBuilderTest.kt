package com.fruit.ffmpeggui.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaCommandBuilderTest {
    private val builder = MediaCommandBuilder()

    @Test
    fun buildsTranscodeCommandWithSelectedCodecs() {
        val plan = builder.buildPreset(
            preset = OperationPreset.Transcode,
            settings = PresetSettings(
                outputFormat = OutputFormat.Mkv,
                videoCodec = VideoCodec.H265,
                audioCodec = AudioCodec.Aac,
                crf = 24,
                scalePreset = ScalePreset.P720
            ),
            prepared = PreparedCommandInputs(
                inputs = listOf(PreparedMediaFile("/tmp/input.mp4", "input.mp4")),
                outputPath = "/tmp/output.mkv"
            )
        )

        assertEquals("mkv", plan.outputExtension)
        assertTrue(plan.arguments.containsAll(listOf("-c:v", "libx265", "-crf", "24")))
        assertTrue(plan.arguments.containsAll(listOf("-vf", "scale=-2:720")))
        assertEquals("/tmp/output.mkv", plan.arguments.last())
    }

    @Test
    fun expandsCustomPlaceholders() {
        val plan = builder.buildCustom(
            template = "-i {input} -i {watermark} -filter_complex overlay {output}",
            prepared = PreparedCommandInputs(
                inputs = listOf(PreparedMediaFile("/tmp/in.mp4", "in.mp4")),
                outputPath = "/tmp/out.mp4",
                watermark = PreparedMediaFile("/tmp/logo.png", "logo.png")
            )
        )

        assertEquals(
            listOf(
                "-i", "/tmp/in.mp4",
                "-i", "/tmp/logo.png",
                "-filter_complex", "overlay",
                "/tmp/out.mp4"
            ),
            plan.arguments
        )
    }

    @Test
    fun mergeRequiresMultipleInputs() {
        assertThrows(IllegalArgumentException::class.java) {
            builder.buildPreset(
                preset = OperationPreset.Merge,
                settings = PresetSettings(),
                prepared = PreparedCommandInputs(
                    inputs = listOf(PreparedMediaFile("/tmp/one.mp4", "one.mp4")),
                    outputPath = "/tmp/out.mp4"
                )
            )
        }
    }

    @Test
    fun webmTranscodeDefaultsToVp9AndOpus() {
        val plan = builder.buildPreset(
            preset = OperationPreset.Transcode,
            settings = PresetSettings(outputFormat = OutputFormat.Webm),
            prepared = prepared("/tmp/output.webm")
        )

        assertEquals("webm", plan.outputExtension)
        assertTrue(plan.arguments.containsAll(listOf("-c:v", "libvpx-vp9")))
        assertTrue(plan.arguments.containsAll(listOf("-c:a", "libopus")))
    }

    @Test
    fun newPresetsBuildExpectedArgumentsAndExtensions() {
        val cases = listOf(
            PresetCase(OperationPreset.Remux, "mp4", listOf("-map", "0", "-c", "copy")),
            PresetCase(OperationPreset.MuteVideo, "mp4", listOf("-af", "volume=0")),
            PresetCase(OperationPreset.VideoOnly, "mp4", listOf("-an")),
            PresetCase(OperationPreset.RotateFlip, "mp4", listOf("-vf", "transpose=1")),
            PresetCase(OperationPreset.ChangeFrameRate, "mp4", listOf("-r", "30")),
            PresetCase(OperationPreset.Social1080p, "mp4", listOf("-movflags", "+faststart")),
            PresetCase(
                OperationPreset.AudioConvert,
                "flac",
                listOf("-vn", "-c:a", "flac"),
                PresetSettings(outputFormat = OutputFormat.Flac)
            ),
            PresetCase(OperationPreset.AudioTrim, "mp3", listOf("-ss", "00:00:00", "-t", "00:00:10")),
            PresetCase(
                OperationPreset.VolumeAdjust,
                "mp3",
                listOf("-af", "volume=1.50"),
                PresetSettings(outputFormat = OutputFormat.Mp3, volumePercent = 150)
            ),
            PresetCase(OperationPreset.LoudnessNormalize, "mp3", listOf("-af", "loudnorm=I=-16:TP=-1.5:LRA=11")),
            PresetCase(OperationPreset.FastMp4, "mp4", listOf("-preset", "veryfast")),
            PresetCase(OperationPreset.LosslessCopy, "mp4", listOf("-map", "0", "-c", "copy")),
            PresetCase(OperationPreset.FixTimestamps, "mp4", listOf("-fflags", "+genpts", "-avoid_negative_ts", "make_zero"))
        )

        cases.forEach { case ->
            assertEquals("${case.preset} min input", 1, case.preset.minInputs)
            val plan = builder.buildPreset(
                preset = case.preset,
                settings = case.settings,
                prepared = prepared("/tmp/output.${case.expectedExtension}")
            )

            assertEquals(case.preset.name, case.expectedExtension, plan.outputExtension)
            assertTrue(case.preset.name, plan.arguments.containsAll(case.expectedTokens))
            assertEquals("/tmp/output.${case.expectedExtension}", plan.arguments.last())
        }
    }

    @Test
    fun outputFormatsAreFilteredByPresetType() {
        assertTrue(compatibleOutputFormats(OperationPreset.AudioConvert).all { it.family == OutputFamily.Audio })
        assertTrue(compatibleOutputFormats(OperationPreset.Transcode).all { it.family == OutputFamily.Video })
        assertTrue(compatibleOutputFormats(OperationPreset.Frame).all { it.family == OutputFamily.Image })
        assertTrue(OutputFormat.Mp4 !in compatibleOutputFormats(OperationPreset.AudioConvert))
        assertTrue(OutputFormat.Mp3 !in compatibleOutputFormats(OperationPreset.Frame))
    }

    @Test
    fun formatConversionBuildsVideoCommandWithSelectedContainerAndCodecs() {
        val plan = builder.buildFormatConversion(
            settings = PresetSettings(
                formatMode = FormatConversionMode.Video,
                outputFormat = OutputFormat.Mkv,
                videoCodec = VideoCodec.H265,
                audioCodec = AudioCodec.Flac,
                crf = 22
            ),
            prepared = prepared("/tmp/output.mkv")
        )

        assertEquals("mkv", plan.outputExtension)
        assertTrue(plan.arguments.containsAll(listOf("-c:v", "libx265", "-crf", "22")))
        assertTrue(plan.arguments.containsAll(listOf("-c:a", "flac")))
        assertEquals("/tmp/output.mkv", plan.arguments.last())
    }

    @Test
    fun formatConversionCoercesIncompatibleWebmCodecs() {
        val plan = builder.buildFormatConversion(
            settings = PresetSettings(
                formatMode = FormatConversionMode.Video,
                outputFormat = OutputFormat.Webm,
                videoCodec = VideoCodec.H264,
                audioCodec = AudioCodec.Aac
            ),
            prepared = prepared("/tmp/output.webm")
        )

        assertEquals("webm", plan.outputExtension)
        assertTrue(plan.arguments.containsAll(listOf("-c:v", "libvpx-vp9")))
        assertTrue(plan.arguments.containsAll(listOf("-c:a", "libopus")))
    }

    @Test
    fun formatConversionBuildsAudioOnlyCommandWithSelectedCodec() {
        val plan = builder.buildFormatConversion(
            settings = PresetSettings(
                formatMode = FormatConversionMode.Audio,
                outputFormat = OutputFormat.Ogg,
                audioCodec = AudioCodec.Opus,
                audioBitrateKbps = 96
            ),
            prepared = prepared("/tmp/output.ogg")
        )

        assertEquals("ogg", plan.outputExtension)
        assertTrue(plan.arguments.containsAll(listOf("-vn", "-c:a", "libopus", "-b:a", "96k")))
    }

    @Test
    fun formatConversionSupportsMp4AudioContainerWithAlac() {
        val plan = builder.buildFormatConversion(
            settings = PresetSettings(
                formatMode = FormatConversionMode.Audio,
                outputFormat = OutputFormat.Mp4Audio,
                audioCodec = AudioCodec.Alac
            ),
            prepared = prepared("/tmp/output.mp4")
        )

        assertEquals("mp4", plan.outputExtension)
        assertTrue(plan.arguments.containsAll(listOf("-vn", "-c:a", "alac")))
        assertFalse(plan.arguments.contains("-b:a"))
    }

    @Test
    fun formatCompatibilityFiltersCodecsByContainer() {
        assertEquals(
            listOf(VideoCodec.Vp9, VideoCodec.Vp8, VideoCodec.Av1, VideoCodec.Copy),
            compatibleVideoCodecs(OutputFormat.Webm)
        )
        assertEquals(listOf(AudioCodec.Opus, AudioCodec.Vorbis, AudioCodec.Copy), compatibleAudioCodecs(OutputFormat.Webm))
        assertEquals(listOf(AudioCodec.PcmS16le), compatibleAudioCodecs(OutputFormat.Wav))
        assertTrue(OutputFormat.Mp4 in compatibleOutputFormats(FormatConversionMode.Video))
        assertTrue(OutputFormat.Mp4Audio in compatibleOutputFormats(FormatConversionMode.Audio))
        assertTrue(OutputFormat.Mp3 in compatibleOutputFormats(FormatConversionMode.Audio))
        assertTrue(OutputFormat.Png in compatibleOutputFormats(FormatConversionMode.Image))
    }

    @Test
    fun probeBuildsFfprobeStyleArgumentsWithoutOutput() {
        val plan = builder.buildPreset(
            preset = OperationPreset.Probe,
            settings = PresetSettings(),
            prepared = PreparedCommandInputs(
                inputs = listOf(PreparedMediaFile("/tmp/input.mp4", "input.mp4"))
            )
        )

        assertEquals(null, plan.outputExtension)
        assertFalse(plan.requiresOutput)
        assertTrue(plan.arguments.containsAll(listOf("-show_format", "-show_streams", "/tmp/input.mp4")))
        assertFalse(plan.arguments.contains("-i"))
    }

    private fun prepared(outputPath: String): PreparedCommandInputs =
        PreparedCommandInputs(
            inputs = listOf(PreparedMediaFile("/tmp/input.mp4", "input.mp4")),
            outputPath = outputPath
        )

    private data class PresetCase(
        val preset: OperationPreset,
        val expectedExtension: String,
        val expectedTokens: List<String>,
        val settings: PresetSettings = PresetSettings()
    )
}
