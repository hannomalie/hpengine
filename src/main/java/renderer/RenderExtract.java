package renderer;

import camera.Camera;
import config.Config;
import engine.model.Entity;
import org.lwjgl.util.vector.Vector4f;
import renderer.drawstrategy.DrawResult;
import renderer.light.DirectionalLight;
import renderer.material.MaterialFactory;
import util.stopwatch.GPUProfiler;

import java.util.*;

public class RenderExtract {
    public boolean anEntityHasMoved;
    public boolean directionalLightNeedsShadowMapRender;
    public Camera camera = new Camera();
    public List<Entity> entities;
    public List<Entity> visibleEntities = new ArrayList<>();
    public DirectionalLight directionalLight;
    public boolean anyPointLightHasMoved;
    public boolean sceneInitiallyDrawn;
    public Vector4f sceneMin;
    public Vector4f sceneMax;
    private Map properties = new HashMap<>();

    public RenderExtract() {
    }

    public RenderExtract init(Camera camera, List<Entity> entities, DirectionalLight directionalLight, boolean anEntityHasMoved, boolean directionalLightNeedsShadowMapRender, boolean anyPointLightHasMoved, boolean sceneInitiallyDrawn, Vector4f sceneMin, Vector4f sceneMax, DrawResult latestDrawResult) {
        this.camera.init(camera);
        this.entities = Collections.unmodifiableList(new ArrayList<>(entities));
        visibleEntities.clear();
        if (Config.useFrustumCulling) {
            GPUProfiler.start("Culling");
            for (int i = 0; i < entities.size(); i++) {
                if (entities.get(i).isInFrustum(camera) || entities.get(i).getInstanceCount() > 1) { // TODO: Better culling for instances
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
        if(latestDrawResult != null) {
            this.properties.putAll(latestDrawResult.getProperties());
        }

        return this;
    }
}
