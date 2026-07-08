package com.fruit.ffmpeggui.ffmpeg

import com.fruit.ffmpeggui.core.CommandTokenizer
import com.fruit.ffmpeggui.core.FfmpegExecutionResult
import com.fruit.ffmpeggui.core.JobStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

class ReflectiveFfmpegRunner : FfmpegRunner {
    private val binding: Binding? by lazy { resolveBinding() }
    private val activeSessionId = AtomicReference<Long?>()

    override val isAvailable: Boolean
        get() = binding != null

    override val engineName: String
        get() = binding?.packageName ?: "FFmpegKitNext"

    override suspend fun execute(
        arguments: List<String>,
        onLog: (String) -> Unit,
        onProgressTime: (Long) -> Unit
    ): FfmpegExecutionResult {
        val resolved = binding ?: return unavailableResult()
        return runKit(
            kitClass = resolved.ffmpegKitClass,
            packageName = resolved.packageName,
            completeCallbackName = "FFmpegSessionCompleteCallback",
            arguments = arguments,
            onLog = onLog,
            onProgressTime = onProgressTime,
            preferAsync = true
        )
    }

    override suspend fun probe(
        arguments: List<String>,
        onLog: (String) -> Unit
    ): FfmpegExecutionResult {
        val resolved = binding ?: return unavailableResult()
        val hasProbeKit = resolved.ffprobeKitClass != null
        val kitClass = resolved.ffprobeKitClass ?: resolved.ffmpegKitClass
        val completeName = if (hasProbeKit) "FFprobeSessionCompleteCallback" else "FFmpegSessionCompleteCallback"
        val probeArguments = if (hasProbeKit) arguments else ffmpegProbeFallbackArguments(arguments)
        return runKit(
            kitClass = kitClass,
            packageName = resolved.packageName,
            completeCallbackName = completeName,
            arguments = probeArguments,
            onLog = onLog,
            onProgressTime = {},
            preferAsync = !hasProbeKit
        )
    }

    override fun cancel() {
        val resolved = binding ?: return
        val kitClass = resolved.ffmpegKitClass
        val sessionId = activeSessionId.get()
        runCatching {
            val cancelWithId = kitClass.methods.firstOrNull {
                it.name == "cancel" && Modifier.isStatic(it.modifiers) && it.parameterTypes.size == 1
            }
            if (sessionId != null && cancelWithId != null) {
                cancelWithId.invoke(null, sessionId)
            } else {
                kitClass.methods.firstOrNull {
                    it.name == "cancel" && Modifier.isStatic(it.modifiers) && it.parameterTypes.isEmpty()
                }?.invoke(null)
            }
        }
    }

    private suspend fun runKit(
        kitClass: Class<*>,
        packageName: String,
        completeCallbackName: String,
        arguments: List<String>,
        onLog: (String) -> Unit,
        onProgressTime: (Long) -> Unit,
        preferAsync: Boolean
    ): FfmpegExecutionResult {
        if (preferAsync) {
            val asyncResult = runCatching {
                runAsync(
                    kitClass = kitClass,
                    packageName = packageName,
                    completeCallbackName = completeCallbackName,
                    arguments = arguments,
                    onLog = onLog,
                    onProgressTime = onProgressTime
                )
            }
            if (asyncResult.isSuccess) {
                return asyncResult.getOrThrow()
            }
            val asyncFailure = asyncResult.exceptionOrNull()
            if (asyncFailure != null && !asyncFailure.isMissingAsyncApi()) {
                return exceptionResult(asyncFailure, onLog)
            }
        }
        return runCatching {
            runSync(kitClass, arguments, onLog)
        }.getOrElse { throwable ->
            exceptionResult(throwable, onLog)
        }
    }

