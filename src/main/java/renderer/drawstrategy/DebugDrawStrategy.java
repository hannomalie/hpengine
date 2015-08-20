package renderer.drawstrategy;

import camera.Camera;
import com.bulletphysics.dynamics.DynamicsWorld;
import component.ModelComponent;
import config.Config;
import engine.Transform;
import engine.World;
import engine.model.Entity;
import octree.Octree;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;
import renderer.Renderer;
import renderer.light.AreaLight;
import renderer.light.LightFactory;
import renderer.light.PointLight;
import renderer.light.TubeLight;
import shader.Program;
import texture.CubeMap;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

public class DebugDrawStrategy extends SimpleDrawStrategy {

    private FloatBuffer identityMatrixBuffer = BufferUtils.createFloatBuffer(16);

    protected Program linesProgram;

    public DebugDrawStrategy(Renderer renderer) {
        super(renderer);
        linesProgram = renderer.getProgramFactory().getProgram("mvp_vertex.glsl", "simple_color_fragment.glsl");
    }

    @Override
    public void draw(World world) {
        LightFactory lightFactory = world.getRenderer().getLightFactory();
        drawDebug(world.getActiveCamera(), world.getPhysicsFactory().getDynamicsWorld(),
                world.getScene().getOctree(), world.getScene().getEntities(),
                lightFactory.getPointLights(), lightFactory.getTubeLights(),
                lightFactory.getAreaLights(), world.getRenderer().getEnvironmentMap());
    }


    public void drawDebug(Camera camera, DynamicsWorld dynamicsWorld, Octree octree, List<Entity> entities, List<PointLight> pointLights, List<TubeLight> tubeLights, List<AreaLight> areaLights, CubeMap cubeMap) {
        ///////////// firstpass
        GBuffer gBuffer = renderer.getGBuffer();

        gBuffer.use(true);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_BLEND);

        linesProgram.use();
        linesProgram.setUniform("screenWidth", (float) Config.WIDTH);
        linesProgram.setUniform("screenHeight", (float) Config.HEIGHT);
        linesProgram.setUniformAsMatrix4("viewMatrix", camera.getViewMatrixAsBuffer());
        linesProgram.setUniformAsMatrix4("projectionMatrix", camera.getProjectionMatrixAsBuffer());
        linesProgram.setUniform("eyePosition", camera.getPosition());

