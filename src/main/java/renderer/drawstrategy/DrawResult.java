package renderer.drawstrategy;

import java.util.Map;

public class DrawResult {
    private final FirstPassResult firstPassResult;
    private final SecondPassResult secondPassResult;
    public boolean directionalLightShadowMapRendered = false;

    public DrawResult(FirstPassResult firstPassResult, SecondPassResult secondPassResult) {
        this.firstPassResult = firstPassResult;
        this.secondPassResult = secondPassResult;
    }

    public int getVerticesCount() {
        return firstPassResult.verticesDrawn;
    }

    public int getEntityCount() {
        return firstPassResult.entitiesDrawn;
    }

    @Override
    public String toString() {
        return "Vertices drawn: " + getVerticesCount() + "\n" +
                "Entities visible: " + getEntityCount() + "\n\n";
    }

    public boolean directionalLightShadowMapWasRendered() {
        return firstPassResult.directionalLightShadowMapWasRendered;
    }

    public Map<String, Object> getProperties() {
        return firstPassResult.getProperties();
    }
}