    private suspend fun runAsync(
        kitClass: Class<*>,
        packageName: String,
        completeCallbackName: String,
        arguments: List<String>,
        onLog: (String) -> Unit,
        onProgressTime: (Long) -> Unit
    ): FfmpegExecutionResult = suspendCancellableCoroutine { continuation ->
        val completeCallbackClass = Class.forName("$packageName.$completeCallbackName")
        val logCallbackClass = Class.forName("$packageName.LogCallback")
        val statisticsCallbackClass = runCatching {
            Class.forName("$packageName.StatisticsCallback")
        }.getOrNull()
        val logs = StringBuilder()

        val completeProxy = callbackProxy(completeCallbackClass) { session ->
            activeSessionId.set(null)
            val result = buildResult(session, logs.toString(), packageName)
            if (continuation.isActive) {
                continuation.resume(result)
            }
        }
        val logProxy = callbackProxy(logCallbackClass) { log ->
            val message = log.callString("getMessage").orEmpty()
            if (message.isNotBlank()) {
                logs.append(message)
                onLog(message.trimEnd())
            }
        }
        val statisticsProxy = statisticsCallbackClass?.let {
            callbackProxy(it) { statistics ->
                statistics.callLong("getTime")?.let(onProgressTime)
            }
        }

        val method = findAsyncMethod(
            kitClass = kitClass,
            completeCallbackClass = completeCallbackClass,
            logCallbackClass = logCallbackClass,
            statisticsCallbackClass = statisticsCallbackClass,
            withStatistics = statisticsProxy != null
        )
        val session = if (statisticsProxy != null && method.parameterTypes.size == 4) {
            method.invokeStatic(arguments.toTypedArray(), completeProxy, logProxy, statisticsProxy)
        } else {
            method.invokeStatic(arguments.toTypedArray(), completeProxy, logProxy)
        }
        activeSessionId.set(session.callLong("getSessionId"))
        continuation.invokeOnCancellation { cancel() }
    }

    private suspend fun runSync(
        kitClass: Class<*>,
        arguments: List<String>,
        onLog: (String) -> Unit
    ): FfmpegExecutionResult = withContext(Dispatchers.IO) {
        val session = invokeSync(kitClass, arguments)
        val logs = session.callString("getAllLogsAsString")
            ?: session.callString("getOutput")
            ?: ""
        if (logs.isNotBlank()) {
            onLog(logs.trimEnd())
        }
        val packageName = kitClass.name.substringBeforeLast('.')
        buildResult(session, logs, packageName)
    }

    private fun findAsyncMethod(
        kitClass: Class<*>,
        completeCallbackClass: Class<*>,
        logCallbackClass: Class<*>,
        statisticsCallbackClass: Class<*>?,
        withStatistics: Boolean
    ): Method {
        if (withStatistics && statisticsCallbackClass != null) {
            kitClass.methods.firstOrNull {
                it.name == "executeWithArgumentsAsync" &&
                    Modifier.isStatic(it.modifiers) &&
                    it.parameterTypes.contentEquals(
                        arrayOf(
                            Array<String>::class.java,
                            completeCallbackClass,
                            logCallbackClass,
                            statisticsCallbackClass
                        )
                    )
            }?.let { return it }
        }

        return kitClass.methods.firstOrNull {
            it.name == "executeWithArgumentsAsync" &&
                Modifier.isStatic(it.modifiers) &&
                it.parameterTypes.contentEquals(
                    arrayOf(Array<String>::class.java, completeCallbackClass, logCallbackClass)
                )
        } ?: error("FFmpegKit async argument API is not available.")
    }

    private fun invokeSync(kitClass: Class<*>, arguments: List<String>): Any {
        kitClass.methods.firstOrNull {
            it.name == "executeWithArguments" &&
                Modifier.isStatic(it.modifiers) &&
                it.parameterTypes.contentEquals(arrayOf(Array<String>::class.java))
        }?.let { method ->
            return method.invokeStatic(arguments.toTypedArray())
                ?: error("FFmpegKit returned a null session.")
        }

        kitClass.methods.firstOrNull {
            it.name == "execute" &&
                Modifier.isStatic(it.modifiers) &&
                it.parameterTypes.contentEquals(arrayOf(String::class.java))
        }?.let { method ->
            return method.invokeStatic(CommandTokenizer.quote(arguments))
                ?: error("FFmpegKit returned a null session.")
        }

        error("FFmpegKit execute API is not available.")
    }

    private fun buildResult(session: Any?, logs: String, packageName: String): FfmpegExecutionResult {
        if (session == null) {
            return FfmpegExecutionResult(
                status = JobStatus.Failed,
                returnCode = null,
                logs = logs,
                failureMessage = "No FFmpeg session was returned."
            )
        }
        val returnCode = session.call("getReturnCode")
        val failStack = session.callString("getFailStackTrace")
        val status = when {
            returnCode != null && callReturnCodeBoolean(packageName, "isSuccess", returnCode) -> {
                JobStatus.Success
            }
            returnCode != null && callReturnCodeBoolean(packageName, "isCancel", returnCode) -> {
                JobStatus.Cancelled
            }
            else -> JobStatus.Failed
        }
        return FfmpegExecutionResult(
            status = status,
            returnCode = returnCode?.toString(),
            logs = logs,
            failureMessage = failStack?.takeIf { it.isNotBlank() }
        )
    }

