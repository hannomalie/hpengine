package util.gui;

import renderer.Renderer;
import renderer.Result;

import javax.swing.*;
import java.util.concurrent.ExecutionException;

public abstract class SwingWorkerWithProgress<RESULT_TYPE extends Result> {

    private final Renderer renderer;
    private final DebugFrame debugFrame;

    private SwingWorker<Result, Object> worker;

    protected String startProgress = null;
    protected String taskFailed = null;

    public SwingWorkerWithProgress(Renderer renderer, DebugFrame debugFrame, String before) {
        this(renderer, debugFrame, before, null);
    }
    public SwingWorkerWithProgress(Renderer renderer, String fail, DebugFrame debugFrame) {
        this(renderer, debugFrame, null, fail);
    }

    public SwingWorkerWithProgress(Renderer renderer, DebugFrame debugFrame, String before, String fail) {
        this.renderer = renderer;
        this.debugFrame = debugFrame;
        this.startProgress = before;
        this.taskFailed = fail;
    }

    public void execute() {

        SwingWorkerWithProgress parent = this;

        worker = new SwingWorker<Result, Object>() {

            Result result = null;

            @Override
            protected Result doInBackground() throws Exception {
                debugFrame.startProgress(startProgress);
                try {
                    result = parent.doInBackground();
                } catch (Exception e) {
                    e.printStackTrace();
                    debugFrame.showError(taskFailed);
                }
                return result;
            }

            @Override
            public void done() {
                try {
                    parent.done(result);
                } catch (Exception e) {
                    e.printStackTrace();
                    debugFrame.showError(taskFailed);
                }
                debugFrame.stopProgress();
            }
        };

        worker.execute();
    }

    public RESULT_TYPE get() {
        try {
            return (RESULT_TYPE) worker.get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void done(RESULT_TYPE result) throws Exception {}
    public abstract RESULT_TYPE doInBackground() throws Exception;

}