        if(World.DRAWSCENE_ENABLED) {
            linesProgram.setUniform("diffuseColor", new Vector3f(0,0,1));
            List<Entity> visibleEntities = new ArrayList<>();
            if (World.useFrustumCulling) {
                visibleEntities.addAll(octree.getVisible(camera));
                for (int i = 0; i < visibleEntities.size(); i++) {
                    if (!visibleEntities.get(i).isInFrustum(camera)) {
                        visibleEntities.remove(i);
                    }
                }
            } else {
                visibleEntities.addAll(octree.getEntities());
            }

            for (Entity entity : visibleEntities) {
                entity.getComponentOption(ModelComponent.class).ifPresent(modelComponent -> {
                    modelComponent.drawDebug(linesProgram, entity.getModelMatrixAsBuffer());
                });
            }
            linesProgram.setUniform("diffuseColor", new Vector3f(0,1,0));
            for (Entity entity : visibleEntities) {
                linesProgram.setUniformAsMatrix4("modelMatrix", entity.getModelMatrixAsBuffer());
                renderer.drawLine(entity.getWorldPosition(), Vector3f.add(entity.getWorldPosition(), (Vector3f) new Vector3f(Transform.WORLD_VIEW).scale(1.5f), null));
                renderer.drawLine(entity.getWorldPosition(), Vector3f.add(entity.getWorldPosition(), (Vector3f) new Vector3f(Transform.WORLD_RIGHT).scale(1.5f), null));
                renderer.drawLine(entity.getWorldPosition(), Vector3f.add(entity.getWorldPosition(), (Vector3f) new Vector3f(Transform.WORLD_UP).scale(1.5f), null));
            }
            renderer.drawLines(linesProgram);
            // draw coord system for entity
            linesProgram.setUniformAsMatrix4("modelMatrix", identityMatrixBuffer);
            linesProgram.setUniform("diffuseColor", new Vector3f(1,0,1));
            for (Entity entity : visibleEntities) {
                Vector4f view = Matrix4f.transform(entity.getModelMatrix(), new Vector4f(Transform.WORLD_VIEW.x, Transform.WORLD_VIEW.y, Transform.WORLD_VIEW.z, 0.0f), null);
                Vector4f right = Matrix4f.transform(entity.getModelMatrix(), new Vector4f(Transform.WORLD_RIGHT.x, Transform.WORLD_RIGHT.y, Transform.WORLD_RIGHT.z, 0.0f), null);
                Vector4f up = Matrix4f.transform(entity.getModelMatrix(), new Vector4f(Transform.WORLD_UP.x, Transform.WORLD_UP.y, Transform.WORLD_UP.z, 0.0f), null);

                renderer.drawLine(entity.getWorldPosition(), (Vector3f) Vector3f.add(entity.getWorldPosition(), new Vector3f(view.x, view.y, view.z), null).scale(1));
                renderer.drawLine(entity.getWorldPosition(), (Vector3f) Vector3f.add(entity.getWorldPosition(), new Vector3f(right.x, right.y, right.z), null).scale(1));
                renderer.drawLine(entity.getWorldPosition(), (Vector3f) Vector3f.add(entity.getWorldPosition(), new Vector3f(up.x, up.y, up.z), null).scale(1));
            }
            renderer.drawLines(linesProgram);
            linesProgram.setUniform("diffuseColor", new Vector3f(0.5f,0.5f,0));
            for (Entity entity : visibleEntities) {
                Vector4f view = Matrix4f.transform(entity.getViewMatrix(), new Vector4f(entity.getViewDirection().x, entity.getViewDirection().y, entity.getViewDirection().z, 0.0f), null);
                Vector4f right = Matrix4f.transform(entity.getViewMatrix(), new Vector4f(entity.getRightDirection().x, entity.getRightDirection().y, entity.getRightDirection().z, 0.0f), null);
                Vector4f up = Matrix4f.transform(entity.getViewMatrix(), new Vector4f(entity.getUpDirection().x, entity.getUpDirection().y, entity.getUpDirection().z, 0.0f), null);

                renderer.drawLine(entity.getWorldPosition(), (Vector3f) Vector3f.add(entity.getWorldPosition(), new Vector3f(view.x, view.y, view.z), null).scale(1));
                renderer.drawLine(entity.getWorldPosition(), (Vector3f) Vector3f.add(entity.getWorldPosition(), new Vector3f(right.x, right.y, right.z), null).scale(1));
                renderer.drawLine(entity.getWorldPosition(), (Vector3f) Vector3f.add(entity.getWorldPosition(), new Vector3f(up.x, up.y, up.z), null).scale(1));
            }
            renderer.drawLines(linesProgram);
        }

        linesProgram.setUniformAsMatrix4("modelMatrix", identityMatrixBuffer);
        if (World.DRAWLIGHTS_ENABLED) {
            linesProgram.setUniform("diffuseColor", new Vector3f(0,1,1));
            for (Entity entity : pointLights) {
                entity.getComponent(ModelComponent.class).drawDebug(linesProgram, entity.getModelMatrixAsBuffer());
            }
            for (Entity entity : tubeLights) {
                entity.getComponent(ModelComponent.class).drawDebug(linesProgram, entity.getModelMatrixAsBuffer());
            }
        }

        if (Octree.DRAW_LINES) {
            linesProgram.setUniform("diffuseColor", new Vector3f(1,1,1));
            octree.drawDebug(renderer, camera, linesProgram);
        }

        if (World.DRAW_PROBES) {
            linesProgram.setUniform("diffuseColor", new Vector3f(0,1,0));
            linesProgram.setUniformAsMatrix4("modelMatrix", identityMatrixBuffer);
            renderer.getEnvironmentProbeFactory().drawDebug(linesProgram, octree);
        }

        renderer.drawLine(new Vector3f(), new Vector3f(15,0,0));
        renderer.drawLine(new Vector3f(), new Vector3f(0,15,0));
        renderer.drawLine(new Vector3f(), new Vector3f(0,0,-15));
        renderer.drawLine(new Vector3f(), (Vector3f) ((Vector3f) (camera.getViewDirection())).scale(15));
        renderer.drawLine(new Vector3f(), (Vector3f) ((Vector3f) (camera.getRightDirection())).scale(15));
        renderer.drawLine(new Vector3f(), (Vector3f) ((Vector3f) (camera.getUpDirection())).scale(15));
        dynamicsWorld.debugDrawWorld();
        renderer.drawLines(linesProgram);

        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        ////////////////////

        drawSecondPass(camera, renderer.getLightFactory().getDirectionalLight(), pointLights, tubeLights, areaLights, cubeMap);

        GL11.glViewport(0, 0, Config.WIDTH, Config.HEIGHT);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        renderer.drawToQuad(renderer.getGBuffer().getPositionMap()); // the first color attachment
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }
}
