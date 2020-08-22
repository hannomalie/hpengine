package de.hanno.hpengine.engine.graphics.renderer.extensions

import de.hanno.hpengine.engine.backend.Backend
import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.backend.gpuContext
import de.hanno.hpengine.engine.backend.programManager
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.component.allocateForComponent
import de.hanno.hpengine.engine.component.putToBuffer
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.entity.index
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.draw
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.RenderExtension
import de.hanno.hpengine.engine.graphics.renderer.pipelines.DrawElementsIndirectCommand
import de.hanno.hpengine.engine.graphics.shader.define.Define
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.model.Update
import de.hanno.hpengine.engine.model.loader.assimp.StaticModelLoader
import de.hanno.hpengine.engine.model.material.MaterialManager
import de.hanno.hpengine.engine.scene.VertexIndexBuffer
import org.joml.Vector3f
import org.lwjgl.BufferUtils
import java.io.File

class SkyBoxRenderExtension(val engineContext: EngineContext): RenderExtension<OpenGl> {

    val materialManager: MaterialManager = engineContext.materialManager
    private val skyBoxProgram = engineContext.programManager.getProgramFromFileNames("mvp_vertex.glsl", "skybox.glsl", Defines(Define.getDefine("PROGRAMMABLE_VERTEX_PULLING", true)))

    private val modelMatrixBuffer = BufferUtils.createFloatBuffer(16)

    private val gpuContext = engineContext.gpuContext
    private val skyBoxEntity = Entity("Skybox")

    private val skyBox = run {
        StaticModelLoader().load(File("assets/models/skybox.obj"), materialManager, engineContext.config.directories.engineDir)
    }
    private val skyBoxModelComponent = ModelComponent(skyBoxEntity, skyBox, materialManager.defaultMaterial).apply {
        skyBoxEntity.addComponent(this)
    }
    private val skyboxVertexIndexBuffer = VertexIndexBuffer(gpuContext, 10, 10, ModelComponent.DEFAULTCHANNELS)

    private val vertexIndexOffsets = skyboxVertexIndexBuffer.allocateForComponent(skyBoxModelComponent).apply {
        skyBoxModelComponent.putToBuffer(engineContext.gpuContext, skyboxVertexIndexBuffer, this)
    }
    private val skyBoxCommand = DrawElementsIndirectCommand().apply {
        primCount = 1
        count = skyBox.indices.size
        firstIndex = vertexIndexOffsets.indexOffset
        baseVertex = vertexIndexOffsets.vertexOffset
    }
    private val skyBoxRenderBatch = RenderBatch(
            entityBufferIndex = 0,
            isDrawLines = false,
            cameraWorldPosition = Vector3f(0f, 0f, 0f),
            isVisibleForCamera = true,
            update = Update.DYNAMIC,
            entityMinWorld = Vector3f(0f, 0f, 0f),
            entityMaxWorld = Vector3f(0f, 0f, 0f),
            centerWorld = Vector3f(),
            boundingSphereRadius = 1000f,
            animated = false,
            materialInfo = skyBoxModelComponent.material.materialInfo,
            entityIndex = skyBoxEntity.index,
            meshIndex = 0
    )


    override fun renderFirstPass(backend: Backend<OpenGl>, gpuContext: GpuContext<OpenGl>, firstPassResult: FirstPassResult, renderState: RenderState) {

        val camera = renderState.camera

        engineContext.deferredRenderingBuffer.use(gpuContext, false)

        gpuContext.enable(GlCap.DEPTH_TEST)
        gpuContext.disable(GlCap.CULL_FACE)
        gpuContext.disable(GlCap.BLEND)
        gpuContext.depthMask = true
        skyBoxEntity.transform.identity().scale(10f)
        skyBoxEntity.transform.setTranslation(camera.getPosition())
        skyBoxProgram.use()
        skyBoxProgram.setUniform("eyeVec", camera.getViewDirection())
        val translation = Vector3f()
        skyBoxProgram.setUniform("eyePos_world", camera.getTranslation(translation))
        skyBoxProgram.setUniform("materialIndex", materialManager.skyboxMaterial.materialIndex)
        skyBoxProgram.setUniformAsMatrix4("modelMatrix", skyBoxEntity.transform.transformation.get(modelMatrixBuffer))
        skyBoxProgram.setUniformAsMatrix4("viewMatrix", camera.viewMatrixAsBuffer)
        skyBoxProgram.setUniformAsMatrix4("projectionMatrix", camera.projectionMatrixAsBuffer)
        skyBoxProgram.bindShaderStorageBuffer(7, skyboxVertexIndexBuffer.vertexStructArray)
        val textureId = if (renderState.materialBuffer.size > 0) {
            renderState.materialBuffer[renderState.skyBoxMaterialIndex].environmentMapId.takeIf { it > 0 } ?: backend.textureManager.cubeMap.id
        } else {
            backend.textureManager.cubeMap.id
        }
        gpuContext.bindTexture(6, GlTextureTarget.TEXTURE_CUBE_MAP, textureId)
        draw(skyboxVertexIndexBuffer.vertexBuffer, skyboxVertexIndexBuffer.indexBuffer, skyBoxRenderBatch, skyBoxProgram, false, false)

    }
}