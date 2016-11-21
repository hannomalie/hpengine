package engine;

import camera.Camera;
import renderer.drawstrategy.DrawResult;
import renderer.light.DirectionalLight;

import java.util.List;

public interface Extractor<T> {
    void resetState();

    T extract(DirectionalLight directionalLight, boolean anyPointLightHasMoved, Camera extractedCamera, DrawResult latestDrawResult, List<PerEntityInfo> perEntityInfos, boolean entityHasMoved);
}
