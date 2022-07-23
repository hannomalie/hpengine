package de.hanno.hpengine.camera

import Vector4fStruktImpl.Companion.type
import com.artemis.World
import com.artemis.hackedOutComponents
import de.hanno.hpengine.backend.Backend
import de.hanno.hpengine.backend.OpenGl
import de.hanno.hpengine.artemis.CameraComponent
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.RenderStateManager
import de.hanno.hpengine.graphics.renderer.drawLines
import de.hanno.hpengine.graphics.renderer.drawstrategy.FirstPassResult
import de.hanno.hpengine.graphics.renderer.drawstrategy.extensions.DeferredRenderExtension
import de.hanno.hpengine.graphics.renderer.pipelines.PersistentMappedBuffer
import de.hanno.hpengine.graphics.renderer.pipelines.typed
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.math.Vector4fStrukt
import org.joml.Vector3f
import org.joml.Vector3fc

class CameraRenderExtension(
    val config: Config,
    val gpuContext: GpuContext<OpenGl>,
    val renderStateManager: RenderStateManager,
    val programManager: ProgramManager<OpenGl>
): DeferredRenderExtension<OpenGl> {

    private val frustumLines = renderStateManager.renderState.registerState { mutableListOf<Vector3fc>() }
    private val lineVertices = PersistentMappedBuffer(24 * Vector4fStrukt.type.sizeInBytes, gpuContext).typed(Vector4fStrukt.type)

    override fun renderFirstPass(
        backend: Backend<OpenGl>,
        gpuContext: GpuContext<OpenGl>,
        firstPassResult: FirstPassResult,
        renderState: RenderState
    ) {
        if (config.debug.isDrawCameras) {
            drawLines(renderStateManager, programManager, lineVertices, renderState[frustumLines], color = Vector3f(1f, 0f, 0f))
        }
    }

    override fun extract(renderState: RenderState, world: World) {
        if (config.debug.isDrawCameras) {
            renderState[frustumLines].apply {
                clear()
                val components = world.getMapper(CameraComponent::class.java).hackedOutComponents
                components.indices.forEach { i ->
                    // TODO: cache frustum somehow for camera components
//                    val corners = components[i].frustumCorners
//
//                    addLine(corners[0], corners[1])
//                    addLine(corners[1], corners[2])
//                    addLine(corners[2], corners[3])
//                    addLine(corners[3], corners[0])
//
//                    addLine(corners[4], corners[5])
//                    addLine(corners[5], corners[6])
//                    addLine(corners[6], corners[7])
//                    addLine(corners[7], corners[4])
//
//                    addLine(corners[0], corners[6])
//                    addLine(corners[1], corners[7])
//                    addLine(corners[2], corners[4])
//                    addLine(corners[3], corners[5])
                }
            }
        }

    }
}
