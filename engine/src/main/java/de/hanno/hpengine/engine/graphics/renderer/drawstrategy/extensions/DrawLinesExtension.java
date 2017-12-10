package de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions;

import de.hanno.hpengine.engine.transform.AABB;
import de.hanno.hpengine.engine.transform.SimpleTransform;
import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch;
import de.hanno.hpengine.engine.graphics.renderer.GraphicsContext;
import de.hanno.hpengine.engine.graphics.renderer.Renderer;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult;
import de.hanno.hpengine.engine.graphics.state.RenderState;
import de.hanno.hpengine.engine.graphics.shader.Program;
import de.hanno.hpengine.engine.graphics.shader.ProgramFactory;
import org.joml.Vector3f;

import java.nio.FloatBuffer;
import java.util.List;

import static de.hanno.hpengine.engine.graphics.renderer.constants.GlCap.CULL_FACE;

public class DrawLinesExtension implements RenderExtension {

    private final Program linesProgram;
    private final FloatBuffer identityMatrix44Buffer;

    public DrawLinesExtension() throws Exception {
        identityMatrix44Buffer = new SimpleTransform().getTransformationBuffer();
        linesProgram = ProgramFactory.getInstance().getProgram("mvp_vertex.glsl", "firstpass_ambient_color_fragment.glsl");
    }

    @Override
    public void renderFirstPass(FirstPassResult firstPassResult, RenderState renderState) {

        if(Config.getInstance().isDrawBoundingVolumes()) {
            GraphicsContext context = GraphicsContext.getInstance();
            context.disable(CULL_FACE);
            context.depthMask(false);

            linesProgram.use();
            linesProgram.setUniform("diffuseColor", new Vector3f(0,1,0));
            linesProgram.setUniformAsMatrix4("modelMatrix", identityMatrix44Buffer);
            linesProgram.setUniformAsMatrix4("viewMatrix", renderState.camera.getViewMatrixAsBuffer());
            linesProgram.setUniformAsMatrix4("projectionMatrix", renderState.camera.getProjectionMatrixAsBuffer());

            renderBatches(renderState.getRenderBatchesStatic());
            renderBatches(renderState.getRenderBatchesAnimated());
            firstPassResult.linesDrawn += Renderer.getInstance().drawLines(linesProgram);

//            linesProgram.setUniform("diffuseColor", new Vector3f(1,0,0));
//            Engine.getInstance().getScene().getEntitiesContainer().drawDebug(Renderer.getInstance(), renderState.camera, linesProgram);

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

            Renderer.getInstance().batchLine(new Vector3f(0,0,0), new Vector3f(15,0,0));
            Renderer.getInstance().batchLine(new Vector3f(0,0,0), new Vector3f(0,15,0));
            Renderer.getInstance().batchLine(new Vector3f(0,0,0), new Vector3f(0,0,15));
            linesProgram.setUniform("diffuseColor", new Vector3f(1,0,0));
            int linesDrawn = Renderer.getInstance().drawLines(linesProgram);

            Renderer.getInstance().batchLine(new Vector3f(0,0,0), renderState.camera.getRightDirection().mul(15));
            Renderer.getInstance().batchLine(new Vector3f(0,0,0), renderState.camera.getUpDirection().mul(15));
            Renderer.getInstance().batchLine(new Vector3f(0,0,0), renderState.camera.getViewDirection().mul(15));
            linesProgram.setUniform("diffuseColor", new Vector3f(1,1,0));
            linesDrawn += Renderer.getInstance().drawLines(linesProgram);
//            firstPassResult.linesDrawn += linesDrawn;


            Engine.getInstance().getPhysicsFactory().debugDrawWorld();
            firstPassResult.linesDrawn += Renderer.getInstance().drawLines(linesProgram);
        }
    }

