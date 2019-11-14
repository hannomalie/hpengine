package de.hanno.hpengine.engine.backend

import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.RenderStateManager
import de.hanno.hpengine.engine.graphics.Window
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DeferredRenderingBuffer
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.input.Input
import de.hanno.hpengine.engine.model.material.MaterialManager
import de.hanno.hpengine.engine.model.texture.TextureManager
import de.hanno.hpengine.engine.scene.SingleThreadContext
import de.hanno.hpengine.util.commandqueue.CommandQueue

interface EngineContext<TYPE: BackendType>: Backend<TYPE> {
    val window: Window<TYPE>
    val backend: Backend<TYPE>
    val commandQueue: CommandQueue
    val config: Config
    val deferredRenderingBuffer: DeferredRenderingBuffer
    val renderSystems: MutableList<RenderSystem>
    val renderStateManager: RenderStateManager
    val materialManager: MaterialManager

    override val eventBus
        get() = backend.eventBus
    override val gpuContext: GpuContext<TYPE>
        get() = backend.gpuContext
    override val programManager: ProgramManager<TYPE>
        get() = backend.programManager
    override val textureManager: TextureManager
        get() = backend.textureManager
    override val input: Input
        get() = backend.input
    override val singleThreadContext: SingleThreadContext
        get() = backend.singleThreadContext
}