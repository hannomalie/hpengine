package de.hanno.hpengine.util.gui;

public interface HostComponent {
    void startProgress(String startProgress);

    void showError(String error);

    void stopProgress();

}
