package de.hanno.hpengine.engine.config

import de.hanno.hpengine.engine.directory.Directories
import de.hanno.hpengine.engine.directory.Directories.Companion.WORKDIR_NAME
import de.hanno.hpengine.util.gui.Adjustable
import org.apache.commons.beanutils.BeanUtils
import org.joml.Vector3f
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.lang.reflect.InvocationTargetException
import java.util.Properties

interface Config {
    val quality: IQualityConfig
    val debug: IDebugConfig
    val effects: IEffectsConfig
    val performance: IPerformanceConfig

    val initFileName: String
    val directories: Directories
    val gameDir: String
    val width: Int
    val height: Int
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
    val isUseCpuFrustumCulling: Boolean
    val isUseGpuOcclusionCulling: Boolean
    val isDrawLines: Boolean
    val isDrawBoundingVolumes: Boolean
    val isDrawCameras: Boolean
    val isDrawScene: Boolean
    val isUseDirectTextureOutput: Boolean
    val isDebugframeEnabled: Boolean
    val isDrawlightsEnabled: Boolean
    val isPrintPipelineDebugOutput: Boolean
    val isUseComputeShaderDrawCommandAppend: Boolean
    val isDebugVoxels: Boolean
    val isUseFileReloading: Boolean
    val isLockUpdaterate: Boolean
    val directTextureOutputTextureIndex: Int
    val isForceRevoxelization: Boolean
}

interface IEffectsConfig {
    val cameraSpeed: Float
    val isScattering: Boolean
    val rainEffect: Float
    val ambientocclusionTotalStrength: Float
    val ambientocclusionRadius: Float
    val exposure: Float
    val isUseBloom: Boolean
    val isAutoExposureEnabled: Boolean
    val isEnablePostprocessing: Boolean
    val ambientLight: Vector3f
}

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
    override var isUseCpuFrustumCulling: Boolean = true,
    override var isUseGpuOcclusionCulling: Boolean = false,
    override var isDrawLines: Boolean = false,
    override var isDrawBoundingVolumes: Boolean = false,
    override var isDrawCameras: Boolean = true,
    override var isDrawScene: Boolean = true,
    override var isUseDirectTextureOutput: Boolean = false,
    override var isDebugframeEnabled: Boolean = false,
    override var isDrawlightsEnabled: Boolean = false,
    override var isPrintPipelineDebugOutput: Boolean = false,
    override var isUseComputeShaderDrawCommandAppend: Boolean = false,
    override var isDebugVoxels: Boolean = false,
    override var isUseFileReloading: Boolean = true,
    override var isLockUpdaterate: Boolean = true,
    override var directTextureOutputTextureIndex: Int = 0,
    override var isForceRevoxelization: Boolean = false
) : IDebugConfig

data class EffectsConfig(
        override var cameraSpeed: Float = 1.0f,
        override var isScattering: Boolean = false,
        @Adjustable(minimum = 0, maximum = 100, minorTickSpacing = 5, majorTickSpacing = 10)
        override var rainEffect: Float = 0.0f,
        @Adjustable(maximum = 200, minorTickSpacing = 20, majorTickSpacing = 50)
        override var ambientocclusionTotalStrength: Float = 0.5f,
        override var ambientocclusionRadius: Float = 0.0250f,
        @Adjustable(minimum = 1, maximum = 40, factor = 1f, minorTickSpacing = 1, majorTickSpacing = 5)
        override var exposure: Float = 5f,
        override var isUseBloom: Boolean = false,
        override var isAutoExposureEnabled: Boolean = true,
        override var isEnablePostprocessing: Boolean = false,
        override var ambientLight: Vector3f = Vector3f(0.1f, 0.1f, 0.11f)
) : IEffectsConfig

interface IPerformanceConfig {
    val isIndirectRendering: Boolean
    val isLockFps: Boolean
    val isVsync: Boolean
}

data class PerformanceConfig(
    override val isIndirectRendering: Boolean = true,
    override val isLockFps: Boolean = false,
    override val isVsync: Boolean = true
) : IPerformanceConfig

class SimpleConfig(override val quality: QualityConfig = QualityConfig(),
                   override val debug: DebugConfig = DebugConfig(),
                   override val effects: EffectsConfig = EffectsConfig(),
                   override val performance: PerformanceConfig = PerformanceConfig())
        : Config {

    override var gameDir = Directories.GAMEDIR_NAME
        set(gameDir) {
            field = gameDir
            this.directories = Directories(WORKDIR_NAME, gameDir, initFileName)
        }
    override var initFileName = "Init.java"
    override var directories = Directories(WORKDIR_NAME, this.gameDir, initFileName)
    override var width = 1280
    override var height = 720

    companion object {
//        TODO: Remove this
        @JvmStatic
        val isUseFileReloadXXX = true
    }
}

fun SimpleConfig.populateConfigurationWithProperties(gameDir: File) {
    FileInputStream(gameDir.resolve("default.properties")).use {
        populateConfigurationWithProperties(it.toProperties())
    }

}
fun SimpleConfig.populateConfigurationWithProperties(properties: Properties) {
    val propertiesMap = mutableMapOf<String, Any>()
    for (key in properties.stringPropertyNames()) {
        propertiesMap[key] = properties[key]!!
    }
    try {
        BeanUtils.populate(this, propertiesMap)
    } catch (e: IllegalAccessException) {
        e.printStackTrace()
    } catch (e: InvocationTargetException) {
        e.printStackTrace()
    }

    directories = Directories(WORKDIR_NAME, gameDir, initFileName)
}

fun InputStream.toProperties(): Properties {
    val properties = Properties()
    try {
        properties.load(this)
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return properties
}