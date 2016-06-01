package util.gui;

import test.TestWithAppContext;

import javax.swing.*;

/**
 * Created by pernpeintner on 01.06.2016.
 */
public class ViewTest extends TestWithAppContext {
    protected void openViewInFrame(JPanel panel) {
        JFrame frame = new JFrame();
        frame.getContentPane().add(panel);
        frame.pack();
        frame.setVisible(true);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        while (true) {
        }
    }
}
