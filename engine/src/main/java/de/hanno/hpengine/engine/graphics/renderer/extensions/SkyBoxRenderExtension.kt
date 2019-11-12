package de.hanno.hpengine.engine.graphics.renderer.extensions

import de.hanno.hpengine.engine.backend.Backend
import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.draw
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.RenderExtension
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.model.OBJLoader
import de.hanno.hpengine.engine.model.Update
import de.hanno.hpengine.engine.model.material.MaterialManager
import de.hanno.hpengine.engine.scene.VertexIndexBuffer
import org.joml.Vector3f
import org.lwjgl.BufferUtils

class SkyBoxRenderExtension(val engineContext: EngineContext<OpenGl>): RenderExtension<OpenGl> {

    val materialManager: MaterialManager = engineContext.materialManager
    private val skyBoxProgram = engineContext.programManager.getProgramFromFileNames("mvp_vertex.glsl", "skybox.glsl")

    private val modelMatrixBuffer = BufferUtils.createFloatBuffer(16)

    private val gpuContext = engineContext.gpuContext
    private val skyBoxEntity = Entity("Skybox")

    private val skyBox = OBJLoader().loadTexturedModel(materialManager, engineContext.config.directories.engineDir.resolve("assets/models/skybox.obj"))
    private val skyBoxModelComponent = ModelComponent(skyBoxEntity, skyBox).apply {
        skyBoxEntity.addComponent(this)
    }
    private val skyboxVertexIndexBuffer = VertexIndexBuffer(gpuContext, 10, 10, ModelComponent.DEFAULTCHANNELS)

    private val vertexIndexOffsets = skyBoxEntity.getComponent(ModelComponent::class.java)!!.putToBuffer(engineContext.gpuContext, skyboxVertexIndexBuffer, ModelComponent.DEFAULTCHANNELS)
    private val skyBoxRenderBatch = RenderBatch().init(0, true, false, false, Vector3f(0f, 0f, 0f), true, 1, true, Update.DYNAMIC, Vector3f(0f, 0f, 0f), Vector3f(0f, 0f, 0f), Vector3f(), 1000f, skyBox.indices.size, vertexIndexOffsets.indexOffset, vertexIndexOffsets.vertexOffset, false, skyBoxEntity.instanceMinMaxWorlds, skyBoxModelComponent.getMaterial(materialManager).materialInfo, skyBoxEntity.index)


    override fun renderFirstPass(backend: Backend<OpenGl>, gpuContext: GpuContext<OpenGl>, firstPassResult: FirstPassResult, renderState: RenderState) {

        val camera = renderState.camera

        engineContext.deferredRenderingBuffer.use(gpuContext, false)

        gpuContext.enable(GlCap.DEPTH_TEST)
        gpuContext.disable(GlCap.CULL_FACE)
        gpuContext.disable(GlCap.BLEND)
        gpuContext.depthMask(true)
        skyBoxEntity.identity().scale(10f)
        skyBoxEntity.setTranslation(camera.getPosition())
        skyBoxProgram.use()
        skyBoxProgram.setUniform("eyeVec", camera.getViewDirection())
        val translation = Vector3f()
        skyBoxProgram.setUniform("eyePos_world", camera.getTranslation(translation))
        skyBoxProgram.setUniform("materialIndex", materialManager.skyboxMaterial.materialIndex)
        skyBoxProgram.setUniformAsMatrix4("modelMatrix", skyBoxEntity.transformation.get(modelMatrixBuffer))
        skyBoxProgram.setUniformAsMatrix4("viewMatrix", camera.viewMatrixAsBuffer)
        skyBoxProgram.setUniformAsMatrix4("projectionMatrix", camera.projectionMatrixAsBuffer)
        gpuContext.bindTexture(6, backend.textureManager.cubeMap)
        draw(skyboxVertexIndexBuffer.vertexBuffer, skyboxVertexIndexBuffer.indexBuffer, skyBoxRenderBatch, skyBoxProgram, false, false)

    }
}