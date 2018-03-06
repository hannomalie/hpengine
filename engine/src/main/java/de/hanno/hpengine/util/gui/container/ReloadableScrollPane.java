package de.hanno.hpengine.util.gui.container;

import javax.swing.*;
import java.awt.*;

public class ReloadableScrollPane extends JScrollPane {

    private final Component child;

    public <TYPE extends Component> ReloadableScrollPane(TYPE component) {
        super(component);
        if(component == null) {
            throw new IllegalArgumentException("Don't pass null child");
        }
        this.child = component;
    }

    public void reload() {
        setViewportView(child);
        child.revalidate();
        child.repaint();
        this.getRootPane().invalidate();
        this.getRootPane().revalidate();
        this.getRootPane().repaint();
    }
}
