package de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions;

import de.hanno.hpengine.engine.backend.Backend;
import de.hanno.hpengine.engine.backend.BackendType;
import de.hanno.hpengine.engine.backend.EngineContext;
import de.hanno.hpengine.engine.backend.OpenGl;
import de.hanno.hpengine.engine.graphics.GpuContext;
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch;
import de.hanno.hpengine.engine.graphics.renderer.Renderer;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult;
import de.hanno.hpengine.engine.graphics.shader.Program;
import de.hanno.hpengine.engine.graphics.shader.ProgramManager;
import de.hanno.hpengine.engine.graphics.state.RenderState;
import de.hanno.hpengine.engine.transform.AABB;
import de.hanno.hpengine.engine.transform.SimpleTransform;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.util.List;

public class DrawLinesExtension implements RenderExtension<OpenGl> {

    private final Program linesProgram;
    private final FloatBuffer identityMatrix44Buffer = BufferUtils.createFloatBuffer(16);
    private final Renderer<? extends BackendType> renderer;
    private final EngineContext<?> engine;

    public DrawLinesExtension(EngineContext<?> engineContext, Renderer<? extends BackendType> renderer, ProgramManager programManager) {
        this.engine = engineContext;
        new SimpleTransform().get(identityMatrix44Buffer);
        this.renderer = renderer;
        linesProgram = programManager.getProgramFromFileNames("mvp_vertex.glsl", "firstpass_ambient_color_fragment.glsl");
    }

    @Override
    public void renderFirstPass(Backend<OpenGl> backend, GpuContext<OpenGl> gpuContext, FirstPassResult firstPassResult, RenderState renderState) {

        if(engine.getConfig().getDebug().isDrawBoundingVolumes() || engine.getConfig().getDebug().isDrawCameras()) {


            linesProgram.use();
            linesProgram.setUniform("diffuseColor", new Vector3f(0,1,0));
            linesProgram.setUniformAsMatrix4("modelMatrix", identityMatrix44Buffer);
            linesProgram.setUniformAsMatrix4("viewMatrix", renderState.getCamera().getViewMatrixAsBuffer());
            linesProgram.setUniformAsMatrix4("projectionMatrix", renderState.getCamera().getProjectionMatrixAsBuffer());

            renderBatches(renderState.getRenderBatchesStatic());
            renderBatches(renderState.getRenderBatchesAnimated());
            firstPassResult.linesDrawn += renderer.drawLines(linesProgram);

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

            renderer.batchLine(new Vector3f(0,0,0), new Vector3f(15,0,0));
            renderer.batchLine(new Vector3f(0,0,0), new Vector3f(0,15,0));
            renderer.batchLine(new Vector3f(0,0,0), new Vector3f(0,0,15));
            linesProgram.setUniform("diffuseColor", new Vector3f(1,0,0));
            int linesDrawn = renderer.drawLines(linesProgram);

            renderer.batchLine(new Vector3f(0,0,0), renderState.getCamera().getRightDirection().mul(15));
            renderer.batchLine(new Vector3f(0,0,0), renderState.getCamera().getUpDirection().mul(15));
            renderer.batchLine(new Vector3f(0,0,0), renderState.getCamera().getViewDirection().mul(15));
            linesProgram.setUniform("diffuseColor", new Vector3f(1,1,0));
            linesDrawn += renderer.drawLines(linesProgram);

            firstPassResult.linesDrawn += linesDrawn;
        }
    }

