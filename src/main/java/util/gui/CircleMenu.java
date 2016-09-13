package util.gui;

import com.alee.extended.menu.DynamicMenuType;
import com.alee.extended.menu.WebDynamicMenu;
import com.alee.extended.menu.WebDynamicMenuItem;
import com.alee.laf.WebLookAndFeel;
import com.alee.laf.combobox.WebComboBox;
import com.alee.laf.text.WebTextField;
import com.alee.utils.SwingUtils;
import com.alee.utils.swing.IntTextDocument;
import com.google.common.eventbus.Subscribe;
import engine.AppContext;
import event.ClickEvent;
import net.engio.mbassy.listener.Handler;
import net.engio.mbassy.listener.Synchronized;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public class CircleMenu {

    public CircleMenu() {
        webMenu.removeAll();
        WebComboBox type = new WebComboBox(DynamicMenuType.values(), DynamicMenuType.fade);
        WebComboBox hidingType = new WebComboBox(DynamicMenuType.values(), DynamicMenuType.fade);
        WebTextField radius = new WebTextField(new IntTextDocument(), "70", 4);
        webMenu.setType ( ( DynamicMenuType ) type.getSelectedItem () );
        webMenu.setHideType ( (DynamicMenuType) hidingType.getSelectedItem () );
        webMenu.setRadius ( Integer.parseInt ( radius.getText () ) );
        webMenu.setStepProgress ( 0.57f );

        final int items = 5;
        for ( int i = 1; i <= items; i++ )
        {
            final ImageIcon icon = WebLookAndFeel.getIcon ( 24 );
            final ActionListener action = e -> {
                System.out.println(icon);
                isBusy.set(false);
                System.out.println("ActionListener triggered");
            };
            final WebDynamicMenuItem item = new WebDynamicMenuItem( icon, action );
            item.setMargin ( new Insets( 8, 8, 8, 8 ) );
            webMenu.addItem ( item );
        }

        AppContext.getEventBus().register(this);
    }

    final WebDynamicMenu webMenu = new WebDynamicMenu();
    private AtomicBoolean isBusy = new AtomicBoolean(false);

    @Subscribe
    @Handler
    @Synchronized
    public void handle(ClickEvent clickEvent) {
        System.out.println("Clicked");
        if(isBusy.compareAndSet(false, true)) {
            toggleMenu();
        }
    }

    private void toggleMenu() {
        System.out.println("WebMenu showing: " + webMenu.isShowing());
        if (!webMenu.isShowing()) {
            System.out.println("Set true");

            SwingUtils.invokeLater(() -> {
                webMenu.showMenu(Display.getParent(), Mouse.getX(), AppContext.getInstance().getFrame().getHeight()-Mouse.getY());
                System.out.println("Menu shown");
                if(!isBusy.compareAndSet(true, false)) {
                    throw new IllegalStateException("Illegal State with isBusy!");
                }
                System.out.println("Set false");
            });
            System.out.println("Action pushed");
        } else {
            SwingUtils.invokeLater(() -> {
                webMenu.hideMenu();
                if(!isBusy.compareAndSet(true, false)) {
                    throw new IllegalStateException("Illegal State with isBusy!");
                }
                System.out.println("Set false");
            });
            System.out.println("Action pushed");
        }
    }
}
