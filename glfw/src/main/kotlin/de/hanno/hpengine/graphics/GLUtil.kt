/*
 * Copyright LWJGL. All rights reserved.
 * License terms: https://www.lwjgl.org/license
 */

import org.lwjgl.opengl.*
import org.lwjgl.system.APIUtil
import org.lwjgl.system.Callback
import org.lwjgl.system.MemoryUtil
import java.io.PrintStream
import java.util.*


/** OpenGL utilities.  */
object GLUtil {
    // TODO: Support NONE
    enum class Severity(val glValue: Int) {
        HIGH(GL43C.GL_DEBUG_SEVERITY_HIGH),
        MEDIUM(GL43C.GL_DEBUG_SEVERITY_MEDIUM),
        LOW(GL43C.GL_DEBUG_SEVERITY_LOW),
        NOTIFICATION(GL43C.GL_DEBUG_SEVERITY_NOTIFICATION),
    }
    /**
     * Detects the best debug output functionality to use and creates a callback that prints information to the specified [PrintStream]. The callback
     * function is returned as a [Callback], that should be [freed][Callback.free] when no longer needed.
     *
     * @param stream the output [PrintStream]
     */
    /**
     * Detects the best debug output functionality to use and creates a callback that prints information to [APIUtil.DEBUG_STREAM]. The callback
     * function is returned as a [Callback], that should be [freed][Callback.free] when no longer needed.
     */
    fun setupDebugMessageCallback(stream: PrintStream = APIUtil.DEBUG_STREAM, logLevel: Severity): Callback? {
        val caps = GL.getCapabilities()
        if (caps.OpenGL43) {
            APIUtil.apiLog("[GL] Using OpenGL 4.3 for error logging.")
            val proc =
                GLDebugMessageCallback.create { source: Int, type: Int, id: Int, severity: Int, length: Int, message: Long, userParam: Long ->
                    if(logLevel.logLevelMatched(severity)) {
                        val sb = StringBuilder(300)
                        sb.append("[LWJGL] OpenGL debug message\n")
                        printDetail(
                            sb,
                            "ID",
                            "0x" + Integer.toHexString(id).uppercase(Locale.getDefault())
                        )
                        printDetail(sb, "Source", getDebugSource(source))
                        printDetail(sb, "Type", getDebugType(type))
                        printDetail(sb, "Severity", getDebugSeverity(severity))
                        printDetail(sb, "Message", GLDebugMessageCallback.getMessage(length, message))
                        stream.print(sb)
                    }
                }
            GL43C.glDebugMessageCallback(proc, MemoryUtil.NULL)
            if (GL11C.glGetInteger(GL30C.GL_CONTEXT_FLAGS) and GL43C.GL_CONTEXT_FLAG_DEBUG_BIT == 0) {
                APIUtil.apiLog("[GL] Warning: A non-debug context may not produce any debug output.")
                GL11C.glEnable(GL43C.GL_DEBUG_OUTPUT)
            }
            return proc
        }
        if (caps.GL_KHR_debug) {
            APIUtil.apiLog("[GL] Using KHR_debug for error logging.")
            val proc =
                GLDebugMessageCallback.create { source: Int, type: Int, id: Int, severity: Int, length: Int, message: Long, userParam: Long ->
                    val sb = StringBuilder(300)
                    sb.append("[LWJGL] OpenGL debug message\n")
                    printDetail(
                        sb,
                        "ID",
                        "0x" + Integer.toHexString(id).uppercase(Locale.getDefault())
                    )
                    printDetail(sb, "Source", getDebugSource(source))
                    printDetail(sb, "Type", getDebugType(type))
                    printDetail(sb, "Severity", getDebugSeverity(severity))
                    printDetail(sb, "Message", GLDebugMessageCallback.getMessage(length, message))
                    stream.print(sb)
                }
            KHRDebug.glDebugMessageCallback(proc, MemoryUtil.NULL)
            if (caps.OpenGL30 && GL11C.glGetInteger(GL30C.GL_CONTEXT_FLAGS) and GL43C.GL_CONTEXT_FLAG_DEBUG_BIT == 0) {
                APIUtil.apiLog("[GL] Warning: A non-debug context may not produce any debug output.")
                GL11C.glEnable(GL43C.GL_DEBUG_OUTPUT)
            }
            return proc
        }
        if (caps.GL_ARB_debug_output) {
            APIUtil.apiLog("[GL] Using ARB_debug_output for error logging.")
            val proc =
                GLDebugMessageARBCallback.create { source: Int, type: Int, id: Int, severity: Int, length: Int, message: Long, userParam: Long ->
                    val sb = StringBuilder(300)
                    sb.append("[LWJGL] ARB_debug_output message\n")
                    printDetail(
                        sb,
                        "ID",
                        "0x" + Integer.toHexString(id).uppercase(Locale.getDefault())
                    )
                    printDetail(sb, "Source", getSourceARB(source))
                    printDetail(sb, "Type", getTypeARB(type))
                    printDetail(sb, "Severity", getSeverityARB(severity))
                    printDetail(
                        sb,
                        "Message",
                        GLDebugMessageARBCallback.getMessage(length, message)
                    )
                    stream.print(sb)
                }
            ARBDebugOutput.glDebugMessageCallbackARB(proc, MemoryUtil.NULL)
            return proc
        }
        if (caps.GL_AMD_debug_output) {
            APIUtil.apiLog("[GL] Using AMD_debug_output for error logging.")
            val proc =
                GLDebugMessageAMDCallback.create { id: Int, category: Int, severity: Int, length: Int, message: Long, userParam: Long ->
                    val sb = StringBuilder(300)
                    sb.append("[LWJGL] AMD_debug_output message\n")
                    printDetail(
                        sb,
                        "ID",
                        "0x" + Integer.toHexString(id).uppercase(Locale.getDefault())
                    )
                    printDetail(sb, "Category", getCategoryAMD(category))
                    printDetail(sb, "Severity", getSeverityAMD(severity))
                    printDetail(
                        sb,
                        "Message",
                        GLDebugMessageAMDCallback.getMessage(length, message)
                    )
                    stream.print(sb)
                }
            AMDDebugOutput.glDebugMessageCallbackAMD(proc, MemoryUtil.NULL)
            return proc
        }
        APIUtil.apiLog("[GL] No debug output implementation is available.")
        return null
    }

