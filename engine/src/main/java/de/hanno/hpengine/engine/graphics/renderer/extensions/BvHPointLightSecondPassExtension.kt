package de.hanno.hpengine.engine.graphics.renderer.extensions

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.graphics.light.point.PointLightSystem
import de.hanno.hpengine.engine.graphics.profiled
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.SecondPassResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.RenderExtension
import de.hanno.hpengine.engine.graphics.renderer.pipelines.IntStruct
import de.hanno.hpengine.engine.graphics.renderer.pipelines.PersistentMappedStructBuffer
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.scene.HpVector3f
import de.hanno.hpengine.engine.scene.HpVector4f
import de.hanno.struct.Struct
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL42
import kotlin.math.max

class BvhNode: Struct() {
    val positionRadius by HpVector4f()
    val missPointer by IntStruct()
    val dummy0 by IntStruct()
    val dummy1 by IntStruct()
    val dummy2 by IntStruct()
    val color by HpVector3f()
    val dummy3 by IntStruct()
}

class BvHPointLightSecondPassExtension(val engineContext: EngineContext<OpenGl>): RenderExtension<OpenGl> {
    private val gpuContext = engineContext.gpuContext
    private val deferredRenderingBuffer = engineContext.deferredRenderingBuffer

    private val secondPassPointBvhComputeProgram = engineContext.programManager.getComputeProgram("second_pass_point_trivial_bvh_compute.glsl")

    private val bvh = PersistentMappedStructBuffer(0, engineContext.gpuContext, { BvhNode() })

    override fun renderSecondPassFullScreen(renderState: RenderState, secondPassResult: SecondPassResult) {
//        if (renderState.lightState.pointLights.isEmpty()) {
//            return
//        }
        bvh.enlarge(3)
        val sceneExtents = Vector3f(renderState.sceneMax).sub(renderState.sceneMin)
        val max = max(sceneExtents.x, max(sceneExtents.y, sceneExtents.z))
        val sceneCenter = Vector3f(renderState.sceneMin).add(sceneExtents.mul(0.5f))
        bvh[0].apply {
            positionRadius.set(sceneCenter)
            positionRadius.w = max
            missPointer.value = 0
        }
        bvh[1].apply {
            positionRadius.set(sceneCenter)
            positionRadius.w = 25f
            missPointer.value = 2
            color.set(Vector4f(1f,0f,0f,0f))
        }
        bvh[2].apply {
            positionRadius.set(Vector3f(5f, 5f, 5f))
            positionRadius.w = 5f
            missPointer.value = 3
            color.set(Vector4f(0f,1f,0f,0f))
        }
        val nodeCount = 3

        profiled("Seconds pass PointLights") {

            val viewMatrix = renderState.camera.viewMatrixAsBuffer
            val projectionMatrix = renderState.camera.projectionMatrixAsBuffer

            gpuContext.bindTexture(0, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.positionMap)
            gpuContext.bindTexture(1, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.normalMap)
            gpuContext.bindTexture(2, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.colorReflectivenessMap)
            gpuContext.bindTexture(3, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.motionMap)
            gpuContext.bindTexture(4, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.lightAccumulationMapOneId)
            gpuContext.bindTexture(5, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.visibilityMap)
            renderState.lightState.pointLightShadowMapStrategy.bindTextures()
            // TODO: Add glbindimagetexture to openglcontext class
            GL42.glBindImageTexture(4, deferredRenderingBuffer.lightAccumulationMapOneId, 0, false, 0, GL15.GL_READ_WRITE, GL30.GL_RGBA16F)
            secondPassPointBvhComputeProgram.use()
            secondPassPointBvhComputeProgram.setUniform("nodeCount", nodeCount)
            secondPassPointBvhComputeProgram.setUniform("pointLightCount", renderState.lightState.pointLights.size)
            secondPassPointBvhComputeProgram.setUniform("screenWidth", engineContext.config.width.toFloat())
            secondPassPointBvhComputeProgram.setUniform("screenHeight", engineContext.config.height.toFloat())
            secondPassPointBvhComputeProgram.setUniformAsMatrix4("viewMatrix", viewMatrix)
            secondPassPointBvhComputeProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix)
            secondPassPointBvhComputeProgram.setUniform("maxPointLightShadowmaps", PointLightSystem.MAX_POINTLIGHT_SHADOWMAPS)
            secondPassPointBvhComputeProgram.bindShaderStorageBuffer(1, renderState.materialBuffer)
            secondPassPointBvhComputeProgram.bindShaderStorageBuffer(2, renderState.lightState.pointLightBuffer)
            secondPassPointBvhComputeProgram.bindShaderStorageBuffer(3, bvh)
            secondPassPointBvhComputeProgram.dispatchCompute(engineContext.config.width / 16, engineContext.config.height / 16, 1)
        }
    }
}