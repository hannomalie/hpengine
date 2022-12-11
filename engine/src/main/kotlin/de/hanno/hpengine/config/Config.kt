package de.hanno.hpengine.config

import de.hanno.hpengine.directory.Directories
import de.hanno.hpengine.directory.Directories.Companion.ENGINEDIR_NAME
import de.hanno.hpengine.directory.Directories.Companion.GAMEDIR_NAME
import de.hanno.hpengine.directory.EngineDirectory
import de.hanno.hpengine.directory.GameDirectory
import org.joml.Vector3f

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
    override var isUseCpuFrustumCulling: Boolean = true,
    override var isUseGpuFrustumCulling: Boolean = true,
    override var isUseGpuOcclusionCulling: Boolean = true,
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
    override var isUseFileReloading: Boolean = true,
    override var isLockUpdaterate: Boolean = true,
    override var directTextureOutputTextureIndex: Int = 0,
    override var directTextureOutputArrayIndex: Int = 0,
    override var isForceRevoxelization: Boolean = false,
    override var freezeCulling: Boolean = false,
    override var forceSingleThreadedRendering: Boolean = false,
    override var profiling: Boolean = false,
) : IDebugConfig

data class EffectsConfig(
        override var isScattering: Boolean = false,
        override var rainEffect: Float = 0.0f,
        override var ambientocclusionTotalStrength: Float = 0.5f,
        override var ambientocclusionRadius: Float = 0.0250f,
        override var isUseBloom: Boolean = false,
        override var isAutoExposureEnabled: Boolean = false,
        override var isEnablePostprocessing: Boolean = false,
        override var ambientLight: Vector3f = Vector3f(0.1f, 0.1f, 0.11f)
) : IEffectsConfig

data class PerformanceConfig(
        override var updateGiOnSceneChange: Boolean = false,
        override var isIndirectRendering: Boolean = false, // TODO: This causes flickering, investigate
        override var isVsync: Boolean = true
) : IPerformanceConfig

data class ConfigImpl(override var directories: Directories = Directories(ENGINEDIR_NAME, GAMEDIR_NAME),
                      override var width: Int = 1920,
                      override var height: Int = 1080,
                      override val quality: QualityConfig = QualityConfig(),
                      override val debug: DebugConfig = DebugConfig(),
                      override val effects: EffectsConfig = EffectsConfig(),
                      override val performance: PerformanceConfig = PerformanceConfig(),
                      override val profiling: ProfilingConfig = ProfilingConfig()
) : Config {

    override val engineDir: EngineDirectory
        get() = directories.engineDir
    override val gameDir: GameDirectory
        get() = directories.gameDir
}
