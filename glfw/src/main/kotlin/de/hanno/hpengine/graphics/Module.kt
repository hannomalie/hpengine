import de.hanno.hpengine.graphics.GlfwWindow
import de.hanno.hpengine.graphics.window.Window
import org.koin.dsl.binds
import org.koin.dsl.module

val glfwModule = module {
    single {
        GlfwWindow(get(), get())
    } binds arrayOf(Window::class)
}