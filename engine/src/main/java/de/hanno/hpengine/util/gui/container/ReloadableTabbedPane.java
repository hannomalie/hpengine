package de.hanno.hpengine.util.gui.container;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class ReloadableTabbedPane extends JTabbedPane {

    public ReloadableTabbedPane() {
        super();

        ChangeListener changeListener = new ChangeListener() {
            public void stateChanged(ChangeEvent changeEvent) {
                JTabbedPane sourceTabbedPane = (JTabbedPane) changeEvent.getSource();
                int index = sourceTabbedPane.getSelectedIndex();

                java.awt.Component tab = sourceTabbedPane.getComponentAt(index);

                if(tab instanceof ReloadableScrollPane) {
                    ReloadableScrollPane reloadableTab = (ReloadableScrollPane) tab;
                    reloadableTab.reload();
                }
            }
        };
        addChangeListener(changeListener);
    }

}
