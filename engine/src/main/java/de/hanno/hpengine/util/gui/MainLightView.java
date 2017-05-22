package de.hanno.hpengine.util.gui;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.alee.extended.panel.GridPanel;
import com.alee.extended.panel.GroupPanel;
import com.alee.extended.panel.TwoSidesPanel;
import com.alee.laf.button.WebButton;
import com.alee.laf.label.WebLabel;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.text.WebFormattedTextField;
import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.graphics.light.DirectionalLight;
import de.hanno.hpengine.util.gui.input.SliderInput;
import de.hanno.hpengine.util.gui.input.WebFormattedVec3Field;

import org.lwjgl.util.vector.Quaternion;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

import com.alee.extended.panel.WebComponentPanel;
import com.alee.laf.colorchooser.WebColorChooserPanel;
import com.alee.laf.slider.WebSlider;

public class MainLightView extends WebPanel {

    public MainLightView() {

        List<Component> panels = getPanels();
        this.removeAll();
        JScrollPane scrollPane = new JScrollPane(new TwoSidesPanel(panels.get(0), panels.get(1)));
        scrollPane.getVerticalScrollBar().setUnitIncrement(32);
        this.add(scrollPane);
        repaint();
    }

    protected List<Component> getPanels() {
        List<Component> panels = new ArrayList<>();

        WebColorChooserPanel lightColorChooserPanel = new WebColorChooserPanel();
        lightColorChooserPanel.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                DirectionalLight light = Engine.getInstance().getScene().getDirectionalLight();
                Color color = lightColorChooserPanel.getColor();
                light.setColor(new Vector3f(color.getRed() / 255.f,
                        color.getGreen() / 255.f,
                        color.getBlue() / 255.f));
            }
        });
        WebColorChooserPanel ambientLightColorChooserPanel = new WebColorChooserPanel();
        ambientLightColorChooserPanel.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                Color color = ambientLightColorChooserPanel.getColor();
                Config.getInstance().getAmbientLight().set(new Vector3f(color.getRed() / 255.f,
                        color.getGreen() / 255.f,
                        color.getBlue() / 255.f));
            }
        });

        WebComponentPanel webComponentPanel = new WebComponentPanel(false);
        webComponentPanel.setElementMargin(4);

        GridPanel gridPanel = new GridPanel(2, 1, ambientLightColorChooserPanel, lightColorChooserPanel);
        webComponentPanel.add(gridPanel);
        panels.add(getAttributesPanel());

        panels.add(webComponentPanel);

        return panels;
    }

    protected WebComponentPanel getAttributesPanel() {

        WebComponentPanel webComponentPanel = new WebComponentPanel(false);
        webComponentPanel.setElementMargin(4);

        addNamePanel(webComponentPanel);

        WebComponentPanel movablePanel = new WebComponentPanel(false);
        movablePanel.setElementMargin(4);

        WebFormattedVec3Field positionField = new WebFormattedVec3Field("Position", new Vector3f()) {
            @Override
            public void onValueChange(Vector3f current) {
                Engine.getInstance().getScene().getDirectionalLight().setPosition(current);
            }
        };
        movablePanel.addElement(positionField);

        movablePanel.addElement(new SliderInput("Orientation X", WebSlider.HORIZONTAL, 0, 3600, 0) {
            @Override
            public void onValueChange(int value, int delta) {
                Engine.getInstance().getScene().getDirectionalLight().rotateWorld(new Vector4f(1, 0, 0, 0.01f * delta));
            }
        });
        movablePanel.addElement(new SliderInput("Orientation Y", WebSlider.HORIZONTAL, 0, 3600, 0) {
            @Override
            public void onValueChange(int value, int delta) {
                Engine.getInstance().getScene().getDirectionalLight().rotateWorld(new Vector4f(0, 1, 0, 0.01f * delta));
            }
        });
        movablePanel.addElement(new SliderInput("Orientation Z", WebSlider.HORIZONTAL, 0, 3600, 0) {
            @Override
            public void onValueChange(int value, int delta) {
                Engine.getInstance().getScene().getDirectionalLight().rotateWorld(new Vector4f(0, 0, 1, 0.01f * delta));
            }
        });

        movablePanel.addElement(new SliderInput("Position X", WebSlider.HORIZONTAL, 0, 200, 100) {
            @Override
            public void onValueChange(int value, int delta) {
                DirectionalLight directionalLight = Engine.getInstance().getScene().getDirectionalLight();
                Vector3f axis = directionalLight.getRightDirection();
                axis = new Vector3f(1, 0, 0);
                directionalLight.moveInWorld((Vector3f) axis.scale(delta));
                positionField.setValue(directionalLight.getPosition());
            }
        });
        movablePanel.addElement(new SliderInput("Position Y", WebSlider.HORIZONTAL, 0, 200, 100) {
            @Override
            public void onValueChange(int value, int delta) {
                DirectionalLight directionalLight = Engine.getInstance().getScene().getDirectionalLight();
                Vector3f axis = directionalLight.getUpDirection();
                axis = new Vector3f(0, 1, 0);
                directionalLight.moveInWorld((Vector3f) axis.scale(delta));
                positionField.setValue(directionalLight.getPosition());
            }
        });
        movablePanel.addElement(new SliderInput("Position Z", WebSlider.HORIZONTAL, 0, 200, 100) {
            @Override
            public void onValueChange(int value, int delta) {
                DirectionalLight directionalLight = Engine.getInstance().getScene().getDirectionalLight();
                Vector3f axis = directionalLight.getViewDirection().negate(null);
                axis = new Vector3f(0, 0, -1);
                directionalLight.moveInWorld((Vector3f) axis.scale(delta));
                positionField.setValue(directionalLight.getPosition());
            }
        });

        movablePanel.addElement(new WebFormattedVec3Field("View Direction", new Vector3f(0, 0, -1)) {
            @Override
            public void onValueChange(Vector3f current) {
                DirectionalLight directionalLight = Engine.getInstance().getScene().getDirectionalLight();
                Quaternion temp = new Quaternion();
                temp.setFromAxisAngle(new Vector4f(current.x, current.y, current.z, 0));
                directionalLight.setOrientation(temp);
            }
        });

        webComponentPanel.addElement(movablePanel);

        webComponentPanel.addElement(new WebFormattedVec3Field("Width, Height, Z Max", new Vector3f(0, 0, 0)) {
            @Override
            public void onValueChange(Vector3f current) {
                DirectionalLight light = Engine.getInstance().getScene().getDirectionalLight();
                light.getCamera().setWidth(current.x);
                light.getCamera().setHeight(current.y);
                light.getCamera().setFar(current.z);
            }
        });
        webComponentPanel.addElement(new WebFormattedVec3Field("Camera Position", new Vector3f()) {
            @Override
            public void onValueChange(Vector3f current) {
                Engine.getInstance().getScene().getDirectionalLight().getCamera().setPosition(current);
            }
        });

        webComponentPanel.addElement(new WebButton("Use Light Cam") {{
            addActionListener(e -> {
                Engine.getInstance().setActiveCamera(Engine.getInstance().getScene().getDirectionalLight().getCamera());
            });
        }});
        webComponentPanel.addElement(new WebButton("Use World Cam") {{
            addActionListener(e -> {
                Engine.getInstance().restoreWorldCamera();
            });
        }});

        return webComponentPanel;
    }

    private void addNamePanel(WebComponentPanel webComponentPanel) {
        WebLabel labelName = new WebLabel("Name");
        WebFormattedTextField nameField = new WebFormattedTextField();
        nameField.setValue("Directional lights");
        GroupPanel groupPanel = new GroupPanel(4, labelName, nameField);
        webComponentPanel.addElement(groupPanel);
    }
}
