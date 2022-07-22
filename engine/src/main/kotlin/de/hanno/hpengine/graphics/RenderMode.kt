package de.hanno.hpengine.graphics

import java.util.concurrent.atomic.AtomicBoolean

sealed interface RenderMode {
    object Normal: RenderMode
    class SingleFrame: RenderMode {
        var frameRequested = AtomicBoolean(true)
    }
}