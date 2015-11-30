package event;

import renderer.drawstrategy.DrawResult;

public class FrameFinishedEvent {

    private final DrawResult drawResult;
    private final String latestGPUProfilingResult;

    public FrameFinishedEvent(DrawResult drawResult, String latestGPUProfilingResult) {
        this.drawResult = drawResult;
        this.latestGPUProfilingResult = latestGPUProfilingResult;
    }

    public DrawResult getDrawResult() {
        return drawResult;
    }

    public String getLatestGPUProfilingResult() {
        return latestGPUProfilingResult;
    }
}
