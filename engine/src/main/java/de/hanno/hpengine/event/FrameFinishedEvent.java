package de.hanno.hpengine.event;

import de.hanno.hpengine.renderer.drawstrategy.DrawResult;

public class FrameFinishedEvent {

    private final DrawResult drawResult;
    private final String latestGPUProfilingResult;

    public FrameFinishedEvent(DrawResult drawResult) {
        this.drawResult = drawResult;
        this.latestGPUProfilingResult = drawResult.GPUProfilingResult;
    }

    public DrawResult getDrawResult() {
        return drawResult;
    }

    public String getLatestGPUProfilingResult() {
        return latestGPUProfilingResult;
    }
}
