package de.hanno.hpengine.engine.graphics.renderer.drawstrategy;

import java.util.Map;

public class DrawResult {
    private final FirstPassResult firstPassResult;
    private final SecondPassResult secondPassResult;
    public volatile String GPUProfilingResult = "";

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

    public Map<String, Object> getProperties() {
        return firstPassResult.getProperties();
    }

    public FirstPassResult getFirstPassResult() {
        return firstPassResult;
    }

    public SecondPassResult getSecondPassResult() {
        return secondPassResult;
    }

    public void reset() {
        firstPassResult.reset();
        secondPassResult.reset();
    }

    public void set(DrawResult latestDrawResult) {
        this.firstPassResult.set(latestDrawResult.firstPassResult);
        this.secondPassResult.set(latestDrawResult.secondPassResult);
    }
}
