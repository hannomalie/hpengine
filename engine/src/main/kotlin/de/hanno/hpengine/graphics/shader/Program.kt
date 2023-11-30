package de.hanno.hpengine.graphics.shader

import de.hanno.hpengine.graphics.GraphicsApi


context(GraphicsApi)
inline fun <T: Uniforms> using(program: Program<T>, block: (T) -> Unit) {
    program.use()
    block(program.uniforms)
//    program.unuse() // TODO: Anything like this possible?
}

context(GraphicsApi)
inline fun <T: Uniforms> Program<T>.useAndBind(setUniforms: T.() -> Unit, block: () -> Unit) {
    using(this) {
        uniforms.setUniforms()
        bind()
        block()
    }
}
