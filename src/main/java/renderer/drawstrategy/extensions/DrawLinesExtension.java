package renderer.drawstrategy.extensions;

import config.Config;
import engine.AppContext;
import engine.Transform;
import engine.model.Entity;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;
import renderer.OpenGLContext;
import renderer.RenderExtract;
import renderer.Renderer;
import renderer.constants.GlCap;
import renderer.drawstrategy.FirstPassResult;
import shader.Program;
import shader.ProgramFactory;

import java.nio.FloatBuffer;

import static renderer.constants.GlCap.CULL_FACE;

public class DrawLinesExtension implements RenderExtension {

    private final Program linesProgram;
    private final FloatBuffer identityMatrix44Buffer;

    public DrawLinesExtension() throws Exception {
        identityMatrix44Buffer = new Transform().getTransformationBuffer();
        linesProgram = ProgramFactory.getInstance().getProgram("mvp_vertex.glsl", "simple_color_fragment.glsl");
    }

    @Override
    public void renderFirstPass(RenderExtract renderExtract, FirstPassResult firstPassResult) {

        if(Config.DRAWLINES_ENABLED) {
            OpenGLContext openGLContext = OpenGLContext.getInstance();
            openGLContext.disable(CULL_FACE);
            openGLContext.depthMask(false);
//            openGLContext.disable(GlCap.DEPTH_TEST);

            linesProgram.use();
            linesProgram.setUniform("diffuseColor", new Vector3f(0,1,0));
            linesProgram.setUniformAsMatrix4("modelMatrix", identityMatrix44Buffer);
            linesProgram.setUniformAsMatrix4("viewMatrix", renderExtract.camera.getViewMatrixAsBuffer());
            linesProgram.setUniformAsMatrix4("projectionMatrix", renderExtract.camera.getProjectionMatrixAsBuffer());

            for (Entity entity : renderExtract.visibleEntities) {
                if(entity.getComponents().containsKey("ModelComponent")){// && entity.getName().equals("sponza_322")) {
                    Vector4f[] minMax = entity.getMinMaxWorld();
                    Vector3f min = new Vector3f(minMax[0].x, minMax[0].y, minMax[0].z);
                    Vector3f max = new Vector3f(minMax[1].x, minMax[1].y, minMax[1].z);
                    Renderer.getInstance().batchLine(min, max);
                }
            }
            Renderer.getInstance().drawLines(linesProgram);

            linesProgram.setUniformAsMatrix4("modelMatrix", identityMatrix44Buffer);

//            int max = 500;
//            for(int x = -max; x < max; x+=25) {
//                for(int y = -max; y < max; y+=25) {
//                    Renderer.getInstance().batchLine(new Vector3f(x,y,max), new Vector3f(x,y,-max));
//                }
//                for(int z = -max; z < max; z+=25) {
//                    Renderer.getInstance().batchLine(new Vector3f(x,max,z), new Vector3f(x,-max,z));
//                }
//            }

            Renderer.getInstance().batchLine(new Vector3f(0,0,0), new Vector3f(0,15,0));
            Renderer.getInstance().batchLine(new Vector3f(0,0,0), new Vector3f(0,-15,0));
            Renderer.getInstance().batchLine(new Vector3f(0,0,0), new Vector3f(15,15,0));
            int linesDrawn = Renderer.getInstance().drawLines(linesProgram);
            firstPassResult.linesDrawn += linesDrawn;


            AppContext.getInstance().getPhysicsFactory().getDynamicsWorld().debugDrawWorld();
            firstPassResult.linesDrawn += Renderer.getInstance().drawLines(linesProgram);
        }
    }
}
