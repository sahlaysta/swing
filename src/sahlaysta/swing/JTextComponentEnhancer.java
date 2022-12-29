/*
 * MIT License
 *
 * Copyright (c) 2022 sahlaysta
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package sahlaysta.swing;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.ContainerEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.HierarchyBoundsListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.InputEvent;
import java.awt.event.InputMethodEvent;
import java.awt.event.InputMethodListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.WeakHashMap;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Caret;
import javax.swing.text.DefaultCaret;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import javax.swing.text.Keymap;
import javax.swing.text.NavigationFilter;
import javax.swing.text.Position;
import javax.swing.text.StyledEditorKit;
import javax.swing.text.TextAction;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import javax.swing.undo.UndoManager;
import javax.swing.undo.UndoableEdit;

/**
 * Custom enhancements to Swing text components.
 *
 * <ul>
 * <li> Add compound undo/redo functionality and shortcuts
 * <li> Add right-click popup menu with (Cut, Copy, Paste...)
 * <li> Adjust behavior of having text selected and the right/left arrow key is pressed
 * <li> Adjust right-click behavior on text selection
 * <li> Add hyperlink right-click support
 * <li> Disable beeps (for example, backspace in an empty text field)
 * </ul>
 *
 * @author sahlaysta
 */
public final class JTextComponentEnhancer {

    private JTextComponentEnhancer() { }

    //keys in map data
    private static final String UNDO_KEY = "sahlaysta.undo";
    private static final String REDO_KEY = "sahlaysta.redo";
    private static final String PASTE_PLAIN_KEY = "sahlasyta.pasteplain";

    //store enhanced components by weak reference
    private static final Set<JTextComponent> enhancedComps = Collections.newSetFromMap(new WeakHashMap<>());

    /**
     * Automatically enhances all Swing text components.
     */
    public static void applyGlobalEnhancer() {
        Toolkit.getDefaultToolkit().addAWTEventListener(e -> {
            if (e.getID() != ContainerEvent.COMPONENT_ADDED) return;
            if (!(e instanceof ContainerEvent)) return;
            if (!(e.getSource() instanceof Container)) return;
            Component c = ((ContainerEvent)e).getChild();
            if (!(c instanceof JTextComponent)) return;
            JTextComponent jtc = (JTextComponent)c;
            enhanceJTextComponent(jtc);
        }, AWTEvent.CONTAINER_EVENT_MASK);
    }

