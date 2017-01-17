package de.hanno.hpengine.util.gui;

import de.hanno.hpengine.test.TestWithEngine;

import javax.swing.*;

/**
 * Created by pernpeintner on 01.06.2016.
 */
public class ViewTest extends TestWithEngine {
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
