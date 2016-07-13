package renderer;

import camera.Camera;
import config.Config;
import engine.model.Entity;
import org.lwjgl.util.vector.Vector4f;
import renderer.light.DirectionalLight;
import util.stopwatch.GPUProfiler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RenderExtract {
    public boolean anEntityHasMoved;
    public boolean directionalLightNeedsShadowMapRender;
    public Camera camera;
    public List<Entity> entities;
    public List<Entity> visibleEntities = new ArrayList<>();
    public DirectionalLight directionalLight;
    public boolean anyPointLightHasMoved;
    public boolean sceneInitiallyDrawn;
    public Vector4f sceneMin;
    public Vector4f sceneMax;

    public RenderExtract() {
    }

    public RenderExtract init(Camera camera, List<Entity> entities, DirectionalLight directionalLight, boolean anEntityHasMoved, boolean directionalLightNeedsShadowMapRender, boolean anyPointLightHasMoved, boolean sceneInitiallyDrawn, Vector4f sceneMin, Vector4f sceneMax) {
        this.camera = camera;
        this.entities = Collections.unmodifiableList(new ArrayList<>(entities));
        visibleEntities.clear();
        if (Config.useFrustumCulling) {
            GPUProfiler.start("Culling");
            for (int i = 0; i < entities.size(); i++) {
                if (entities.get(i).isInFrustum(camera)) {
                    visibleEntities.add(entities.get(i));
                }
            }
            GPUProfiler.end();
        }
        this.directionalLight = directionalLight;
        this.anEntityHasMoved = anEntityHasMoved;
        this.directionalLightNeedsShadowMapRender = directionalLightNeedsShadowMapRender;
        this.anyPointLightHasMoved = anyPointLightHasMoved;
        this.sceneInitiallyDrawn = sceneInitiallyDrawn;
        this.sceneMin = sceneMin;
        this.sceneMax = sceneMax;

        return this;
    }
}
