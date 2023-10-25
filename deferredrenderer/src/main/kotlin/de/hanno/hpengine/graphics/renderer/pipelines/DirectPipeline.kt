package de.hanno.hpengine.graphics.renderer.pipelines


import de.hanno.hpengine.camera.Camera
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.constants.PrimitiveType
import de.hanno.hpengine.graphics.renderer.DirectDrawDescription
import de.hanno.hpengine.graphics.renderer.RenderBatch
import de.hanno.hpengine.graphics.renderer.forward.FirstPassUniforms
import de.hanno.hpengine.graphics.shader.Program
import de.hanno.hpengine.graphics.shader.TesselationControlShader
import de.hanno.hpengine.graphics.shader.Uniforms
import de.hanno.hpengine.model.material.Material
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.joml.FrustumIntersection

private val <T: Uniforms> Program<T>.tesselationControlShader: TesselationControlShader?
    get() = shaders.firstIsInstanceOrNull<TesselationControlShader>()

context(GraphicsApi)
fun DirectDrawDescription<FirstPassUniforms>.draw() {
    beforeDraw(renderState, program, drawCam)
    if(ignoreCustomPrograms) {
        program.use()
    }

    val batchesWithOwnProgram: Map<Material, List<RenderBatch>> = renderBatches.filter { it.hasOwnProgram }.groupBy { it.material }
    vertexIndexBuffer.indexBuffer.bind()
    for (groupedBatches in batchesWithOwnProgram) {
        var program: Program<FirstPassUniforms> // TODO: Assign this program in the loop below and use() only on change
        for(batch in groupedBatches.value.sortedBy { it.material.renderPriority }) {
            if (!ignoreCustomPrograms) {
                program = ((batch.program ?: this.program) as Program<FirstPassUniforms>) // TODO: This is not safe
                program.use()
            } else {
                program = this.program
            }
            program.uniforms.entityIndex = batch.entityBufferIndex
            beforeDraw(renderState, program, drawCam)
            cullFace = batch.material.cullBackFaces
            depthTest = batch.material.depthTest
            depthMask = batch.material.writesDepth
            program.setTextureUniforms(batch.material.maps)
            val primitiveType = if(program.tesselationControlShader != null) PrimitiveType.Patches else PrimitiveType.Triangles

            program.bind()
            vertexIndexBuffer.indexBuffer.draw(
                batch.drawElementsIndirectCommand,
                primitiveType = primitiveType,
                mode = mode,
                bindIndexBuffer = false,
            )
        }
    }

    beforeDraw(renderState, program, drawCam)
    vertexIndexBuffer.indexBuffer.bind()
    for (batch in renderBatches.filter { !it.hasOwnProgram }.sortedBy { it.material.renderPriority }) {
        depthMask = batch.material.writesDepth
        cullFace = batch.material.cullBackFaces
        depthTest = batch.material.depthTest
        program.setTextureUniforms(batch.material.maps)
        program.uniforms.entityIndex = batch.entityBufferIndex
        program.bind()
        vertexIndexBuffer.indexBuffer.draw(
            batch.drawElementsIndirectCommand,
            primitiveType = PrimitiveType.Triangles,
            mode = mode,
            bindIndexBuffer = false,
        )
    }

    depthMask = true // TODO: Resetting defaults here should not be necessary
}

fun RenderBatch.isCulled(cullCam: Camera): Boolean {
    if (!isVisible) return true

    val intersectAABB = cullCam.frustum.frustumIntersection.intersectAab(meshMinWorld, meshMaxWorld)
    val meshIsInFrustum = intersectAABB == FrustumIntersection.INTERSECT || intersectAABB == FrustumIntersection.INSIDE

    val visibleForCamera =
        meshIsInFrustum || drawElementsIndirectCommand.instanceCount > 1 // TODO: Better culling for instances

    return !visibleForCamera
}

val RenderBatch.isForwardRendered: Boolean get() = material.transparencyType.needsForwardRendering
