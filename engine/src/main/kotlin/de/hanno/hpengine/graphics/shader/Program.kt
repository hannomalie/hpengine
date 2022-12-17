package de.hanno.hpengine.graphics.shader

import de.hanno.hpengine.graphics.GpuContext


context(GpuContext)
inline fun <T: Uniforms> IProgram<T>.useAndBind(block: (T) -> Unit) {
    use()
    block(uniforms)
    bind()
}
