package renderer.drawstrategy;

public class DrawResult {
    private final FirstPassResult firstPassResult;
    private final SecondPassResult secondPassResult;

    public DrawResult(FirstPassResult firstPassResult, SecondPassResult secondPassResult) {
        this.firstPassResult = firstPassResult;
        this.secondPassResult = secondPassResult;
    }

    public int getVerticesCount() {
        return firstPassResult.verticesCount;
    }

    public int getEntityCount() {
        return firstPassResult.entityCount;
    }

    @Override
    public String toString() {
        return "Vertices drawn: " + getVerticesCount() + "\n" +
                "Entities visible: " + getEntityCount() + "\n\n";
    }
}
