package com.fruit.ffmpeggui.ffmpeg

import com.fruit.ffmpeggui.core.FfmpegExecutionResult

interface FfmpegRunner {
    val isAvailable: Boolean
    val engineName: String

    suspend fun execute(
        arguments: List<String>,
        onLog: (String) -> Unit,
        onProgressTime: (Long) -> Unit
    ): FfmpegExecutionResult

    suspend fun probe(
        arguments: List<String>,
        onLog: (String) -> Unit
    ): FfmpegExecutionResult

    fun cancel()
}
