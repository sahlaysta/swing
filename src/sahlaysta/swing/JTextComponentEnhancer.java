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

import sun.awt.SunToolkit;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.ComponentOrientation;
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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
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
import javax.swing.text.EditorKit;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import javax.swing.text.Keymap;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.NavigationFilter;
import javax.swing.text.PlainDocument;
import javax.swing.text.Position;
import javax.swing.text.Segment;
import javax.swing.text.StyledEditorKit;
import javax.swing.text.TextAction;
import javax.swing.text.Utilities;
import javax.swing.text.ViewFactory;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import javax.swing.undo.UndoManager;
import javax.swing.undo.UndoableEdit;

/*
custom enhancements to Swing text components
- add compound undo/redo functionality and shortcuts
- add right-click popup menu with (Cut, Copy, Paste...)
- adjust behavior of having text selected and the right/left arrow key is pressed
- adjust right-click behavior on text selection
- add hyperlink right-click support
- disable beeps (for example, backspace in an empty text field)
 */
public final class JTextComponentEnhancer {

    private JTextComponentEnhancer() { }

    //keys in map data
    private static final String UNDO_KEY = "sahlaysta.undo";
    private static final String REDO_KEY = "sahlaysta.redo";
    private static final String PASTE_PLAIN_KEY = "sahlasyta.pasteplain";

    //store enhanced components by weak reference
    private static final Set<JTextComponent> enhancedComps = Collections.newSetFromMap(new WeakHashMap<>());

    //automatically enhance all JTextComponents
    public static void applyGlobalEnhancer() {
        Toolkit.getDefaultToolkit().addAWTEventListener(e -> {
            if (e.getID() != ContainerEvent.COMPONENT_ADDED) return;
            if (!(e instanceof ContainerEvent)) return;
            if (!(e.getSource() instanceof Container)) return;
            Component c = ((ContainerEvent)e).getChild();
            if (!(c instanceof JTextComponent)) return;

            JTextComponent jtc = (JTextComponent)c;

            //if jtextcomponent is already enhanced
            if (enhancedComps.contains(jtc)) return;
            enhancedComps.add(jtc);

            enhanceJTextComponent(jtc);
        }, AWTEvent.CONTAINER_EVENT_MASK);
    }