    private void renderBatches(List<RenderBatch> batches) {
        for (RenderBatch batch : batches) {
            if(Config.getInstance().isDrawBoundingVolumes()) {
                boolean renderAABBs = true;
                if(renderAABBs) {
                    batchAABBLines(batch.getMinWorld(), batch.getMaxWorld());
                    for(AABB minMax : batch.getInstanceMinMaxWorlds()) {
                        batchAABBLines(minMax.getMin(), minMax.getMax());
                    }
                } else {
                    float radius = batch.getBoundingSphereRadius();
                    Renderer renderer = Renderer.getInstance();
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

    public static void batchAABBLines(Vector3f minWorld, Vector3f maxWorld) {
        {
            Vector3f min = new Vector3f(minWorld.x(), minWorld.y(), minWorld.z());
            Vector3f max = new Vector3f(minWorld.x(), minWorld.y(), maxWorld.z());
            Renderer.getInstance().batchLine(min, max);
        }
        {
            Vector3f min = new Vector3f(minWorld.x(), minWorld.y(), minWorld.z());
            Vector3f max = new Vector3f(minWorld.x(), maxWorld.y(), minWorld.z());
            Renderer.getInstance().batchLine(min, max);
        }
        {
            Vector3f min = new Vector3f(minWorld.x(), minWorld.y(), minWorld.z());
            Vector3f max = new Vector3f(maxWorld.x(), minWorld.y(), minWorld.z());
            Renderer.getInstance().batchLine(min, max);
        }
        {
            Vector3f min = new Vector3f(minWorld.x(), maxWorld.y(), minWorld.z());
            Vector3f max = new Vector3f(maxWorld.x(), maxWorld.y(), minWorld.z());
            Renderer.getInstance().batchLine(min, max);
        }
        {
            Vector3f min = new Vector3f(minWorld.x(), maxWorld.y(), minWorld.z());
            Vector3f max = new Vector3f(minWorld.x(), maxWorld.y(), maxWorld.z());
            Renderer.getInstance().batchLine(min, max);
        }


        {
            Vector3f min = new Vector3f(maxWorld.x(), maxWorld.y(), minWorld.z());
            Vector3f max = new Vector3f(maxWorld.x(), maxWorld.y(), maxWorld.z());
            Renderer.getInstance().batchLine(min, max);
        }
        {
            Vector3f min = new Vector3f(maxWorld.x(), minWorld.y(), maxWorld.z());
            Vector3f max = new Vector3f(maxWorld.x(), maxWorld.y(), maxWorld.z());
            Renderer.getInstance().batchLine(min, max);
        }
        {
            Vector3f min = new Vector3f(minWorld.x(), maxWorld.y(), maxWorld.z());
            Vector3f max = new Vector3f(maxWorld.x(), maxWorld.y(), maxWorld.z());
            Renderer.getInstance().batchLine(min, max);
        }
        {
            Vector3f min = new Vector3f(minWorld.x(), minWorld.y(), maxWorld.z());
            Vector3f max = new Vector3f(maxWorld.x(), minWorld.y(), maxWorld.z());
            Renderer.getInstance().batchLine(min, max);
        }
        {
            Vector3f min = new Vector3f(maxWorld.x(), minWorld.y(), minWorld.z());
            Vector3f max = new Vector3f(maxWorld.x(), minWorld.y(), maxWorld.z());
            Renderer.getInstance().batchLine(min, max);
        }
        {
            Vector3f min = new Vector3f(maxWorld.x(), maxWorld.y(), minWorld.z());
            Vector3f max = new Vector3f(maxWorld.x(), minWorld.y(), minWorld.z());
            Renderer.getInstance().batchLine(min, max);
        }
        {
            Vector3f min = new Vector3f(minWorld.x(), maxWorld.y(), maxWorld.z());
            Vector3f max = new Vector3f(minWorld.x(), minWorld.y(), maxWorld.z());
            Renderer.getInstance().batchLine(min, max);
        }
    }
}