    private fun printDetail(sb: StringBuilder, type: String, message: String) {
        sb
            .append("\t")
            .append(type)
            .append(": ")
            .append(message)
            .append("\n")
    }

    private fun getDebugSource(source: Int): String {
        return when (source) {
            GL43C.GL_DEBUG_SOURCE_API -> "API"
            GL43C.GL_DEBUG_SOURCE_WINDOW_SYSTEM -> "WINDOW SYSTEM"
            GL43C.GL_DEBUG_SOURCE_SHADER_COMPILER -> "SHADER COMPILER"
            GL43C.GL_DEBUG_SOURCE_THIRD_PARTY -> "THIRD PARTY"
            GL43C.GL_DEBUG_SOURCE_APPLICATION -> "APPLICATION"
            GL43C.GL_DEBUG_SOURCE_OTHER -> "OTHER"
            else -> APIUtil.apiUnknownToken(source)
        }
    }

    private fun getDebugType(type: Int): String {
        return when (type) {
            GL43C.GL_DEBUG_TYPE_ERROR -> "ERROR"
            GL43C.GL_DEBUG_TYPE_DEPRECATED_BEHAVIOR -> "DEPRECATED BEHAVIOR"
            GL43C.GL_DEBUG_TYPE_UNDEFINED_BEHAVIOR -> "UNDEFINED BEHAVIOR"
            GL43C.GL_DEBUG_TYPE_PORTABILITY -> "PORTABILITY"
            GL43C.GL_DEBUG_TYPE_PERFORMANCE -> "PERFORMANCE"
            GL43C.GL_DEBUG_TYPE_OTHER -> "OTHER"
            GL43C.GL_DEBUG_TYPE_MARKER -> "MARKER"
            else -> APIUtil.apiUnknownToken(type)
        }
    }

    private fun getDebugSeverity(severity: Int): String {
        return when (severity) {
            GL43C.GL_DEBUG_SEVERITY_HIGH -> "HIGH"
            GL43C.GL_DEBUG_SEVERITY_MEDIUM -> "MEDIUM"
            GL43C.GL_DEBUG_SEVERITY_LOW -> "LOW"
            GL43C.GL_DEBUG_SEVERITY_NOTIFICATION -> "NOTIFICATION"
            else -> APIUtil.apiUnknownToken(severity)
        }
    }
    private fun Severity.logLevelMatched(level: Int) = level > glValue

