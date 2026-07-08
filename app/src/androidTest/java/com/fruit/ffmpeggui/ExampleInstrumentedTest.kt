package com.fruit.ffmpeggui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fruit.ffmpeggui.core.JobStatus
import com.fruit.ffmpeggui.ffmpeg.ReflectiveFfmpegRunner
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.fruit.ffmpeggui", appContext.packageName)
    }

    @Test
    fun ffmpegKitVersionRunsOnDevice() = runBlocking {
        val logs = logList()
        val result = ReflectiveFfmpegRunner().execute(
            arguments = listOf("-version"),
            onLog = logs::add,
            onProgressTime = {}
        )

        assertEquals(logs.joinToString("\n") + "\n" + result.failureMessage, JobStatus.Success, result.status)
    }

    @Test
    fun ffmpegKitEncodesMp3OnDevice() = runBlocking {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(appContext.cacheDir, "ffmpegkit-smoke.mp3").apply { delete() }
        val logs = logList()
        val result = runFfmpeg(
            output = output,
            logs = logs,
            arguments = listOf(
                "-y", "-f", "lavfi", "-i", "sine=frequency=1000", "-t", "0.2",
                "-c:a", "libmp3lame", "-b:a", "128k", output.absolutePath
            )
        )

        assertEquals(logs.joinToString("\n") + "\n" + result.failureMessage, JobStatus.Success, result.status)
        assertTrue("MP3 output was not created", output.length() > 0L)
    }

    @Test
    fun ffmpegKitEncodesH264Mp4OnDevice() = runBlocking {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(appContext.cacheDir, "ffmpegkit-smoke.mp4").apply { delete() }
        val logs = logList()
        val result = runFfmpeg(
            output = output,
            logs = logs,
            arguments = listOf(
                "-y", "-f", "lavfi", "-i", "testsrc=size=16x16:rate=1", "-t", "0.2",
                "-c:v", "libx264", "-pix_fmt", "yuv420p", output.absolutePath
            )
        )

        assertEquals(logs.joinToString("\n") + "\n" + result.failureMessage, JobStatus.Success, result.status)
        assertTrue("MP4 output was not created", output.length() > 0L)
    }

    @Test
    fun ffmpegKitEncodesFlacOnDevice() = runBlocking {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(appContext.cacheDir, "ffmpegkit-smoke.flac").apply { delete() }
        val logs = logList()
        val result = runFfmpeg(
            output = output,
            logs = logs,
            arguments = listOf(
                "-y", "-f", "lavfi", "-i", "sine=frequency=440", "-t", "0.2",
                "-c:a", "flac", output.absolutePath
            )
        )

        assertEquals(logs.joinToString("\n") + "\n" + result.failureMessage, JobStatus.Success, result.status)
        assertTrue("FLAC output was not created", output.length() > 0L)
    }

    @Test
    fun ffmpegKitExportsPngFrameOnDevice() = runBlocking {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(appContext.cacheDir, "ffmpegkit-smoke.png").apply { delete() }
        val logs = logList()
        val result = runFfmpeg(
            output = output,
            logs = logs,
            arguments = listOf(
                "-y", "-f", "lavfi", "-i", "color=c=red:size=16x16", "-t", "0.1",
                "-frames:v", "1", output.absolutePath
            )
        )

        assertEquals(logs.joinToString("\n") + "\n" + result.failureMessage, JobStatus.Success, result.status)
        assertTrue("PNG output was not created", output.length() > 0L)
    }

    @Test
    fun ffmpegKitProbesGeneratedMediaOnDevice() = runBlocking {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(appContext.cacheDir, "ffmpegkit-probe-source.mp3").apply { delete() }
        val encodeLogs = logList()
        val encodeResult = runFfmpeg(
            output = output,
            logs = encodeLogs,
            arguments = listOf(
                "-y", "-f", "lavfi", "-i", "sine=frequency=500", "-t", "0.1",
                "-c:a", "libmp3lame", "-b:a", "96k", output.absolutePath
            )
        )
        assertEquals(
            encodeLogs.joinToString("\n") + "\n" + encodeResult.failureMessage,
            JobStatus.Success,
            encodeResult.status
        )

        val probeLogs = logList()
        val probeResult = withTimeout(30_000) {
            ReflectiveFfmpegRunner().probe(
                arguments = listOf("-hide_banner", "-show_format", "-show_streams", output.absolutePath),
                onLog = probeLogs::add
            )
        }

        assertEquals(probeLogs.joinToString("\n") + "\n" + probeResult.failureMessage, JobStatus.Success, probeResult.status)
        assertTrue("Probe did not emit media information", probeLogs.joinToString("\n").contains("mp3", ignoreCase = true))
    }

    private suspend fun runFfmpeg(
        output: File,
        logs: MutableList<String>,
        arguments: List<String>
    ) = withTimeout(30_000) {
        ReflectiveFfmpegRunner().execute(
            arguments = arguments,
            onLog = logs::add,
            onProgressTime = {}
        )
    }.also {
        if (!it.succeeded) {
            output.delete()
        }
    }

    private fun logList(): MutableList<String> = CopyOnWriteArrayList()
}
