package de.hanno.hpengine.engine.config

import de.hanno.hpengine.engine.directory.DirectoryManager
import de.hanno.hpengine.engine.directory.DirectoryManager.Companion.WORKDIR_NAME
import de.hanno.hpengine.util.gui.Adjustable
import de.hanno.hpengine.util.gui.Toggable
import org.apache.commons.beanutils.BeanUtils
import org.joml.Vector3f
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.lang.reflect.InvocationTargetException
import java.util.Properties

class Config internal constructor() {
    var gameDir = DirectoryManager.GAMEDIR_NAME
        set(gameDir) {
            field = gameDir
            this.directoryManager = DirectoryManager(WORKDIR_NAME, gameDir, initFileName)
        }
    var initFileName = "Init.java"
    var directoryManager = DirectoryManager(WORKDIR_NAME, this.gameDir, initFileName)
        internal set
    var isUseFileReloading = true
    var width = 1280
    var height = 720
    var ambientLight = Vector3f(0.1f, 0.1f, 0.11f)

    @Toggable(group = "Quality settings")
    @Volatile
    var isUseParallax = false
    @Toggable(group = "Quality settings")
    @Volatile
    var isUseSteepParallax = false
    @Toggable(group = "Quality settings")
    @Volatile
    var isUseAmbientOcclusion = true
    @Toggable(group = "Debug")
    @Volatile
    var isUseCpuFrustumCulling = true
    @Toggable(group = "Debug")
    @Volatile
    var isUseGpuOcclusionCulling = false
    @Volatile
    var isUseInstantRadiosity = false
    @Volatile
    var isForceRevoxelization = false
    @Toggable(group = "Quality settings")
    @Volatile
    var isUseGi = false
    @Toggable(group = "Quality settings")
    @Volatile
    var isUseSSR = false
    @Toggable(group = "Quality settings")
    @Volatile
    var isUseMultipleDiffuseSamples = true
    @Toggable(group = "Quality settings")
    @Volatile
    var isUseMultipleDiffuseSamplesProbes = true
    @Toggable(group = "Quality settings")
    @Volatile
    var isUseConetracingForDiffuse = false
    @Toggable(group = "Quality settings")
    @Volatile
    var isUseConetracingForDiffuseProbes = false
    @Toggable(group = "Quality settings")
    @Volatile
    var isUseConetracingForSpecular = false
    @Toggable(group = "Quality settings")
    @Volatile
    var isUseConetracingForSpecularProbes = false
    @Toggable(group = "Quality settings")
    @Volatile
    var isUsePrecomputedRadiance = true
    @Toggable(group = "Quality settings")
    @Volatile
    var isCalculateActualRadiance = false
    @Toggable(group = "Quality settings")
    @Volatile
    var isSsrFadeToScreenBorders = true
    @Toggable(group = "Quality settings")
    @Volatile
    var isSsrTemporalFiltering = true
    @Toggable(group = "Quality settings")
    @Volatile
    var isUsePcf = false
    @Toggable(group = "Debug")
    @Volatile
    var isDrawLines = false
    @Toggable(group = "Debug")
    var isDrawBoundingVolumes = false
    @Toggable(group = "Debug")
    var isDrawCameras = true
    @Toggable(group = "Debug")
    @Volatile
    var isDrawScene = true
    @Toggable(group = "Debug")
    @Volatile
    var isUseDirectTextureOutput = false
    @Volatile
    var directTextureOutputTextureIndex = 0
    @Toggable(group = "Quality settings")
    @Volatile
    var isContinuousDrawProbes = false
    @Toggable(group = "Debug")
    @Volatile
    var isDebugframeEnabled = false
    @Toggable(group = "Debug")
    @Volatile
    var isDrawlightsEnabled = false
    @Toggable(group = "Debug")
    val isPrintPipelineDebugOutput = false
    @Toggable(group = "Debug")
    @Volatile
    var isUseComputeShaderDrawCommandAppend = false

    @Toggable(group = "Quality settings")
    @Volatile
    var isDrawProbes = true
    @Adjustable(group = "Debug")
    @Volatile
    var cameraSpeed = 1.0f
    @Toggable(group = "Debug")
    @Volatile
    var isDebugVoxels = false
    @Toggable(group = "Effects")
    @Volatile
    var isScattering = false
    @Adjustable(group = "Effects")
    @Volatile
    var rainEffect = 0.0f
    @Adjustable(group = "Effects")
    @Volatile
    var ambientocclusionTotalStrength = 0.5f
    @Adjustable(group = "Effects")
    @Volatile
    var ambientocclusionRadius = 0.0250f
    @Adjustable(group = "Effects")
    @Volatile
    var exposure = 5f
    @Toggable(group = "Effects")
    @Volatile
    var isUseBloom = false
    @Toggable(group = "Effects")
    @Volatile
    var isAutoExposureEnabled = true
    @Toggable(group = "Effects")
    @Volatile
    var isEnablePostprocessing = false
    @Toggable(group = "Quality settings")
    @Volatile
    var isUseDpsm = false

    @Toggable(group = "Performance")
    var isIndirectRendering = true
    @Toggable(group = "Performance")
    @Volatile
    var isLockFps = false
    @Volatile
    var isVsync = true
    @Toggable(group = "Performance")
    @Volatile
    var isLockUpdaterate = true

    var isLoadDefaultMaterials = false

    companion object {
//        TODO: Remove this
        @JvmStatic val isUseFileReloadXXX = true
    }
}

fun Config.populateConfigurationWithProperties(gameDir: File) {
    FileInputStream(gameDir.resolve("default.properties")).use {
        populateConfigurationWithProperties(it.toProperties())
    }

}
fun Config.populateConfigurationWithProperties(properties: Properties) {
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

    directoryManager = DirectoryManager(WORKDIR_NAME, gameDir, initFileName)
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