package de.hanno.hpengine.engine.graphics

import de.hanno.hpengine.engine.backend.BackendType

interface Window<T: BackendType> {
    val title: String

    var width: Int
    var height: Int

    fun getCursorPosition(mouseX: DoubleArray, mouseY: DoubleArray)
    fun getFrameBufferSize(width: IntArray, height: IntArray)
    fun getKey(keyCode: Int): Int
    fun getMouseButton(buttonCode: Int): Int
    fun showWindow()
    fun hideWindow()
    fun pollEvents()
    fun update(deltaSeconds: Float) = pollEvents()
}