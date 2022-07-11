package de.hanno.hpengine.engine.graphics.renderer.drawstrategy

class FirstPassResult {
    var verticesDrawn = 0
    var entitiesDrawn = 0
    var linesDrawn = 0
    var directionalLightShadowMapWasRendered = false
    private val properties: MutableMap<String, Any> = HashMap()
    private var notYetUploadedVertexBufferDrawn = false
    fun init(
        verticesDrawn: Int,
        entityCount: Int,
        linesDrawn: Int,
        directionalLightShadowMapWasRendered: Boolean,
        notYetUploadedVertexBufferDrawn: Boolean
    ) {
        this.verticesDrawn = verticesDrawn
        entitiesDrawn = entityCount
        this.linesDrawn = linesDrawn
        this.directionalLightShadowMapWasRendered = directionalLightShadowMapWasRendered
        this.notYetUploadedVertexBufferDrawn = notYetUploadedVertexBufferDrawn
    }

    fun reset() {
        init(0, 0, 0, false, false)
    }

    fun setProperty(vctLightInjectedFramesAgo: String, value: Any) {
        properties[vctLightInjectedFramesAgo] = value
    }

    fun getProperty(key: String): Any? {
        return properties[key]
    }

    fun getProperties(): Map<String, Any> {
        return properties
    }

    fun set(firstPassResult: FirstPassResult) {
        init(
            firstPassResult.verticesDrawn,
            firstPassResult.entitiesDrawn,
            firstPassResult.linesDrawn,
            firstPassResult.directionalLightShadowMapWasRendered,
            firstPassResult.notYetUploadedVertexBufferDrawn
        )
    }
}