package de.hanno.hpengine.graphics.imgui.editor

import de.hanno.hpengine.config.ConfigImpl
import de.hanno.hpengine.graphics.imgui.dsl.TabBar
import de.hanno.hpengine.graphics.Window
import imgui.ImGui

fun TabBar.configTab(config: ConfigImpl, window: Window<*>) {
    tab("Config") {
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
        if (ImGui.checkbox("Editor", config.debug.isEditorOverlay)) {
            config.debug.isEditorOverlay = !config.debug.isEditorOverlay
        }
        if (ImGui.checkbox("GPU Profiling", de.hanno.hpengine.stopwatch.GPUProfiler.profiling)) {
            de.hanno.hpengine.stopwatch.GPUProfiler.profiling = !de.hanno.hpengine.stopwatch.GPUProfiler.profiling
        }
        if (ImGui.checkbox("VSync", window.vSync)) {
            window.vSync = !window.vSync
        }
    }
}