package de.hanno.hpengine.renderer.drawstrategy.extensions;

import de.hanno.hpengine.config.Config;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.PerMeshInfo;
import de.hanno.hpengine.engine.Transform;
import de.hanno.hpengine.engine.model.Mesh;
import de.hanno.hpengine.renderer.GraphicsContext;
import de.hanno.hpengine.renderer.Renderer;
import de.hanno.hpengine.renderer.drawstrategy.FirstPassResult;
import de.hanno.hpengine.renderer.state.RenderState;
import de.hanno.hpengine.shader.Program;
import de.hanno.hpengine.shader.ProgramFactory;
import org.lwjgl.util.vector.ReadableVector3f;
import org.lwjgl.util.vector.Vector3f;

import java.nio.FloatBuffer;

import static de.hanno.hpengine.renderer.constants.GlCap.CULL_FACE;

public class DrawLinesExtension implements RenderExtension {

    private final Program linesProgram;
    private final FloatBuffer identityMatrix44Buffer;

    public DrawLinesExtension() throws Exception {
        identityMatrix44Buffer = new Transform().getTransformationBuffer();
        linesProgram = ProgramFactory.getInstance().getProgram("mvp_vertex.glsl", "firstpass_ambient_color_fragment.glsl");
    }

    @Override
    public void renderFirstPass(FirstPassResult firstPassResult, RenderState renderState) {

        if(Config.getInstance().isDrawLines()) {
            GraphicsContext context = GraphicsContext.getInstance();
            context.disable(CULL_FACE);
            context.depthMask(false);

            linesProgram.use();
            linesProgram.setUniform("diffuseColor", new Vector3f(0,1,0));
            linesProgram.setUniformAsMatrix4("modelMatrix", identityMatrix44Buffer);
            linesProgram.setUniformAsMatrix4("viewMatrix", renderState.camera.getViewMatrixAsBuffer());
            linesProgram.setUniformAsMatrix4("projectionMatrix", renderState.camera.getProjectionMatrixAsBuffer());

            for (PerMeshInfo mesh : renderState.perEntityInfos()) {
                if(Config.getInstance().isDrawBoundingBoxes()) {
                    batchAABBLines(mesh.getMinWorld(), mesh.getMaxWorld());
                } else {
                    float boundingSphereRadius = Mesh.getBoundingSphereRadius(mesh.getMinWorld(), mesh.getMaxWorld());
                    Renderer.getInstance().batchLine(Vector3f.add(mesh.getCenterWorld(), new Vector3f(0, boundingSphereRadius, 0), null), Vector3f.add(mesh.getCenterWorld(), new Vector3f(boundingSphereRadius, 0, 0), null));
                    Renderer.getInstance().batchLine(Vector3f.add(mesh.getCenterWorld(), new Vector3f(0, boundingSphereRadius, 0), null), Vector3f.add(mesh.getCenterWorld(), new Vector3f(-boundingSphereRadius, 0, 0), null));
                    Renderer.getInstance().batchLine(Vector3f.add(mesh.getCenterWorld(), new Vector3f(0, boundingSphereRadius, 0), null), Vector3f.add(mesh.getCenterWorld(), new Vector3f(0, 0, boundingSphereRadius), null));
                    Renderer.getInstance().batchLine(Vector3f.add(mesh.getCenterWorld(), new Vector3f(0, boundingSphereRadius, 0), null), Vector3f.add(mesh.getCenterWorld(), new Vector3f(0, 0, -boundingSphereRadius), null));
                    Renderer.getInstance().batchLine(Vector3f.add(mesh.getCenterWorld(), new Vector3f(0, -boundingSphereRadius, 0), null), Vector3f.add(mesh.getCenterWorld(), new Vector3f(boundingSphereRadius, 0, 0), null));
                    Renderer.getInstance().batchLine(Vector3f.add(mesh.getCenterWorld(), new Vector3f(0, -boundingSphereRadius, 0), null), Vector3f.add(mesh.getCenterWorld(), new Vector3f(-boundingSphereRadius, 0, 0), null));
                    Renderer.getInstance().batchLine(Vector3f.add(mesh.getCenterWorld(), new Vector3f(0, -boundingSphereRadius, 0), null), Vector3f.add(mesh.getCenterWorld(), new Vector3f(0, 0, boundingSphereRadius), null));
                    Renderer.getInstance().batchLine(Vector3f.add(mesh.getCenterWorld(), new Vector3f(0, -boundingSphereRadius, 0), null), Vector3f.add(mesh.getCenterWorld(), new Vector3f(0, 0, -boundingSphereRadius), null));
                }
            }
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

//            Renderer.getInstance().batchLine(new Vector3f(0,0,0), new Vector3f(0,15,0));
//            Renderer.getInstance().batchLine(new Vector3f(0,0,0), new Vector3f(0,-15,0));
//            Renderer.getInstance().batchLine(new Vector3f(0,0,0), new Vector3f(15,15,0));
//            linesProgram.setUniform("diffuseColor", new Vector3f(1,0,0));
//            int linesDrawn = Renderer.getInstance().drawLines(linesProgram);
//            firstPassResult.linesDrawn += linesDrawn;


            Engine.getInstance().getPhysicsFactory().debugDrawWorld();
            firstPassResult.linesDrawn += Renderer.getInstance().drawLines(linesProgram);
        }
    }

