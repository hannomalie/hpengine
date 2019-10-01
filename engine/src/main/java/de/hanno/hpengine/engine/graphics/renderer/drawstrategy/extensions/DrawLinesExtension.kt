package de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions

import de.hanno.hpengine.engine.backend.Backend
import de.hanno.hpengine.engine.backend.BackendType
import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch
import de.hanno.hpengine.engine.graphics.renderer.Renderer
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.SimpleTransform
import org.joml.Vector3f
import org.lwjgl.BufferUtils

import java.nio.FloatBuffer

import de.hanno.hpengine.engine.math.toHp

class DrawLinesExtension(private val engine: EngineContext<*>, private val renderer: Renderer<out BackendType>, programManager: ProgramManager<*>) : RenderExtension<OpenGl> {

    private val linesProgram: Program
    private val identityMatrix44Buffer = BufferUtils.createFloatBuffer(16)

    init {
        SimpleTransform().get(identityMatrix44Buffer)
        linesProgram = programManager.getProgramFromFileNames("mvp_vertex.glsl", "firstpass_ambient_color_fragment.glsl")
    }

    override fun renderFirstPass(backend: Backend<OpenGl>, gpuContext: GpuContext<OpenGl>, firstPassResult: FirstPassResult, renderState: RenderState) {

        if (engine.config.debug.isDrawBoundingVolumes || engine.config.debug.isDrawCameras) {


            linesProgram.use()
            linesProgram.setUniform("diffuseColor", Vector3f(0f, 1f, 0f))
            linesProgram.setUniformAsMatrix4("modelMatrix", identityMatrix44Buffer)
            linesProgram.setUniformAsMatrix4("viewMatrix", renderState.camera.viewMatrixAsBuffer)
            linesProgram.setUniformAsMatrix4("projectionMatrix", renderState.camera.projectionMatrixAsBuffer)

            renderBatches(renderState.renderBatchesStatic)
            renderBatches(renderState.renderBatchesAnimated)
            firstPassResult.linesDrawn += renderer.drawLines(linesProgram)

            //            linesProgram.setUniform("diffuseColor", new Vector3f(1,0,0));
            //            managerContext.getSceneManager().getScene().getEntityManager().drawDebug(Renderer.getInstance(), renderState.getCamera(), linesProgram);

            //            linesProgram.setUniformAsMatrix4("modelMatrix", identityMatrix44Buffer);
            //            int max = 500;
            //            for(int x = -max; x < max; x+=25) {
            //                for(int y = -max; y < max; y+=25) {
            //                    Renderer.getInstance().batchLine(new Vector3f(x,y,max), new Vector3f(x,y,-max));
            //                }
            //                for(int z = -max; z < max; z+=25) {
            //                    Renderer.getInstance().batchLine(new Vector3f(x,max,z), new Vector3f(x,-max,z));
            //                }
            //            }

            renderer.batchLine(Vector3f(0f, 0f, 0f), Vector3f(15f, 0f, 0f))
            renderer.batchLine(Vector3f(0f, 0f, 0f), Vector3f(0f, 15f, 0f))
            renderer.batchLine(Vector3f(0f, 0f, 0f), Vector3f(0f, 0f, 15f))
            linesProgram.setUniform("diffuseColor", Vector3f(1f, 0f, 0f))
            var linesDrawn = renderer.drawLines(linesProgram)

            renderer.batchLine(Vector3f(0f, 0f, 0f), renderState.camera.getRightDirection().mul(15f))
            renderer.batchLine(Vector3f(0f, 0f, 0f), renderState.camera.getUpDirection().mul(15f))
            renderer.batchLine(Vector3f(0f, 0f, 0f), renderState.camera.getViewDirection().mul(15f))
            linesProgram.setUniform("diffuseColor", Vector3f(1f, 1f, 0f))
            linesDrawn += renderer.drawLines(linesProgram)

            firstPassResult.linesDrawn += linesDrawn
        }
    }

