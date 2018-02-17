package de.hanno.hpengine.engine;

public interface PerFrameCommandProvider {
    Runnable getDrawCommand();

    void postRun();

    boolean isReadyForExecution();
}
