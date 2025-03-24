package scenes

import de.hanno.hpengine.Engine

fun main() {

    val demoAndEngineConfig = createDemoAndEngineConfig(Demo.Editor)

    val engine = createEngine(demoAndEngineConfig)

    engine.runSponza()
}

internal fun Engine.runEditor() {
    runMultipleObjects()
}