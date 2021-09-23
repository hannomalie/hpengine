package de.hanno.hpengine.editor.modules

import de.hanno.hpengine.editor.window.AWTEditorWindow
import de.hanno.hpengine.editor.graphics.EditorRendersystem
import de.hanno.hpengine.editor.scene.EditorEntitySystem
import de.hanno.hpengine.editor.manager.EditorManager
import de.hanno.hpengine.editor.graphics.OutputConfig
import de.hanno.hpengine.editor.graphics.OutputConfigHolder
import de.hanno.hpengine.editor.graphics.Pivot
import de.hanno.hpengine.editor.RibbonEditor
import de.hanno.hpengine.editor.appmenu.ApplicationMenu
import de.hanno.hpengine.editor.input.EditorInputConfig
import de.hanno.hpengine.editor.input.EditorInputConfigImpl
import de.hanno.hpengine.editor.scene.SceneTree
import de.hanno.hpengine.editor.selection.MouseAdapterImpl
import de.hanno.hpengine.editor.selection.SelectionSystem
import de.hanno.hpengine.editor.tasks.EditorRibbonTask
import de.hanno.hpengine.editor.tasks.MaterialRibbonBand
import de.hanno.hpengine.editor.tasks.MaterialRibbonTask
import de.hanno.hpengine.editor.tasks.SceneRibbonBands
import de.hanno.hpengine.editor.tasks.SceneRibbonTask
import de.hanno.hpengine.editor.tasks.TextureBand
import de.hanno.hpengine.editor.tasks.TextureRibbonTask
import de.hanno.hpengine.editor.tasks.TransformBands
import de.hanno.hpengine.editor.tasks.TransformRibbonTask
import de.hanno.hpengine.editor.tasks.ViewRibbonBands
import de.hanno.hpengine.editor.tasks.ViewRibbonTask
import de.hanno.hpengine.editor.window.SwingUtils
import de.hanno.hpengine.engine.extension.entitySystem
import de.hanno.hpengine.engine.graphics.FinalOutput
import de.hanno.hpengine.engine.graphics.OpenGlExecutorImpl
import de.hanno.hpengine.engine.graphics.Window
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.manager.Manager
import org.joml.Vector3f
import org.koin.core.module.Module
import org.koin.core.scope.Scope
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module
import org.pushingpixels.flamingo.api.ribbon.JRibbonFrame
import org.pushingpixels.flamingo.api.ribbon.RibbonTask
import org.pushingpixels.substance.api.SubstanceCortex
import org.pushingpixels.substance.api.skin.MarinerSkin

val editorWindowModule = module {
    single { OpenGlExecutorImpl() }
    single {
        SwingUtils.invokeAndWait {
            JRibbonFrame.setDefaultLookAndFeelDecorated(true)
            SubstanceCortex.GlobalScope.setSkin(MarinerSkin())
            RibbonEditor(get(), get())
        }
    }
    single { AWTEditorWindow(get(), get(), get()) } bind Window::class
    single {
        val editorWindow: AWTEditorWindow = get()
        editorWindow.canvas
    }
}
val editorModule = module {
    single {
        val window: Window<*> = get()
        window.frontBuffer
    }
    single {
        SwingUtils.invokeAndWait {
            SceneTree(get(), get(), get(), get())
        }
    }
    entitySystem {
        EditorEntitySystem(get())
    }
    single {
        EditorInputConfigImpl()
    } binds (arrayOf(EditorInputConfigImpl::class, EditorInputConfig::class))

    single {
        val editor: RibbonEditor = get()
        MouseAdapterImpl(editor.canvas)
    }
    single {
        val editor: RibbonEditor = get()
        editor.sidePanel
    }
    single {
        SelectionSystem(
            config = get(),
            editorInputConfig = get(),
            gpuContext = get(),
            mouseAdapter = get(),
            editor = get(),
            sidePanel = get(),
            renderStateManager = get(),
            programManager = get(),
            textureManager = get(),
            idTexture = get(),
            sceneManager = get(),
            sceneTree = get()
        )
    }
    single { OutputConfigHolder(OutputConfig.Default) }
    single { ApplicationMenu(get()) }
    single { Pivot(Vector3f()) }
    single {
        val finalOutput: FinalOutput = get()

        EditorRendersystem(
            gpuContext = get(),
            config = get(),
            window = get(),
            editor = get(),
            programManager = get(),
            textureManager = get(),
            addResourceContext = get(),
            renderStateManager = get(),
            sceneManager = get(),
            targetTexture = finalOutput.texture2D,
            editorInputConfig = get(),
            sceneTree = get(),
            selectionSystem = get(),
            mouseAdapter = get(),
            outputConfigHolder = get(),
            tasks = getAll<RibbonTask>().distinct(),
            applicationMenu = get(),
            pivot = get()
        )
    } binds (arrayOf(RenderSystem::class, Manager::class))

    single { EditorManager(get(), get()) } bind Manager::class

    single { MaterialRibbonBand(get(), get(), get(), get(), get(), get()) }
    task { MaterialRibbonTask(get()) }

    single { TextureBand(get(), get(), get(), get()) }
    task { TextureRibbonTask(get()) }

    single { TransformBands(get(), get()) }
    task { TransformRibbonTask(get()) }

    single { SceneRibbonBands(get(), get()) }
    task { SceneRibbonTask(get()) }

    single { ViewRibbonBands(get(), get(), get(), get(), get()) }
    task { ViewRibbonTask(get()) }
}

inline fun <reified T: RibbonTask> Module.task(noinline block: Scope.() -> T) {
    single {
        SwingUtils.invokeAndWait { block() }
    } binds (arrayOf(T::class, RibbonTask::class, EditorRibbonTask::class))
}