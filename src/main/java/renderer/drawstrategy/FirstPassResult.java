package renderer.drawstrategy;

import java.util.HashMap;
import java.util.Map;

public final class FirstPassResult {
    public int verticesDrawn;
    public int entitiesDrawn;
    public int linesDrawn;
    public boolean directionalLightShadowMapWasRendered;
    private Map<String, Object> properties = new HashMap<>();
    public boolean notYetUploadedVertexBufferDrawn;

    public FirstPassResult() {
    }

    public void init(int verticesDrawn, int entityCount, int linesDrawn, boolean directionalLightShadowMapWasRendered, boolean notYetUploadedVertexBufferDrawn) {
        this.verticesDrawn = verticesDrawn;
        this.entitiesDrawn = entityCount;
        this.linesDrawn = linesDrawn;
        this.directionalLightShadowMapWasRendered = directionalLightShadowMapWasRendered;
        this.notYetUploadedVertexBufferDrawn = notYetUploadedVertexBufferDrawn;
    }

    public void reset() {
        init(0,0,0,false, false);
    }

    public void setProperty(String vctLightInjectedFramesAgo, Object value) {
        properties.put(vctLightInjectedFramesAgo, value);
    }

    public Object getProperty(String key) {
        return properties.get(key);
    }

    public Map<String, Object> getProperties() {
        return properties;
    }
}
