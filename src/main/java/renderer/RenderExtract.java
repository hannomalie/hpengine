package renderer;

import camera.Camera;
import engine.PerEntityInfo;
import engine.model.Entity;
import org.lwjgl.util.vector.Vector4f;
import renderer.drawstrategy.DrawResult;
import renderer.light.DirectionalLight;

import java.util.*;

public class RenderExtract {
    private DrawResult latestDrawResult;
    public boolean anEntityHasMoved;
    public boolean directionalLightNeedsShadowMapRender;
    public Camera camera = new Camera();
    public DirectionalLight directionalLight = new DirectionalLight();
    public boolean anyPointLightHasMoved;
    public boolean sceneInitiallyDrawn;
    public Vector4f sceneMin = new Vector4f();
    public Vector4f sceneMax = new Vector4f();
    private Map properties = new HashMap<>();
    private List<PerEntityInfo> perEntityInfos;

    /**
     * Copy constructor
     * @param source
     */
    public RenderExtract(RenderExtract source) {
        init(source.camera, source.directionalLight, source.anEntityHasMoved, source.directionalLightNeedsShadowMapRender, source.anyPointLightHasMoved, source.sceneInitiallyDrawn, source.sceneMin, source.sceneMax, source.latestDrawResult, source.perEntityInfos);
    }

    public RenderExtract() {
    }

    public RenderExtract init(Camera camera, DirectionalLight directionalLight, boolean anEntityHasMoved, boolean directionalLightNeedsShadowMapRender, boolean anyPointLightHasMoved, boolean sceneInitiallyDrawn, Vector4f sceneMin, Vector4f sceneMax, DrawResult latestDrawResult, List<PerEntityInfo> perEntityInfos) {
        this.camera.init(camera);
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
        this.perEntityInfos = Collections.unmodifiableList(perEntityInfos);
        this.latestDrawResult = latestDrawResult;
        return this;
    }

    public List<PerEntityInfo> perEntityInfos() {
        return perEntityInfos;
    }
}
