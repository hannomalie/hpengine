package de.hanno.hpengine.engine.graphics.renderer.pipelines

import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.profiled
import de.hanno.hpengine.engine.graphics.renderer.DirectDrawDescription
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.PrimitiveType
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.RenderingMode.Faces
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.RenderingMode.Lines
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.actuallyDraw
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.model.material.Material
import org.joml.FrustumIntersection

open class DirectPipeline(
    private val config: Config,
    private val gpuContext: GpuContext<OpenGl>,
    private val useBackFaceCulling: Boolean = true,
    private val shouldBeSkipped: RenderBatch.(Camera) -> Boolean = RenderBatch::isCulledOrForwardRendered
) : Pipeline {

    private var verticesCount = 0
    private var entitiesCount = 0
    private var filteredRenderBatchesStatic = emptyList<RenderBatch>()
    private var filteredRenderBatchesAnimated = emptyList<RenderBatch>()

    override fun prepare(renderState: RenderState) = prepare(renderState, renderState.camera)

    fun prepare(renderState: RenderState, camera: Camera) {
        if(config.debug.freezeCulling) return
        verticesCount = 0
        entitiesCount = 0

        filteredRenderBatchesStatic = renderState.renderBatchesStatic.filterNot { it.shouldBeSkipped(camera) || it.isCulledOrForwardRendered(camera) }
        filteredRenderBatchesAnimated = renderState.renderBatchesAnimated.filterNot { it.shouldBeSkipped(camera) || it.isCulledOrForwardRendered(camera) }
    }

    override fun draw(renderState: RenderState,
                      programStatic: Program<StaticFirstPassUniforms>,
                      programAnimated: Program<AnimatedFirstPassUniforms>,
                      firstPassResult: FirstPassResult) = profiled("Actual draw entities") {

        val mode = if (config.debug.isDrawLines) Lines else Faces

        val drawDescriptionStatic = DirectDrawDescription(renderState, filteredRenderBatchesStatic, programStatic, renderState.vertexIndexBufferStatic, this::beforeDrawStatic, mode, renderState.camera)
        drawDescriptionStatic.draw(gpuContext)

        val drawDescriptionAnimated = DirectDrawDescription(renderState, filteredRenderBatchesAnimated, programAnimated, renderState.vertexIndexBufferAnimated, this::beforeDrawAnimated, mode, renderState.camera)
        drawDescriptionAnimated.draw(gpuContext)

        firstPassResult.verticesDrawn += verticesCount
        firstPassResult.entitiesDrawn += entitiesCount
    }

    override fun beforeDrawStatic(renderState: RenderState, program: Program<StaticFirstPassUniforms>, renderCam: Camera) {
        beforeDraw(renderState, program, renderCam)
    }

    override fun beforeDrawAnimated(renderState: RenderState, program: Program<AnimatedFirstPassUniforms>, renderCam: Camera) {
        beforeDraw(renderState, program, renderCam)
    }

    fun beforeDraw(
        renderState: RenderState, program: Program<out FirstPassUniforms>,
        renderCam: Camera
    ) {
        gpuContext.cullFace = useBackFaceCulling || !config.debug.isDrawLines
        program.use()
        program.setUniforms(renderState, renderCam, config, false)
        program.uniforms.indirect = false
    }

}

fun <T: FirstPassUniforms> DirectDrawDescription<T>.draw(gpuContext: GpuContext<OpenGl>) {
    beforeDraw(renderState, program, drawCam)
    val batchesWithOwnProgram: Map<Material, List<RenderBatch>> = renderBatches.filter { it.hasOwnProgram }.groupBy { it.material }
    vertexIndexBuffer.indexBuffer.bind()
    for (groupedBatches in batchesWithOwnProgram) {
        var program: Program<T> // TODO: Assign this program in the loop below and use() only on change
        for(batch in groupedBatches.value.sortedBy { it.material.renderPriority }) {
            program = (batch.program ?: this.program) as Program<T>
            program.use()
            program.uniforms.entityIndex = batch.entityBufferIndex
            beforeDraw(renderState, program, drawCam)
            gpuContext.cullFace = batch.material.cullBackFaces
            gpuContext.depthTest = batch.material.depthTest
            gpuContext.depthMask = batch.material.writesDepth
            program.setTextureUniforms(batch.material.maps)
            val primitiveType = if(program.tesselationControlShader != null) PrimitiveType.Patches else PrimitiveType.Triangles

            vertexIndexBuffer.indexBuffer.actuallyDraw(batch.entityBufferIndex, batch.drawElementsIndirectCommand, program, mode = mode, primitiveType = primitiveType)
        }
    }

    beforeDraw(renderState, program, drawCam)
    vertexIndexBuffer.indexBuffer.bind()
    for (batch in renderBatches.filter { !it.hasOwnProgram }.sortedBy { it.material.renderPriority }) {
        gpuContext.depthMask = batch.material.writesDepth
        gpuContext.cullFace = batch.material.cullBackFaces
        gpuContext.depthTest = batch.material.depthTest
        program.setTextureUniforms(batch.material.maps)
        program.uniforms.entityIndex = batch.entityBufferIndex
        vertexIndexBuffer.indexBuffer.actuallyDraw(batch.entityBufferIndex, batch.drawElementsIndirectCommand, program, mode = mode, primitiveType = PrimitiveType.Triangles)
    }

    gpuContext.depthMask = true // TODO: Resetting defaults here should not be necessary
}

fun RenderBatch.isCulledOrForwardRendered(cullCam: Camera): Boolean {
    if(!isVisible) return true
    val intersectAABB = cullCam.frustum.frustumIntersection.intersectAab(meshMinWorld, meshMaxWorld)
    val meshIsInFrustum = intersectAABB == FrustumIntersection.INTERSECT || intersectAABB == FrustumIntersection.INSIDE

    val visibleForCamera = meshIsInFrustum || drawElementsIndirectCommand.instanceCount > 1 // TODO: Better culling for instances

    val culled = !visibleForCamera
    val isForward = material.transparencyType.needsForwardRendering
    return culled || isForward
}
fun RenderBatch.isForwardRendered(): Boolean {
    val isForward = material.transparencyType.needsForwardRendering
    return isForward || !isVisible
}
