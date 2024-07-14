package de.hanno.hpengine.model

import AnimatedVertexStruktPackedImpl.Companion.type
import VertexStruktPackedImpl.Companion.type
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.state.EntitiesState
import de.hanno.hpengine.graphics.state.RenderStateContext
import de.hanno.hpengine.scene.AnimatedVertexStruktPacked
import de.hanno.hpengine.scene.VertexBuffer
import de.hanno.hpengine.scene.VertexIndexBuffer
import de.hanno.hpengine.scene.VertexStruktPacked
import org.koin.core.annotation.Single

@Single
class EntitiesStateHolder(
    graphicsApi: GraphicsApi,
    renderStateContext: RenderStateContext,
) {
    val geometryBufferStatic = VertexIndexBuffer(graphicsApi, VertexStruktPacked.type, 10)
    val geometryBufferAnimated = VertexIndexBuffer(graphicsApi, AnimatedVertexStruktPacked.type, 10)
//    val geometryBufferStatic = VertexBuffer(graphicsApi, VertexStruktPacked.type)
//    val geometryBufferAnimated = VertexBuffer(graphicsApi, AnimatedVertexStruktPacked.type)
    val entitiesState = renderStateContext.renderState.registerState {
        EntitiesState(
            graphicsApi,
            geometryBufferStatic,
            geometryBufferAnimated,
        )
    }
}