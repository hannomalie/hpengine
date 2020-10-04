package de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions

import de.hanno.hpengine.engine.backend.Backend
import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.backend.gpuContext
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.renderer.addAABBLines
import de.hanno.hpengine.engine.graphics.renderer.addLine
import de.hanno.hpengine.engine.graphics.renderer.drawLines
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult
import de.hanno.hpengine.engine.graphics.renderer.pipelines.PersistentMappedStructBuffer
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.scene.VertexStructPacked
import org.joml.Vector3f
import org.joml.Vector3fc

class DrawLinesExtension(private val engineContext: EngineContext) : RenderExtension<OpenGl> {

    private val lineVertices = PersistentMappedStructBuffer(100, engineContext.gpuContext, { VertexStructPacked() })

    override fun renderFirstPass(backend: Backend<OpenGl>, gpuContext: GpuContext<OpenGl>, firstPassResult: FirstPassResult, renderState: RenderState) {

        if (engineContext.config.debug.isDrawBoundingVolumes || engineContext.config.debug.isDrawCameras) {

            run {
                val linePoints = mutableListOf<Vector3fc>().apply {
                    renderState.renderBatchesStatic.forEach { batch ->
                        addAABBLines(batch.meshMinWorld, batch.meshMaxWorld)
                    }
                    renderState.renderBatchesAnimated.forEach { batch ->
                        addAABBLines(batch.meshMinWorld, batch.meshMaxWorld)
                    }
                }
                engineContext.drawLines(lineVertices, linePoints, color = Vector3f(0f, 1f, 0f))
            }

            run {
                val linePoints = mutableListOf<Vector3fc>().apply {
                    addLine(Vector3f(0f, 0f, 0f), Vector3f(15f, 0f, 0f))
                    addLine(Vector3f(0f, 0f, 0f), Vector3f(0f, 15f, 0f))
                    addLine(Vector3f(0f, 0f, 0f), Vector3f(0f, 0f, 15f))
                }
                engineContext.drawLines(lineVertices, linePoints, color = Vector3f(1f, 0f, 0f))
            }

            run {
                val linePoints = mutableListOf<Vector3fc>().apply {
                    val camera = renderState.camera
                    addLine(Vector3f(0f, 0f, 0f), camera.getRightDirection().mul(15f))
                    addLine(Vector3f(0f, 0f, 0f), camera.getUpDirection().mul(15f))
                    addLine(Vector3f(0f, 0f, 0f), camera.getViewDirection().mul(15f))
                }
                engineContext.drawLines(lineVertices, linePoints, color = Vector3f(1f, 1f, 0f))
            }

            run {
                val linePoints = mutableListOf<Vector3fc>().apply {
                    renderState.lightState.pointLights.forEach {
                        val max = Vector3f(it.entity.transform.position).add(Vector3f(it.radius*0.5f))
                        val min = Vector3f(it.entity.transform.position).sub(Vector3f(it.radius*0.5f))
                        addLine(min, max)
                    }
                }
                engineContext.drawLines(lineVertices, linePoints, color = Vector3f(1f, 0f, 0f))
            }
        }
    }

}
