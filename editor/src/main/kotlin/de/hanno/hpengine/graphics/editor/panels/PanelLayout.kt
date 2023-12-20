package de.hanno.hpengine.graphics.editor.panels

data class PanelLayout(
    var windowPositionX: Float = 0f,
    var windowPositionY: Float = 0f,

    var windowWidth: Float = 0f,
    var windowHeight: Float = 0f,

    var leftPanelYOffset: Float = 0f,
    var leftPanelWidthPercentage: Float = 0.1f,
    var leftPanelWidth: Float = 0f,

    var rightPanelWidthPercentage: Float = 0.2f,
    var rightPanelWidth: Float = 0f,

    var midPanelHeight: Float = 0f,
    var midPanelWidth: Float = 0f,

    var bottomPanelHeightPercentage: Float = 0.2f,
) {

    val midPanelPositionX get() = leftPanelWidth
    val panelPositionY get() = leftPanelYOffset

    val bottomPanelPositionX get() = leftPanelWidth
    val bottomPanelPositionY get() = windowHeight - bottomPanelHeight
    val bottomPanelWidth get() = midPanelWidth
    val bottomPanelHeight get() = windowHeight * bottomPanelHeightPercentage

    fun update(windowWidth: Float, windowHeight: Float) {
        this.windowWidth = windowWidth
        this.windowHeight = windowHeight

        leftPanelWidth = windowWidth * leftPanelWidthPercentage

        rightPanelWidth = windowWidth * rightPanelWidthPercentage

        midPanelHeight = windowHeight - leftPanelYOffset
        midPanelWidth = windowWidth - leftPanelWidth - rightPanelWidth
    }
}