package de.hanno.hpengine.editor

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.gpuContext
import de.hanno.hpengine.engine.backend.programManager
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.component.allocateForComponent
import de.hanno.hpengine.engine.component.putToBuffer
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.entity.index
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.draw
import de.hanno.hpengine.engine.graphics.renderer.pipelines.DrawElementsIndirectCommand
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.model.StaticModel
import de.hanno.hpengine.engine.model.Update
import de.hanno.hpengine.engine.model.loader.assimp.StaticModelLoader
import de.hanno.hpengine.engine.model.material.MaterialManager
import de.hanno.hpengine.engine.scene.VertexIndexBuffer
import de.hanno.hpengine.engine.transform.Transform
import org.joml.Vector3f
import org.lwjgl.BufferUtils

class SimpleModelRenderer(val engine: EngineContext,
                          val model: StaticModel = StaticModelLoader().load("assets/models/cube.obj", engine.materialManager, engine.config.directories.engineDir),
                          val program: Program = engine.run { programManager.getProgram(
                                  EngineAsset("shaders/mvp_vertex.glsl"),
                                  EngineAsset("shaders/simple_color_fragment.glsl")) }) : RenderSystem {

    val materialManager: MaterialManager = engine.materialManager
    val gpuContext = engine.gpuContext
    val modelEntity = Entity("Box")

    val modelComponent = ModelComponent(modelEntity, model, materialManager.defaultMaterial).apply {
        modelEntity.addComponent(this)
    }
    val modelVertexIndexBuffer = VertexIndexBuffer(gpuContext, 10)

    val vertexIndexOffsets = modelVertexIndexBuffer.allocateForComponent(modelComponent).apply {
        modelComponent.putToBuffer(engine.gpuContext, modelVertexIndexBuffer, this)
    }
    val modelCommand = DrawElementsIndirectCommand().apply {
        count = model.indices.size
        primCount = 1
        firstIndex = vertexIndexOffsets.indexOffset
        baseVertex = vertexIndexOffsets.vertexOffset
        baseInstance = 0
    }
    val modelRenderBatch = RenderBatch(entityBufferIndex = 0, isDrawLines = false,
            cameraWorldPosition = Vector3f(0f, 0f, 0f), drawElementsIndirectCommand = modelCommand, isVisibleForCamera = true, update = Update.DYNAMIC,
            entityMinWorld = Vector3f(0f, 0f, 0f), entityMaxWorld = Vector3f(0f, 0f, 0f), centerWorld = Vector3f(),
            boundingSphereRadius = 1000f, animated = false, materialInfo = modelComponent.material.materialInfo,
            entityIndex = modelEntity.index, meshIndex = 0)

    val transformBuffer = BufferUtils.createFloatBuffer(16).apply {
        Transform().get(this)
    }
    override fun render(result: DrawResult, state: RenderState) {
        render(state, modelEntity.transform.position, Vector3f(0f, 0f, 1f), Vector3f(1f))
    }
    fun render(state: RenderState, boxPosition: Vector3f, boxScale: Vector3f,
               color: Vector3f, useDepthTest: Boolean = true,
               beforeDraw: (Program.() -> Unit)? = null) {

        val scaling = (0.1f * modelEntity.transform.position.distance(state.camera.getPosition())).coerceIn(0.5f, 1f)
        val transformation = Transform().scale(scaling).translate(boxPosition)
        if(useDepthTest) engine.gpuContext.enable(GlCap.DEPTH_TEST) else engine.gpuContext.disable(GlCap.DEPTH_TEST)
        engine.deferredRenderingBuffer.finalBuffer.use(engine.gpuContext, false)
        program.use()
        program.setUniformAsMatrix4("modelMatrix", transformation.get(transformBuffer))
        program.setUniformAsMatrix4("viewMatrix", state.camera.viewMatrixAsBuffer)
        program.setUniformAsMatrix4("projectionMatrix", state.camera.projectionMatrixAsBuffer)
        program.setUniform("diffuseColor", color)
        program.bindShaderStorageBuffer(7, modelVertexIndexBuffer.vertexStructArray)
        if (beforeDraw != null) { program.beforeDraw() }

        modelVertexIndexBuffer.indexBuffer.draw(modelRenderBatch, program)

    }
    fun render(state: RenderState, useDepthTest: Boolean = true,
               draw: (SimpleModelRenderer.(RenderState) -> Unit)) {

        val transformation = Transform()
        if(useDepthTest) engine.gpuContext.enable(GlCap.DEPTH_TEST) else engine.gpuContext.disable(GlCap.DEPTH_TEST)
        engine.deferredRenderingBuffer.finalBuffer.use(engine.gpuContext, false)
        program.use()
        program.setUniformAsMatrix4("modelMatrix", transformation.get(transformBuffer))
        program.setUniformAsMatrix4("viewMatrix", state.camera.viewMatrixAsBuffer)
        program.setUniformAsMatrix4("projectionMatrix", state.camera.projectionMatrixAsBuffer)
        program.setUniform("diffuseColor", Vector3f(1f,0f,0f))
        program.bindShaderStorageBuffer(7, modelVertexIndexBuffer.vertexStructArray)

        draw(state)
    }
}