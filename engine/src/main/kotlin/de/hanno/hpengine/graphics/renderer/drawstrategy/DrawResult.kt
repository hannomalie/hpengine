package de.hanno.hpengine.graphics.renderer.drawstrategy

class DrawResult(val firstPassResult: FirstPassResult, val secondPassResult: SecondPassResult) {

    val verticesCount: Int
        get() = firstPassResult.verticesDrawn
    val entityCount: Int
        get() = firstPassResult.entitiesDrawn

    override fun toString(): String {
        return """
            Vertices drawn: $verticesCount
            Entities visible: $entityCount
            
            
            """.trimIndent()
    }

    fun reset() {
        firstPassResult.reset()
        secondPassResult.reset()
    }

    fun set(latestDrawResult: DrawResult) {
        firstPassResult.set(latestDrawResult.firstPassResult)
        secondPassResult.set(latestDrawResult.secondPassResult)
    }
}