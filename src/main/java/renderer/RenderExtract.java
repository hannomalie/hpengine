package renderer;

import camera.Camera;
import config.Config;
import engine.model.Entity;
import renderer.light.DirectionalLight;
import util.stopwatch.GPUProfiler;

import java.util.ArrayList;
import java.util.List;

public class RenderExtract {
    public final boolean anEntityHasMoved;
    public final boolean directionalLightNeedsShadowMapRender;
    public final Camera camera;
    public final List<Entity> entities;
    public final List<Entity> visibleEntities = new ArrayList<>();
    public final DirectionalLight directionalLight;

    public RenderExtract(Camera camera, List<Entity> entities, DirectionalLight directionalLight, boolean anEntityHasMoved, boolean directionalLightNeedsShadowMapRender) {
        this.camera = camera;
        this.entities = entities;
        visibleEntities.clear();
        visibleEntities.addAll(entities);
        if (Config.useFrustumCulling) {
            GPUProfiler.start("Culling");
            for (int i = 0; i < entities.size(); i++) {
                if (!entities.get(i).isInFrustum(camera)) {
                    entities.remove(i);
                }
            }
            GPUProfiler.end();
        }
        this.directionalLight = directionalLight;
        this.anEntityHasMoved = anEntityHasMoved;
        this.directionalLightNeedsShadowMapRender = directionalLightNeedsShadowMapRender;
    }
}
