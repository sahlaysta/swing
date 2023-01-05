/*
 * MIT License
 *
 * Copyright (c) 2023 sahlaysta
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

import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import javax.swing.text.Keymap;
import javax.swing.text.StyledEditorKit;
import javax.swing.text.TextAction;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;
import javax.swing.undo.UndoableEdit;
import java.applet.Applet;
import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ContainerEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Custom enhancements to Swing text components.
 *
 * <ul>
 * <li> Add compound undo/redo functionality and shortcuts
 * <li> Add 'paste as plain text' option and shortcut
 * <li> Shortcuts are platform-specific
 * <li> Add right-click popup menu (Cut, Copy, Paste...)
 * <ul>
 *     <li> This menu can also be activated by the 'show context menu' key
 *          (for example this is Shift+F10 on Windows)
 * </ul>
 * <li> Adjust caret behavior while text is selected: when the left/right arrow key is pressed the caret
 *      will move to the beginning/end of the selection (this should be normal behavior in a text entry
 *      but Java Swing does not do it)
 * <li> Add hyperlink right-click support (popup menu will include options to open/copy link)
 * <li> Disable beeps (for example, backspace in an empty text field)
 * </ul>
 *
 * @author sahlaysta
 */
public final class JTextComponentEnhancer {

    private JTextComponentEnhancer() { }

    //store enhanced components by weak reference
    private static final Set<JTextComponent> enhancedComps = Collections.newSetFromMap(new WeakHashMap<>());

    //keys for InputMap data
    private static final String UNDO_KEY = "sahlaysta.undo";
    private static final String REDO_KEY = "sahlaysta.redo";
    private static final String PASTE_PLAIN_KEY = "sahlaysta.pastePlain";

