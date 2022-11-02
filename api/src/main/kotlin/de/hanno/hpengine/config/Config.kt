package de.hanno.hpengine.config

import de.hanno.hpengine.directory.*
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
    val isUseCpuFrustumCulling: Boolean
    val isUseGpuFrustumCulling: Boolean
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
    var forceSingleThreadedRendering: Boolean
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

interface IPerformanceConfig {
    val updateGiOnSceneChange: Boolean
    val isIndirectRendering: Boolean
    val isVsync: Boolean
}

class ProfilingConfig {
    var showFps = false
    var dumpAverages by ::_dumpAveragesRequested
    var profiling by ::_profiling
    var printing by ::_printing
}
// TODO: Move this to non global scope
var _dumpAveragesRequested = false
var _profiling = false
var _printing = false

operator fun <T> KMutableProperty0<T>.setValue(receiver: Any?, property: KProperty<*>, any: T) = set(any)
operator fun <T> KMutableProperty0<T>.getValue(receiver: Any?, property: KProperty<*>): T = getter.call()

