package de.hanno.hpengine.editor.graphics

import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.component.allocateForComponent
import de.hanno.hpengine.engine.component.putToBuffer
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.entity.index
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.draw
import de.hanno.hpengine.engine.graphics.renderer.pipelines.DrawElementsIndirectCommand
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget2D
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.graphics.shader.Uniforms
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.model.Update
import de.hanno.hpengine.engine.model.loader.assimp.StaticModelLoader
import de.hanno.hpengine.engine.model.material.MaterialManager.Companion.createDefaultMaterial
import de.hanno.hpengine.engine.model.texture.TextureManager
import de.hanno.hpengine.engine.scene.VertexIndexBuffer
import de.hanno.hpengine.engine.transform.Transform
import de.hanno.hpengine.util.ressources.FileBasedCodeSource.Companion.toCodeSource
import org.joml.Vector3f
import org.lwjgl.BufferUtils

class SphereHolder(
    val config: Config,
    val textureManager: TextureManager,
    val gpuContext: GpuContext<OpenGl>,
    val programManager: ProgramManager<OpenGl>,
    val sphereProgram: Program<Uniforms> = config.run {
        programManager.getProgram(
            EngineAsset("shaders/mvp_vertex.glsl").toCodeSource(),
            EngineAsset("shaders/simple_color_fragment.glsl").toCodeSource()
        )
    },
    val targetBuffer: RenderTarget2D
) : RenderSystem {

    val sphereEntity = Entity("[Editor] Pivot")

    val sphere = run {
        StaticModelLoader().load("assets/models/sphere.obj", textureManager, config.directories.engineDir)
    }

    val sphereModelComponent = ModelComponent(sphereEntity, sphere, createDefaultMaterial(config, textureManager)).apply {
        sphereEntity.addComponent(this)
    }
    val sphereVertexIndexBuffer = VertexIndexBuffer(gpuContext, 10)

    val vertexIndexOffsets = sphereVertexIndexBuffer.allocateForComponent(sphereModelComponent).apply {
        sphereModelComponent.putToBuffer(sphereVertexIndexBuffer, this)
    }
    val sphereCommand = DrawElementsIndirectCommand().apply {
        count = sphere.indices.size
        primCount = 1
        firstIndex = vertexIndexOffsets.indexOffset
        baseVertex = vertexIndexOffsets.vertexOffset
        baseInstance = 0
    }
    val sphereRenderBatch = RenderBatch(
        entityBufferIndex = 0,
        isDrawLines = false,
        cameraWorldPosition = Vector3f(0f, 0f, 0f),
        drawElementsIndirectCommand = sphereCommand,
        isVisibleForCamera = true,
        update = Update.DYNAMIC,
        entityMinWorld = Vector3f(0f, 0f, 0f),
        entityMaxWorld = Vector3f(0f, 0f, 0f),
        centerWorld = Vector3f(),
        boundingSphereRadius = 1000f,
        animated = false,
        materialInfo = sphereModelComponent.material.materialInfo,
        entityIndex = sphereEntity.index,
        meshIndex = 0
    )

    val transformBuffer = BufferUtils.createFloatBuffer(16).apply {
        Transform().get(this)
    }

    override fun render(result: DrawResult, renderState: RenderState) {
        render(renderState, sphereEntity.transform.position, Vector3f(0f, 0f, 1f))
    }

    fun render(
        state: RenderState, spherePosition: Vector3f,
        color: Vector3f, useDepthTest: Boolean = true,
        beforeDraw: (Program<Uniforms>.() -> Unit)? = null
    ) {

        val scaling = (0.1f * sphereEntity.transform.position.distance(state.camera.getPosition())).coerceIn(0.5f, 1f)
        val transformation = Transform().scale(scaling).translate(spherePosition)
        if (useDepthTest) gpuContext.enable(GlCap.DEPTH_TEST) else gpuContext.disable(GlCap.DEPTH_TEST)
        targetBuffer.use(gpuContext, false)
        sphereProgram.use()
        sphereProgram.setUniformAsMatrix4("modelMatrix", transformation.get(transformBuffer))
        sphereProgram.setUniformAsMatrix4("viewMatrix", state.camera.viewMatrixAsBuffer)
        sphereProgram.setUniformAsMatrix4("projectionMatrix", state.camera.projectionMatrixAsBuffer)
        sphereProgram.setUniform("diffuseColor", color)
        sphereProgram.bindShaderStorageBuffer(7, sphereVertexIndexBuffer.vertexStructArray)
        if (beforeDraw != null) {
            sphereProgram.beforeDraw()
        }

        sphereVertexIndexBuffer.indexBuffer.draw(sphereRenderBatch, sphereProgram)

    }

    fun render(
        state: RenderState, useDepthTest: Boolean = true,
        draw: (SphereHolder.(RenderState) -> Unit)
    ) {

        val transformation = Transform()
        if (useDepthTest) gpuContext.enable(GlCap.DEPTH_TEST) else gpuContext.disable(GlCap.DEPTH_TEST)
        gpuContext.cullFace = false
        targetBuffer.use(gpuContext, false)
        sphereProgram.use()
        sphereProgram.setUniformAsMatrix4("modelMatrix", transformation.get(transformBuffer))
        sphereProgram.setUniformAsMatrix4("viewMatrix", state.camera.viewMatrixAsBuffer)
        sphereProgram.setUniformAsMatrix4("projectionMatrix", state.camera.projectionMatrixAsBuffer)
        sphereProgram.setUniform("diffuseColor", Vector3f(1f, 0f, 0f))
        sphereProgram.bindShaderStorageBuffer(7, sphereVertexIndexBuffer.vertexStructArray)

        draw(state)
    }
}