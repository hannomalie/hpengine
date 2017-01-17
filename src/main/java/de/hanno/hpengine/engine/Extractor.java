package de.hanno.hpengine.engine;

import de.hanno.hpengine.camera.Camera;
import de.hanno.hpengine.renderer.drawstrategy.DrawResult;
import de.hanno.hpengine.renderer.light.DirectionalLight;

import java.util.List;

public interface Extractor<T> {
    void resetState();

    T extract(DirectionalLight directionalLight, boolean anyPointLightHasMoved, Camera extractedCamera, DrawResult latestDrawResult, List<PerEntityInfo> perEntityInfos, boolean entityHasMoved);
}