    private fun getSourceARB(source: Int): String {
        return when (source) {
            ARBDebugOutput.GL_DEBUG_SOURCE_API_ARB -> "API"
            ARBDebugOutput.GL_DEBUG_SOURCE_WINDOW_SYSTEM_ARB -> "WINDOW SYSTEM"
            ARBDebugOutput.GL_DEBUG_SOURCE_SHADER_COMPILER_ARB -> "SHADER COMPILER"
            ARBDebugOutput.GL_DEBUG_SOURCE_THIRD_PARTY_ARB -> "THIRD PARTY"
            ARBDebugOutput.GL_DEBUG_SOURCE_APPLICATION_ARB -> "APPLICATION"
            ARBDebugOutput.GL_DEBUG_SOURCE_OTHER_ARB -> "OTHER"
            else -> APIUtil.apiUnknownToken(source)
        }
    }

    private fun getTypeARB(type: Int): String {
        return when (type) {
            ARBDebugOutput.GL_DEBUG_TYPE_ERROR_ARB -> "ERROR"
            ARBDebugOutput.GL_DEBUG_TYPE_DEPRECATED_BEHAVIOR_ARB -> "DEPRECATED BEHAVIOR"
            ARBDebugOutput.GL_DEBUG_TYPE_UNDEFINED_BEHAVIOR_ARB -> "UNDEFINED BEHAVIOR"
            ARBDebugOutput.GL_DEBUG_TYPE_PORTABILITY_ARB -> "PORTABILITY"
            ARBDebugOutput.GL_DEBUG_TYPE_PERFORMANCE_ARB -> "PERFORMANCE"
            ARBDebugOutput.GL_DEBUG_TYPE_OTHER_ARB -> "OTHER"
            else -> APIUtil.apiUnknownToken(type)
        }
    }

    private fun getSeverityARB(severity: Int): String {
        return when (severity) {
            ARBDebugOutput.GL_DEBUG_SEVERITY_HIGH_ARB -> "HIGH"
            ARBDebugOutput.GL_DEBUG_SEVERITY_MEDIUM_ARB -> "MEDIUM"
            ARBDebugOutput.GL_DEBUG_SEVERITY_LOW_ARB -> "LOW"
            else -> APIUtil.apiUnknownToken(severity)
        }
    }

    private fun getCategoryAMD(category: Int): String {
        return when (category) {
            AMDDebugOutput.GL_DEBUG_CATEGORY_API_ERROR_AMD -> "API ERROR"
            AMDDebugOutput.GL_DEBUG_CATEGORY_WINDOW_SYSTEM_AMD -> "WINDOW SYSTEM"
            AMDDebugOutput.GL_DEBUG_CATEGORY_DEPRECATION_AMD -> "DEPRECATION"
            AMDDebugOutput.GL_DEBUG_CATEGORY_UNDEFINED_BEHAVIOR_AMD -> "UNDEFINED BEHAVIOR"
            AMDDebugOutput.GL_DEBUG_CATEGORY_PERFORMANCE_AMD -> "PERFORMANCE"
            AMDDebugOutput.GL_DEBUG_CATEGORY_SHADER_COMPILER_AMD -> "SHADER COMPILER"
            AMDDebugOutput.GL_DEBUG_CATEGORY_APPLICATION_AMD -> "APPLICATION"
            AMDDebugOutput.GL_DEBUG_CATEGORY_OTHER_AMD -> "OTHER"
            else -> APIUtil.apiUnknownToken(category)
        }
    }

    private fun getSeverityAMD(severity: Int): String {
        return when (severity) {
            AMDDebugOutput.GL_DEBUG_SEVERITY_HIGH_AMD -> "HIGH"
            AMDDebugOutput.GL_DEBUG_SEVERITY_MEDIUM_AMD -> "MEDIUM"
            AMDDebugOutput.GL_DEBUG_SEVERITY_LOW_AMD -> "LOW"
            else -> APIUtil.apiUnknownToken(severity)
        }
    }
}