    private void renderBatches(List<RenderBatch> batches) {
        for (RenderBatch batch : batches) {
            if(engine.getConfig().getDebug().isDrawBoundingVolumes()) {
                boolean renderAABBs = true;
                if(renderAABBs) {
                    batchAABBLines(renderer, batch.getMinWorld(), batch.getMaxWorld());
                    for(AABB minMax : batch.getInstanceMinMaxWorlds()) {
                        batchAABBLines(renderer, minMax.getMin(), minMax.getMax());
                    }
                } else {
                    float radius = batch.getBoundingSphereRadius();
                    Vector3f center = batch.getCenterWorld();
                    renderer.batchLine(new Vector3f(center).add(new Vector3f(0, radius, 0)), new Vector3f(center).add(new Vector3f(radius, 0, 0)));
                    renderer.batchLine(new Vector3f(center).add(new Vector3f(0, radius, 0)), new Vector3f(center).add(new Vector3f(-radius, 0, 0)));
                    renderer.batchLine(new Vector3f(center).add(new Vector3f(0, radius, 0)), new Vector3f(center).add(new Vector3f(0, 0, radius)));
                    renderer.batchLine(new Vector3f(center).add(new Vector3f(0, radius, 0)), new Vector3f(center).add(new Vector3f(0, 0, -radius)));

                    renderer.batchLine(new Vector3f(center).add(new Vector3f(-radius, 0, 0)), new Vector3f(center).add(new Vector3f(0, 0, -radius)));
                    renderer.batchLine(new Vector3f(center).add(new Vector3f(-radius, 0, 0)), new Vector3f(center).add(new Vector3f(0, 0, radius)));
                    renderer.batchLine(new Vector3f(center).add(new Vector3f(radius, 0, 0)), new Vector3f(center).add(new Vector3f(0, 0, radius)));
                    renderer.batchLine(new Vector3f(center).add(new Vector3f(radius, 0, 0)), new Vector3f(center).add(new Vector3f(0, 0, -radius)));

                    renderer.batchLine(new Vector3f(center).add(new Vector3f(0, -radius, 0)), new Vector3f(center).add(new Vector3f(radius, 0, 0)));
                    renderer.batchLine(new Vector3f(center).add(new Vector3f(0, -radius, 0)), new Vector3f(center).add(new Vector3f(-radius, 0, 0)));
                    renderer.batchLine(new Vector3f(center).add(new Vector3f(0, -radius, 0)), new Vector3f(center).add(new Vector3f(0, 0, radius)));
                    renderer.batchLine(new Vector3f(center).add(new Vector3f(0, -radius, 0)), new Vector3f(center).add(new Vector3f(0, 0, -radius)));
                    renderer.batchLine(new Vector3f(center).add(new Vector3f(0, -radius, 0)), new Vector3f(center).add(new Vector3f(0, radius, 0)));
                    renderer.batchLine(new Vector3f(center).add(new Vector3f(0, -radius, 0)), new Vector3f(center).add(new Vector3f(0, -radius, 0)));
                }

            }
        }
    }

    public static void batchAABBLines(Renderer renderer, Vector3f minWorld, Vector3f maxWorld) {
        {
            Vector3f min = new Vector3f(minWorld.x(), minWorld.y(), minWorld.z());
            Vector3f max = new Vector3f(minWorld.x(), minWorld.y(), maxWorld.z());
            renderer.batchLine(min, max);
        }
        {
            Vector3f min = new Vector3f(minWorld.x(), minWorld.y(), minWorld.z());
            Vector3f max = new Vector3f(minWorld.x(), maxWorld.y(), minWorld.z());
            renderer.batchLine(min, max);
        }
        {
            Vector3f min = new Vector3f(minWorld.x(), minWorld.y(), minWorld.z());
            Vector3f max = new Vector3f(maxWorld.x(), minWorld.y(), minWorld.z());
            renderer.batchLine(min, max);
        }
        {
            Vector3f min = new Vector3f(minWorld.x(), maxWorld.y(), minWorld.z());
            Vector3f max = new Vector3f(maxWorld.x(), maxWorld.y(), minWorld.z());
            renderer.batchLine(min, max);
        }
        {
            Vector3f min = new Vector3f(minWorld.x(), maxWorld.y(), minWorld.z());
            Vector3f max = new Vector3f(minWorld.x(), maxWorld.y(), maxWorld.z());
            renderer.batchLine(min, max);
        }


        {
            Vector3f min = new Vector3f(maxWorld.x(), maxWorld.y(), minWorld.z());
            Vector3f max = new Vector3f(maxWorld.x(), maxWorld.y(), maxWorld.z());
            renderer.batchLine(min, max);
        }
        {
            Vector3f min = new Vector3f(maxWorld.x(), minWorld.y(), maxWorld.z());
            Vector3f max = new Vector3f(maxWorld.x(), maxWorld.y(), maxWorld.z());
            renderer.batchLine(min, max);
        }
        {
            Vector3f min = new Vector3f(minWorld.x(), maxWorld.y(), maxWorld.z());
            Vector3f max = new Vector3f(maxWorld.x(), maxWorld.y(), maxWorld.z());
            renderer.batchLine(min, max);
        }
        {
            Vector3f min = new Vector3f(minWorld.x(), minWorld.y(), maxWorld.z());
            Vector3f max = new Vector3f(maxWorld.x(), minWorld.y(), maxWorld.z());
            renderer.batchLine(min, max);
        }
        {
            Vector3f min = new Vector3f(maxWorld.x(), minWorld.y(), minWorld.z());
            Vector3f max = new Vector3f(maxWorld.x(), minWorld.y(), maxWorld.z());
            renderer.batchLine(min, max);
        }
        {
            Vector3f min = new Vector3f(maxWorld.x(), maxWorld.y(), minWorld.z());
            Vector3f max = new Vector3f(maxWorld.x(), minWorld.y(), minWorld.z());
            renderer.batchLine(min, max);
        }
        {
            Vector3f min = new Vector3f(minWorld.x(), maxWorld.y(), maxWorld.z());
            Vector3f max = new Vector3f(minWorld.x(), minWorld.y(), maxWorld.z());
            renderer.batchLine(min, max);
        }
    }
}
