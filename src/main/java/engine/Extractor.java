package engine;

import camera.Camera;
import renderer.RenderExtract;
import renderer.drawstrategy.DrawResult;
import renderer.light.DirectionalLight;

public interface Extractor<T> {
    void resetState(T currentExtract);

    T extract(DirectionalLight directionalLight, boolean anyPointLightHasMoved, Camera extractedCamera, DrawResult latestDrawResult);
}
