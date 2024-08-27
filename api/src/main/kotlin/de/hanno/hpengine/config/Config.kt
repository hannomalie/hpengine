package de.hanno.hpengine.config

import de.hanno.hpengine.directory.*
import org.apache.logging.log4j.Level
import org.joml.Vector3f
import org.koin.core.annotation.Single
import java.io.File
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty


// TODO: Remove this global config stuff and let modules read config values themselves
@Target(AnnotationTarget.PROPERTY)
annotation class Button

@Single
data class Config(
    var logLevel: Level = Level.INFO,
    var quality: QualityConfig = QualityConfig(),
    var debug: DebugConfig = DebugConfig(),
    var effects: EffectsConfig = EffectsConfig(),
    var performance: PerformanceConfig = PerformanceConfig(),
    var profiling: ProfilingConfig = ProfilingConfig(),
    var directories: Directories = Directories(
        EngineDirectory(File(Directories.ENGINEDIR_NAME)),
        GameDirectory(File(Directories.GAMEDIR_NAME), null),
    ),
    var width: Int = 1920,
    var height: Int = 1080,
) {
    val engineDir by directories::engineDir
    val gameDir by directories::gameDir

    fun EngineAsset(relativePath: String): EngineAsset = engineDir.toAsset(relativePath)
    fun GameAsset(relativePath: String): GameAsset = gameDir.toAsset(relativePath)
}

data class QualityConfig(
    var isUseAmbientOcclusion: Boolean = true,
    var isUseParallax: Boolean = true,
    var isUseSteepParallax: Boolean = false,
    var isUseGi: Boolean = true,
    var isUseSSR: Boolean = true,
    var isUseMultipleDiffuseSamples: Boolean = true,
    var isUseMultipleDiffuseSamplesProbes: Boolean = true,
    var isUseConetracingForDiffuse: Boolean = true,
    var isUseConetracingForDiffuseProbes: Boolean = true,
    var isUseConetracingForSpecular: Boolean = true,
    var isUseConetracingForSpecularProbes: Boolean = true,
    var isUsePrecomputedRadiance: Boolean = true,
    var isCalculateActualRadiance: Boolean = false,
    var isSsrFadeToScreenBorders: Boolean = true,
    var isSsrTemporalFiltering: Boolean = true,
    var isContinuousDrawProbes: Boolean = false,
    var isDrawProbes: Boolean = false,
    var isUsePcf: Boolean = false,
)

data class DebugConfig(
    @Button var reRenderProbes: Boolean = false,
    var visualizeProbes: Boolean = false,
    var drawBvhInnerNodes: Boolean = false,
    var isUseCpuFrustumCulling: Boolean = true,
    var isUseGpuFrustumCulling: Boolean = true,
    var isUseGpuOcclusionCulling: Boolean = true,
    var isDrawLines: Boolean = false,
    var isDrawBoundingVolumes: Boolean = false,
    var isDrawPointLightShadowMaps: Boolean = false,
    var isDrawCameras: Boolean = false,
    var isDrawScene: Boolean = true,
    var isDebugframeEnabled: Boolean = false,
    var isDrawlightsEnabled: Boolean = false,
    var isPrintPipelineDebugOutput: Boolean = false,
    var isUseComputeShaderDrawCommandAppend: Boolean = false,
    var isDebugVoxels: Boolean = false,
    var isUseFileReloading: Boolean = true,
    var isLockUpdaterate: Boolean = false,
    var directTextureOutputTextureIndex: Int = 0,
    var directTextureOutputArrayIndex: Int = 0,
    var isForceRevoxelization: Boolean = false,
    var freezeCulling: Boolean = false,
    var forceSingleThreadedRendering: Boolean = false,
    var profiling: Boolean = false,
    var simulateSlowTextureStreaming: Boolean = false,
)

data class EffectsConfig (
    var isScattering: Boolean = true,
    var rainEffect: Float = 0f,
    var ambientocclusionTotalStrength: Float = 0.5f,
    var ambientocclusionRadius: Float = 0.025f,
    var isUseBloom: Boolean = true,
    var isAutoExposureEnabled: Boolean = true,
    var isEnablePostprocessing: Boolean = true,
    var ambientLight: Vector3f = Vector3f(0.1f),
)

data class PerformanceConfig (
    var updateGiOnSceneChange: Boolean = true,
    var isIndirectRendering: Boolean = false,
    var isVsync: Boolean = true,
    var usePixelBufferForTextureUpload: Boolean = true,
    var textureCompressionByDefault: Boolean = false, // This messes up textures, figure out why
)

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

