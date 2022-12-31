package de.hanno.hpengine.graphics.editor

import com.artemis.BaseSystem
import de.hanno.hpengine.extension.renderSystem
import de.hanno.hpengine.graphics.FinalOutput
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.RenderStateContext
import de.hanno.hpengine.graphics.renderer.drawstrategy.extensions.OnClickListener
import de.hanno.hpengine.graphics.state.RenderSystem
import org.koin.dsl.binds
import org.koin.dsl.module

val editorModule = module {
    single {
        EditorCameraInputSystem(get())
    } binds arrayOf(BaseSystem::class, EditorCameraInputSystem::class)

    single {
        val finalOutput: FinalOutput = get()

        get<GpuContext>().run {
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