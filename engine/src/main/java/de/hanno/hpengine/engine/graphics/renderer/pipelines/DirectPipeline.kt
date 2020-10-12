package de.hanno.hpengine.engine.graphics.renderer.pipelines

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.backend.gpuContext
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.graphics.EntityStruct
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.profiled
import de.hanno.hpengine.engine.graphics.renderer.DirectDrawDescription
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.PrimitiveMode.Lines
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.PrimitiveMode.Triangles
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.actuallyDraw
import de.hanno.hpengine.engine.graphics.shader.BooleanType
import de.hanno.hpengine.engine.graphics.shader.FloatType
import de.hanno.hpengine.engine.graphics.shader.IntType
import de.hanno.hpengine.engine.graphics.shader.Mat4
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.shader.SSBO
import de.hanno.hpengine.engine.graphics.shader.Uniforms
import de.hanno.hpengine.engine.graphics.shader.Vec3
import de.hanno.hpengine.engine.graphics.shader.useAndBind
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.math.Matrix4f
import de.hanno.hpengine.engine.model.material.MaterialStruct
import de.hanno.hpengine.engine.model.material.SimpleMaterial
import de.hanno.hpengine.engine.model.texture.Texture
import de.hanno.hpengine.engine.scene.AnimatedVertexStructPacked
import de.hanno.hpengine.engine.scene.VertexStructPacked
import de.hanno.hpengine.engine.transform.Transform
import org.joml.FrustumIntersection
import org.joml.Vector3f
import org.lwjgl.BufferUtils

open class DirectPipeline(private val engine: EngineContext) : Pipeline {

    private var verticesCount = 0
    private var entitiesCount = 0
    private var filteredRenderBatchesStatic = emptyList<RenderBatch>()
    private var filteredRenderBatchesAnimated = emptyList<RenderBatch>()

    override fun prepare(renderState: RenderState) = prepare(renderState, renderState.camera)

    fun prepare(renderState: RenderState, camera: Camera) {
        verticesCount = 0
        entitiesCount = 0

        filteredRenderBatchesStatic = renderState.renderBatchesStatic.filter { !it.shouldBeSkipped(camera) }
        filteredRenderBatchesAnimated = renderState.renderBatchesAnimated.filter { !it.shouldBeSkipped(camera) }
    }

    override fun draw(renderState: RenderState,
                      programStatic: Program<StaticFirstPassUniforms>,
                      programAnimated: Program<AnimatedFirstPassUniforms>,
                      firstPassResult: FirstPassResult) = profiled("Actual draw entities") {

        val mode = if (engine.config.debug.isDrawLines) Lines else Triangles

        val drawDescriptionStatic = DirectDrawDescription(renderState, filteredRenderBatchesStatic, programStatic, renderState.vertexIndexBufferStatic, this::beforeDrawStatic, mode, renderState.camera)
        drawDescriptionStatic.draw(engine.gpuContext)

        val drawDescriptionAnimated = DirectDrawDescription(renderState, filteredRenderBatchesAnimated, programAnimated, renderState.vertexIndexBufferAnimated, this::beforeDrawAnimated, mode, renderState.camera)
        drawDescriptionAnimated.draw(engine.gpuContext)

        firstPassResult.verticesDrawn += verticesCount
        firstPassResult.entitiesDrawn += entitiesCount
    }

    override fun beforeDrawStatic(renderState: RenderState, program: Program<StaticFirstPassUniforms>, renderCam: Camera) {
        beforeDraw(renderState, program, renderState.vertexIndexBufferStatic.vertexStructArray, renderCam)
    }

    override fun beforeDrawAnimated(renderState: RenderState, program: Program<AnimatedFirstPassUniforms>, renderCam: Camera) {
        beforeDraw(renderState, program, renderState.vertexIndexBufferAnimated.animatedVertexStructArray, renderCam)
    }

    fun beforeDraw(renderState: RenderState, program: Program<out FirstPassUniforms>,
                   vertexStructArray: PersistentMappedStructBuffer<*>,
                   renderCam: Camera) {
        engine.gpuContext.cullFace = !engine.config.debug.isDrawLines
        program.use()
        program.setUniforms(renderState, renderCam, engine.config)
    }

}

fun <T: FirstPassUniforms> DirectDrawDescription<T>.draw(gpuContext: GpuContext<OpenGl>) {
    beforeDraw(renderState, program, drawCam)
    vertexIndexBuffer.indexBuffer.bind()
    for (batch in renderBatches.filter { !it.hasOwnProgram }) {
        gpuContext.cullFace = batch.materialInfo.cullBackFaces
        gpuContext.depthTest = batch.materialInfo.depthTest
        program.setTextureUniforms(batch.materialInfo.maps)
        vertexIndexBuffer.indexBuffer.actuallyDraw(batch.entityBufferIndex, batch.drawElementsIndirectCommand, program, mode = mode)
    }
    val batchesWithOwnProgram = renderBatches.filter { it.hasOwnProgram }.groupBy { it.materialInfo }
    for (groupedBatches in batchesWithOwnProgram) {
        vertexIndexBuffer.indexBuffer.bind()

        val program = groupedBatches.key.program!!
        program.use()
        for(batch in groupedBatches.value) {
            beforeDraw(renderState, program as Program<T>, drawCam)
            gpuContext.cullFace = batch.materialInfo.cullBackFaces
            gpuContext.depthTest = batch.materialInfo.depthTest
            program.setTextureUniforms(batch.materialInfo.maps)
            vertexIndexBuffer.indexBuffer.actuallyDraw(batch.entityBufferIndex, batch.drawElementsIndirectCommand, program, mode = mode)
        }
    }
}

fun RenderBatch.shouldBeSkipped(cullCam: Camera): Boolean {
    if(!isVisible) return true
    val intersectAABB = cullCam.frustum.frustumIntersection.intersectAab(meshMinWorld, meshMaxWorld)
    val meshIsInFrustum = intersectAABB == FrustumIntersection.INTERSECT || intersectAABB == FrustumIntersection.INSIDE

    val visibleForCamera = meshIsInFrustum || drawElementsIndirectCommand.primCount > 1 // TODO: Better culling for instances

    val culled = !visibleForCamera
    val isForward = materialInfo.transparencyType.needsForwardRendering
    return culled || isForward
}
