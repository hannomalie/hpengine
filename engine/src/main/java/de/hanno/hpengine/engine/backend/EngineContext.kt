package de.hanno.hpengine.engine.backend

import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.directory.EngineAsset
import de.hanno.hpengine.engine.directory.GameAsset
import de.hanno.hpengine.engine.graphics.GlfwWindow
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.RenderStateManager
import de.hanno.hpengine.engine.graphics.Window
import de.hanno.hpengine.engine.graphics.renderer.ExtensibleDeferredRenderer
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DeferredRenderingBuffer
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.RenderExtension
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.input.Input
import de.hanno.hpengine.engine.model.material.MaterialManager
import de.hanno.hpengine.engine.model.texture.TextureManager
import de.hanno.hpengine.engine.scene.AddResourceContext
import de.hanno.hpengine.engine.scene.Extension
import de.hanno.hpengine.engine.scene.Scene
import java.util.concurrent.CopyOnWriteArrayList

data class EngineContext(
    val config: Config,
    val addResourceContext: AddResourceContext,
    val window: Window<OpenGl> = GlfwWindow(config.width, config.height, "HPEngine", config.performance.isVsync),
    val backend: Backend<OpenGl>,
    val deferredRenderingBuffer: DeferredRenderingBuffer,
    val renderSystems: List<RenderSystem>,
    val renderStateManager: RenderStateManager
) {

    inline val engineDir
        get() = config.engineDir
    inline val gameDir
        get() = config.gameDir

    fun EngineAsset(relativePath: String): EngineAsset = config.EngineAsset(relativePath)
    fun GameAsset(relativePath: String): GameAsset = config.GameAsset(relativePath)

    fun extract(scene: Scene, renderState: RenderState) {
        renderSystems.forEach { it.extract(scene, renderState) }
    }

    override fun toString(): String = "EngineContext"
}

inline val EngineContext.input: Input
    get() = backend.input
inline val EngineContext.textureManager: TextureManager
    get() = backend.textureManager
inline val EngineContext.programManager: ProgramManager<OpenGl>
    get() = backend.programManager
inline val EngineContext.gpuContext: GpuContext<OpenGl>
    get() = backend.gpuContext
inline val EngineContext.eventBus
    get() = backend.eventBus