    //enhance a jtextcomponent
    public static void enhanceJTextComponent(JTextComponent jtc) {
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
        private boolean editingNormally;
        private final Set<UndoableEdit> normalEdits = Collections.newSetFromMap(new WeakHashMap<>());
        private final WeakHashMap<UndoableEdit, AWTEvent> editEventMap = new WeakHashMap<>();
        private AWTEvent editEvent;
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
        public static CompoundUndoManager createAndApply(JTextComponent jtc) {
            return new CompoundUndoManager(jtc);
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
                if (areSeparateEdits(ue, prevue)) return;
            }
        }
        public void compoundRedo() {
            while (canRedo()) {
                UndoableEdit ue = editToBeRedone();
                redo();
                UndoableEdit nextue = editToBeRedone();
                if (areSeparateEdits(nextue, ue)) return;
            }
        }
        @Override
        protected void trimForLimit() {//overridden for compound limit support
            int limit = getLimit();
            if (limit <= 0) {
                super.trimForLimit();
                return;
            }
            UndoableEdit[] sEdits = edits.stream().filter(UndoableEdit::isSignificant).toArray(UndoableEdit[]::new);
            if (sEdits.length <= 1) return;
            int count = 0;
            UndoableEdit ue, nextue = null;
            for (int i = sEdits.length - 1; i >= 0; i--) {
                ue = sEdits[i];
                if (areSeparateEdits(nextue, ue)) count++;
                nextue = ue;
                if (count > limit) {
                    trimEdits(0, i);
                    return;
                }
            }
        }
        private boolean areSeparateEdits(UndoableEdit edit, UndoableEdit precedingEdit) {
            if (!(edit instanceof AbstractDocument.DefaultDocumentEvent)
                    || !(precedingEdit instanceof AbstractDocument.DefaultDocumentEvent)) {
                return true;
            }
            if (editEventMap.get(edit) == editEventMap.get(precedingEdit)) return false;
            if (!normalEdits.contains(edit) || !normalEdits.contains(precedingEdit)) return true;
            AbstractDocument.DefaultDocumentEvent dde = (AbstractDocument.DefaultDocumentEvent)edit;
            AbstractDocument.DefaultDocumentEvent prevdde = (AbstractDocument.DefaultDocumentEvent)precedingEdit;
            DocumentEvent.EventType type = dde.getType();
            if (type != prevdde.getType()) return true;
            if (type == DocumentEvent.EventType.INSERT) {
                return prevdde.getOffset() + prevdde.getLength() != dde.getOffset();
            } else if (type == DocumentEvent.EventType.REMOVE) {
                return dde.getOffset() + dde.getLength() != prevdde.getOffset();
            }
            return true;
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

    //replace TextAction in XDefaultEditorKit for custom beep action
    private static abstract class BeepableTextAction extends TextAction {
        BeepableTextAction(String name) {
            super(name);
        }
        void provideErrorFeedback(Component component) {
            //javax.swing.UIManager.getLookAndFeel().provideErrorFeedback(component);
        }
    }

    //copy-pasted DefaultEditorKit.java
    /**
     * This is the set of things needed by a text component
     * to be a reasonably functioning editor for some <em>type</em>
     * of text document.  This implementation provides a default
     * implementation which treats text as plain text and
     * provides a minimal set of actions for a simple editor.
     *
     * <dl>
     * <dt><b><font size=+1>Newlines</font></b>
     * <dd>
     * There are two properties which deal with newlines.  The
     * system property, <code>line.separator</code>, is defined to be
     * platform-dependent, either "\n", "\r", or "\r\n".  There is also
     * a property defined in <code>DefaultEditorKit</code>, called
     * <a href=#EndOfLineStringProperty><code>EndOfLineStringProperty</code></a>,
     * which is defined automatically when a document is loaded, to be
     * the first occurrence of any of the newline characters.
     * When a document is loaded, <code>EndOfLineStringProperty</code>
     * is set appropriately, and when the document is written back out, the
     * <code>EndOfLineStringProperty</code> is used.  But while the document
     * is in memory, the "\n" character is used to define a
     * newline, regardless of how the newline is defined when
     * the document is on disk.  Therefore, for searching purposes,
     * "\n" should always be used.  When a new document is created,
     * and the <code>EndOfLineStringProperty</code> has not been defined,
     * it will use the System property when writing out the
     * document.
     * <p>Note that <code>EndOfLineStringProperty</code> is set
     * on the <code>Document</code> using the <code>get/putProperty</code>
     * methods.  Subclasses may override this behavior.
     *
     * </dl>
     *
     * @author  Timothy Prinzing
     */
    private static final class XDefaultEditorKit extends EditorKit {

        /**
         * default constructor for DefaultEditorKit
         */
        public XDefaultEditorKit() {
        }

        /**
         * Gets the MIME type of the data that this
         * kit represents support for.  The default
         * is <code>text/plain</code>.
         *
         * @return the type
         */
        public String getContentType() {
            return "text/plain";
        }

        /**
         * Fetches a factory that is suitable for producing
         * views of any models that are produced by this
         * kit.  The default is to have the UI produce the
         * factory, so this method has no implementation.
         *
         * @return the view factory
         */
        public ViewFactory getViewFactory() {
            return null;
        }

        /**
         * Fetches the set of commands that can be used
         * on a text component that is using a model and
         * view produced by this kit.
         *
         * @return the command list
         */
        public Action[] getActions() {
            return defaultActions.clone();
        }

        /**
         * Fetches a caret that can navigate through views
         * produced by the associated ViewFactory.
         *
         * @return the caret
         */
        public Caret createCaret() {
            return null;
        }

        /**
         * Creates an uninitialized text storage model (PlainDocument)
         * that is appropriate for this type of editor.
         *
         * @return the model
         */
        public Document createDefaultDocument() {
            return new PlainDocument();
        }

        /**
         * Inserts content from the given stream which is expected
         * to be in a format appropriate for this kind of content
         * handler.
         *
         * @param in  The stream to read from
         * @param doc The destination for the insertion.
         * @param pos The location in the document to place the
         *   content &gt;=0.
         * @exception IOException on any I/O error
         * @exception BadLocationException if pos represents an invalid
         *   location within the document.
         */
        public void read(InputStream in, Document doc, int pos)
                throws IOException, BadLocationException {

            read(new InputStreamReader(in), doc, pos);
        }

        /**
         * Writes content from a document to the given stream
         * in a format appropriate for this kind of content handler.
         *
         * @param out The stream to write to
         * @param doc The source for the write.
         * @param pos The location in the document to fetch the
         *   content &gt;=0.
         * @param len The amount to write out &gt;=0.
         * @exception IOException on any I/O error
         * @exception BadLocationException if pos represents an invalid
         *   location within the document.
         */
        public void write(OutputStream out, Document doc, int pos, int len)
                throws IOException, BadLocationException {
            OutputStreamWriter osw = new OutputStreamWriter(out);

            write(osw, doc, pos, len);
            osw.flush();
        }

        /**
         * Gets the input attributes for the pane. This method exists for
         * the benefit of StyledEditorKit so that the read method will
         * pick up the correct attributes to apply to inserted text.
         * This class's implementation simply returns null.
         *
         * @return null
         */
        MutableAttributeSet getInputAttributes() {
            return null;
        }

        /**
         * Inserts content from the given stream, which will be
         * treated as plain text.
         *
         * @param in  The stream to read from
         * @param doc The destination for the insertion.
         * @param pos The location in the document to place the
         *   content &gt;=0.
         * @exception IOException on any I/O error
         * @exception BadLocationException if pos represents an invalid
         *   location within the document.
         */
        public void read(Reader in, Document doc, int pos)
                throws IOException, BadLocationException {

            char[] buff = new char[4096];
            int nch;
            boolean lastWasCR = false;
            boolean isCRLF = false;
            boolean isCR = false;
            int last;
            boolean wasEmpty = (doc.getLength() == 0);
            AttributeSet attr = getInputAttributes();

            // Read in a block at a time, mapping \r\n to \n, as well as single
            // \r's to \n's. If a \r\n is encountered, \r\n will be set as the
            // newline string for the document, if \r is encountered it will
            // be set as the newline character, otherwise the newline property
            // for the document will be removed.
            while ((nch = in.read(buff, 0, buff.length)) != -1) {
                last = 0;
                for(int counter = 0; counter < nch; counter++) {
                    switch(buff[counter]) {
                        case '\r':
                            if (lastWasCR) {
                                isCR = true;
                                if (counter == 0) {
                                    doc.insertString(pos, "\n", attr);
                                    pos++;
                                }
                                else {
                                    buff[counter - 1] = '\n';
                                }
                            }
                            else {
                                lastWasCR = true;
                            }
                            break;
                        case '\n':
                            if (lastWasCR) {
                                if (counter > (last + 1)) {
                                    doc.insertString(pos, new String(buff, last,
                                            counter - last - 1), attr);
                                    pos += (counter - last - 1);
                                }
                                // else nothing to do, can skip \r, next write will
                                // write \n
                                lastWasCR = false;
                                last = counter;
                                isCRLF = true;
                            }
                            break;
                        default:
                            if (lastWasCR) {
                                isCR = true;
                                if (counter == 0) {
                                    doc.insertString(pos, "\n", attr);
                                    pos++;
                                }
                                else {
                                    buff[counter - 1] = '\n';
                                }
                                lastWasCR = false;
                            }
                            break;
                    }
                }
                if (last < nch) {
                    if(lastWasCR) {
                        if (last < (nch - 1)) {
                            doc.insertString(pos, new String(buff, last,
                                    nch - last - 1), attr);
                            pos += (nch - last - 1);
                        }
                    }
                    else {
                        doc.insertString(pos, new String(buff, last,
                                nch - last), attr);
                        pos += (nch - last);
                    }
                }
            }
            if (lastWasCR) {
                doc.insertString(pos, "\n", attr);
                isCR = true;
            }
            if (wasEmpty) {
                if (isCRLF) {
                    doc.putProperty(EndOfLineStringProperty, "\r\n");
                }
                else if (isCR) {
                    doc.putProperty(EndOfLineStringProperty, "\r");
                }
                else {
                    doc.putProperty(EndOfLineStringProperty, "\n");
                }
            }
        }

        /**
         * Writes content from a document to the given stream
         * as plain text.
         *
         * @param out  The stream to write to
         * @param doc The source for the write.
         * @param pos The location in the document to fetch the
         *   content from &gt;=0.
         * @param len The amount to write out &gt;=0.
         * @exception IOException on any I/O error
         * @exception BadLocationException if pos is not within 0 and
         *   the length of the document.
         */
        public void write(Writer out, Document doc, int pos, int len)
                throws IOException, BadLocationException {

            if ((pos < 0) || ((pos + len) > doc.getLength())) {
                throw new BadLocationException("DefaultEditorKit.write", pos);
            }
            Segment data = new Segment();
            int nleft = len;
            int offs = pos;
            Object endOfLineProperty = doc.getProperty(EndOfLineStringProperty);
            if (endOfLineProperty == null) {
                try {
                    endOfLineProperty = System.getProperty("line.separator");
                } catch (SecurityException se) { }
            }
            String endOfLine;
            if (endOfLineProperty instanceof String) {
                endOfLine = (String)endOfLineProperty;
            }
            else {
                endOfLine = null;
            }
            if (endOfLineProperty != null && !endOfLine.equals("\n")) {
                // There is an end of line string that isn't \n, have to iterate
                // through and find all \n's and translate to end of line string.
                while (nleft > 0) {
                    int n = Math.min(nleft, 4096);
                    doc.getText(offs, n, data);
                    int last = data.offset;
                    char[] array = data.array;
                    int maxCounter = last + data.count;
                    for (int counter = last; counter < maxCounter; counter++) {
                        if (array[counter] == '\n') {
                            if (counter > last) {
                                out.write(array, last, counter - last);
                            }
                            out.write(endOfLine);
                            last = counter + 1;
                        }
                    }
                    if (maxCounter > last) {
                        out.write(array, last, maxCounter - last);
                    }
                    offs += n;
                    nleft -= n;
                }
            }
            else {
                // Just write out text, will already have \n, no mapping to
                // do.
                while (nleft > 0) {
                    int n = Math.min(nleft, 4096);
                    doc.getText(offs, n, data);
                    out.write(data.array, data.offset, data.count);
                    offs += n;
                    nleft -= n;
                }
            }
            out.flush();
        }


        /**
         * When reading a document if a CRLF is encountered a property
         * with this name is added and the value will be "\r\n".
         */
        public static final String EndOfLineStringProperty = "__EndOfLine__";

        // --- names of well-known actions ---------------------------

        /**
         * Name of the action to place content into the associated
         * document.  If there is a selection, it is removed before
         * the new content is added.
         * @see #getActions
         */
        public static final String insertContentAction = "insert-content";

        /**
         * Name of the action to place a line/paragraph break into
         * the document.  If there is a selection, it is removed before
         * the break is added.
         * @see #getActions
         */
        public static final String insertBreakAction = "insert-break";

        /**
         * Name of the action to place a tab character into
         * the document.  If there is a selection, it is removed before
         * the tab is added.
         * @see #getActions
         */
        public static final String insertTabAction = "insert-tab";

        /**
         * Name of the action to delete the character of content that
         * precedes the current caret position.
         * @see #getActions
         */
        public static final String deletePrevCharAction = "delete-previous";

        /**
         * Name of the action to delete the character of content that
         * follows the current caret position.
         * @see #getActions
         */
        public static final String deleteNextCharAction = "delete-next";

        /**
         * Name of the action to delete the word that
         * follows the beginning of the selection.
         * @see #getActions
         * @see JTextComponent#getSelectionStart
         * @since 1.6
         */
        public static final String deleteNextWordAction = "delete-next-word";

        /**
         * Name of the action to delete the word that
         * precedes the beginning of the selection.
         * @see #getActions
         * @see JTextComponent#getSelectionStart
         * @since 1.6
         */
        public static final String deletePrevWordAction = "delete-previous-word";

        /**
         * Name of the action to set the editor into read-only
         * mode.
         * @see #getActions
         */
        public static final String readOnlyAction = "set-read-only";

        /**
         * Name of the action to set the editor into writeable
         * mode.
         * @see #getActions
         */
        public static final String writableAction = "set-writable";

        /**
         * Name of the action to cut the selected region
         * and place the contents into the system clipboard.
         * @see JTextComponent#cut
         * @see #getActions
         */
        public static final String cutAction = "cut-to-clipboard";

        /**
         * Name of the action to copy the selected region
         * and place the contents into the system clipboard.
         * @see JTextComponent#copy
         * @see #getActions
         */
        public static final String copyAction = "copy-to-clipboard";

        /**
         * Name of the action to paste the contents of the
         * system clipboard into the selected region, or before the
         * caret if nothing is selected.
         * @see JTextComponent#paste
         * @see #getActions
         */
        public static final String pasteAction = "paste-from-clipboard";

        /**
         * Name of the action to create a beep.
         * @see #getActions
         */
        public static final String beepAction = "beep";

        /**
         * Name of the action to page up vertically.
         * @see #getActions
         */
        public static final String pageUpAction = "page-up";

        /**
         * Name of the action to page down vertically.
         * @see #getActions
         */
        public static final String pageDownAction = "page-down";

        /**
         * Name of the action to page up vertically, and move the
         * selection.
         * @see #getActions
         */
        /*public*/ static final String selectionPageUpAction = "selection-page-up";

        /**
         * Name of the action to page down vertically, and move the
         * selection.
         * @see #getActions
         */
        /*public*/ static final String selectionPageDownAction = "selection-page-down";

        /**
         * Name of the action to page left horizontally, and move the
         * selection.
         * @see #getActions
         */
        /*public*/ static final String selectionPageLeftAction = "selection-page-left";

        /**
         * Name of the action to page right horizontally, and move the
         * selection.
         * @see #getActions
         */
        /*public*/ static final String selectionPageRightAction = "selection-page-right";

        /**
         * Name of the Action for moving the caret
         * logically forward one position.
         * @see #getActions
         */
        public static final String forwardAction = "caret-forward";

        /**
         * Name of the Action for moving the caret
         * logically backward one position.
         * @see #getActions
         */
        public static final String backwardAction = "caret-backward";

        /**
         * Name of the Action for extending the selection
         * by moving the caret logically forward one position.
         * @see #getActions
         */
        public static final String selectionForwardAction = "selection-forward";

        /**
         * Name of the Action for extending the selection
         * by moving the caret logically backward one position.
         * @see #getActions
         */
        public static final String selectionBackwardAction = "selection-backward";

        /**
         * Name of the Action for moving the caret
         * logically upward one position.
         * @see #getActions
         */
        public static final String upAction = "caret-up";

        /**
         * Name of the Action for moving the caret
         * logically downward one position.
         * @see #getActions
         */
        public static final String downAction = "caret-down";

        /**
         * Name of the Action for moving the caret
         * logically upward one position, extending the selection.
         * @see #getActions
         */
        public static final String selectionUpAction = "selection-up";

        /**
         * Name of the Action for moving the caret
         * logically downward one position, extending the selection.
         * @see #getActions
         */
        public static final String selectionDownAction = "selection-down";

        /**
         * Name of the <code>Action</code> for moving the caret
         * to the beginning of a word.
         * @see #getActions
         */
        public static final String beginWordAction = "caret-begin-word";

        /**
         * Name of the Action for moving the caret
         * to the end of a word.
         * @see #getActions
         */
        public static final String endWordAction = "caret-end-word";

        /**
         * Name of the <code>Action</code> for moving the caret
         * to the beginning of a word, extending the selection.
         * @see #getActions
         */
        public static final String selectionBeginWordAction = "selection-begin-word";

        /**
         * Name of the Action for moving the caret
         * to the end of a word, extending the selection.
         * @see #getActions
         */
        public static final String selectionEndWordAction = "selection-end-word";

        /**
         * Name of the <code>Action</code> for moving the caret to the
         * beginning of the previous word.
         * @see #getActions
         */
        public static final String previousWordAction = "caret-previous-word";

        /**
         * Name of the <code>Action</code> for moving the caret to the
         * beginning of the next word.
         * @see #getActions
         */
        public static final String nextWordAction = "caret-next-word";

        /**
         * Name of the <code>Action</code> for moving the selection to the
         * beginning of the previous word, extending the selection.
         * @see #getActions
         */
        public static final String selectionPreviousWordAction = "selection-previous-word";

        /**
         * Name of the <code>Action</code> for moving the selection to the
         * beginning of the next word, extending the selection.
         * @see #getActions
         */
        public static final String selectionNextWordAction = "selection-next-word";

        /**
         * Name of the <code>Action</code> for moving the caret
         * to the beginning of a line.
         * @see #getActions
         */
        public static final String beginLineAction = "caret-begin-line";

        /**
         * Name of the <code>Action</code> for moving the caret
         * to the end of a line.
         * @see #getActions
         */
        public static final String endLineAction = "caret-end-line";

        /**
         * Name of the <code>Action</code> for moving the caret
         * to the beginning of a line, extending the selection.
         * @see #getActions
         */
        public static final String selectionBeginLineAction = "selection-begin-line";

        /**
         * Name of the <code>Action</code> for moving the caret
         * to the end of a line, extending the selection.
         * @see #getActions
         */
        public static final String selectionEndLineAction = "selection-end-line";

        /**
         * Name of the <code>Action</code> for moving the caret
         * to the beginning of a paragraph.
         * @see #getActions
         */
        public static final String beginParagraphAction = "caret-begin-paragraph";

        /**
         * Name of the <code>Action</code> for moving the caret
         * to the end of a paragraph.
         * @see #getActions
         */
        public static final String endParagraphAction = "caret-end-paragraph";

        /**
         * Name of the <code>Action</code> for moving the caret
         * to the beginning of a paragraph, extending the selection.
         * @see #getActions
         */
        public static final String selectionBeginParagraphAction = "selection-begin-paragraph";

        /**
         * Name of the <code>Action</code> for moving the caret
         * to the end of a paragraph, extending the selection.
         * @see #getActions
         */
        public static final String selectionEndParagraphAction = "selection-end-paragraph";

        /**
         * Name of the <code>Action</code> for moving the caret
         * to the beginning of the document.
         * @see #getActions
         */
        public static final String beginAction = "caret-begin";

        /**
         * Name of the <code>Action</code> for moving the caret
         * to the end of the document.
         * @see #getActions
         */
        public static final String endAction = "caret-end";

        /**
         * Name of the <code>Action</code> for moving the caret
         * to the beginning of the document.
         * @see #getActions
         */
        public static final String selectionBeginAction = "selection-begin";

        /**
         * Name of the Action for moving the caret
         * to the end of the document.
         * @see #getActions
         */
        public static final String selectionEndAction = "selection-end";

        /**
         * Name of the Action for selecting a word around the caret.
         * @see #getActions
         */
        public static final String selectWordAction = "select-word";

        /**
         * Name of the Action for selecting a line around the caret.
         * @see #getActions
         */
        public static final String selectLineAction = "select-line";

        /**
         * Name of the Action for selecting a paragraph around the caret.
         * @see #getActions
         */
        public static final String selectParagraphAction = "select-paragraph";

        /**
         * Name of the Action for selecting the entire document
         * @see #getActions
         */
        public static final String selectAllAction = "select-all";

        /**
         * Name of the Action for removing selection
         * @see #getActions
         */
        /*public*/ static final String unselectAction = "unselect";

        /**
         * Name of the Action for toggling the component's orientation.
         * @see #getActions
         */
        /*public*/ static final String toggleComponentOrientationAction
                = "toggle-componentOrientation";

        /**
         * Name of the action that is executed by default if
         * a <em>key typed event</em> is received and there
         * is no keymap entry.
         * @see #getActions
         */
        public static final String defaultKeyTypedAction = "default-typed";

        // --- Action implementations ---------------------------------

        private static final Action[] defaultActions = {
                new InsertContentAction(), new DeletePrevCharAction(),
                new DeleteNextCharAction(), new ReadOnlyAction(),
                new DeleteWordAction(deletePrevWordAction),
                new DeleteWordAction(deleteNextWordAction),
                new WritableAction(), new CutAction(),
                new CopyAction(), new PasteAction(),
                new VerticalPageAction(pageUpAction, -1, false),
                new VerticalPageAction(pageDownAction, 1, false),
                new VerticalPageAction(selectionPageUpAction, -1, true),
                new VerticalPageAction(selectionPageDownAction, 1, true),
                new PageAction(selectionPageLeftAction, true, true),
                new PageAction(selectionPageRightAction, false, true),
                new InsertBreakAction(), new BeepAction(),
                new NextVisualPositionAction(forwardAction, false,
                        SwingConstants.EAST),
                new NextVisualPositionAction(backwardAction, false,
                        SwingConstants.WEST),
                new NextVisualPositionAction(selectionForwardAction, true,
                        SwingConstants.EAST),
                new NextVisualPositionAction(selectionBackwardAction, true,
                        SwingConstants.WEST),
                new NextVisualPositionAction(upAction, false,
                        SwingConstants.NORTH),
                new NextVisualPositionAction(downAction, false,
                        SwingConstants.SOUTH),
                new NextVisualPositionAction(selectionUpAction, true,
                        SwingConstants.NORTH),
                new NextVisualPositionAction(selectionDownAction, true,
                        SwingConstants.SOUTH),
                new BeginWordAction(beginWordAction, false),
                new EndWordAction(endWordAction, false),
                new BeginWordAction(selectionBeginWordAction, true),
                new EndWordAction(selectionEndWordAction, true),
                new PreviousWordAction(previousWordAction, false),
                new NextWordAction(nextWordAction, false),
                new PreviousWordAction(selectionPreviousWordAction, true),
                new NextWordAction(selectionNextWordAction, true),
                new BeginLineAction(beginLineAction, false),
                new EndLineAction(endLineAction, false),
                new BeginLineAction(selectionBeginLineAction, true),
                new EndLineAction(selectionEndLineAction, true),
                new BeginParagraphAction(beginParagraphAction, false),
                new EndParagraphAction(endParagraphAction, false),
                new BeginParagraphAction(selectionBeginParagraphAction, true),
                new EndParagraphAction(selectionEndParagraphAction, true),
                new BeginAction(beginAction, false),
                new EndAction(endAction, false),
                new BeginAction(selectionBeginAction, true),
                new EndAction(selectionEndAction, true),
                new DefaultKeyTypedAction(), new InsertTabAction(),
                new SelectWordAction(), new SelectLineAction(),
                new SelectParagraphAction(), new SelectAllAction(),
                new UnselectAction(), new ToggleComponentOrientationAction(),
                new DumpModelAction()
        };

        /**
         * The action that is executed by default if
         * a <em>key typed event</em> is received and there
         * is no keymap entry.  There is a variation across
         * different VM's in what gets sent as a <em>key typed</em>
         * event, and this action tries to filter out the undesired
         * events.  This filters the control characters and those
         * with the ALT modifier.  It allows Control-Alt sequences
         * through as these form legitimate unicode characters on
         * some PC keyboards.
         * <p>
         * If the event doesn't get filtered, it will try to insert
         * content into the text editor.  The content is fetched
         * from the command string of the ActionEvent.  The text
         * entry is done through the <code>replaceSelection</code>
         * method on the target text component.  This is the
         * action that will be fired for most text entry tasks.
         * <p>
         * <strong>Warning:</strong>
         * Serialized objects of this class will not be compatible with
         * future Swing releases. The current serialization support is
         * appropriate for short term storage or RMI between applications running
         * the same version of Swing.  As of 1.4, support for long term storage
         * of all JavaBeans&trade;
         * has been added to the <code>java.beans</code> package.
         * Please see {@link java.beans.XMLEncoder}.
         *
         * @see DefaultEditorKit#defaultKeyTypedAction
         * @see DefaultEditorKit#getActions
         * @see Keymap#setDefaultAction
         * @see Keymap#getDefaultAction
         */
        public static class DefaultKeyTypedAction extends BeepableTextAction {

            /**
             * Creates this object with the appropriate identifier.
             */
            public DefaultKeyTypedAction() {
                super(defaultKeyTypedAction);
            }

            /**
             * The operation to perform when this action is triggered.
             *
             * @param e the action event
             */
            public void actionPerformed(ActionEvent e) {
                JTextComponent target = getTextComponent(e);
                if ((target != null) && (e != null)) {
                    if ((! target.isEditable()) || (! target.isEnabled())) {
                        return;
                    }
                    String content = e.getActionCommand();
                    int mod = e.getModifiers();
                    if ((content != null) && (content.length() > 0)) {
                        boolean isPrintableMask = true;
                        Toolkit tk = Toolkit.getDefaultToolkit();
                        if (tk instanceof SunToolkit) {
                            isPrintableMask = ((SunToolkit)tk).isPrintableCharacterModifiersMask(mod);
                        }

                        char c = content.charAt(0);
                        if ((isPrintableMask && (c >= 0x20) && (c != 0x7F)) ||
                                (!isPrintableMask && (c >= 0x200C) && (c <= 0x200D))) {
                            target.replaceSelection(content);
                        }
                    }
                }
            }
        }

        /**
         * Places content into the associated document.
         * If there is a selection, it is removed before
         * the new content is added.
         * <p>
         * <strong>Warning:</strong>
         * Serialized objects of this class will not be compatible with
         * future Swing releases. The current serialization support is
         * appropriate for short term storage or RMI between applications running
         * the same version of Swing.  As of 1.4, support for long term storage
         * of all JavaBeans&trade;
         * has been added to the <code>java.beans</code> package.
         * Please see {@link java.beans.XMLEncoder}.
         *
         * @see DefaultEditorKit#insertContentAction
         * @see DefaultEditorKit#getActions
         */
        public static class InsertContentAction extends BeepableTextAction {

            /**
             * Creates this object with the appropriate identifier.
             */
            public InsertContentAction() {
                super(insertContentAction);
            }

            /**
             * The operation to perform when this action is triggered.
             *
             * @param e the action event
             */
            public void actionPerformed(ActionEvent e) {
                JTextComponent target = getTextComponent(e);
                if ((target != null) && (e != null)) {
                    if ((! target.isEditable()) || (! target.isEnabled())) {
                        provideErrorFeedback(target);
                        return;
                    }
                    String content = e.getActionCommand();
                    if (content != null) {
                        target.replaceSelection(content);
                    } else {
                        provideErrorFeedback(target);
                    }
                }
            }
        }

        /**
         * Places a line/paragraph break into the document.
         * If there is a selection, it is removed before
         * the break is added.
         * <p>
         * <strong>Warning:</strong>
         * Serialized objects of this class will not be compatible with
         * future Swing releases. The current serialization support is
         * appropriate for short term storage or RMI between applications running
         * the same version of Swing.  As of 1.4, support for long term storage
         * of all JavaBeans&trade;
         * has been added to the <code>java.beans</code> package.
         * Please see {@link java.beans.XMLEncoder}.
         *
         * @see DefaultEditorKit#insertBreakAction
         * @see DefaultEditorKit#getActions
         */
        public static class InsertBreakAction extends BeepableTextAction {

            /**
             * Creates this object with the appropriate identifier.
             */
            public InsertBreakAction() {
                super(insertBreakAction);
            }

            /**
             * The operation to perform when this action is triggered.
             *
             * @param e the action event
             */
            public void actionPerformed(ActionEvent e) {
                JTextComponent target = getTextComponent(e);
                if (target != null) {
                    if ((! target.isEditable()) || (! target.isEnabled())) {
                        provideErrorFeedback(target);
                        return;
                    }
                    target.replaceSelection("\n");
                }
            }
        }

        /**
         * Places a tab character into the document. If there
         * is a selection, it is removed before the tab is added.
         * <p>
         * <strong>Warning:</strong>
         * Serialized objects of this class will not be compatible with
         * future Swing releases. The current serialization support is
         * appropriate for short term storage or RMI between applications running
         * the same version of Swing.  As of 1.4, support for long term storage
         * of all JavaBeans&trade;
         * has been added to the <code>java.beans</code> package.
         * Please see {@link java.beans.XMLEncoder}.
         *
         * @see DefaultEditorKit#insertTabAction
         * @see DefaultEditorKit#getActions
         */
        public static class InsertTabAction extends BeepableTextAction {

            /**
             * Creates this object with the appropriate identifier.
             */
            public InsertTabAction() {
                super(insertTabAction);
            }

            /**
             * The operation to perform when this action is triggered.
             *
             * @param e the action event
             */
            public void actionPerformed(ActionEvent e) {
                JTextComponent target = getTextComponent(e);
                if (target != null) {
                    if ((! target.isEditable()) || (! target.isEnabled())) {
                        provideErrorFeedback(target);
                        return;
                    }
                    target.replaceSelection("\t");
                }
            }
        }

        /*
         * Deletes the character of content that precedes the
         * current caret position.
         * @see DefaultEditorKit#deletePrevCharAction
         * @see DefaultEditorKit#getActions
         */
        static class DeletePrevCharAction extends BeepableTextAction {

            /**
             * Creates this object with the appropriate identifier.
             */
            DeletePrevCharAction() {
                super(deletePrevCharAction);
            }

            /**
             * The operation to perform when this action is triggered.
             *
             * @param e the action event
             */
            public void actionPerformed(ActionEvent e) {
                JTextComponent target = getTextComponent(e);
                boolean beep = true;
                if ((target != null) && (target.isEditable())) {
                    try {
                        Document doc = target.getDocument();
                        Caret caret = target.getCaret();
                        int dot = caret.getDot();
                        int mark = caret.getMark();
                        if (dot != mark) {
                            doc.remove(Math.min(dot, mark), Math.abs(dot - mark));
                            beep = false;
                        } else if (dot > 0) {
                            int delChars = 1;

                            if (dot > 1) {
                                String dotChars = doc.getText(dot - 2, 2);
                                char c0 = dotChars.charAt(0);
                                char c1 = dotChars.charAt(1);

                                if (c0 >= '\uD800' && c0 <= '\uDBFF' &&
                                        c1 >= '\uDC00' && c1 <= '\uDFFF') {
                                    delChars = 2;
                                }
                            }

                            doc.remove(dot - delChars, delChars);
                            beep = false;
                        }
                    } catch (BadLocationException bl) {
                    }
                }
                if (beep) {
                    provideErrorFeedback(target);
                }
            }
        }

        /*
         * Deletes the character of content that follows the
         * current caret position.
         * @see DefaultEditorKit#deleteNextCharAction
         * @see DefaultEditorKit#getActions
         */
        static class DeleteNextCharAction extends BeepableTextAction {

            /* Create this object with the appropriate identifier. */
            DeleteNextCharAction() {
                super(deleteNextCharAction);
            }

            /** The operation to perform when this action is triggered. */
            public void actionPerformed(ActionEvent e) {
                JTextComponent target = getTextComponent(e);
                boolean beep = true;
                if ((target != null) && (target.isEditable())) {
                    try {
                        Document doc = target.getDocument();
                        Caret caret = target.getCaret();
                        int dot = caret.getDot();
                        int mark = caret.getMark();
                        if (dot != mark) {
                            doc.remove(Math.min(dot, mark), Math.abs(dot - mark));
                            beep = false;
                        } else if (dot < doc.getLength()) {
                            int delChars = 1;

                            if (dot < doc.getLength() - 1) {
                                String dotChars = doc.getText(dot, 2);
                                char c0 = dotChars.charAt(0);
                                char c1 = dotChars.charAt(1);

                                if (c0 >= '\uD800' && c0 <= '\uDBFF' &&
                                        c1 >= '\uDC00' && c1 <= '\uDFFF') {
                                    delChars = 2;
                                }
                            }

                            doc.remove(dot, delChars);
                            beep = false;
                        }
                    } catch (BadLocationException bl) {
                    }
                }
                if (beep) {
                    provideErrorFeedback(target);
                }
            }
        }


        /*
         * Deletes the word that precedes/follows the beginning of the selection.
         * @see DefaultEditorKit#getActions
         */
        static class DeleteWordAction extends BeepableTextAction {
            DeleteWordAction(String name) {
                super(name);
                assert (name == deletePrevWordAction)
                        || (name == deleteNextWordAction);
            }
            /**
             * The operation to perform when this action is triggered.
             *
             * @param e the action event
             */
            public void actionPerformed(ActionEvent e) {
                final JTextComponent target = getTextComponent(e);
                if ((target != null) && (e != null)) {
                    if ((! target.isEditable()) || (! target.isEnabled())) {
                        provideErrorFeedback(target);
                        return;
                    }
                    boolean beep = true;
                    try {
                        final int start = target.getSelectionStart();
                        final Element line =
                                Utilities.getParagraphElement(target, start);
                        int end;
                        if (deleteNextWordAction == getValue(Action.NAME)) {
                            end = getNextWordInParagraph(target, line, start, false);
                            if (end == java.text.BreakIterator.DONE) {
                                //last word in the paragraph
                                final int endOfLine = line.getEndOffset();
                                if (start == endOfLine - 1) {
                                    //for last position remove last \n
                                    end = endOfLine;
                                } else {
                                    //remove to the end of the paragraph
                                    end = endOfLine - 1;
                                }
                            }
                        } else {
                            end = getPrevWordInParagraph(target, line, start);
                            if (end == java.text.BreakIterator.DONE) {
                                //there is no previous word in the paragraph
                                final int startOfLine = line.getStartOffset();
                                if (start == startOfLine) {
                                    //for first position remove previous \n
                                    end = startOfLine - 1;
                                } else {
                                    //remove to the start of the paragraph
                                    end = startOfLine;
                                }
                            }
                        }
                        int offs = Math.min(start, end);
                        int len = Math.abs(end - start);
                        if (offs >= 0) {
                            target.getDocument().remove(offs, len);
                            beep = false;
                        }
                    } catch (BadLocationException ignore) {
                    }
                    if (beep) {
                        provideErrorFeedback(target);
                    }
                }
            }
        }


        /*
         * Sets the editor into read-only mode.
         * @see DefaultEditorKit#readOnlyAction
         * @see DefaultEditorKit#getActions
         */
        static class ReadOnlyAction extends BeepableTextAction {

            /* Create this object with the appropriate identifier. */
            ReadOnlyAction() {
                super(readOnlyAction);
            }

            /**
             * The operation to perform when this action is triggered.
             *
             * @param e the action event
             */
            public void actionPerformed(ActionEvent e) {
                JTextComponent target = getTextComponent(e);
                if (target != null) {
                    target.setEditable(false);
                }
            }
        }

        /*
         * Sets the editor into writeable mode.
         * @see DefaultEditorKit#writableAction
         * @see DefaultEditorKit#getActions
         */
        static class WritableAction extends BeepableTextAction {

            /* Create this object with the appropriate identifier. */
            WritableAction() {
                super(writableAction);
            }

            /**
             * The operation to perform when this action is triggered.
             *
             * @param e the action event
             */
            public void actionPerformed(ActionEvent e) {
                JTextComponent target = getTextComponent(e);
                if (target != null) {
                    target.setEditable(true);
                }
            }
        }

        /**
         * Cuts the selected region and place its contents
         * into the system clipboard.
         * <p>
         * <strong>Warning:</strong>
         * Serialized objects of this class will not be compatible with
         * future Swing releases. The current serialization support is
         * appropriate for short term storage or RMI between applications running
         * the same version of Swing.  As of 1.4, support for long term storage
         * of all JavaBeans&trade;
         * has been added to the <code>java.beans</code> package.
         * Please see {@link java.beans.XMLEncoder}.
         *
         * @see DefaultEditorKit#cutAction
         * @see DefaultEditorKit#getActions
         */
        public static class CutAction extends BeepableTextAction {

            /** Create this object with the appropriate identifier. */
            public CutAction() {
                super(cutAction);
            }

            /**
             * The operation to perform when this action is triggered.
             *
             * @param e the action event
             */
            public void actionPerformed(ActionEvent e) {
                JTextComponent target = getTextComponent(e);
                if (target != null) {
                    target.cut();
                }
            }
        }

        /**
         * Copies the selected region and place its contents
         * into the system clipboard.
         * <p>
         * <strong>Warning:</strong>
         * Serialized objects of this class will not be compatible with
         * future Swing releases. The current serialization support is
         * appropriate for short term storage or RMI between applications running
         * the same version of Swing.  As of 1.4, support for long term storage
         * of all JavaBeans&trade;
         * has been added to the <code>java.beans</code> package.
         * Please see {@link java.beans.XMLEncoder}.
         *
         * @see DefaultEditorKit#copyAction
         * @see DefaultEditorKit#getActions
         */
        public static class CopyAction extends BeepableTextAction {

            /** Create this object with the appropriate identifier. */
            public CopyAction() {
                super(copyAction);
            }

            /**
             * The operation to perform when this action is triggered.
             *
             * @param e the action event
             */
            public void actionPerformed(ActionEvent e) {
                JTextComponent target = getTextComponent(e);
                if (target != null) {
                    target.copy();
                }
            }
        }

        /**
         * Pastes the contents of the system clipboard into the
         * selected region, or before the caret if nothing is
         * selected.
         * <p>
         * <strong>Warning:</strong>
         * Serialized objects of this class will not be compatible with
         * future Swing releases. The current serialization support is
         * appropriate for short term storage or RMI between applications running
         * the same version of Swing.  As of 1.4, support for long term storage
         * of all JavaBeans&trade;
         * has been added to the <code>java.beans</code> package.
         * Please see {@link java.beans.XMLEncoder}.
         *
         * @see DefaultEditorKit#pasteAction
         * @see DefaultEditorKit#getActions
         */
        public static class PasteAction extends BeepableTextAction {

            /** Create this object with the appropriate identifier. */
            public PasteAction() {
                super(pasteAction);
            }

            /**
             * The operation to perform when this action is triggered.
             *
             * @param e the action event
             */
            public void actionPerformed(ActionEvent e) {
                JTextComponent target = getTextComponent(e);
                if (target != null) {
                    target.paste();
                }
            }
        }

        /**
         * Creates a beep.
         * <p>
         * <strong>Warning:</strong>
         * Serialized objects of this class will not be compatible with
         * future Swing releases. The current serialization support is
         * appropriate for short term storage or RMI between applications running
         * the same version of Swing.  As of 1.4, support for long term storage
         * of all JavaBeans&trade;
         * has been added to the <code>java.beans</code> package.
         * Please see {@link java.beans.XMLEncoder}.
         *
         * @see DefaultEditorKit#beepAction
         * @see DefaultEditorKit#getActions
         */
        public static class BeepAction extends BeepableTextAction {

            /** Create this object with the appropriate identifier. */
            public BeepAction() {
                super(beepAction);
            }

            /**
             * The operation to perform when this action is triggered.
             *
             * @param e the action event
             */
            public void actionPerformed(ActionEvent e) {
                JTextComponent target = getTextComponent(e);
                provideErrorFeedback(target);
            }
        }

        /**
         * Scrolls up/down vertically.  The select version of this action extends
         * the selection, instead of simply moving the caret.
         *
         * @see DefaultEditorKit#pageUpAction
         * @see DefaultEditorKit#pageDownAction
         * @see DefaultEditorKit#getActions
         */
        static class VerticalPageAction extends BeepableTextAction {

            /** Create this object with the appropriate identifier. */
            public VerticalPageAction(String nm, int direction, boolean select) {
                super(nm);
                this.select = select;
                this.direction = direction;
            }

            /** The operation to perform when this action is triggered. */
            public void actionPerformed(ActionEvent e) {
                JTextComponent target = getTextComponent(e);
                if (target != null) {
                    Rectangle visible = target.getVisibleRect();
                    Rectangle newVis = new Rectangle(visible);
                    int selectedIndex = target.getCaretPosition();
                    int scrollAmount = direction *
                            target.getScrollableBlockIncrement(
                                    visible, SwingConstants.VERTICAL, direction);
                    int initialY = visible.y;
                    Caret caret = target.getCaret();
                    Point magicPosition = caret.getMagicCaretPosition();

                    if (selectedIndex != -1) {
                        try {
                            Rectangle dotBounds = target.modelToView(
                                    selectedIndex);
                            int x = (magicPosition != null) ? magicPosition.x :
                                    dotBounds.x;
                            int h = dotBounds.height;
                            if (h > 0) {
                                // We want to scroll by a multiple of caret height,
                                // rounding towards lower integer
                                scrollAmount = scrollAmount / h * h;
                            }
                            newVis.y = constrainY(target,
                                    initialY + scrollAmount, visible.height);

                            int newIndex;

                            if (visible.contains(dotBounds.x, dotBounds.y)) {
                                // Dot is currently visible, base the new
                                // location off the old, or
                                newIndex = target.viewToModel(
                                        new Point(x, constrainY(target,
                                                dotBounds.y + scrollAmount, 0)));
                            }
                            else {
                                // Dot isn't visible, choose the top or the bottom
                                // for the new location.
                                if (direction == -1) {
                                    newIndex = target.viewToModel(new Point(
                                            x, newVis.y));
                                }
                                else {
                                    newIndex = target.viewToModel(new Point(
                                            x, newVis.y + visible.height));
                                }
                            }
                            newIndex = constrainOffset(target, newIndex);
                            if (newIndex != selectedIndex) {
                                // Make sure the new visible location contains
                                // the location of dot, otherwise Caret will
                                // cause an additional scroll.
                                int newY = getAdjustedY(target, newVis, newIndex);

                                if (direction == -1 && newY <= initialY || direction == 1 && newY >= initialY) {
                                    // Change index and correct newVis.y only if won't cause scrolling upward
                                    newVis.y = newY;

                                    if (select) {
                                        target.moveCaretPosition(newIndex);
                                    } else {
                                        target.setCaretPosition(newIndex);
                                    }
                                }
                            }
                        } catch (BadLocationException ble) { }
                    } else {
                        newVis.y = constrainY(target,
                                initialY + scrollAmount, visible.height);
                    }
                    if (magicPosition != null) {
                        caret.setMagicCaretPosition(magicPosition);
                    }
                    target.scrollRectToVisible(newVis);
                }
            }

            /**
             * Makes sure <code>y</code> is a valid location in
             * <code>target</code>.
             */
            private int constrainY(JTextComponent target, int y, int vis) {
                if (y < 0) {
                    y = 0;
                }
                else if (y + vis > target.getHeight()) {
                    y = Math.max(0, target.getHeight() - vis);
                }
                return y;
            }

            /**
             * Ensures that <code>offset</code> is a valid offset into the
             * model for <code>text</code>.
             */
            private int constrainOffset(JTextComponent text, int offset) {
                Document doc = text.getDocument();

                if ((offset != 0) && (offset > doc.getLength())) {
                    offset = doc.getLength();
                }
                if (offset  < 0) {
                    offset = 0;
                }
                return offset;
            }

            /**
             * Returns adjustsed {@code y} position that indicates the location to scroll to
             * after selecting <code>index</code>.
             */
            private int getAdjustedY(JTextComponent text, Rectangle visible, int index) {
                int result = visible.y;

                try {
                    Rectangle dotBounds = text.modelToView(index);

                    if (dotBounds.y < visible.y) {
                        result = dotBounds.y;
                    } else {
                        if ((dotBounds.y > visible.y + visible.height) ||
                                (dotBounds.y + dotBounds.height > visible.y + visible.height)) {
                            result = dotBounds.y + dotBounds.height - visible.height;
                        }
                    }
                } catch (BadLocationException ble) {
                }

                return result;
            }

            /**
             * Adjusts the Rectangle to contain the bounds of the character at
             * <code>index</code> in response to a page up.
             */
            private boolean select;

            /**
             * Direction to scroll, 1 is down, -1 is up.
             */
            private int direction;
        }


        /**
         * Pages one view to the left or right.
         */
        static class PageAction extends BeepableTextAction {

            /** Create this object with the appropriate identifier. */
            public PageAction(String nm, boolean left, boolean select) {
                super(nm);
                this.select = select;
                this.left = left;
            }

            /** The operation to perform when this action is triggered. */
            public void actionPerformed(ActionEvent e) {
                JTextComponent target = getTextComponent(e);
                if (target != null) {
                    int selectedIndex;
                    Rectangle visible = new Rectangle();
                    target.computeVisibleRect(visible);
                    if (left) {
                        visible.x = Math.max(0, visible.x - visible.width);
                    }
                    else {
                        visible.x += visible.width;
                    }

                    selectedIndex = target.getCaretPosition();
                    if(selectedIndex != -1) {
                        if (left) {
                            selectedIndex = target.viewToModel
                                    (new Point(visible.x, visible.y));
                        }
                        else {
                            selectedIndex = target.viewToModel
                                    (new Point(visible.x + visible.width - 1,
                                            visible.y + visible.height - 1));
                        }
                        Document doc = target.getDocument();
                        if ((selectedIndex != 0) &&
                                (selectedIndex  > (doc.getLength()-1))) {
                            selectedIndex = doc.getLength()-1;
                        }
                        else if(selectedIndex  < 0) {
                            selectedIndex = 0;
                        }
                        if (select)
                            target.moveCaretPosition(selectedIndex);
                        else
                            target.setCaretPosition(selectedIndex);
                    }
                }
            }

            private boolean select;
            private boolean left;
        }

        static class DumpModelAction extends BeepableTextAction {

            DumpModelAction() {
                super("dump-model");
            }

            public void actionPerformed(ActionEvent e) {
                JTextComponent target = getTextComponent(e);
                if (target != null) {
                    Document d = target.getDocument();
                    if (d instanceof AbstractDocument) {
                        ((AbstractDocument) d).dump(System.err);
                    }
                }
            }
        }

        /*
         * Action to move the selection by way of the
         * getNextVisualPositionFrom method. Constructor indicates direction
         * to use.
         */
        static class NextVisualPositionAction extends BeepableTextAction {

            /**
             * Create this action with the appropriate identifier.
             * @param nm  the name of the action, Action.NAME.
             * @param select whether to extend the selection when
             *  changing the caret position.
             */
            NextVisualPositionAction(String nm, boolean select, int direction) {
                super(nm);
                this.select = select;
                this.direction = direction;
            }

            /** The operation to perform when this action is triggered. */
            public void actionPerformed(ActionEvent e) {
                JTextComponent target = getTextComponent(e);
                if (target != null) {
                    Caret caret = target.getCaret();
                    DefaultCaret bidiCaret = (caret instanceof DefaultCaret) ?
                            (DefaultCaret)caret : null;
                    int dot = caret.getDot();
                    Position.Bias[] bias = new Position.Bias[1];
                    Point magicPosition = caret.getMagicCaretPosition();

                    try {
                        if(magicPosition == null &&
                                (direction == SwingConstants.NORTH ||
                                        direction == SwingConstants.SOUTH)) {
                            Rectangle r = (bidiCaret != null) ?
                                    target.getUI().modelToView(target, dot,
                                            bidiCaret.getDotBias()) :
                                    target.modelToView(dot);
                            magicPosition = new Point(r.x, r.y);
                        }

                        NavigationFilter filter = target.getNavigationFilter();

                        if (filter != null) {
                            dot = filter.getNextVisualPositionFrom
                                    (target, dot, (bidiCaret != null) ?
                                            bidiCaret.getDotBias() :
                                            Position.Bias.Forward, direction, bias);
                        }
                        else {
                            dot = target.getUI().getNextVisualPositionFrom
                                    (target, dot, (bidiCaret != null) ?
                                            bidiCaret.getDotBias() :
                                            Position.Bias.Forward, direction, bias);
                        }
                        if(bias[0] == null) {
                            bias[0] = Position.Bias.Forward;
                        }
                        if(bidiCaret != null) {
                            if (select) {
                                bidiCaret.moveDot(dot, bias[0]);
                            } else {
                                bidiCaret.setDot(dot, bias[0]);
                            }
                        }
                        else {
                            if (select) {
                                caret.moveDot(dot);
                            } else {
                                caret.setDot(dot);
                            }
                        }
                        if(magicPosition != null &&
                                (direction == SwingConstants.NORTH ||
                                        direction == SwingConstants.SOUTH)) {
                            target.getCaret().setMagicCaretPosition(magicPosition);
                        }
                    } catch (BadLocationException ex) {
                    }
                }
            }

            private boolean select;
            private int direction;
        }

        /*
         * Position the caret to the beginning of the word.
         * @see DefaultEditorKit#beginWordAction
         * @see DefaultEditorKit#selectBeginWordAction
         * @see DefaultEditorKit#getActions
         */
        static class BeginWordAction extends BeepableTextAction {

            /**
             * Create this action with the appropriate identifier.
             * @param nm  the name of the action, Action.NAME.
             * @param select whether to extend the selection when
             *  changing the caret position.
             */
            BeginWordAction(String nm, boolean select) {
                super(nm);
                this.select = select;
            }

            /** The operation to perform when this action is triggered. */
            public void actionPerformed(ActionEvent e) {
                JTextComponent target = getTextComponent(e);
                if (target != null) {
                    try {
                        int offs = target.getCaretPosition();
                        int begOffs = Utilities.getWordStart(target, offs);
                        if (select) {
                            target.moveCaretPosition(begOffs);
                        } else {
                            target.setCaretPosition(begOffs);
                        }
                    } catch (BadLocationException bl) {
                        provideErrorFeedback(target);
                    }
                }
            }

            private boolean select;
        }

        /*
         * Position the caret to the end of the word.
         * @see DefaultEditorKit#endWordAction
         * @see DefaultEditorKit#selectEndWordAction
         * @see DefaultEditorKit#getActions
         */
        static class EndWordAction extends BeepableTextAction {

            /**
             * Create this action with the appropriate identifier.
             * @param nm  the name of the action, Action.NAME.
             * @param select whether to extend the selection when
             *  changing the caret position.
             */
            EndWordAction(String nm, boolean select) {
                super(nm);
                this.select = select;
            }

            /** The operation to perform when this action is triggered. */
            public void actionPerformed(ActionEvent e) {
                JTextComponent target = getTextComponent(e);
                if (target != null) {
                    try {
                        int offs = target.getCaretPosition();
                        int endOffs = Utilities.getWordEnd(target, offs);
                        if (select) {
                            target.moveCaretPosition(endOffs);
                        } else {
                            target.setCaretPosition(endOffs);
                        }
                    } catch (BadLocationException bl) {
                        provideErrorFeedback(target);
                    }
                }
            }

            private boolean select;
        }

        /*
         * Position the caret to the beginning of the previous word.
         * @see DefaultEditorKit#previousWordAction
         * @see DefaultEditorKit#selectPreviousWordAction
         * @see DefaultEditorKit#getActions
         */
        static class PreviousWordAction extends BeepableTextAction {

            /**
             * Create this action with the appropriate identifier.
             * @param nm  the name of the action, Action.NAME.
             * @param select whether to extend the selection when
             *  changing the caret position.
             */
            PreviousWordAction(String nm, boolean select) {
                super(nm);
                this.select = select;
            }

            /** The operation to perform when this action is triggered. */
            public void actionPerformed(ActionEvent e) {
                JTextComponent target = getTextComponent(e);
                if (target != null) {
                    int offs = target.getCaretPosition();
                    boolean failed = false;
                    try {
                        Element curPara =
                                Utilities.getParagraphElement(target, offs);
                        offs = Utilities.getPreviousWord(target, offs);
                        if(offs < curPara.getStartOffset()) {
                            // we should first move to the end of the
                            // previous paragraph (bug #4278839)
                            offs = Utilities.getParagraphElement(target, offs).
                                    getEndOffset() - 1;
                        }
                    } catch (BadLocationException bl) {
                        if (offs != 0) {
                            offs = 0;
                        }
                        else {
                            failed = true;
                        }
                    }
                    if (!failed) {
                        if (select) {
                            target.moveCaretPosition(offs);
                        } else {
                            target.setCaretPosition(offs);
                        }
                    }
                    else {
                        provideErrorFeedback(target);
                    }
                }
            }

            private boolean select;
        }

        /*
         * Position the caret to the next of the word.
         * @see DefaultEditorKit#nextWordAction
         * @see DefaultEditorKit#selectNextWordAction
         * @see DefaultEditorKit#getActions
         */
        static class NextWordAction extends BeepableTextAction {

            /**
             * Create this action with the appropriate identifier.
             * @param nm  the name of the action, Action.NAME.
             * @param select whether to extend the selection when
             *  changing the caret position.
             */
            NextWordAction(String nm, boolean select) {
                super(nm);
                this.select = select;
            }

            /** The operation to perform when this action is triggered. */
            public void actionPerformed(ActionEvent e) {
                JTextComponent target = getTextComponent(e);
                if (target != null) {
                    int offs = target.getCaretPosition();
                    boolean failed = false;
                    int oldOffs = offs;
                    Element curPara =
                            Utilities.getParagraphElement(target, offs);
                    try {
                        offs = Utilities.getNextWord(target, offs);
                        if(offs >= curPara.getEndOffset() &&
                                oldOffs != curPara.getEndOffset() - 1) {
                            // we should first move to the end of current
                            // paragraph (bug #4278839)
                            offs = curPara.getEndOffset() - 1;
                        }
                    } catch (BadLocationException bl) {
                        int end = target.getDocument().getLength();
                        if (offs != end) {
                            if(oldOffs != curPara.getEndOffset() - 1) {
                                offs = curPara.getEndOffset() - 1;
                            } else {
                                offs = end;
                            }
                        }
                        else {
                            failed = true;
                        }
                    }
                    if (!failed) {
                        if (select) {
                            target.moveCaretPosition(offs);
                        } else {
                            target.setCaretPosition(offs);
                        }
                    }
                    else {
                        provideErrorFeedback(target);
                    }
                }
            }

            private boolean select;
        }

        /*
         * Position the caret to the beginning of the line.
         * @see DefaultEditorKit#beginLineAction
         * @see DefaultEditorKit#selectBeginLineAction
         * @see DefaultEditorKit#getActions
         */
        static class BeginLineAction extends BeepableTextAction {

            /**
             * Create this action with the appropriate identifier.
             * @param nm  the name of the action, Action.NAME.
             * @param select whether to extend the selection when
             *  changing the caret position.
             */
            BeginLineAction(String nm, boolean select) {
                super(nm);
                this.select = select;
            }

            /** The operation to perform when this action is triggered. */
            public void actionPerformed(ActionEvent e) {
                JTextComponent target = getTextComponent(e);
                if (target != null) {
                    try {
                        int offs = target.getCaretPosition();
                        int begOffs = Utilities.getRowStart(target, offs);
                        if (select) {
                            target.moveCaretPosition(begOffs);
                        } else {
                            target.setCaretPosition(begOffs);
                        }
                    } catch (BadLocationException bl) {
                        provideErrorFeedback(target);
                    }
                }
            }

            private boolean select;
        }

        /*
         * Position the caret to the end of the line.
         * @see DefaultEditorKit#endLineAction
         * @see DefaultEditorKit#selectEndLineAction
         * @see DefaultEditorKit#getActions
         */
        static class EndLineAction extends BeepableTextAction {

            /**
             * Create this action with the appropriate identifier.
             * @param nm  the name of the action, Action.NAME.
             * @param select whether to extend the selection when
             *  changing the caret position.
             */
            EndLineAction(String nm, boolean select) {
                super(nm);
                this.select = select;
            }

            /** The operation to perform when this action is triggered. */
            public void actionPerformed(ActionEvent e) {
                JTextComponent target = getTextComponent(e);
                if (target != null) {
                    try {
                        int offs = target.getCaretPosition();
                        int endOffs = Utilities.getRowEnd(target, offs);
                        if (select) {
                            target.moveCaretPosition(endOffs);
                        } else {
                            target.setCaretPosition(endOffs);
                        }
                    } catch (BadLocationException bl) {
                        provideErrorFeedback(target);
                    }
                }
            }

            private boolean select;
        }

        /*
         * Position the caret to the beginning of the paragraph.
         * @see DefaultEditorKit#beginParagraphAction
         * @see DefaultEditorKit#selectBeginParagraphAction
         * @see DefaultEditorKit#getActions
         */
        static class BeginParagraphAction extends BeepableTextAction {

            /**
             * Create this action with the appropriate identifier.
             * @param nm  the name of the action, Action.NAME.
             * @param select whether to extend the selection when
             *  changing the caret position.
             */
            BeginParagraphAction(String nm, boolean select) {
                super(nm);
                this.select = select;
            }

            /** The operation to perform when this action is triggered. */
            public void actionPerformed(ActionEvent e) {
                JTextComponent target = getTextComponent(e);
                if (target != null) {
                    int offs = target.getCaretPosition();
                    Element elem = Utilities.getParagraphElement(target, offs);
                    offs = elem.getStartOffset();
                    if (select) {
                        target.moveCaretPosition(offs);
                    } else {
                        target.setCaretPosition(offs);
                    }
                }
            }

            private boolean select;
        }

        /*
         * Position the caret to the end of the paragraph.
         * @see DefaultEditorKit#endParagraphAction
         * @see DefaultEditorKit#selectEndParagraphAction
         * @see DefaultEditorKit#getActions
         */
        static class EndParagraphAction extends BeepableTextAction {

            /**
             * Create this action with the appropriate identifier.
             * @param nm  the name of the action, Action.NAME.
             * @param select whether to extend the selection when
             *  changing the caret position.
             */
            EndParagraphAction(String nm, boolean select) {
                super(nm);
                this.select = select;
            }

            /** The operation to perform when this action is triggered. */
            public void actionPerformed(ActionEvent e) {
                JTextComponent target = getTextComponent(e);
                if (target != null) {
                    int offs = target.getCaretPosition();
                    Element elem = Utilities.getParagraphElement(target, offs);
                    offs = Math.min(target.getDocument().getLength(),
                            elem.getEndOffset());
                    if (select) {
                        target.moveCaretPosition(offs);
                    } else {
                        target.setCaretPosition(offs);
                    }
                }
            }

            private boolean select;
        }

        /*
         * Move the caret to the beginning of the document.
         * @see DefaultEditorKit#beginAction
         * @see DefaultEditorKit#getActions
         */
        static class BeginAction extends BeepableTextAction {

            /* Create this object with the appropriate identifier. */
            BeginAction(String nm, boolean select) {
                super(nm);
                this.select = select;
            }

            /** The operation to perform when this action is triggered. */
            public void actionPerformed(ActionEvent e) {
                JTextComponent target = getTextComponent(e);
                if (target != null) {
                    if (select) {
                        target.moveCaretPosition(0);
                    } else {
                        target.setCaretPosition(0);
                    }
                }
            }

            private boolean select;
        }

        /*
         * Move the caret to the end of the document.
         * @see DefaultEditorKit#endAction
         * @see DefaultEditorKit#getActions
         */
        static class EndAction extends BeepableTextAction {

            /* Create this object with the appropriate identifier. */
            EndAction(String nm, boolean select) {
                super(nm);
                this.select = select;
            }

            /** The operation to perform when this action is triggered. */
            public void actionPerformed(ActionEvent e) {
                JTextComponent target = getTextComponent(e);
                if (target != null) {
                    Document doc = target.getDocument();
                    int dot = doc.getLength();
                    if (select) {
                        target.moveCaretPosition(dot);
                    } else {
                        target.setCaretPosition(dot);
                    }
                }
            }

            private boolean select;
        }

        /*
         * Select the word around the caret
         * @see DefaultEditorKit#endAction
         * @see DefaultEditorKit#getActions
         */
        static class SelectWordAction extends BeepableTextAction {

            /**
             * Create this action with the appropriate identifier.
             * @param nm  the name of the action, Action.NAME.
             * @param select whether to extend the selection when
             *  changing the caret position.
             */
            SelectWordAction() {
                super(selectWordAction);
                start = new BeginWordAction("pigdog", false);
                end = new EndWordAction("pigdog", true);
            }

            /** The operation to perform when this action is triggered. */
            public void actionPerformed(ActionEvent e) {
                start.actionPerformed(e);
                end.actionPerformed(e);
            }

            private Action start;
            private Action end;
        }

        /*
         * Select the line around the caret
         * @see DefaultEditorKit#endAction
         * @see DefaultEditorKit#getActions
         */
        static class SelectLineAction extends BeepableTextAction {

            /**
             * Create this action with the appropriate identifier.
             * @param nm  the name of the action, Action.NAME.
             * @param select whether to extend the selection when
             *  changing the caret position.
             */
            SelectLineAction() {
                super(selectLineAction);
                start = new BeginLineAction("pigdog", false);
                end = new EndLineAction("pigdog", true);
            }

            /** The operation to perform when this action is triggered. */
            public void actionPerformed(ActionEvent e) {
                start.actionPerformed(e);
                end.actionPerformed(e);
            }

            private Action start;
            private Action end;
        }

        /*
         * Select the paragraph around the caret
         * @see DefaultEditorKit#endAction
         * @see DefaultEditorKit#getActions
         */
        static class SelectParagraphAction extends BeepableTextAction {

            /**
             * Create this action with the appropriate identifier.
             * @param nm  the name of the action, Action.NAME.
             * @param select whether to extend the selection when
             *  changing the caret position.
             */
            SelectParagraphAction() {
                super(selectParagraphAction);
                start = new BeginParagraphAction("pigdog", false);
                end = new EndParagraphAction("pigdog", true);
            }

            /** The operation to perform when this action is triggered. */
            public void actionPerformed(ActionEvent e) {
                start.actionPerformed(e);
                end.actionPerformed(e);
            }

            private Action start;
            private Action end;
        }

        /*
         * Select the entire document
         * @see DefaultEditorKit#endAction
         * @see DefaultEditorKit#getActions
         */
        static class SelectAllAction extends BeepableTextAction {

            /**
             * Create this action with the appropriate identifier.
             * @param nm  the name of the action, Action.NAME.
             * @param select whether to extend the selection when
             *  changing the caret position.
             */
            SelectAllAction() {
                super(selectAllAction);
            }

            /** The operation to perform when this action is triggered. */
            public void actionPerformed(ActionEvent e) {
                JTextComponent target = getTextComponent(e);
                if (target != null) {
                    Document doc = target.getDocument();
                    target.setCaretPosition(0);
                    target.moveCaretPosition(doc.getLength());
                }
            }

        }

        /*
         * Remove the selection, if any.
         * @see DefaultEditorKit#unselectAction
         * @see DefaultEditorKit#getActions
         */
        static class UnselectAction extends BeepableTextAction {

            /**
             * Create this action with the appropriate identifier.
             */
            UnselectAction() {
                super(unselectAction);
            }

            /** The operation to perform when this action is triggered. */
            public void actionPerformed(ActionEvent e) {
                JTextComponent target = getTextComponent(e);
                if (target != null) {
                    target.setCaretPosition(target.getCaretPosition());
                }
            }

        }

        /*
         * Toggles the ComponentOrientation of the text component.
         * @see DefaultEditorKit#toggleComponentOrientationAction
         * @see DefaultEditorKit#getActions
         */
        static class ToggleComponentOrientationAction extends BeepableTextAction {

            /**
             * Create this action with the appropriate identifier.
             */
            ToggleComponentOrientationAction() {
                super(toggleComponentOrientationAction);
            }

            /** The operation to perform when this action is triggered. */
            public void actionPerformed(ActionEvent e) {
                JTextComponent target = getTextComponent(e);
                if (target != null) {
                    ComponentOrientation last = target.getComponentOrientation();
                    ComponentOrientation next;
                    if( last == ComponentOrientation.RIGHT_TO_LEFT )
                        next = ComponentOrientation.LEFT_TO_RIGHT;
                    else
                        next = ComponentOrientation.RIGHT_TO_LEFT;
                    target.setComponentOrientation(next);
                    target.repaint();
                }
            }
        }

        static int getNextWordInParagraph(JTextComponent c, Element line, int offs, boolean first)
                throws BadLocationException {
            if (line == null) {
                throw new BadLocationException("No more words", offs);
            }
            Document doc = line.getDocument();
            int lineStart = line.getStartOffset();
            int lineEnd = Math.min(line.getEndOffset(), doc.getLength());
            if ((offs >= lineEnd) || (offs < lineStart)) {
                throw new BadLocationException("No more words", offs);
            }
            Segment seg = SegmentCache.getSharedSegment();
            doc.getText(lineStart, lineEnd - lineStart, seg);
            BreakIterator words = BreakIterator.getWordInstance(c.getLocale());
            words.setText(seg);
            if ((first && (words.first() == (seg.offset + offs - lineStart))) &&
                    (! Character.isWhitespace(seg.array[words.first()]))) {

                return offs;
            }
            int wordPosition = words.following(seg.offset + offs - lineStart);
            if ((wordPosition == BreakIterator.DONE) ||
                    (wordPosition >= seg.offset + seg.count)) {
                // there are no more words on this line.
                return BreakIterator.DONE;
            }
            // if we haven't shot past the end... check to
            // see if the current boundary represents whitespace.
            // if so, we need to try again
            char ch = seg.array[wordPosition];
            if (! Character.isWhitespace(ch)) {
                return lineStart + wordPosition - seg.offset;
            }

            // it was whitespace, try again.  The assumption
            // is that it must be a word start if the last
            // one had whitespace following it.
            wordPosition = words.next();
            if (wordPosition != BreakIterator.DONE) {
                offs = lineStart + wordPosition - seg.offset;
                if (offs != lineEnd) {
                    return offs;
                }
            }
            SegmentCache.releaseSharedSegment(seg);
            return BreakIterator.DONE;
        }

        static int getPrevWordInParagraph(JTextComponent c, Element line, int offs) throws BadLocationException {
            if (line == null) {
                throw new BadLocationException("No more words", offs);
            }
            Document doc = line.getDocument();
            int lineStart = line.getStartOffset();
            int lineEnd = line.getEndOffset();
            if ((offs > lineEnd) || (offs < lineStart)) {
                throw new BadLocationException("No more words", offs);
            }
            Segment seg = SegmentCache.getSharedSegment();
            doc.getText(lineStart, lineEnd - lineStart, seg);
            BreakIterator words = BreakIterator.getWordInstance(c.getLocale());
            words.setText(seg);
            if (words.following(seg.offset + offs - lineStart) == BreakIterator.DONE) {
                words.last();
            }
            int wordPosition = words.previous();
            if (wordPosition == (seg.offset + offs - lineStart)) {
                wordPosition = words.previous();
            }

            if (wordPosition == BreakIterator.DONE) {
                // there are no more words on this line.
                return BreakIterator.DONE;
            }
            // if we haven't shot past the end... check to
            // see if the current boundary represents whitespace.
            // if so, we need to try again
            char ch = seg.array[wordPosition];
            if (! Character.isWhitespace(ch)) {
                return lineStart + wordPosition - seg.offset;
            }

            // it was whitespace, try again.  The assumption
            // is that it must be a word start if the last
            // one had whitespace following it.
            wordPosition = words.previous();
            if (wordPosition != BreakIterator.DONE) {
                return lineStart + wordPosition - seg.offset;
            }
            SegmentCache.releaseSharedSegment(seg);
            return BreakIterator.DONE;
        }

        /**
         * SegmentCache caches <code>Segment</code>s to avoid continually creating
         * and destroying of <code>Segment</code>s. A common use of this class would
         * be:
         * <pre>
         *   Segment segment = segmentCache.getSegment();
         *   // do something with segment
         *   ...
         *   segmentCache.releaseSegment(segment);
         * </pre>
         *
         */
        static final class SegmentCache {
            /**
             * A global cache.
             */
            private static SegmentCache sharedCache = new SegmentCache();

            /**
             * A list of the currently unused Segments.
             */
            private List<Segment> segments;


            /**
             * Returns the shared SegmentCache.
             */
            public static SegmentCache getSharedInstance() {
                return sharedCache;
            }

            /**
             * A convenience method to get a Segment from the shared
             * <code>SegmentCache</code>.
             */
            public static Segment getSharedSegment() {
                return getSharedInstance().getSegment();
            }

            /**
             * A convenience method to release a Segment to the shared
             * <code>SegmentCache</code>.
             */
            public static void releaseSharedSegment(Segment segment) {
                getSharedInstance().releaseSegment(segment);
            }



            /**
             * Creates and returns a SegmentCache.
             */
            public SegmentCache() {
                segments = new ArrayList<Segment>(11);
            }

            /**
             * Returns a <code>Segment</code>. When done, the <code>Segment</code>
             * should be recycled by invoking <code>releaseSegment</code>.
             */
            public Segment getSegment() {
                synchronized(this) {
                    int size = segments.size();

                    if (size > 0) {
                        return segments.remove(size - 1);
                    }
                }
                return new CachedSegment();
            }

            /**
             * Releases a Segment. You should not use a Segment after you release it,
             * and you should NEVER release the same Segment more than once, eg:
             * <pre>
             *   segmentCache.releaseSegment(segment);
             *   segmentCache.releaseSegment(segment);
             * </pre>
             * Will likely result in very bad things happening!
             */
            public void releaseSegment(Segment segment) {
                if (segment instanceof CachedSegment) {
                    synchronized(this) {
                        segment.array = null;
                        segment.count = 0;
                        segments.add(segment);
                    }
                }
            }


            /**
             * CachedSegment is used as a tagging interface to determine if
             * a Segment can successfully be shared.
             */
            private static class CachedSegment extends Segment {
            }
        }


    }


}
