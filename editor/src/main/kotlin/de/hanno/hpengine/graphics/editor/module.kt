package de.hanno.hpengine.graphics.editor

import com.artemis.BaseSystem
import de.hanno.hpengine.graphics.output.FinalOutput
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.state.RenderStateContext
import de.hanno.hpengine.graphics.renderer.picking.OnClickListener
import de.hanno.hpengine.graphics.RenderSystem
import org.koin.dsl.binds
import org.koin.dsl.module

val editorModule = module {
    single {
        EditorCameraInputSystem(get())
    } binds arrayOf(BaseSystem::class, EditorCameraInputSystem::class)

    single {
        val finalOutput: FinalOutput = get()

        get<GraphicsApi>().run {
            get<RenderStateContext>().run {
                ImGuiEditor(
                    get(),
                    get(),
                    finalOutput,
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    getAll<ImGuiEditorExtension>().distinct(),
                    get(),
                    get(),
                    get(),
                )
            }
        }
    } binds arrayOf(BaseSystem::class, RenderSystem::class)

    single {
        EntityClickListener()
    } binds arrayOf(EntityClickListener::class, OnClickListener::class)
}