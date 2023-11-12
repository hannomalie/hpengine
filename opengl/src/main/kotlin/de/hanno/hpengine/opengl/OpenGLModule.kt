package de.hanno.hpengine.opengl

import com.artemis.BaseSystem
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.OpenGLContext
import de.hanno.hpengine.graphics.profiling.GPUProfiler
import de.hanno.hpengine.graphics.shader.OpenGlProgramManager
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.texture.OpenGLTextureManager
import de.hanno.hpengine.graphics.texture.TextureManager
import de.hanno.hpengine.graphics.texture.TextureManagerBaseSystem
import de.hanno.hpengine.graphics.window.Window
import de.hanno.hpengine.ressources.FileMonitor
import de.hanno.hpengine.stopwatch.OpenGLGPUProfiler
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.koin.ksp.generated.module

@Module
class OpenGLModule {
    @Single(binds = [GraphicsApi::class])
    fun openGLContext(window: Window, config: Config) = OpenGLContext(window, config)
    @Single(binds = [GPUProfiler::class, OpenGLGPUProfiler::class])
    fun openGLProfiler(config: Config) = OpenGLGPUProfiler(config.debug::profiling)
    @Single(binds = [
        TextureManager::class,
        OpenGLTextureManager::class,
        TextureManagerBaseSystem::class,
        BaseSystem::class,
    ])
    fun openglTextureManager(
        config: Config, openGLContext: OpenGLContext, programManager: OpenGlProgramManager
    ) = OpenGLTextureManager(config, openGLContext, programManager)

    @Single(binds = [
        ProgramManager::class,
        OpenGlProgramManager::class,
        BaseSystem::class,
    ])
    fun openglProgramManager(
        graphicsApi: GraphicsApi,
        fileMonitor: FileMonitor,
        config: Config,
    ) = OpenGlProgramManager(graphicsApi, fileMonitor, config,)
}

val openglModule = OpenGLModule().module