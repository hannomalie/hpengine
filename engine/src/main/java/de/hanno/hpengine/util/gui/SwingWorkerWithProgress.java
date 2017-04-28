package de.hanno.hpengine.util.gui;

import de.hanno.hpengine.renderer.command.Result;

import javax.swing.*;
import java.util.concurrent.ExecutionException;

public abstract class SwingWorkerWithProgress<RESULT_TYPE extends Result> {

    private final HostComponent hostComponent;

    private SwingWorker<Result, Object> worker;

    protected String startProgress = null;
    protected String taskFailed = null;

    public SwingWorkerWithProgress(HostComponent hostComponent, String before) {
        this(hostComponent, before, null);
    }
    public SwingWorkerWithProgress(String fail, HostComponent hostComponent) {
        this(hostComponent, null, fail);
    }

    public SwingWorkerWithProgress(HostComponent hostComponent, String before, String fail) {
        this.hostComponent = hostComponent;
        this.startProgress = before;
        this.taskFailed = fail;
    }

    public void execute() {

        SwingWorkerWithProgress parent = this;

        worker = new SwingWorker<Result, Object>() {

            Result result = null;

            @Override
            protected Result doInBackground() throws Exception {
                hostComponent.startProgress(startProgress);
                try {
                    result = parent.doInBackground();
                } catch (Exception e) {
                    e.printStackTrace();
                    hostComponent.showError(taskFailed);
                }
                return result;
            }

            @Override
            public void done() {
                try {
                    parent.done(result);
                } catch (Exception e) {
                    e.printStackTrace();
                    hostComponent.showError(taskFailed);
                }
                hostComponent.stopProgress();
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
