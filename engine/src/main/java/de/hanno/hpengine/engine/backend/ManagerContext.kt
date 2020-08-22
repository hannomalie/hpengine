package de.hanno.hpengine.engine.backend

import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.directory.Directories
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.RenderManager
import de.hanno.hpengine.engine.graphics.RenderStateManager
import de.hanno.hpengine.engine.graphics.Window
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DeferredRenderingBuffer
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.input.Input
import de.hanno.hpengine.engine.manager.ManagerRegistry
import de.hanno.hpengine.engine.model.material.MaterialManager
import de.hanno.hpengine.engine.model.texture.TextureManager
import de.hanno.hpengine.engine.physics.PhysicsManager
import de.hanno.hpengine.engine.scene.AddResourceContext
import de.hanno.hpengine.engine.scene.Scene

inline val ManagerContext.backend: Backend<*>
    get() = engineContext.backend
inline val ManagerContext.config: Config
    get() = engineContext.config
inline val ManagerContext.renderSystems: MutableList<RenderSystem>
    get() = engineContext.renderSystems
inline val ManagerContext.renderStateManager: RenderStateManager
    get() = engineContext.renderStateManager
inline val ManagerContext.programManager: ProgramManager<*>
    get() = engineContext.programManager
inline val ManagerContext.textureManager: TextureManager
    get() = engineContext.textureManager
inline val ManagerContext.deferredRenderingBuffer: DeferredRenderingBuffer
    get() = engineContext.deferredRenderingBuffer
inline val ManagerContext.materialManager: MaterialManager
    get() = engineContext.materialManager
inline val ManagerContext.window: Window<*>
    get() = engineContext.window
inline val ManagerContext.addResourceContext: AddResourceContext
    get() = engineContext.backend.addResourceContext
inline val ManagerContext.gpuContext: GpuContext<OpenGl>
    get() = engineContext.backend.gpuContext
inline val ManagerContext.input: Input
    get() = engineContext.backend.input