    public static void batchAABBLines(ReadableVector3f minWorld, ReadableVector3f maxWorld) {
        {
            Vector3f min = new Vector3f(minWorld.getX(), minWorld.getY(), minWorld.getZ());
            Vector3f max = new Vector3f(minWorld.getX(), minWorld.getY(), maxWorld.getZ());
            Renderer.getInstance().batchLine(min, max);
        }
        {
            Vector3f min = new Vector3f(minWorld.getX(), minWorld.getY(), minWorld.getZ());
            Vector3f max = new Vector3f(minWorld.getX(), maxWorld.getY(), minWorld.getZ());
            Renderer.getInstance().batchLine(min, max);
        }
        {
            Vector3f min = new Vector3f(minWorld.getX(), minWorld.getY(), minWorld.getZ());
            Vector3f max = new Vector3f(maxWorld.getX(), minWorld.getY(), minWorld.getZ());
            Renderer.getInstance().batchLine(min, max);
        }
        {
            Vector3f min = new Vector3f(minWorld.getX(), maxWorld.getY(), minWorld.getZ());
            Vector3f max = new Vector3f(maxWorld.getX(), maxWorld.getY(), minWorld.getZ());
            Renderer.getInstance().batchLine(min, max);
        }
        {
            Vector3f min = new Vector3f(minWorld.getX(), maxWorld.getY(), minWorld.getZ());
            Vector3f max = new Vector3f(minWorld.getX(), maxWorld.getY(), maxWorld.getZ());
            Renderer.getInstance().batchLine(min, max);
        }


        {
            Vector3f min = new Vector3f(maxWorld.getX(), maxWorld.getY(), minWorld.getZ());
            Vector3f max = new Vector3f(maxWorld.getX(), maxWorld.getY(), maxWorld.getZ());
            Renderer.getInstance().batchLine(min, max);
        }
        {
            Vector3f min = new Vector3f(maxWorld.getX(), minWorld.getY(), maxWorld.getZ());
            Vector3f max = new Vector3f(maxWorld.getX(), maxWorld.getY(), maxWorld.getZ());
            Renderer.getInstance().batchLine(min, max);
        }
        {
            Vector3f min = new Vector3f(minWorld.getX(), maxWorld.getY(), maxWorld.getZ());
            Vector3f max = new Vector3f(maxWorld.getX(), maxWorld.getY(), maxWorld.getZ());
            Renderer.getInstance().batchLine(min, max);
        }
        {
            Vector3f min = new Vector3f(minWorld.getX(), minWorld.getY(), maxWorld.getZ());
            Vector3f max = new Vector3f(maxWorld.getX(), minWorld.getY(), maxWorld.getZ());
            Renderer.getInstance().batchLine(min, max);
        }
        {
            Vector3f min = new Vector3f(maxWorld.getX(), minWorld.getY(), minWorld.getZ());
            Vector3f max = new Vector3f(maxWorld.getX(), minWorld.getY(), maxWorld.getZ());
            Renderer.getInstance().batchLine(min, max);
        }
        {
            Vector3f min = new Vector3f(maxWorld.getX(), maxWorld.getY(), minWorld.getZ());
            Vector3f max = new Vector3f(maxWorld.getX(), minWorld.getY(), minWorld.getZ());
            Renderer.getInstance().batchLine(min, max);
        }
        {
            Vector3f min = new Vector3f(minWorld.getX(), maxWorld.getY(), maxWorld.getZ());
            Vector3f max = new Vector3f(minWorld.getX(), minWorld.getY(), maxWorld.getZ());
            Renderer.getInstance().batchLine(min, max);
        }
    }
}
