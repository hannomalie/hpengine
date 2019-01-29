package de.hanno.hpengine.engine.model.texture

import de.hanno.hpengine.engine.EngineImpl
import de.hanno.hpengine.engine.backend.BackendImpl
import de.hanno.hpengine.engine.backend.EngineContextImpl
import de.hanno.hpengine.engine.component.InputControllerComponent
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.event.bus.MBassadorEventBus
import de.hanno.hpengine.engine.graphics.OpenGLContext
import de.hanno.hpengine.engine.graphics.renderer.SimpleTextureRenderer
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.model.material.MaterialManager
import org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_BRACKET
import org.lwjgl.glfw.GLFW.GLFW_KEY_SLASH


fun main() {
    val eventBus = MBassadorEventBus()
    val gpuContext = OpenGLContext()

    val xxx = SimpleTexture2D(gpuContext, "C:\\workspace\\hpengine\\hp\\assets\\models\\textures\\brick.png")
    val yyy = SimpleTexture2D(gpuContext, "C:\\workspace\\hpengine\\hp\\assets\\models\\textures\\kamen.dds")

    val programManager = ProgramManager(gpuContext, eventBus)
    val textureManager = TextureManager(programManager, gpuContext)
    textureManager.textures["C:\\workspace\\hpengine\\hp\\assets\\models\\textures\\brick.png"] = xxx
    textureManager.textures["C:\\workspace\\hpengine\\hp\\assets\\models\\textures\\kamen.dds"] = yyy
    val backend = BackendImpl(eventBus, gpuContext, textureManager = textureManager)
    val engineContext = EngineContextImpl(backend = backend)
    val materialManager = MaterialManager(engineContext)
    val texture = textureManager.textures.values.toList()[0]
    val renderer = SimpleTextureRenderer(programManager, texture)
    val engine = EngineImpl(engineContext, materialManager, renderer)
//    materialManager.initDefaultMaterials()

    engine.renderManager.renderer = renderer
    val entity = Entity()
    entity.addComponent(object: InputControllerComponent(entity) {
        var currentTextureIndex = 0
        override fun update(seconds: Float) {
            val textures = textureManager.textures.values.toList()
            if(backend.input.isKeyReleased(GLFW_KEY_RIGHT_BRACKET)) {
                currentTextureIndex++
                currentTextureIndex = Math.min(currentTextureIndex, textures.size-1)
                currentTextureIndex = Math.max(currentTextureIndex, 0)
                println("++Showing texture $currentTextureIndex")
            } else if(backend.input.isKeyReleased(GLFW_KEY_SLASH)) {
                currentTextureIndex--
                currentTextureIndex = Math.min(currentTextureIndex, textures.size-1)
                currentTextureIndex = Math.max(currentTextureIndex, 0)
                println("--Showing texture $currentTextureIndex")
            }

            val texture = textures[currentTextureIndex]
            renderer.texture = texture
        }
    })
    engine.scene.add(entity)

//    textureManager.getTexture("hp/assets/textures/stone_diffuse2.png", true)
//    Thread.sleep(5000)
//    textureManager.getTexture("C:\\workspace\\hpengine\\hp\\assets\\models\\textures\\brick.png")
//    textureManager.getTexture("C:\\workspace\\hpengine\\hp\\assets\\models\\textures\\kamen.dds", true)
}