    private fun callbackProxy(interfaceClass: Class<*>, onCallback: (Any?) -> Unit): Any =
        Proxy.newProxyInstance(interfaceClass.classLoader, arrayOf(interfaceClass)) { proxy, method, args ->
            when (method.name) {
                "toString" -> "CodecNestCallbackProxy(${interfaceClass.simpleName})"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> {
                    onCallback(args?.firstOrNull())
                    null
                }
            }
        }

    private fun callReturnCodeBoolean(
        packageName: String,
        methodName: String,
        returnCode: Any
    ): Boolean {
        val returnCodeClass = runCatching { Class.forName("$packageName.ReturnCode") }.getOrNull()
        val staticValue = returnCodeClass?.methods?.firstOrNull {
            it.name == methodName && Modifier.isStatic(it.modifiers) && it.parameterTypes.size == 1
        }?.let { runCatching { it.invoke(null, returnCode) as? Boolean }.getOrNull() }
        if (staticValue != null) return staticValue

        val instanceMethod = when (methodName) {
            "isSuccess" -> "isValueSuccess"
            "isCancel" -> "isValueCancel"
            else -> methodName
        }
        return returnCode.call(instanceMethod) as? Boolean ?: false
    }

    private fun resolveBinding(): Binding? {
        val packageCandidates = listOf("com.arthenica.ffmpegkit", "org.ffmpegkit")
        return packageCandidates.firstNotNullOfOrNull { packageName ->
            val ffmpegKit = runCatching { Class.forName("$packageName.FFmpegKit") }.getOrNull()
            if (ffmpegKit == null) {
                null
            } else {
                Binding(
                    packageName = packageName,
                    ffmpegKitClass = ffmpegKit,
                    ffprobeKitClass = runCatching { Class.forName("$packageName.FFprobeKit") }.getOrNull()
                )
            }
        }
    }

    private fun unavailableResult(): FfmpegExecutionResult = FfmpegExecutionResult(
        status = JobStatus.Failed,
        returnCode = null,
        logs = "",
        failureMessage = "FFmpegKitNext engine is not installed. Copy ffmpeg-kit-next.aar to app/libs/."
    )

    private fun ffmpegProbeFallbackArguments(arguments: List<String>): List<String> {
        val input = arguments.lastOrNull { !it.startsWith("-") } ?: return arguments
        return listOf("-hide_banner", "-i", input, "-t", "0.1", "-map", "0", "-f", "null", "-")
    }

    private fun exceptionResult(
        throwable: Throwable,
        onLog: (String) -> Unit
    ): FfmpegExecutionResult {
        val root = throwable.unwrapReflection()
        val message = root.describeForUser()
        onLog("FFmpegKit failed to start: $message")
        return FfmpegExecutionResult(
            status = JobStatus.Failed,
            returnCode = null,
            logs = "",
            failureMessage = message
        )
    }

    private fun Throwable.isMissingAsyncApi(): Boolean =
        this is IllegalStateException && message == "FFmpegKit async argument API is not available."

    private fun Method.invokeStatic(vararg args: Any?): Any? =
        try {
            invoke(null, *args)
        } catch (exception: InvocationTargetException) {
            throw exception.targetException ?: exception
        }

    private fun Throwable.unwrapReflection(): Throwable {
        var current = this
        while (true) {
            current = when (current) {
                is InvocationTargetException -> current.targetException ?: return current
                is ExceptionInInitializerError -> current.exception ?: current.cause ?: return current
                else -> current.cause?.takeIf {
                    current is Error && current.message?.startsWith("FFmpegKit failed to start") == true
                } ?: return current
            }
        }
    }

    private fun Throwable.describeForUser(): String {
        val className = this::class.java.simpleName
        val detail = message?.takeIf { it.isNotBlank() }
        return if (detail == null) className else "$className: $detail"
    }

    private fun Any?.call(name: String): Any? =
        this?.javaClass?.methods?.firstOrNull { it.name == name && it.parameterTypes.isEmpty() }
            ?.let { method -> runCatching { method.invoke(this) }.getOrNull() }

    private fun Any?.callString(name: String): String? = call(name)?.toString()

    private fun Any?.callLong(name: String): Long? {
        val value = call(name) ?: return null
        return when (value) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }
    }

    private data class Binding(
        val packageName: String,
        val ffmpegKitClass: Class<*>,
        val ffprobeKitClass: Class<*>?
    )
}
