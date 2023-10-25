package de.hanno.hpengine.graphics.probe

import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.buffer.vertex.QuadVertexBuffer
import de.hanno.hpengine.graphics.constants.TextureTarget
import de.hanno.hpengine.graphics.light.directional.DirectionalLightStateHolder
import de.hanno.hpengine.graphics.light.probe.ProbeRenderStrategy
import de.hanno.hpengine.graphics.renderer.deferred.DeferredRenderExtension
import de.hanno.hpengine.graphics.renderer.deferred.DeferredRenderingBuffer
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.shader.Uniforms
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.state.PrimaryCameraStateHolder
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.model.EntitiesStateHolder
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource
import org.joml.Vector3f
import org.koin.core.annotation.Single

@Single(binds = [DeferredRenderExtension::class])
class EvaluateProbeRenderExtension(
    private val graphicsApi: GraphicsApi,
    private val programManager: ProgramManager,
    private val config: Config,
    private val deferredRenderingBuffer: DeferredRenderingBuffer,
    private val directionalLightStateHolder: DirectionalLightStateHolder,
    private val entitiesStateHolder: EntitiesStateHolder,
    private val primaryCameraStateHolder: PrimaryCameraStateHolder,
): DeferredRenderExtension {

    private val fullscreenBuffer = QuadVertexBuffer(graphicsApi)

    private val probeRenderStrategy = ProbeRenderStrategy(
        config,
        graphicsApi,
        programManager,
        directionalLightStateHolder,
        entitiesStateHolder,
    )

    val evaluateProbeProgram = programManager.getProgram(
        config.engineDir.resolve("shaders/passthrough_vertex.glsl").toCodeSource(),
        config.engineDir.resolve("shaders/evaluate_probe_fragment.glsl").toCodeSource(),
        Uniforms.Empty,
        Defines()
    )

    override fun renderFirstPass(
        renderState: RenderState
    ) {
        probeRenderStrategy.renderProbes(renderState)

    }

    override fun renderSecondPassFullScreen(renderState: RenderState): Unit = graphicsApi.run {

        val deferredRenderingBuffer = deferredRenderingBuffer
        deferredRenderingBuffer.lightAccumulationBuffer.use(false)

        graphicsApi.bindTexture(0, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.positionMap)
        graphicsApi.bindTexture(1, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.normalMap)
        graphicsApi.bindTexture(2, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.colorReflectivenessMap)
        graphicsApi.bindTexture(3, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.motionMap)
        graphicsApi.bindTexture(7, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.visibilityMap)

        val camera = renderState[primaryCameraStateHolder.camera]
        evaluateProbeProgram.use()
        val camTranslation = Vector3f()
        evaluateProbeProgram.setUniform(
            "eyePosition",
            camera.transform.getTranslation(camTranslation)
        )
        evaluateProbeProgram.setUniformAsMatrix4("viewMatrix", camera.viewMatrixAsBuffer)
        evaluateProbeProgram.setUniformAsMatrix4("projectionMatrix", camera.projectionMatrixAsBuffer)
        evaluateProbeProgram.bindShaderStorageBuffer(0, deferredRenderingBuffer.exposureBuffer)
        evaluateProbeProgram.bindShaderStorageBuffer(4, probeRenderStrategy.probeGrid)
        evaluateProbeProgram.setUniform("screenWidth", config.width.toFloat())
        evaluateProbeProgram.setUniform("screenHeight", config.height.toFloat())
        evaluateProbeProgram.setUniform("extent", ProbeRenderStrategy.extent)
        evaluateProbeProgram.setUniform("dimension", ProbeRenderStrategy.dimension)
        evaluateProbeProgram.setUniform("dimensionHalf", ProbeRenderStrategy.dimensionHalf)
        fullscreenBuffer.draw(indexBuffer = null)
    }
}