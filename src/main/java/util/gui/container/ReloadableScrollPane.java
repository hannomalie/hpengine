package util.gui.container;

import javax.swing.*;
import java.awt.*;

public class ReloadableScrollPane extends JScrollPane {

    private final Component child;

    public <TYPE extends Component> ReloadableScrollPane(TYPE component) {
        super(component);
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
