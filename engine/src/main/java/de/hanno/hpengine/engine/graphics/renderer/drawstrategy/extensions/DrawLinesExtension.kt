package de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions

import de.hanno.hpengine.engine.backend.Backend
import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.renderer.LineRenderer
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch
import de.hanno.hpengine.engine.graphics.renderer.SimpleLineRenderer
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.transform.SimpleTransform
import org.joml.Vector3f
import org.lwjgl.BufferUtils

class DrawLinesExtension(private val engine: EngineContext<OpenGl>,
                         programManager: ProgramManager<*>) : RenderExtension<OpenGl> {

    private val linesProgram: Program = programManager.getProgramFromFileNames("mvp_vertex.glsl", "firstpass_ambient_color_fragment.glsl")
    private val identityMatrix44Buffer = BufferUtils.createFloatBuffer(16).apply {
        SimpleTransform().get(this)
    }
    private val lineRenderer = SimpleLineRenderer(engine)

    override fun renderFirstPass(backend: Backend<OpenGl>, gpuContext: GpuContext<OpenGl>, firstPassResult: FirstPassResult, renderState: RenderState) {

        if (engine.config.debug.isDrawBoundingVolumes || engine.config.debug.isDrawCameras) {

            linesProgram.use()
            linesProgram.setUniform("diffuseColor", Vector3f(0f, 1f, 0f))
            linesProgram.setUniformAsMatrix4("modelMatrix", identityMatrix44Buffer)
            linesProgram.setUniformAsMatrix4("viewMatrix", renderState.camera.viewMatrixAsBuffer)
            linesProgram.setUniformAsMatrix4("projectionMatrix", renderState.camera.projectionMatrixAsBuffer)

            renderBatches(renderState.renderBatchesStatic)
            renderBatches(renderState.renderBatchesAnimated)
            firstPassResult.linesDrawn += lineRenderer.drawLines(linesProgram)

            lineRenderer.batchLine(Vector3f(0f, 0f, 0f), Vector3f(15f, 0f, 0f))
            lineRenderer.batchLine(Vector3f(0f, 0f, 0f), Vector3f(0f, 15f, 0f))
            lineRenderer.batchLine(Vector3f(0f, 0f, 0f), Vector3f(0f, 0f, 15f))
            linesProgram.setUniform("diffuseColor", Vector3f(1f, 0f, 0f))
            var linesDrawn = lineRenderer.drawLines(linesProgram)

            lineRenderer.batchLine(Vector3f(0f, 0f, 0f), renderState.camera.getRightDirection().mul(15f))
            lineRenderer.batchLine(Vector3f(0f, 0f, 0f), renderState.camera.getUpDirection().mul(15f))
            lineRenderer.batchLine(Vector3f(0f, 0f, 0f), renderState.camera.getViewDirection().mul(15f))
            linesProgram.setUniform("diffuseColor", Vector3f(1f, 1f, 0f))
            linesDrawn += lineRenderer.drawLines(linesProgram)

            firstPassResult.linesDrawn += linesDrawn


            renderState.lightState.pointLights.forEach {
                val max = Vector3f(it.entity.position).add(Vector3f(it.radius*0.5f))
                val min = Vector3f(it.entity.position).sub(Vector3f(it.radius*0.5f))
                lineRenderer.batchLine(min, max)
            }
            linesDrawn += lineRenderer.drawLines(linesProgram)
        }
    }

    private fun renderBatches(batches: List<RenderBatch>) {
        for (batch in batches) {
            if (engine.config.debug.isDrawBoundingVolumes) {
                val renderAABBs = true
                if (renderAABBs) {
                    batchAABBLines(lineRenderer, batch.minWorld, batch.maxWorld)
                    for ((min, max) in batch.getInstanceMinMaxWorlds()) {
                        batchAABBLines(lineRenderer, min, max)
                    }
                } else {
                    val radius = batch.boundingSphereRadius
                    val center = batch.centerWorld
                    lineRenderer.batchLine(Vector3f(center).add(Vector3f(0f, radius, 0f)), Vector3f(center).add(Vector3f(radius, 0f, 0f)))
                    lineRenderer.batchLine(Vector3f(center).add(Vector3f(0f, radius, 0f)), Vector3f(center).add(Vector3f(-radius, 0f, 0f)))
                    lineRenderer.batchLine(Vector3f(center).add(Vector3f(0f, radius, 0f)), Vector3f(center).add(Vector3f(0f, 0f, radius)))
                    lineRenderer.batchLine(Vector3f(center).add(Vector3f(0f, radius, 0f)), Vector3f(center).add(Vector3f(0f, 0f, -radius)))

                    lineRenderer.batchLine(Vector3f(center).add(Vector3f(-radius, 0f, 0f)), Vector3f(center).add(Vector3f(0f, 0f, -radius)))
                    lineRenderer.batchLine(Vector3f(center).add(Vector3f(-radius, 0f, 0f)), Vector3f(center).add(Vector3f(0f, 0f, radius)))
                    lineRenderer.batchLine(Vector3f(center).add(Vector3f(radius, 0f, 0f)), Vector3f(center).add(Vector3f(0f, 0f, radius)))
                    lineRenderer.batchLine(Vector3f(center).add(Vector3f(radius, 0f, 0f)), Vector3f(center).add(Vector3f(0f, 0f, -radius)))

                    lineRenderer.batchLine(Vector3f(center).add(Vector3f(0f, -radius, 0f)), Vector3f(center).add(Vector3f(radius, 0f, 0f)))
                    lineRenderer.batchLine(Vector3f(center).add(Vector3f(0f, -radius, 0f)), Vector3f(center).add(Vector3f(-radius, 0f, 0f)))
                    lineRenderer.batchLine(Vector3f(center).add(Vector3f(0f, -radius, 0f)), Vector3f(center).add(Vector3f(0f, 0f, radius)))
                    lineRenderer.batchLine(Vector3f(center).add(Vector3f(0f, -radius, 0f)), Vector3f(center).add(Vector3f(0f, 0f, -radius)))
                    lineRenderer.batchLine(Vector3f(center).add(Vector3f(0f, -radius, 0f)), Vector3f(center).add(Vector3f(0f, radius, 0f)))
                    lineRenderer.batchLine(Vector3f(center).add(Vector3f(0f, -radius, 0f)), Vector3f(center).add(Vector3f(0f, -radius, 0f)))
                }

            }
        }
    }

    companion object {

        fun batchAABBLines(lineRenderer: LineRenderer, minWorld: Vector3f, maxWorld: Vector3f) {
            run {
                val min = Vector3f(minWorld.x(), minWorld.y(), minWorld.z())
                val max = Vector3f(minWorld.x(), minWorld.y(), maxWorld.z())
                lineRenderer.batchLine(min, max)
            }
            run {
                val min = Vector3f(minWorld.x(), minWorld.y(), minWorld.z())
                val max = Vector3f(minWorld.x(), maxWorld.y(), minWorld.z())
                lineRenderer.batchLine(min, max)
            }
            run {
                val min = Vector3f(minWorld.x(), minWorld.y(), minWorld.z())
                val max = Vector3f(maxWorld.x(), minWorld.y(), minWorld.z())
                lineRenderer.batchLine(min, max)
            }
            run {
                val min = Vector3f(minWorld.x(), maxWorld.y(), minWorld.z())
                val max = Vector3f(maxWorld.x(), maxWorld.y(), minWorld.z())
                lineRenderer.batchLine(min, max)
            }
            run {
                val min = Vector3f(minWorld.x(), maxWorld.y(), minWorld.z())
                val max = Vector3f(minWorld.x(), maxWorld.y(), maxWorld.z())
                lineRenderer.batchLine(min, max)
            }


            run {
                val min = Vector3f(maxWorld.x(), maxWorld.y(), minWorld.z())
                val max = Vector3f(maxWorld.x(), maxWorld.y(), maxWorld.z())
                lineRenderer.batchLine(min, max)
            }
            run {
                val min = Vector3f(maxWorld.x(), minWorld.y(), maxWorld.z())
                val max = Vector3f(maxWorld.x(), maxWorld.y(), maxWorld.z())
                lineRenderer.batchLine(min, max)
            }
            run {
                val min = Vector3f(minWorld.x(), maxWorld.y(), maxWorld.z())
                val max = Vector3f(maxWorld.x(), maxWorld.y(), maxWorld.z())
                lineRenderer.batchLine(min, max)
            }
            run {
                val min = Vector3f(minWorld.x(), minWorld.y(), maxWorld.z())
                val max = Vector3f(maxWorld.x(), minWorld.y(), maxWorld.z())
                lineRenderer.batchLine(min, max)
            }
            run {
                val min = Vector3f(maxWorld.x(), minWorld.y(), minWorld.z())
                val max = Vector3f(maxWorld.x(), minWorld.y(), maxWorld.z())
                lineRenderer.batchLine(min, max)
            }
            run {
                val min = Vector3f(maxWorld.x(), maxWorld.y(), minWorld.z())
                val max = Vector3f(maxWorld.x(), minWorld.y(), minWorld.z())
                lineRenderer.batchLine(min, max)
            }
            run {
                val min = Vector3f(minWorld.x(), maxWorld.y(), maxWorld.z())
                val max = Vector3f(minWorld.x(), minWorld.y(), maxWorld.z())
                lineRenderer.batchLine(min, max)
            }
        }
    }
}