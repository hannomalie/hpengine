package renderer;

import camera.Camera;

public class RenderExtract {
    public final boolean anEntityHasMoved;
    public final boolean directionalLightNeedsShadowMapRender;
    public final Camera camera;

    public RenderExtract(Camera camera, boolean anEntityHasMoved, boolean directionalLightNeedsShadowMapRender) {
        this.camera = camera;
        this.anEntityHasMoved = anEntityHasMoved;
        this.directionalLightNeedsShadowMapRender = directionalLightNeedsShadowMapRender;
    }
}