    private fun renderBatches(batches: List<RenderBatch>) {
        for (batch in batches) {
            if (engine.config.debug.isDrawBoundingVolumes) {
                val renderAABBs = true
                if (renderAABBs) {
                    batchAABBLines(renderer, batch.minWorld, batch.maxWorld)
                    for ((min, max) in batch.getInstanceMinMaxWorlds()) {
                        batchAABBLines(renderer, min, max)
                    }
                } else {
                    val radius = batch.boundingSphereRadius
                    val center = batch.centerWorld
                    renderer.batchLine(Vector3f(center).add(Vector3f(0f, radius, 0f)), Vector3f(center).add(Vector3f(radius, 0f, 0f)))
                    renderer.batchLine(Vector3f(center).add(Vector3f(0f, radius, 0f)), Vector3f(center).add(Vector3f(-radius, 0f, 0f)))
                    renderer.batchLine(Vector3f(center).add(Vector3f(0f, radius, 0f)), Vector3f(center).add(Vector3f(0f, 0f, radius)))
                    renderer.batchLine(Vector3f(center).add(Vector3f(0f, radius, 0f)), Vector3f(center).add(Vector3f(0f, 0f, -radius)))

                    renderer.batchLine(Vector3f(center).add(Vector3f(-radius, 0f, 0f)), Vector3f(center).add(Vector3f(0f, 0f, -radius)))
                    renderer.batchLine(Vector3f(center).add(Vector3f(-radius, 0f, 0f)), Vector3f(center).add(Vector3f(0f, 0f, radius)))
                    renderer.batchLine(Vector3f(center).add(Vector3f(radius, 0f, 0f)), Vector3f(center).add(Vector3f(0f, 0f, radius)))
                    renderer.batchLine(Vector3f(center).add(Vector3f(radius, 0f, 0f)), Vector3f(center).add(Vector3f(0f, 0f, -radius)))

                    renderer.batchLine(Vector3f(center).add(Vector3f(0f, -radius, 0f)), Vector3f(center).add(Vector3f(radius, 0f, 0f)))
                    renderer.batchLine(Vector3f(center).add(Vector3f(0f, -radius, 0f)), Vector3f(center).add(Vector3f(-radius, 0f, 0f)))
                    renderer.batchLine(Vector3f(center).add(Vector3f(0f, -radius, 0f)), Vector3f(center).add(Vector3f(0f, 0f, radius)))
                    renderer.batchLine(Vector3f(center).add(Vector3f(0f, -radius, 0f)), Vector3f(center).add(Vector3f(0f, 0f, -radius)))
                    renderer.batchLine(Vector3f(center).add(Vector3f(0f, -radius, 0f)), Vector3f(center).add(Vector3f(0f, radius, 0f)))
                    renderer.batchLine(Vector3f(center).add(Vector3f(0f, -radius, 0f)), Vector3f(center).add(Vector3f(0f, -radius, 0f)))
                }

            }
        }
    }

    companion object {

        fun batchAABBLines(renderer: Renderer<*>, minWorld: Vector3f, maxWorld: Vector3f) {
            run {
                val min = Vector3f(minWorld.x(), minWorld.y(), minWorld.z())
                val max = Vector3f(minWorld.x(), minWorld.y(), maxWorld.z())
                renderer.batchLine(min, max)
            }
            run {
                val min = Vector3f(minWorld.x(), minWorld.y(), minWorld.z())
                val max = Vector3f(minWorld.x(), maxWorld.y(), minWorld.z())
                renderer.batchLine(min, max)
            }
            run {
                val min = Vector3f(minWorld.x(), minWorld.y(), minWorld.z())
                val max = Vector3f(maxWorld.x(), minWorld.y(), minWorld.z())
                renderer.batchLine(min, max)
            }
            run {
                val min = Vector3f(minWorld.x(), maxWorld.y(), minWorld.z())
                val max = Vector3f(maxWorld.x(), maxWorld.y(), minWorld.z())
                renderer.batchLine(min, max)
            }
            run {
                val min = Vector3f(minWorld.x(), maxWorld.y(), minWorld.z())
                val max = Vector3f(minWorld.x(), maxWorld.y(), maxWorld.z())
                renderer.batchLine(min, max)
            }


            run {
                val min = Vector3f(maxWorld.x(), maxWorld.y(), minWorld.z())
                val max = Vector3f(maxWorld.x(), maxWorld.y(), maxWorld.z())
                renderer.batchLine(min, max)
            }
            run {
                val min = Vector3f(maxWorld.x(), minWorld.y(), maxWorld.z())
                val max = Vector3f(maxWorld.x(), maxWorld.y(), maxWorld.z())
                renderer.batchLine(min, max)
            }
            run {
                val min = Vector3f(minWorld.x(), maxWorld.y(), maxWorld.z())
                val max = Vector3f(maxWorld.x(), maxWorld.y(), maxWorld.z())
                renderer.batchLine(min, max)
            }
            run {
                val min = Vector3f(minWorld.x(), minWorld.y(), maxWorld.z())
                val max = Vector3f(maxWorld.x(), minWorld.y(), maxWorld.z())
                renderer.batchLine(min, max)
            }
            run {
                val min = Vector3f(maxWorld.x(), minWorld.y(), minWorld.z())
                val max = Vector3f(maxWorld.x(), minWorld.y(), maxWorld.z())
                renderer.batchLine(min, max)
            }
            run {
                val min = Vector3f(maxWorld.x(), maxWorld.y(), minWorld.z())
                val max = Vector3f(maxWorld.x(), minWorld.y(), minWorld.z())
                renderer.batchLine(min, max)
            }
            run {
                val min = Vector3f(minWorld.x(), maxWorld.y(), maxWorld.z())
                val max = Vector3f(minWorld.x(), minWorld.y(), maxWorld.z())
                renderer.batchLine(min, max)
            }
        }
    }
}
