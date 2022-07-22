package de.hanno.hpengine.backend

import de.hanno.hpengine.bus.EventBus
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.input.Input
import de.hanno.hpengine.model.texture.TextureManager
import de.hanno.hpengine.scene.AddResourceContext

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