package de.hanno.hpengine.engine.backend

import de.hanno.hpengine.engine.graphics.renderer.ExtensibleDeferredRenderer

val EngineContext.extensibleDeferredRenderer: ExtensibleDeferredRenderer?
    get() = renderSystems.filterIsInstance<ExtensibleDeferredRenderer>().firstOrNull()