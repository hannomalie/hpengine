package renderer.drawstrategy;

public final class FirstPassResult {
    public int verticesDrawn;
    public int entitiesDrawn;
    public int linesDrawn;
    public boolean directionalLightShadowMapWasRendered;

    public FirstPassResult() {
    }

    public void init(int verticesDrawn, int entityCount, int linesDrawn, boolean directionalLightShadowMapWasRendered) {
        this.verticesDrawn = verticesDrawn;
        this.entitiesDrawn = entityCount;
        this.linesDrawn = linesDrawn;
        this.directionalLightShadowMapWasRendered = directionalLightShadowMapWasRendered;
    }

    public void reset() {
        init(0,0,0,false);
    }
}
