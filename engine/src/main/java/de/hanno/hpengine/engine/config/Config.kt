package de.hanno.hpengine.engine.config

import de.hanno.hpengine.engine.directory.Directories
import de.hanno.hpengine.engine.directory.Directories.Companion.ENGINEDIR_NAME
import de.hanno.hpengine.engine.directory.Directories.Companion.GAMEDIR_NAME
import de.hanno.hpengine.engine.directory.EngineAsset
import de.hanno.hpengine.engine.directory.EngineDirectory
import de.hanno.hpengine.engine.directory.GameAsset
import de.hanno.hpengine.engine.directory.GameDirectory
import de.hanno.hpengine.util.gui.Adjustable
import de.hanno.hpengine.util.stopwatch.GPUProfiler
import org.joml.Vector3f
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty

interface Config {
    val quality: IQualityConfig
    val debug: IDebugConfig
    val effects: IEffectsConfig
    val performance: IPerformanceConfig
    val profiling: ProfilingConfig

    val directories: Directories
    val engineDir: EngineDirectory
    val gameDir: GameDirectory
    val width: Int
    val height: Int

    fun EngineAsset(relativePath: String): EngineAsset = engineDir.toAsset(relativePath)
    fun GameAsset(relativePath: String): GameAsset = gameDir.toAsset(relativePath)
}

interface IQualityConfig {
    val isUseAmbientOcclusion: Boolean
    val isUseParallax: Boolean
    val isUseSteepParallax: Boolean
    val isUseGi: Boolean
    val isUseSSR: Boolean
    val isUseMultipleDiffuseSamples: Boolean
    val isUseMultipleDiffuseSamplesProbes: Boolean
    val isUseConetracingForDiffuse: Boolean
    val isUseConetracingForDiffuseProbes: Boolean
    val isUseConetracingForSpecular: Boolean
    val isUseConetracingForSpecularProbes: Boolean
    val isUsePrecomputedRadiance: Boolean
    val isCalculateActualRadiance: Boolean
    val isSsrFadeToScreenBorders: Boolean
    val isSsrTemporalFiltering: Boolean
    val isContinuousDrawProbes: Boolean
    val isDrawProbes: Boolean
    val isUseDpsm: Boolean
    val isUsePcf: Boolean
}

interface IDebugConfig {
    var reRenderProbes: Boolean
    var visualizeProbes: Boolean
    var drawBvhInnerNodes: Boolean
    var isEditorOverlay: Boolean
    val isUseGpuOcclusionCulling: Boolean
    val isDrawLines: Boolean
    val isDrawBoundingVolumes: Boolean
    val isDrawPointLightShadowMaps: Boolean
    val isDrawCameras: Boolean
    val isDrawScene: Boolean
    val isDebugframeEnabled: Boolean
    val isDrawlightsEnabled: Boolean
    val isPrintPipelineDebugOutput: Boolean
    val isUseComputeShaderDrawCommandAppend: Boolean
    val isDebugVoxels: Boolean
    val isUseFileReloading: Boolean
    val isLockUpdaterate: Boolean
    val directTextureOutputTextureIndex: Int
    val directTextureOutputArrayIndex: Int
    var isForceRevoxelization: Boolean
    var freezeCulling: Boolean
}

interface IEffectsConfig {
    val isScattering: Boolean
    val rainEffect: Float
    val ambientocclusionTotalStrength: Float
    val ambientocclusionRadius: Float
    val isUseBloom: Boolean
    val isAutoExposureEnabled: Boolean
    val isEnablePostprocessing: Boolean
    val ambientLight: Vector3f
}

class ProfilingConfig {
    var showFps = false
    var dumpAverages by GPUProfiler::dumpAveragesRequested
    var profiling by GPUProfiler::porfiling
    var printing by GPUProfiler::printing
}

operator fun <T> KMutableProperty0<T>.setValue(receiver: Any?, property: KProperty<*>, any: T) = set(any)
operator fun <T> KMutableProperty0<T>.getValue(receiver: Any?, property: KProperty<*>): T = getter.call()

@Target(AnnotationTarget.PROPERTY)
annotation class Button

