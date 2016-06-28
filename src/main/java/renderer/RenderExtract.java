package renderer;

import camera.Camera;
import config.Config;
import engine.model.Entity;
import renderer.light.DirectionalLight;
import util.stopwatch.GPUProfiler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RenderExtract {
    public final boolean anEntityHasMoved;
    public final boolean directionalLightNeedsShadowMapRender;
    public final Camera camera;
    public final List<Entity> entities;
    public final List<Entity> visibleEntities = new ArrayList<>();
    public final DirectionalLight directionalLight;
    public final boolean anyPointLightHasMoved;
    public final boolean sceneInitiallyDrawn;

    public RenderExtract(Camera camera, List<Entity> entities,
                         DirectionalLight directionalLight,
                         boolean anEntityHasMoved,
                         boolean directionalLightNeedsShadowMapRender,
                         boolean anyPointLightHasMoved,
                         boolean sceneInitiallyDrawn) {
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
    }
}
