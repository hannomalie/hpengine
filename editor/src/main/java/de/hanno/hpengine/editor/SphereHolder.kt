package de.hanno.hpengine.editor

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.component.allocateForComponent
import de.hanno.hpengine.engine.component.putToBuffer
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.draw
import de.hanno.hpengine.engine.graphics.shader.define.Define
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.model.Update
import de.hanno.hpengine.engine.model.loader.assimp.StaticModelLoader
import de.hanno.hpengine.engine.model.material.MaterialManager
import de.hanno.hpengine.engine.scene.VertexIndexBuffer
import de.hanno.hpengine.engine.transform.SimpleTransform
import org.joml.Vector3f
import org.lwjgl.BufferUtils
import java.io.File

class SphereHolder(val engine: EngineContext<OpenGl>) : RenderSystem {

    private val materialManager: MaterialManager = engine.materialManager
    private val gpuContext = engine.gpuContext
    val sphereEntity = Entity("[Editor] Pivot")
    private val sphereProgram = engine.programManager.getProgramFromFileNames("mvp_vertex.glsl", "simple_color_fragment.glsl", Defines(Define.getDefine("PROGRAMMABLE_VERTEX_PULLING", true)))

    private val sphere = run {
        StaticModelLoader().load(File("assets/models/sphere.obj"), materialManager, engine.config.directories.engineDir)
    }

    private val sphereModelComponent = ModelComponent(sphereEntity, sphere, materialManager.defaultMaterial).apply {
        sphereEntity.addComponent(this)
    }
    private val sphereVertexIndexBuffer = VertexIndexBuffer(gpuContext, 10, 10, ModelComponent.DEFAULTCHANNELS)

    private val vertexIndexOffsets = sphereVertexIndexBuffer.allocateForComponent(sphereModelComponent).apply {
        sphereModelComponent.putToBuffer(engine.gpuContext, sphereVertexIndexBuffer, this)
    }
    private val sphereRenderBatch = RenderBatch().init(0, true, false, false,
            Vector3f(0f, 0f, 0f), true, 1, true, Update.DYNAMIC,
            Vector3f(0f, 0f, 0f), Vector3f(0f, 0f, 0f), Vector3f(),
            1000f, sphere.indices.size, vertexIndexOffsets.indexOffset,
            vertexIndexOffsets.vertexOffset, false, sphereEntity.instanceMinMaxWorlds,
            sphereModelComponent.material.materialInfo, sphereEntity.index, 0)

    private val transformBuffer = BufferUtils.createFloatBuffer(16).apply {
        SimpleTransform().get(this)
    }

    override fun render(result: DrawResult, state: RenderState) {
        val scaling = (0.1f * sphereEntity.position.distance(state.camera.getPosition())).coerceIn(0.5f, 1f)
        val transformation = SimpleTransform().scale(scaling).translate(sphereEntity.position)
        engine.gpuContext.disable(GlCap.DEPTH_TEST)
        engine.deferredRenderingBuffer.finalBuffer.use(engine.gpuContext, false)
        sphereProgram.use()
        sphereProgram.setUniformAsMatrix4("modelMatrix", transformation.get(transformBuffer))
        sphereProgram.setUniformAsMatrix4("viewMatrix", state.camera.viewMatrixAsBuffer)
        sphereProgram.setUniformAsMatrix4("projectionMatrix", state.camera.projectionMatrixAsBuffer)
        sphereProgram.setUniform("diffuseColor", Vector3f(0f, 0f, 1f))
        sphereProgram.bindShaderStorageBuffer(7, sphereVertexIndexBuffer.vertexStructArray)

        draw(sphereVertexIndexBuffer.vertexBuffer,
                sphereVertexIndexBuffer.indexBuffer,
                sphereRenderBatch, sphereProgram, false, false)
    }
}