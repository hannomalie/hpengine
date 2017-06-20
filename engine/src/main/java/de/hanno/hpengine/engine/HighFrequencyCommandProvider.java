package de.hanno.hpengine.engine;

import java.util.concurrent.atomic.AtomicInteger;

public interface HighFrequencyCommandProvider {
    Runnable getDrawCommand();

    AtomicInteger getAtomicCounter();
}