    //the platform-specific shortcuts
    private static final boolean IS_WINDOWS = System.getProperty("os.name").startsWith("Windows");
    private static final boolean IS_MAC = System.getProperty("os.name").startsWith("Mac");
    private static final KeyStroke[]
            UNDO_KS = getPlatformKeystrokes("ctrl Z, alt BACK_SPACE | meta Z | ctrl Z"),
            REDO_KS = getPlatformKeystrokes("ctrl Y, ctrl shift Z | meta shift Z, meta Y | ctrl shift Z, ctrl Y"),
            PASTE_PLAIN_KS = getPlatformKeystrokes("ctrl shift V | meta shift V | ctrl shift V");
    private static KeyStroke[] getPlatformKeystrokes(String str) {
        int index = IS_WINDOWS ? 0 : IS_MAC ? 1 : 2;
        return Arrays.stream(str.split("\\|")[index].split(","))
                .map(String::trim)
                .map(KeyStroke::getKeyStroke)
                .toArray(KeyStroke[]::new);
    }

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
     * Enhances the text component.
     *
     * @param jtc the text component
     */
    public static void enhanceJTextComponent(JTextComponent jtc) {
        Objects.requireNonNull(jtc);

        //if already enhanced
        if (enhancedComps.contains(jtc)) return;
        enhancedComps.add(jtc);

        //the XDefaultEditorKit disables beeps and reconfigures the caret behavior
        //to fully move while text is selected
        replaceXDefaultEditorKitActions(jtc);

        //the CompoundUndoManager groups consecutive undoable edits
        CompoundUndoManager cum = new CompoundUndoManager();
        jtc.getDocument().addUndoableEditListener(cum);

        //actions for Undo, Redo, Paste as plain text
        InputMap im = jtc.getInputMap();
        ActionMap am = jtc.getActionMap();
        for (KeyStroke ks: UNDO_KS) if (im.get(ks) == null) im.put(ks, UNDO_KEY);
        for (KeyStroke ks: REDO_KS) if (im.get(ks) == null) im.put(ks, REDO_KEY);
        for (KeyStroke ks: PASTE_PLAIN_KS) if (im.get(ks) == null) im.put(ks, PASTE_PLAIN_KEY);
        am.put(UNDO_KEY, new TextAction(UNDO_KEY) {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (jtc == getTextComponent(e) && jtc.isEnabled() && jtc.isEditable()) {
                    if (cum.canUndo())
                        cum.undo();
                }
            }
        });
        am.put(REDO_KEY, new TextAction(REDO_KEY) {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (jtc == getTextComponent(e) && jtc.isEnabled() && jtc.isEditable()) {
                    if (cum.canRedo())
                        cum.redo();
                }
            }
        });
        am.put(PASTE_PLAIN_KEY, new TextAction(PASTE_PLAIN_KEY) {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (jtc == getTextComponent(e) && jtc.isEnabled() && jtc.isEditable()) {
                    pasteAsPlainText(jtc);
                }
            }
        });

        //the right-click popup menu with Cut, Copy, Paste, etc.
        jtc.setComponentPopupMenu(new JTCEPopupMenu(jtc, cum));

    }

    //paste from clipboard without formatting
    private static void pasteAsPlainText(JTextComponent jtc) {
        if (isPlainTextContentType(jtc)) {
            jtc.paste();
        } else {
            try {
                Object o = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
                if (o instanceof String) jtc.replaceSelection((String)o);
            } catch (UnsupportedFlavorException | IOException ignore) { }
        }
    }

    //for text entries with plain text content type, 'paste as plain text' is disabled
    private static boolean isPlainTextContentType(JTextComponent jtc) {
        if (jtc instanceof JEditorPane) {
            JEditorPane jep = (JEditorPane)jtc;
            return "text/plain".equals(jep.getContentType());
        } else {
            return !(jtc.getUI().getEditorKit(jtc) instanceof StyledEditorKit);
        }
    }

    //replace all DefaultEditorKit actions with XDefaultEditorKit actions
    private static void replaceXDefaultEditorKitActions(JTextComponent jtc) {
        Action[] defaultActions = new DefaultEditorKit().getActions();
        if (defaultActions == null) return;

        Action[] xActions = new XDefaultEditorKit().getActions();
        if (xActions == null) return;

        //replace the ActionMap actions
        ActionMap am = jtc.getActionMap();
        while (am != null) {
            Object[] keys = am.keys();
            if (keys != null) {
                e: for (Object key: keys) {
                    Action action = am.get(key);
                    if (action != null) {
                        //find defaultAction with the same Class
                        for (Action defaultAction: defaultActions) {
                            if (defaultAction != null) {
                                if (action.getClass() == defaultAction.getClass()) {
                                    Object actionName = action.getValue(Action.NAME);
                                    if (actionName != null) {
                                        //find xAction with the same name
                                        for (Action xAction: xActions) {
                                            if (xAction != null) {
                                                Object xActionName = xAction.getValue(Action.NAME);
                                                if (actionName.equals(xActionName)) {
                                                    //now replace action with xAction
                                                    am.put(key, xAction);
                                                    continue e;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            am = am.getParent();

            //replace the keymap default action
            Keymap km = jtc.getKeymap();
            if (km != null) {
                Action keymapDefaultAction = km.getDefaultAction();
                if (keymapDefaultAction != null) {
                    //find defaultAction with the same Class
                    e: for (Action defaultAction: defaultActions) {
                        if (defaultAction != null) {
                            if (keymapDefaultAction.getClass() == defaultAction.getClass()) {
                                Object keymapDefaultActionName = keymapDefaultAction.getValue(Action.NAME);
                                if (keymapDefaultActionName != null) {
                                    //find xAction with same name
                                    for (Action xAction: xActions) {
                                        if (xAction != null) {
                                            Object xActionName = xAction.getValue(Action.NAME);
                                            if (keymapDefaultActionName.equals(xActionName)) {
                                                //now replace action with xAction
                                                km.setDefaultAction(xAction);
                                                break e;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Undo manager that handles grouping undo and redo edits.
     *
     * @author sahlaysta
     */
    public static class CompoundUndoManager extends UndoManager {

        //save the edits that are normally typed (and not, for example, pasted)
        private final Set<UndoableEdit> normalEdits = Collections.newSetFromMap(new WeakHashMap<>());

        //save the key event for each edit. when edits have the same key event, they are compounded
        //(this happens when you replace a text selection. it will cause two edits,
        // a remove edit and an add edit.)
        private final WeakHashMap<UndoableEdit, KeyEvent> editEvents = new WeakHashMap<>();

        /**
         * Initializes a new instance of the {@link JTextComponentEnhancer.CompoundUndoManager} class.
         */
        public CompoundUndoManager() { }

        @Override
        public synchronized boolean addEdit(UndoableEdit anEdit) {
            registerEdit(anEdit);
            return super.addEdit(anEdit);
        }

        @Override
        public synchronized void undo() throws CannotUndoException {
            if (!isInProgress()) {
                super.undo();
                return;
            }
            while (canUndo()) {
                UndoableEdit ue = editToBeUndone();
                undoTo(ue);
                UndoableEdit prevue = editToBeUndone();
                if (!areCompoundEdits(ue, prevue)) return;
            }
            throw new CannotUndoException();
        }

        @Override
        public synchronized void redo() throws CannotRedoException {
            if (!isInProgress()) {
                super.redo();
                return;
            }
            while (canRedo()) {
                UndoableEdit ue = editToBeRedone();
                redoTo(ue);
                UndoableEdit nextue = editToBeRedone();
                if (!areCompoundEdits(nextue, ue)) return;
            }
            throw new CannotRedoException();
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

        //save the information of an edit
        //try to detect if it has been typed normally (and not, for example, pasted)
        //save the KeyEvent of each edit
        private void registerEdit(UndoableEdit ue) {
            if (!(ue instanceof AbstractDocument.DefaultDocumentEvent)) return;
            AbstractDocument.DefaultDocumentEvent dde = (AbstractDocument.DefaultDocumentEvent)ue;

            AWTEvent event = EventQueue.getCurrentEvent();
            if (!(event instanceof KeyEvent)) return;
            KeyEvent keyEvent = (KeyEvent)event;

            Object keyEventSource = keyEvent.getSource();
            if (!(keyEventSource instanceof JTextComponent)) return;
            JTextComponent jtc = (JTextComponent)keyEventSource;

            if (jtc.getDocument() == dde.getDocument()) {
                ActionListener al = computeKeyAction(keyEvent);
                if (al instanceof Action) {
                    Action action = (Action)al;
                    Object actionName = action.getValue(Action.NAME);
                    if (DefaultEditorKit.defaultKeyTypedAction.equals(actionName)
                            || DefaultEditorKit.deletePrevCharAction.equals(actionName)) {
                        normalEdits.add(ue);
                    }
                    editEvents.put(ue, keyEvent);
                }
            }
        }

        //test an edit and its preceding edit for compounding
        private boolean areCompoundEdits(UndoableEdit edit, UndoableEdit precedingEdit) {
            //probably unnecessary but also serves as null check
            if (!(edit instanceof AbstractDocument.DefaultDocumentEvent)
                    || !(precedingEdit instanceof AbstractDocument.DefaultDocumentEvent)) {
                return false;
            }

            //edits that have the same KeyEvent are compounded
            //(this happens when you replace a text selection. it will cause two edits,
            // a remove edit and an add edit.)
            if (editEvents.containsKey(edit) && editEvents.get(edit) == editEvents.get(precedingEdit)) return true;

            //for edits to be compounded, they must be normal edits
            if (!normalEdits.contains(edit) || !normalEdits.contains(precedingEdit)) return false;

            //compare the edits for type and offset
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

    //try to get the Action that would be invoked of a KeyEvent
    //(the effectiveness of this solution is unknown)
    private static ActionListener computeKeyAction(KeyEvent keyEvent) {
        Object src = keyEvent.getSource();
        if (!(src instanceof JComponent)) return null;
        JComponent jc = (JComponent)src;
        KeyStroke ks = KeyStroke.getKeyStrokeForEvent(keyEvent);
        ActionListener al;

        al = computeKeyBinding(jc, ks, JComponent.WHEN_FOCUSED);
        if (al != null) return al;

        al = computeKeyBinding(jc, ks, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        if (al != null) return al;

        Container current = jc.getParent();
        while (current != null) {
            if (current instanceof JComponent) {
                al = computeKeyBinding((JComponent)current, ks, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
                if (al != null) return al;
            }
            if (current instanceof Window || current instanceof Applet) break;
            current = current.getParent();
        }

        return null;
    }
    private static ActionListener computeKeyBinding(JComponent jc, KeyStroke ks, int cond) {
        return !jc.isEnabled() ? null : new JComponent() {
            {
                setInputMap(cond, jc.getInputMap(cond));
                setActionMap(jc.getActionMap());
            }
        }.getActionForKeyStroke(ks);
    }


    //the right-click popup menu with Cut, Copy, Paste, etc.
    private static final class JTCEPopupMenu extends JPopupMenu {

        final JTextComponent jtc;
        final CompoundUndoManager cum;
        JTCEPopupMenu(JTextComponent jtc, CompoundUndoManager cum) {
            this.jtc = jtc;
            this.cum = cum;
        }

        @Override
        public void show(Component invoker, int x, int y) {
            boolean keyboard = detectPopupTriggeredByKeyboard(invoker);
            boolean hasSelection = jtc.getSelectionStart() != jtc.getSelectionEnd();
            int index = keyboard ? jtc.getCaret().getDot() : jtc.viewToModel(new Point(x, y));

            //if the popup is triggered by the keyboard shortcut, reconfigures the
            //popup location to show at the text caret location
            int popupX, popupY;
            if (keyboard) {
                Point p = jtc.getCaret().getMagicCaretPosition();
                int fontHeight = jtc.getFontMetrics(jtc.getFont()).getHeight();
                if (p == null) {
                    try {
                        Rectangle rect = jtc.modelToView(jtc.getSelectionEnd());
                        popupX = rect.x;
                        popupY = rect.y + fontHeight;
                    } catch (BadLocationException e) { throw new Error(e); }
                } else {
                    popupX = p.x;
                    popupY = p.y + fontHeight;
                }
            } else {
                popupX = x;
                popupY = y;
            }

            //obtain info of hyperlink
            int hyperlinkIndex = index;
            String hyperlink = getHyperlinkAtIndex(hyperlinkIndex);
            if (hyperlink == null && keyboard && hasSelection)
                hyperlink = getHyperlinkAtIndex(--hyperlinkIndex);

            //unselect the text selection if the mouse is not clicked inside the selection
            if (!keyboard && hyperlink == null
                    && (!(jtc.getSelectionStart() <= index && jtc.getSelectionEnd() > index))) {
                jtc.setSelectionStart(index);
                jtc.setSelectionEnd(index);
                hasSelection = false;
            }

            //popup menu items
            JMenuItem
                    cut = getJMenuItem("Cut", jtc::cut, DefaultEditorKit.cutAction),
                    copy = getJMenuItem("Copy", jtc::copy, DefaultEditorKit.copyAction),
                    paste = getJMenuItem("Paste", jtc::paste, DefaultEditorKit.pasteAction),
                    pastePlain = getJMenuItem("Paste as plain text", () -> pasteAsPlainText(jtc), PASTE_PLAIN_KEY),
                    selectAll = getJMenuItem("Select all", jtc::selectAll, DefaultEditorKit.selectAllAction),
                    undo = getJMenuItem("Undo", cum::undo, UNDO_KEY),
                    redo = getJMenuItem("Redo", cum::redo, REDO_KEY);

            //gray out non-editable
            boolean editable = jtc.isEditable();
            cut.setEnabled(editable);
            paste.setEnabled(editable);
            pastePlain.setEnabled(editable);
            undo.setEnabled(editable);
            redo.setEnabled(editable);

            //gray out non-undoable
            if (!cum.canUndo()) undo.setEnabled(false);
            if (!cum.canRedo()) redo.setEnabled(false);

            //require a text selection for cut/copy
            if (!hasSelection) {
                cut.setEnabled(false);
                copy.setEnabled(false);
            }

            //clear menu then add the items
            removeAll();

            //hyperlink actions (activate/copy link)
            if (hyperlink != null && jtc.isEnabled()) {
                final int final_hyperlinkIndex = hyperlinkIndex;
                final String final_hyperLink = hyperlink;

                //activate hyperlink
                if (jtc instanceof JEditorPane) {
                    JEditorPane jep = (JEditorPane)jtc;
                    JMenuItem openHyperlink = new JMenuItem("Open link");
                    openHyperlink.addActionListener(e -> activateHyperlink(jep, final_hyperLink, final_hyperlinkIndex));
                    add(openHyperlink);
                }

                //copy hyperlink
                JMenuItem copyHyperlink = new JMenuItem("Copy link");
                copyHyperlink.addActionListener(e -> copyToClipboard(final_hyperLink));
                add(copyHyperlink);
            }

            //add items
            add(cut);
            add(copy);
            add(paste);
            if (!isPlainTextContentType(jtc)) add(pastePlain);
            add(selectAll);
            add(undo);
            add(redo);

            //disable all for disabled component
            if (!jtc.isEnabled()) {
                Arrays.stream(getComponents())
                        .filter(e -> e instanceof JMenuItem)
                        .forEach(e -> e.setEnabled(false));
            }

            super.show(invoker, popupX, popupY);
        }

        //create a JMenuItem with a display shortcut
        private JMenuItem getJMenuItem(String text, Runnable r, Object shortcutValue) {
            JMenuItem jmi = new JMenuItem(text);
            jmi.addActionListener(e -> r.run());

            //find the shortcut's KeyStroke with its shortcut value
            if (shortcutValue != null) {
                InputMap im = jtc.getInputMap();
                KeyStroke[] keys = im.allKeys();
                if (keys != null && keys.length > 0) {
                    KeyStroke ks = Arrays.stream(keys)
                            .filter(e -> shortcutValue.equals(im.get(e)))
                            .max(Comparator.comparing(JTCEPopupMenu::isAlphaNumeric))
                            .orElse(null);
                    if (ks != null) {
                        //set a silent KeyStroke that only serves as display
                        jmi.setAccelerator(ks);
                        InputMap jmiim = jmi.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
                        while (jmiim != null) {
                            jmiim.clear();
                            jmiim = jmiim.getParent();
                        }
                    }
                }
            }

            return jmi;
        }

        //favor alphanumeric KeyStrokes when deciding what shortcut to display
        private static boolean isAlphaNumeric(KeyStroke ks) {
            int keyCode = ks.getKeyCode();
            return (keyCode >= KeyEvent.VK_0 && keyCode <= KeyEvent.VK_9)
                    || (keyCode >= KeyEvent.VK_A && keyCode <= KeyEvent.VK_Z);
        }

        //detect if keyboard shortcut triggered this popup (for example, Shift+F10 on Windows)
        private boolean detectPopupTriggeredByKeyboard(Component invoker) {
            AWTEvent event = EventQueue.getCurrentEvent();
            if (!(event instanceof KeyEvent)) return false;
            KeyEvent keyEvent = (KeyEvent)event;
            if (keyEvent.getSource() != invoker) return false;
            ActionListener al = computeKeyAction(keyEvent);
            return al instanceof Action && "postPopup".equals(((Action)al).getValue(Action.NAME));
        }

        //iterate the HTML document to find the hyperlink that matches the index
        private String getHyperlinkAtIndex(int index) {
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

        //invoke hyperlink event
        private static void activateHyperlink(JEditorPane jep, String hyperlink, int hyperlinkIndex) {
            HTMLDocument doc = (HTMLDocument) jep.getDocument();
            Element elem = doc.getCharacterElement(hyperlinkIndex);
            URL url;
            try {
                url = new URL(doc.getBase(), hyperlink);
            } catch (MalformedURLException ignore) {
                url = null;
            }
            HyperlinkEvent he = new HyperlinkEvent(jep, HyperlinkEvent.EventType.ACTIVATED, url, hyperlink, elem);
            jep.fireHyperlinkUpdate(he);
        }

        //copy text to clipboard
        private static void copyToClipboard(String text) {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        }

    }

}