data class QualityConfig(
    override var isUseAmbientOcclusion: Boolean = false,
    override var isUseParallax: Boolean = false,
    override var isUseSteepParallax: Boolean = false,
    override var isUseGi: Boolean = false,
    override var isUseSSR: Boolean = false,
    override var isUseMultipleDiffuseSamples: Boolean = true,
    override var isUseMultipleDiffuseSamplesProbes: Boolean = true,
    override var isUseConetracingForDiffuse: Boolean = false,
    override var isUseConetracingForDiffuseProbes: Boolean = false,
    override var isUseConetracingForSpecular: Boolean = false,
    override var isUseConetracingForSpecularProbes: Boolean = false,
    override var isUsePrecomputedRadiance: Boolean = true,
    override var isCalculateActualRadiance: Boolean = false,
    override var isSsrFadeToScreenBorders: Boolean = true,
    override var isSsrTemporalFiltering: Boolean = true,
    override var isContinuousDrawProbes: Boolean = false,
    override var isDrawProbes: Boolean = true,
    override var isUseDpsm: Boolean = false,
    override var isUsePcf: Boolean = false
) : IQualityConfig

data class DebugConfig(
    override @Button var reRenderProbes: Boolean = true,
    override var visualizeProbes: Boolean = false,
    override var drawBvhInnerNodes: Boolean = false,
    override var isEditorOverlay: Boolean = true,
    override var isUseGpuOcclusionCulling: Boolean = false,
    override var isDrawLines: Boolean = false,
    override var isDrawBoundingVolumes: Boolean = false,
    override var isDrawPointLightShadowMaps: Boolean = false,
    override var isDrawCameras: Boolean = false,
    override var isDrawScene: Boolean = true,
    override var isDebugframeEnabled: Boolean = false,
    override var isDrawlightsEnabled: Boolean = false,
    override var isPrintPipelineDebugOutput: Boolean = false,
    override var isUseComputeShaderDrawCommandAppend: Boolean = false,
    override var isDebugVoxels: Boolean = false,
    override var isUseFileReloading: Boolean = false,
    override var isLockUpdaterate: Boolean = true,
    override var directTextureOutputTextureIndex: Int = 0,
    override var directTextureOutputArrayIndex: Int = 0,
    override @Button var isForceRevoxelization: Boolean = false,
    override var freezeCulling: Boolean = false,
) : IDebugConfig

data class EffectsConfig(
        override var isScattering: Boolean = false,
        @Adjustable(minimum = 0, maximum = 100, minorTickSpacing = 5, majorTickSpacing = 10)
        override var rainEffect: Float = 0.0f,
        @Adjustable(maximum = 200, minorTickSpacing = 20, majorTickSpacing = 50)
        override var ambientocclusionTotalStrength: Float = 0.5f,
        override var ambientocclusionRadius: Float = 0.0250f,
        @Adjustable(minimum = 1, maximum = 40, factor = 1f, minorTickSpacing = 1, majorTickSpacing = 5)
        override var isUseBloom: Boolean = false,
        override var isAutoExposureEnabled: Boolean = false,
        override var isEnablePostprocessing: Boolean = false,
        override var ambientLight: Vector3f = Vector3f(0.1f, 0.1f, 0.11f)
) : IEffectsConfig

interface IPerformanceConfig {
    val updateGiOnSceneChange: Boolean
    val isIndirectRendering: Boolean
    val isVsync: Boolean
}

data class PerformanceConfig(
        override var updateGiOnSceneChange: Boolean = false,
        override var isIndirectRendering: Boolean = false, // TODO: This causes flickering, investigate
        override var isVsync: Boolean = true
) : IPerformanceConfig

data class ConfigImpl(override var directories: Directories = Directories(ENGINEDIR_NAME, GAMEDIR_NAME),
                      override var width: Int = 1280,
                      override var height: Int = 720,
                      override val quality: QualityConfig = QualityConfig(),
                      override val debug: DebugConfig = DebugConfig(),
                      override val effects: EffectsConfig = EffectsConfig(),
                      override val performance: PerformanceConfig = PerformanceConfig(),
                      override val profiling: ProfilingConfig = ProfilingConfig()) : Config {

    override val engineDir: EngineDirectory
        get() = directories.engineDir
    override val gameDir: GameDirectory
        get() = directories.gameDir
}