    /**
     * Applies the enhancements to the text component.
     *
     * @param jtc the text component
     */
    public static void enhanceJTextComponent(JTextComponent jtc) {
        //if already enhanced
        if (enhancedComps.contains(jtc)) return;
        enhancedComps.add(jtc);

        //shortcut maps
        InputMap im = jtc.getInputMap();
        ActionMap am = jtc.getActionMap();

        //XDefaultEditorKit
        replaceXDefaultEditorKitActions(jtc);

        //compound undo manager
        CompoundUndoManager cum = CompoundUndoManager.createAndApply(jtc);

        //add shortcuts for undo/redo
        am.put(UNDO_KEY, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cum.compoundUndo();
            }
        });
        am.put(REDO_KEY, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cum.compoundRedo();
            }
        });
        am.put(PASTE_PLAIN_KEY, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                pasteAsPlainText(jtc);
            }
        });
        for (KeyStroke ks: UNDO) im.put(ks, UNDO_KEY);
        for (KeyStroke ks: REDO) im.put(ks, REDO_KEY);
        for (KeyStroke ks: PASTE_PLAIN) im.put(ks, PASTE_PLAIN_KEY);

        /*
        (enhancement to jtextcomponent that for some reason is not default behavior)
        while the text field contains selected text, and the left arrow key is pressed,
        move the caret to the beginning of the selection, same with right arrow key but to the end
         */
        am.put(DefaultEditorKit.forwardAction, new TextAction(DefaultEditorKit.forwardAction) {
            @Override
            public void actionPerformed(ActionEvent e) {
                JTextComponent jtc = getTextComponent(e);
                int selStart = jtc.getSelectionStart();
                int selEnd = jtc.getSelectionEnd();
                int dot = selStart == selEnd ? selEnd : Math.max(selEnd - 1, 0);
                shiftCaret(jtc, dot, SwingConstants.EAST);
            }
        });
        am.put(DefaultEditorKit.backwardAction, new TextAction(DefaultEditorKit.backwardAction) {
            @Override
            public void actionPerformed(ActionEvent e) {
                JTextComponent jtc = getTextComponent(e);
                int selStart = jtc.getSelectionStart();
                int selEnd = jtc.getSelectionEnd();
                int dot = selStart == selEnd ? selEnd : Math.min(selStart + 1, jtc.getDocument().getLength());
                shiftCaret(jtc, dot, SwingConstants.WEST);
            }
        });

        //the mouse right-click popup menu (Cut, Copy, Paste...)
        addAWTEventListener(jtc, e -> {
            if (!(e instanceof MouseEvent)) return;
            MouseEvent me = (MouseEvent)e;
            if (!me.isPopupTrigger()) return;
            if (!jtc.isEnabled()) return;
            if (jtc.getComponentPopupMenu() != null) return;
            int x = me.getX(), y = me.getY();
            showContextMenu(jtc, jtc.viewToModel(new Point(x, y)), x, y, true, cum);
        });

        //the shortcuts to show the right-click popup menu (for example Shift+F10 on Windows)
        Action contextMenuAction = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!jtc.isEnabled()) return;
                if (jtc.getComponentPopupMenu() != null) return;
                Caret caret = jtc.getCaret();
                Point p = caret.getMagicCaretPosition();
                if (p == null) {
                    try {
                        Rectangle rect = jtc.modelToView(jtc.getSelectionEnd());
                        p = new Point(rect.x, rect.y);
                    } catch (BadLocationException e2) { throw new Error(e2); }
                }
                int fontHeight = jtc.getFontMetrics(jtc.getFont()).getHeight();
                showContextMenu(jtc, caret.getDot(), p.x, p.y + fontHeight, false, cum);
            }
        };
        for (KeyStroke ks: getContextMenuKeystrokes())
            im.put(ks, contextMenuAction);
    }

    //the right-click popup menu (Cut, Copy, Paste...)
    private static void showContextMenu(
            JTextComponent jtc, int index, int x, int y, boolean mouse, CompoundUndoManager cum) {
        boolean hasSelection = jtc.getSelectionStart() != jtc.getSelectionEnd();

        //if hyperlink
        String hlink;
        if (!mouse && hasSelection) {
            hlink = getHyperlinkAtIndex(jtc, index);
            if (hlink == null)
                hlink = getHyperlinkAtIndex(jtc, index - 1);
        } else {
            hlink = getHyperlinkAtIndex(jtc, index);
        }
        String hyperlink = hlink;

        //if selected text
        if (mouse && hyperlink == null) {
            if (!(jtc.getSelectionStart() <= index && jtc.getSelectionEnd() > index)) {
                jtc.setSelectionStart(index);
                jtc.setSelectionEnd(index);
            }
        }

        //popup menu
        JPopupMenu pm = new JPopupMenu();
        JMenuItem cut = getJMenuItem("Cut", e -> jtc.cut(), CUT_D);
        JMenuItem copy = getJMenuItem("Copy", e -> jtc.copy(), COPY_D);
        JMenuItem paste = getJMenuItem("Paste", e -> jtc.paste(), PASTE_D);
        JMenuItem pastePlain = getJMenuItem("Paste as plain text", e -> pasteAsPlainText(jtc), PASTE_PLAIN_D);
        JMenuItem selAll = getJMenuItem("Select all", e -> jtc.selectAll(), SELECT_ALL_D);
        JMenuItem undo = getJMenuItem("Undo", e -> cum.compoundUndo(), UNDO_D);
        JMenuItem redo = getJMenuItem("Redo", e -> cum.compoundRedo(), REDO_D);

        //editable options
        boolean editable = jtc.isEditable();
        cut.setEnabled(editable);
        paste.setEnabled(editable);
        pastePlain.setEnabled(editable);
        undo.setEnabled(editable);
        redo.setEnabled(editable);

        //undoable/redoable
        if (!cum.canUndo()) undo.setEnabled(false);
        if (!cum.canRedo()) redo.setEnabled(false);

        //require text selection for cut/copy
        if (jtc.getSelectionStart() == jtc.getSelectionEnd()) {
            cut.setEnabled(false);
            copy.setEnabled(false);
        }

        //hyperlink actions
        if (hyperlink != null) {
            //activate hyperlink
            if (jtc instanceof JEditorPane) {
                JEditorPane jep = (JEditorPane)jtc;
                JMenuItem openHyperlink = new JMenuItem("Open link");
                openHyperlink.addActionListener(e -> {
                    HTMLDocument doc = (HTMLDocument)jep.getDocument();
                    Element elem = doc.getCharacterElement(index);
                    URL url;
                    try {
                        url = new URL(doc.getBase(), hyperlink);
                    } catch (MalformedURLException ignore) {
                        url = null;
                    }
                    HyperlinkEvent he = new HyperlinkEvent(
                            jep, HyperlinkEvent.EventType.ACTIVATED, url, hyperlink, elem);
                    jep.fireHyperlinkUpdate(he);
                });
                pm.add(openHyperlink);
            }

            //copy hyperlink to clipboard
            JMenuItem copyHyperlink = new JMenuItem("Copy link");
            copyHyperlink.addActionListener(e -> copyToClipboard(hyperlink));
            pm.add(copyHyperlink);
        }

        //add menuitems
        pm.add(cut);
        pm.add(copy);
        pm.add(paste);
        if (isNotPlainTextContentType(jtc)) pm.add(pastePlain);
        pm.add(selAll);
        pm.add(undo);
        pm.add(redo);

        //show
        pm.show(jtc, x, y);
    }

    //replace DefaultEditorKit actions with XDefaultEditorKit ones
    private static void replaceXDefaultEditorKitActions(JTextComponent jtc) {
        Action[] defActions = new DefaultEditorKit().getActions();
        Action[] xActions = new XDefaultEditorKit().getActions();
        ActionMap am = jtc.getActionMap();
        while (am != null) {
            Object[] camkeys = am.keys();
            if (camkeys != null) {
                for (Object camkey: camkeys) {
                    Action camaction = am.get(camkey);
                    Object name = camaction.getValue(Action.NAME);
                    if (camkey.equals(name)) {
                        for (Action defAction: defActions) {
                            Object defActionName = defAction.getValue(Action.NAME);
                            if (defAction.getClass() == camaction.getClass() && name.equals(defActionName)) {
                                for (Action xAction: xActions) {
                                    Object xActionName = xAction.getValue(Action.NAME);
                                    if (defActionName.equals(xActionName))
                                        am.put(xActionName, xAction);
                                }
                            }
                        }
                    }
                }
            }
            am = am.getParent();
        }
    }

    //return the UI context menu shortcuts (for example Shift+F10 on Windows)
    private static KeyStroke[] getContextMenuKeystrokes() {
        LinkedHashSet<KeyStroke> keys = new LinkedHashSet<>();
        JRootPane jrp = new JRootPane();//rootpane for the shortcuts
        InputMap im = jrp.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        KeyStroke[] imkeys = im.allKeys();
        if (imkeys != null) {
            for (KeyStroke ks: imkeys) {
                Object value = im.get(ks);
                if ("postPopup".equals(value))
                    keys.add(ks);
            }
        }
        return keys.toArray(new KeyStroke[0]);
    }

    //shift the caret bounds safely
    private static void shiftCaret(JTextComponent jtc, int dot, int direction) {
        Caret caret = jtc.getCaret();
        DefaultCaret bidiCaret = caret instanceof DefaultCaret ? (DefaultCaret)caret : null;
        Position.Bias[] bias = new Position.Bias[1];
        try {
            NavigationFilter nf = jtc.getNavigationFilter();
            if (nf != null) {
                dot = nf.getNextVisualPositionFrom(
                        jtc, dot, bidiCaret != null ? bidiCaret.getDotBias() : Position.Bias.Forward, direction, bias);
            } else {
                dot = jtc.getUI().getNextVisualPositionFrom(
                        jtc, dot, bidiCaret != null ? bidiCaret.getDotBias() : Position.Bias.Forward, direction, bias);
            }
            if (bias[0] == null) bias[0] = Position.Bias.Forward;
            if (bidiCaret != null)
                bidiCaret.setDot(dot, bias[0]);
            else
                caret.setDot(dot);
        } catch (BadLocationException ignore) { }
    }

    //get hyperlink of text index
    private static String getHyperlinkAtIndex(JTextComponent jtc, int index) {
        Document d = jtc.getDocument();
        if (!(d instanceof HTMLDocument)) return null;
        HTMLDocument hd = (HTMLDocument)jtc.getDocument();
        HTMLDocument.Iterator docit = hd.getIterator(HTML.Tag.A);
        while (docit.isValid()) {
            if (docit.getStartOffset() <= index && docit.getEndOffset() > index) {
                AttributeSet as = docit.getAttributes();
                Enumeration<?> en = as.getAttributeNames();
                while (en.hasMoreElements())
                    if (en.nextElement() == HTML.Attribute.HREF)
                        return as.getAttribute(HTML.Attribute.HREF).toString();
            }
            docit.next();
        }
        return null;
    }

    //create a jmenuitem with a non-functioning display shortcut
    private static JMenuItem getJMenuItem(String text, ActionListener al, KeyStroke ks) {
        JMenuItem jmi = new JMenuItem(text);
        jmi.addActionListener(al);
        if (ks != null) {//set accelerator without applying keystroke
            jmi.setAccelerator(ks);
            InputMap im = jmi.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
            while (im != null) {
                im.clear();
                im = im.getParent();
            }
        }
        return jmi;
    }
    private static void copyToClipboard(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }
    private static void pasteAsPlainText(JTextComponent jtc) {
        if (!isNotPlainTextContentType(jtc)) {
            jtc.paste();
            return;
        }
        try {
            Object o = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            if (o instanceof String) jtc.replaceSelection((String)o);
        } catch (UnsupportedFlavorException | IOException ignore) { }
    }
    private static boolean isNotPlainTextContentType(JTextComponent jtc) {
        if (jtc instanceof JEditorPane) {
            JEditorPane jep = (JEditorPane)jtc;
            return !"text/plain".equals(jep.getContentType());
        } else {
            return jtc.getUI().getEditorKit(jtc) instanceof StyledEditorKit;
        }
    }

    //undo manager that groups undo and redo edits
    private static final class CompoundUndoManager extends UndoManager {

        //save edits that were typed normally (and not, for example, pasted)
        private boolean editingNormally;
        private final Set<UndoableEdit> normalEdits = Collections.newSetFromMap(new WeakHashMap<>());

        //save the AWTEvent that generated each edit
        private final WeakHashMap<UndoableEdit, AWTEvent> editEventMap = new WeakHashMap<>();
        private AWTEvent editEvent;

        public static CompoundUndoManager createAndApply(JTextComponent jtc) {
            return new CompoundUndoManager(jtc);
        }

        private CompoundUndoManager(JTextComponent jtc) {
            /*
            this is for the compound undo manager to know that an edit is text typed normally,
            and not, for example, pasted
             */
            ActionMap am = jtc.getActionMap();
            if (am != null) {
                Action defKeyAction = am.get(DefaultEditorKit.defaultKeyTypedAction);
                if (defKeyAction != null) {
                    Action newDefKeyAction = new TextAction(DefaultEditorKit.defaultKeyTypedAction) {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            editingNormally = true;
                            defKeyAction.actionPerformed(e);
                            editingNormally = false;
                        }
                    };
                    am.put(DefaultEditorKit.defaultKeyTypedAction, newDefKeyAction);
                }
                Action delPrevCharAction = am.get(DefaultEditorKit.deletePrevCharAction);
                if (delPrevCharAction != null) {
                    Action newDelPrevCharAction = new TextAction(DefaultEditorKit.deletePrevCharAction) {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            if (jtc.getSelectionStart() != jtc.getSelectionEnd()) {
                                delPrevCharAction.actionPerformed(e);
                            } else {
                                editingNormally = true;
                                delPrevCharAction.actionPerformed(e);
                                editingNormally = false;
                            }
                        }
                    };
                    am.put(DefaultEditorKit.deletePrevCharAction, newDelPrevCharAction);
                }
                Action delNextCharAction = am.get(DefaultEditorKit.deleteNextCharAction);
                if (delNextCharAction != null) {
                    Action newDelNextCharAction = new TextAction(DefaultEditorKit.deleteNextCharAction) {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            if (jtc.getSelectionStart() != jtc.getSelectionEnd()) {
                                delNextCharAction.actionPerformed(e);
                            } else {
                                editingNormally = true;
                                delNextCharAction.actionPerformed(e);
                                editingNormally = false;
                            }
                        }
                    };
                    am.put(DefaultEditorKit.deleteNextCharAction, newDelNextCharAction);
                }
            }
            Keymap km = jtc.getKeymap();
            if (km != null) {
                Action kmDefKeyAction = km.getDefaultAction();
                if (kmDefKeyAction != null) {
                    km.setDefaultAction(new TextAction(DefaultEditorKit.defaultKeyTypedAction) {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            editingNormally = true;
                            kmDefKeyAction.actionPerformed(e);
                            editingNormally = false;
                        }
                    });
                }
            }
            jtc.getDocument().addUndoableEditListener(this);
            addAWTEventListener(jtc, e -> editEvent = e);
            jtc.getComponentPopupMenu();
        }

        @Override
        public boolean addEdit(UndoableEdit anEdit) {
            if (editingNormally) normalEdits.add(anEdit);
            editEventMap.put(anEdit, editEvent);
            return super.addEdit(anEdit);
        }

        public void compoundUndo() {
            while (canUndo()) {
                UndoableEdit ue = editToBeUndone();
                undo();
                UndoableEdit prevue = editToBeUndone();
                if (!areCompoundEdits(ue, prevue)) return;
            }
        }

        public void compoundRedo() {
            while (canRedo()) {
                UndoableEdit ue = editToBeRedone();
                redo();
                UndoableEdit nextue = editToBeRedone();
                if (!areCompoundEdits(nextue, ue)) return;
            }
        }

        @Override
        protected void trimForLimit() {//overridden for compound limit support
            int limit = getLimit();
            if (limit <= 0) {
                super.trimForLimit();
                return;
            }
            if (edits.size() <= 1) return;
            int count = 0;
            UndoableEdit ue, nextue = null;
            for (int i = edits.size() - 1; i >= 0; i--) {
                ue = edits.get(i);
                while (!ue.isSignificant()) {
                    if (i <= 0) return;
                    ue = edits.get(--i);
                }
                if (!areCompoundEdits(nextue, ue)) count++;
                nextue = ue;
                if (count > limit) {
                    trimEdits(0, i);
                    return;
                }
            }
        }

        private boolean areCompoundEdits(UndoableEdit edit, UndoableEdit precedingEdit) {
            //probably unnecessary but also serves as null check
            if (!(edit instanceof AbstractDocument.DefaultDocumentEvent)
                    || !(precedingEdit instanceof AbstractDocument.DefaultDocumentEvent)) {
                return false;
            }

            //edits that have the same AWTEvent are compounded
            //(this happens when you replace text. it will cause two edits,
            // a remove edit and an add edit.)
            if (editEventMap.get(edit) == editEventMap.get(precedingEdit)) return true;

            //compounded edits must be normal edits
            if (!normalEdits.contains(edit) || !normalEdits.contains(precedingEdit)) return false;

            //test equality of type and offset
            AbstractDocument.DefaultDocumentEvent dde = (AbstractDocument.DefaultDocumentEvent)edit;
            AbstractDocument.DefaultDocumentEvent prevdde = (AbstractDocument.DefaultDocumentEvent)precedingEdit;
            DocumentEvent.EventType type = dde.getType();
            if (type != prevdde.getType()) return false;
            if (type == DocumentEvent.EventType.INSERT) {
                return prevdde.getOffset() + prevdde.getLength() == dde.getOffset();
            } else if (type == DocumentEvent.EventType.REMOVE) {
                return dde.getOffset() + dde.getLength() == prevdde.getOffset();
            }
            return false;
        }

    }

    //listen to all AWT events of a component
    private static void addAWTEventListener(Component c, AWTEventListener l) {
        c.addFocusListener(new FocusListener() {
            @Override public void focusGained(FocusEvent e) { l.eventDispatched(e); }
            @Override public void focusLost(FocusEvent e) { l.eventDispatched(e); }
        });
        c.addMouseWheelListener(l::eventDispatched);
        c.addMouseMotionListener(new MouseMotionListener() {
            @Override public void mouseDragged(MouseEvent e) { l.eventDispatched(e); }
            @Override public void mouseMoved(MouseEvent e) { l.eventDispatched(e); }
        });
        c.addMouseListener(new MouseListener() {
            @Override public void mouseClicked(MouseEvent e) { l.eventDispatched(e); }
            @Override public void mousePressed(MouseEvent e) { l.eventDispatched(e); }
            @Override public void mouseReleased(MouseEvent e) { l.eventDispatched(e); }
            @Override public void mouseEntered(MouseEvent e) { l.eventDispatched(e); }
            @Override public void mouseExited(MouseEvent e) { l.eventDispatched(e); }
        });
        c.addKeyListener(new KeyListener() {
            @Override public void keyTyped(KeyEvent e) { l.eventDispatched(e); }
            @Override public void keyPressed(KeyEvent e) { l.eventDispatched(e); }
            @Override public void keyReleased(KeyEvent e) { l.eventDispatched(e); }
        });
        c.addInputMethodListener(new InputMethodListener() {
            @Override public void inputMethodTextChanged(InputMethodEvent event) { l.eventDispatched(event); }
            @Override public void caretPositionChanged(InputMethodEvent event) { l.eventDispatched(event); }
        });
        c.addComponentListener(new ComponentListener() {
            @Override public void componentResized(ComponentEvent e) { l.eventDispatched(e); }
            @Override public void componentMoved(ComponentEvent e) { l.eventDispatched(e); }
            @Override public void componentShown(ComponentEvent e) { l.eventDispatched(e); }
            @Override public void componentHidden(ComponentEvent e) { l.eventDispatched(e); }
        });
        c.addHierarchyListener(l::eventDispatched);
        c.addHierarchyBoundsListener(new HierarchyBoundsListener() {
            @Override public void ancestorMoved(HierarchyEvent e) { l.eventDispatched(e); }
            @Override public void ancestorResized(HierarchyEvent e) { l.eventDispatched(e); }
        });
    }

    //the platform-specific gui shortcuts for Undo and Redo and Paste as plain text
    private static final KeyStroke[] UNDO, REDO, PASTE_PLAIN;
    private static final KeyStroke CUT_D, COPY_D, PASTE_D, PASTE_PLAIN_D, SELECT_ALL_D, UNDO_D, REDO_D;//display
    static {
        boolean isWindows = System.getProperty("os.name").startsWith("Windows");
        boolean isMac = System.getProperty("os.name").startsWith("Mac");

        //input mask: Ctrl on Windows/Linux, Cmd on Mac
        int ctrlOrCmd = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();

        /*
        the shortcuts for Undo
        Windows: [Ctrl + Z] and [Alt + Backspace]
        Linux: [Ctrl + Z]
        Mac: [Cmd + Z]
         */
        KeyStroke cZ = KeyStroke.getKeyStroke(KeyEvent.VK_Z, ctrlOrCmd);
        KeyStroke undo = KeyStroke.getKeyStroke(KeyEvent.VK_UNDO, 0);
        if (isWindows) {
            KeyStroke altBackspace = KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, InputEvent.ALT_DOWN_MASK);
            //necessary for right alt key to work
            KeyStroke altGrBackspace = KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, InputEvent.ALT_GRAPH_DOWN_MASK);
            KeyStroke altAltGrBackspace = KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE,
                    InputEvent.ALT_DOWN_MASK | InputEvent.ALT_GRAPH_DOWN_MASK);
            UNDO = new KeyStroke[] { cZ, altBackspace, altGrBackspace, altAltGrBackspace, undo };
        } else {
            UNDO = new KeyStroke[] { cZ, undo };
        }

        /*
        the shortcuts for Redo
        Windows: [Ctrl + Y] and [Ctrl + Shift + Z]
        Linux: [Ctrl + Shift + Z] and [Ctrl + Y]
        Mac: [Cmd + Shift + Z] and [Cmd + Y]
         */
        KeyStroke cY = KeyStroke.getKeyStroke(KeyEvent.VK_Y, ctrlOrCmd);
        KeyStroke cShiftZ = KeyStroke.getKeyStroke(KeyEvent.VK_Z, ctrlOrCmd | InputEvent.SHIFT_DOWN_MASK);
        REDO = isWindows ? new KeyStroke[] { cY, cShiftZ } : new KeyStroke[] { cShiftZ, cY };

        /*
        the shortcuts for Paste as plain text
        Windows: [Ctrl + Shift + V]
        Linux: [Ctrl + Shift + V]
        Mac: [Option + Cmd + Shift + V]
         */
        KeyStroke cShiftV = KeyStroke.getKeyStroke(KeyEvent.VK_V, ctrlOrCmd | InputEvent.SHIFT_DOWN_MASK);
        KeyStroke cShiftAltV = KeyStroke.getKeyStroke(
                KeyEvent.VK_V, ctrlOrCmd | InputEvent.SHIFT_DOWN_MASK | InputEvent.ALT_DOWN_MASK);
        KeyStroke cShiftAltGrV = KeyStroke.getKeyStroke(
                KeyEvent.VK_V, ctrlOrCmd | InputEvent.SHIFT_DOWN_MASK | InputEvent.ALT_GRAPH_DOWN_MASK);
        KeyStroke cShiftAltAltGrV = KeyStroke.getKeyStroke(
                KeyEvent.VK_V, ctrlOrCmd | InputEvent.SHIFT_DOWN_MASK
                        | InputEvent.ALT_DOWN_MASK | InputEvent.ALT_GRAPH_DOWN_MASK);
        PASTE_PLAIN = isMac
                ? new KeyStroke[] { cShiftAltV, cShiftAltGrV, cShiftAltAltGrV }
                : new KeyStroke[] { cShiftV };

        //the display shortcuts for context menus
        KeyStroke cX = KeyStroke.getKeyStroke(KeyEvent.VK_X, ctrlOrCmd);
        KeyStroke cC = KeyStroke.getKeyStroke(KeyEvent.VK_C, ctrlOrCmd);
        KeyStroke cV = KeyStroke.getKeyStroke(KeyEvent.VK_V, ctrlOrCmd);
        KeyStroke cA = KeyStroke.getKeyStroke(KeyEvent.VK_A, ctrlOrCmd);
        CUT_D = cX;
        COPY_D = cC;
        PASTE_D = cV;
        PASTE_PLAIN_D = PASTE_PLAIN[0];
        SELECT_ALL_D = cA;
        UNDO_D = UNDO[0];
        REDO_D = REDO[0];
    }

}
