package de.hanno.hpengine.engine.backend

import de.hanno.hpengine.engine.event.bus.EventBus
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.input.Input
import de.hanno.hpengine.engine.model.texture.TextureManager
import de.hanno.hpengine.engine.scene.AddResourceContext

interface Backend<Type: BackendType> {
    val eventBus: EventBus
    val gpuContext: GpuContext<Type>
    val programManager: ProgramManager<Type>
    val textureManager: TextureManager
    val input: Input
    val addResourceContext: AddResourceContext

    companion object
}

interface BackendType