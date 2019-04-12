package de.hanno.hpengine.engine.lifecycle

import de.hanno.hpengine.engine.Engine

interface EngineConsumer {
    fun consume(engine: Engine<*>)
}