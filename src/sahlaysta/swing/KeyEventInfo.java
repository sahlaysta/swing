package sahlaysta.swing;

import java.awt.KeyEventDispatcher;
import java.awt.KeyEventPostProcessor;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.util.LinkedList;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JInternalFrame;
import javax.swing.KeyStroke;
import java.applet.Applet;
import java.awt.Container;
import java.awt.Window;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import sun.awt.PeerEvent;

//simulate JComponent.processKeyEvent() code for resolving the key action
class KeyEventInfo {

    private final KeyEvent keyEvent;
    private final JComponent component;
    private final KeyStroke keyStroke;
    private final Object actionBinding;
    private final ActionListener action;

    private KeyEventInfo(
            KeyEvent keyEvent, JComponent component, KeyStroke keyStroke, Object actionBinding, ActionListener action) {
        this.keyEvent = keyEvent;
        this.component = component;
        this.keyStroke = keyStroke;
        this.actionBinding = actionBinding;
        this.action = action;
    }

    public KeyEvent getKeyEvent() { return keyEvent; }

    public JComponent getComponent() { return component; }

    public KeyStroke getKeyStroke() { return keyStroke; }

    public Object getActionBinding() { return actionBinding; }

    public ActionListener getAction() { return action; }

    public boolean nameEquals(String name) {
        return name.equals(actionBinding)
                || (action instanceof Action && name.equals(((Action)action).getValue(Action.NAME)));
    }

    private static KeyboardFocusManager KFM = null;
    private static final LinkedList<KeyEvent> currentKeyEvents = new LinkedList<>();
    public static void monitor() {
        monitor(KeyboardFocusManager.getCurrentKeyboardFocusManager());
    }

    private static final KeyEventDispatcher MONITOR_DISPATCHER = e -> {
        if (KFM == KeyboardFocusManager.getCurrentKeyboardFocusManager())
            currentKeyEvents.add(e);
        clearKeyEventsLater();
        return false;
    };

    private static final KeyEventPostProcessor MONITOR_POST_PROCESSOR = e -> {
        if (KFM == KeyboardFocusManager.getCurrentKeyboardFocusManager()) {
            if (!currentKeyEvents.isEmpty()) {
                currentKeyEvents.removeLast();
            }
        }
        return false;
    };

    private static void monitor(KeyboardFocusManager kfm) {
        if (KFM == kfm) return;
        unmonitor(KFM);
        KFM = kfm;
        kfm.addPropertyChangeListener("managingFocus", e ->
                monitor(KeyboardFocusManager.getCurrentKeyboardFocusManager()));
        kfm.addKeyEventDispatcher(MONITOR_DISPATCHER);
        kfm.addKeyEventPostProcessor(MONITOR_POST_PROCESSOR);
    }

    private static void unmonitor(KeyboardFocusManager kfm) {
        if (kfm == KFM) KFM = null;
        kfm.removeKeyEventDispatcher(MONITOR_DISPATCHER);
        kfm.removeKeyEventPostProcessor(MONITOR_POST_PROCESSOR);
    }

    private static boolean willClearKeyEventsLater = false;
    private static void clearKeyEventsLater() {
        if (willClearKeyEventsLater) return;
        Runnable r = () -> {
            willClearKeyEventsLater = false;
            currentKeyEvents.clear();
        };
        Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(
                new PeerEvent(Toolkit.getDefaultToolkit(), r, PeerEvent.ULTIMATE_PRIORITY_EVENT));
        willClearKeyEventsLater = true;
    }

    public static KeyEventInfo getCurrentKeyEventInfo() {
        KeyEvent keyEvent = currentKeyEvents.peekLast();
        return keyEvent == null ? null : resolveKeyEventInfo(keyEvent);
    }

    public static KeyEventInfo resolveKeyEventInfo(KeyEvent keyEvent) {
        if (keyEvent == null) return null;

        Object src = keyEvent.getSource();
        if (!(src instanceof JComponent)) return null;
        JComponent jc = (JComponent)src;

        boolean pressed = keyEvent.getID() == KeyEvent.KEY_PRESSED;

        KeyStroke ks;
        KeyStroke ksE = null;
        KeyEventInfo kei;

        if (keyEvent.getID() == KeyEvent.KEY_TYPED) {
            ks = KeyStroke.getKeyStroke(keyEvent.getKeyChar());
        } else {
            ks = KeyStroke.getKeyStroke(keyEvent.getKeyCode(), keyEvent.getModifiers(), !pressed);
            if (keyEvent.getKeyCode() != keyEvent.getExtendedKeyCode()) {
                ksE = KeyStroke.getKeyStroke(keyEvent.getExtendedKeyCode(), keyEvent.getModifiers(), !pressed);
            }
        }

        if (ksE != null) {
            kei = getKeyEventInfoForBinding(keyEvent, jc, ksE, JComponent.WHEN_FOCUSED);
            if (kei != null) return kei;
        }

        kei = getKeyEventInfoForBinding(keyEvent, jc, ks, JComponent.WHEN_FOCUSED);
        if (kei != null) return kei;

        Container parent = jc;
        while (parent != null && !(parent instanceof Window) && !(parent instanceof Applet)) {
            if (parent instanceof JComponent) {
                JComponent parentJc = (JComponent)parent;
                if (ksE != null) {
                    kei = getKeyEventInfoForBinding(
                            keyEvent, parentJc, ksE, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
                    if (kei != null) return kei;
                }
                kei = getKeyEventInfoForBinding(
                        keyEvent, parentJc, ks, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
                if (kei != null) return kei;
            }
            if (parent instanceof JInternalFrame) {
                return null;
            }
            parent = parent.getParent();
        }

        return null;
    }

    private static KeyEventInfo getKeyEventInfoForBinding(KeyEvent e, JComponent jc, KeyStroke ks, int cond) {
        if (!jc.isEnabled()) return null;
        ActionMap am = jc.getActionMap();
        if (am == null) return null;
        ActionListener al = getAction(jc, ks, cond);
        for (int counter = 0; counter < 3; counter++) {
            InputMap inputMap = jc.getInputMap(counter);
            if (inputMap != null) {
                Object actionBinding = inputMap.get(ks);
                if (actionBinding != null) {
                    Action action = am.get(actionBinding);
                    if (action != null) {
                        if (action == al) {
                            return new KeyEventInfo(e, jc, ks, actionBinding, al);
                        }
                    }
                }
            }
        }
        return null;
    }

    private static final JComponent resolveKeyBindingComponent = new JComponent() { };

    private static ActionListener getAction(JComponent jc, KeyStroke ks, int cond) {
        resolveKeyBindingComponent.setInputMap(cond, jc.getInputMap(cond));
        resolveKeyBindingComponent.setActionMap(jc.getActionMap());
        return resolveKeyBindingComponent.getActionForKeyStroke(ks);
    }

}
