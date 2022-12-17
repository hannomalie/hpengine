import org.lwjgl.opengl.GL11

internal val Facing.glValue: Int get() = when(this) {
    Facing.Front -> GL11.GL_FRONT
    Facing.Back -> GL11.GL_BACK
    Facing.FrontAndBack -> GL11.GL_FRONT_AND_BACK
}