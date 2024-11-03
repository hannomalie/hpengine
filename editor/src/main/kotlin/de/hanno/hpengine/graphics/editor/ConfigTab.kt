package de.hanno.hpengine.graphics.editor

import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.imgui.dsl.TabBar
import de.hanno.hpengine.graphics.window.Window
import imgui.ImGui
import imgui.type.ImInt
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.config.Configurator
import kotlin.reflect.KMutableProperty0

// TODO: global variable is probably not the best idea
private val logLevelInt = ImInt()
private val logLevels = listOf(
    Level.OFF,
    Level.FATAL,
    Level.ERROR,
    Level.WARN,
    Level.INFO,
    Level.DEBUG,
    Level.TRACE,
    Level.ALL
)

fun TabBar.configTab(config: Config, window: Window) {
    tab("Config") {

        if (ImGui.beginCombo("Log level", config.logLevel.toString())) {
            logLevels.forEach {
                val selected = config.logLevel == it
                if (ImGui.selectable(it.toString(), selected)) {
                    config.logLevel = it
                    Configurator.setRootLevel(it)
                }
                if (selected) {
                    ImGui.setItemDefaultFocus()
                }
            }
            ImGui.endCombo()
        }

        if (ImGui.checkbox("Draw lines", config.debug.isDrawLines)) {
            config.debug.isDrawLines = !config.debug.isDrawLines
        }
        if (ImGui.checkbox("Draw indirect", config.performance.isIndirectRendering)) {
            config.performance.isIndirectRendering = !config.performance.isIndirectRendering
        }
        if (ImGui.checkbox(
                "Force singlethreaded rendering",
                config.debug.forceSingleThreadedRendering
            )
        ) {
            config.debug.forceSingleThreadedRendering = !config.debug.forceSingleThreadedRendering
        }
        if (ImGui.checkbox("Use cpu frustum culling", config.debug.isUseCpuFrustumCulling)) {
            config.debug.isUseCpuFrustumCulling = !config.debug.isUseCpuFrustumCulling
        }
        if (ImGui.checkbox("Use gpu frustum culling", config.debug.isUseGpuFrustumCulling)) {
            config.debug.isUseGpuFrustumCulling = !config.debug.isUseGpuFrustumCulling
        }
        if (ImGui.checkbox("Use gpu occlusion culling", config.debug.isUseGpuOcclusionCulling)) {
            config.debug.isUseGpuOcclusionCulling = !config.debug.isUseGpuOcclusionCulling
        }
        if (ImGui.button("Freeze culling")) {
            config.debug.freezeCulling = !config.debug.freezeCulling
        }
        if (ImGui.checkbox("Print pipeline output", config.debug.isPrintPipelineDebugOutput)) {
            config.debug.isPrintPipelineDebugOutput = !config.debug.isPrintPipelineDebugOutput
        }
        if (ImGui.checkbox("GPU Profiling", config.debug.profiling)) {
            config.debug.profiling = !config.debug.profiling
        }
        if (ImGui.checkbox("VSync", window.vSync)) {
            window.vSync = !window.vSync
        }

        checkbox("Draw bounding volumes", config.debug::drawBoundingVolumes)
    }
}

fun checkbox(name: String, property: KMutableProperty0<Boolean>) {
    if (ImGui.checkbox(name, property.get())) {
        property.set(!property.get())
    }
}
