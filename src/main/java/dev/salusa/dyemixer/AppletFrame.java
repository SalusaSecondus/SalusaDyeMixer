package dev.salusa.dyemixer;

import java.awt.*;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

public class AppletFrame extends Frame {
    public AppletFrame() {
        DyeMixer mixer = new DyeMixer();
        setSize(564, 414);
        setTitle("DyeMixer (SalusaSecondus version 0.1, Base version 1.1)");
        setLayout(new GridLayout(1,1));
        add(mixer);
        addWindowListener(new Listener());
        setVisible(true);
    }

    public class Listener implements WindowListener {

        @Override
        public void windowOpened(WindowEvent e) {
        }

        @Override
        public void windowClosing(WindowEvent e) {
            dispose();
        }

        @Override
        public void windowClosed(WindowEvent e) {
        }

        @Override
        public void windowIconified(WindowEvent e) {
        }

        @Override
        public void windowDeiconified(WindowEvent e) {
        }

        @Override
        public void windowActivated(WindowEvent e) {
        }

        @Override
        public void windowDeactivated(WindowEvent e) {
        }
    }
}
