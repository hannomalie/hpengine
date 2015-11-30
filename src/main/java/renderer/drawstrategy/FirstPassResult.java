package renderer.drawstrategy;

public final class FirstPassResult {
    public final int verticesCount;
    public final int entityCount;

    public FirstPassResult(int verticesCount, int entityCount) {
        this.verticesCount = verticesCount;
        this.entityCount = entityCount;
    }
}
