package sahlaysta.swing;

import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.UndoableEditEvent;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.Keymap;
import javax.swing.text.StyledDocument;
import javax.swing.text.StyledEditorKit;
import javax.swing.text.TextAction;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;
import javax.swing.undo.UndoableEdit;
import java.awt.AWTEvent;
import java.awt.AWTKeyStroke;
import java.awt.Component;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.FontMetrics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
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
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Custom enhancements to Swing text components.
 *
 * <ul>
 * <li> Add compound undo/redo functionality and shortcuts
 * <li> Add 'paste as plain text' option and shortcut
 * <li> Add right-click popup menu (Cut, Copy, Paste...)
 * <ul>
 *     <li> This menu can also be activated by the 'show context menu' key
 *          (for example this is Shift+F10 on Windows)
 * </ul>
 * <li> Adjust caret behavior while text is selected: when the left/right arrow key is pressed the caret
 *      will move to the beginning/end of the selection (this should be normal behavior in a text entry
 *      but Java Swing does not do it)
 * <li> Add hyperlink right-click support (popup menu will include options to activate/copy)
 * <li> Disable all beeps (for example, backspace in an empty text field)
 * </ul>
 */
public class JTextComponentEnhancer {

    private JTextComponentEnhancer() { }

    /** Provides the enhancements to the text component. */
    public static void enhanceJTextComponent(JTextComponent jtc) {
        Objects.requireNonNull(jtc);
        enhanceJTextComponent(jtc, true, true, true, () -> { }, true);
    }

    /**
     * Provides the enhancements to the text component.
     *
     * @param addCompoundUndoManager         if true, will provide the compound undo manager unless already provided
     *
     * @param addActions                     if true, will add the undo, redo, and paste as plain text inputs/actions
     *                                       (will not replace any already existing inputs)
     *
     * @param replaceDefaultEditorKitActions if true, will replace the DefaultEditorKit actions of the component
     *                                       (beep function + move caret fully with selected text)
     *
     * @param beepFunction                   will replace the text component beep action
     *                                       (replaceDefaultEditorKitActions should be true)
     *
     * @param setComponentPopupMenu          if true, will provide the popup menu unless the component has a popup menu
     */
    public static void enhanceJTextComponent(
            JTextComponent jtc,
            boolean addCompoundUndoManager,
            boolean addActions,
            boolean replaceDefaultEditorKitActions,
            Runnable beepFunction,
            boolean setComponentPopupMenu) {
        Objects.requireNonNull(jtc);
        Objects.requireNonNull(beepFunction);

        getOrAddCompoundUndoManager(jtc, addCompoundUndoManager);

        if (addActions)
            addEnhancementActions(jtc);

        if (replaceDefaultEditorKitActions)
            replaceXDefaultEditorKitActions(jtc);

        setBeepFunction(jtc, beepFunction);

        if (setComponentPopupMenu && jtc.getComponentPopupMenu() == null)
            jtc.setComponentPopupMenu(new JTCEPopupMenu(jtc));
    }

    private static boolean appliedGlobalEnhancer = false;
    private static final Set<JTextComponent> enhancedComponents
            = Collections.newSetFromMap(new WeakIdentityHashMap<>());

    /** Automatically enhances all text components. */
    public static void applyGlobalEnhancer() {
        if (appliedGlobalEnhancer) return;
        appliedGlobalEnhancer = true;
        AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
            Toolkit.getDefaultToolkit().addAWTEventListener(e -> {
                if (e.getID() != ContainerEvent.COMPONENT_ADDED) return;
                if (!(e instanceof ContainerEvent)) return;
                if (!(e.getSource() instanceof Container)) return;
                Component c = ((ContainerEvent)e).getChild();
                if (!(c instanceof JTextComponent)) return;
                JTextComponent jtc = (JTextComponent)c;
                if (!enhancedComponents.contains(jtc)) {
                    enhancedComponents.add(jtc);
                    SwingUtilities.invokeLater(() -> enhanceJTextComponent(jtc));
                }
            }, AWTEvent.CONTAINER_EVENT_MASK);
            return null;
        });
    }

    /** The name and binding for the undo action. */
    public static final String undo = "sahlaysta.swing.undo";

    /** The name and binding for the redo action. */
    public static final String redo = "sahlaysta.swing.redo";

    /** The name and binding for the the paste as plain text action. */
    public static final String pasteAsPlainText = "sahlaysta.swing.pasteasplaintext";

    public static class UndoAction extends TextAction {

        public UndoAction() {
            super(undo);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            JTextComponent jtc = this.getTextComponent(e);
            if (jtc != null && jtc.isEnabled() && jtc.isEditable())
                undoForJTextComponent(jtc);
        }

    }

    public static class RedoAction extends TextAction {

        public RedoAction() {
            super(redo);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            JTextComponent jtc = this.getTextComponent(e);
            if (jtc != null && jtc.isEnabled() && jtc.isEditable())
                redoForJTextComponent(jtc);
        }

    }

    public static class PasteAsPlainTextAction extends TextAction {

        public PasteAsPlainTextAction() {
            super(pasteAsPlainText);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            JTextComponent jtc = this.getTextComponent(e);
            if (jtc != null && jtc.isEnabled() && jtc.isEditable())
                pasteAsPlainTextForJTextComponent(jtc);
        }

    }

    /**
     * Undo manager that groups adjacent undo edits.
     *
     * <ul>
     * <li> Edits that are adjacent and made with the defaultKeyTypedAction action are compounded.
     * <li> Edits that are adjacent and made with the deletePrevCharAction action (backspace) are compounded.
     * <li> Edits that occur in the same AWTEvent event instance are always compounded.
     * </ul>
     *
     */
    public static class CompoundUndoManager extends UndoManager {

        //class for grouped edits to be treated as one edit
        private static class CUMEdit implements UndoableEdit {

            final ArrayDeque<UndoableEdit> edits = new ArrayDeque<>();

            enum UndoState { STARTED, UNDOED, REDOED }
            UndoState undoState = UndoState.STARTED;

            CUMEdit(UndoableEdit initialEdit) {
                addCompoundEdit(initialEdit);
            }

            boolean canAddCompoundEdits() {
                return undoState == UndoState.STARTED;
            }

            void addCompoundEdit(UndoableEdit anEdit) {
                edits.add(anEdit);
            }

            @Override
            public boolean addEdit(UndoableEdit anEdit) {
                return false;
            }

            @Override
            public boolean canUndo() {
                return undoState == UndoState.STARTED || undoState == UndoState.REDOED;
            }

            @Override
            public boolean canRedo() {
                return undoState == UndoState.UNDOED;
            }

            @Override
            public void undo() throws CannotUndoException {
                if (!canUndo()) throw new CannotUndoException();
                edits.descendingIterator().forEachRemaining(UndoableEdit::undo);
                undoState = UndoState.UNDOED;
            }

            @Override
            public void redo() throws CannotRedoException {
                if (!canRedo()) throw new CannotRedoException();
                edits.iterator().forEachRemaining(UndoableEdit::redo);
                undoState = UndoState.REDOED;
            }

            @Override
            public boolean isSignificant() {
                return edits.stream().anyMatch(UndoableEdit::isSignificant);
            }

            @Override public void die() { }
            @Override public boolean replaceEdit(UndoableEdit anEdit) { return false; }
            @Override public String getPresentationName() { return ""; }
            @Override public String getUndoPresentationName() { return ""; }
            @Override public String getRedoPresentationName() { return ""; }

        }

        public CompoundUndoManager(JTextComponent jtc) {
            this.jtc = jtc;
        }

        @Deprecated
        public CompoundUndoManager() {
            this(null);
        }

        final JTextComponent jtc;
        private Object doCompoundedToken;

        //remember information on each UndoableEdit instance
        private static final Map<UndoableEdit, AWTEvent> awtEvents = new WeakIdentityHashMap<>();
        private static final Map<UndoableEdit, Object> doCompoundedTokens = new WeakIdentityHashMap<>();
        private static final Set<UndoableEdit> defaultTypedEdits =
                Collections.newSetFromMap(new WeakIdentityHashMap<>());
        private static final Set<UndoableEdit> deletePreviousEdits =
                Collections.newSetFromMap(new WeakIdentityHashMap<>());
        private static final Set<UndoableEdit> compoundEdits =
                Collections.newSetFromMap(new WeakIdentityHashMap<>());

        /** This method is most likely useless now. */
        public synchronized void doCompounded(Runnable runnable) {
            Object previousToken = this.doCompoundedToken;
            this.doCompoundedToken = new Object();
            try {
                runnable.run();
            } finally {
                this.doCompoundedToken = previousToken;
            }
        }

        public boolean isCompoundEdit(UndoableEdit anEdit) {
            return compoundEdits.contains(anEdit);
        }

        @Override
        public void undoableEditHappened(UndoableEditEvent e) {
            registerEditInfo(e.getEdit());
            super.undoableEditHappened(e);
        }

        @Override
        public synchronized boolean addEdit(UndoableEdit anEdit) {
            UndoableEdit lastEdit = lastEdit();
            if (lastEdit == editToBeUndone() && lastEdit instanceof CUMEdit) {
                CUMEdit cumEdit = (CUMEdit) lastEdit;
                UndoableEdit cumLastEdit = cumEdit.edits.peekLast();
                if (cumEdit.canAddCompoundEdits() && areCompoundEdits(cumLastEdit, anEdit)) {
                    cumEdit.addCompoundEdit(anEdit);
                    compoundEdits.add(anEdit);
                    return true;
                }
            }
            return super.addEdit(new CUMEdit(anEdit));
        }

        private void registerEditInfo(UndoableEdit ue) {
            if (doCompoundedToken != null) doCompoundedTokens.put(ue, doCompoundedToken);

            if (jtc == null) return;

            if (!(ue instanceof AbstractDocument.DefaultDocumentEvent)) return;
            AbstractDocument.DefaultDocumentEvent dde = (AbstractDocument.DefaultDocumentEvent) ue;
            if (dde.getDocument() != jtc.getDocument()) return;

            AWTEvent awtEvent = EventQueue.getCurrentEvent();
            if (awtEvent == null) return;
            awtEvents.put(ue, awtEvent);

            if (awtEvent instanceof KeyEvent && awtEvent.getSource() == jtc) {
                KeyEventInfo kei = KeyEventInfo.getCurrentKeyEventInfo();
                if (kei != null) {
                    if (kei.nameEquals("default-typed"))
                        defaultTypedEdits.add(ue);
                    else if (kei.nameEquals("delete-previous"))
                        deletePreviousEdits.add(ue);
                }
            }
        }

        private boolean areCompoundEdits(UndoableEdit ue1, UndoableEdit ue2) {
            if (doCompoundedTokens.get(ue1) != null && doCompoundedTokens.get(ue1) == doCompoundedTokens.get(ue2)) {
                return true;
            }

            if (awtEvents.get(ue1) != null && awtEvents.get(ue1) == awtEvents.get(ue2)) {
                return true;
            }

            if (ue1 instanceof AbstractDocument.DefaultDocumentEvent
                    && ue2 instanceof AbstractDocument.DefaultDocumentEvent) {
                AbstractDocument.DefaultDocumentEvent dde1 = (AbstractDocument.DefaultDocumentEvent) ue1;
                AbstractDocument.DefaultDocumentEvent dde2 = (AbstractDocument.DefaultDocumentEvent) ue2;
                DocumentEvent.EventType dde1type = dde1.getType();
                DocumentEvent.EventType dde2type = dde2.getType();
                if (dde1type == dde2type) {
                    if (defaultTypedEdits.contains(dde1) && defaultTypedEdits.contains(dde2)) {
                        if ((dde1type == DocumentEvent.EventType.INSERT)
                                && (dde1.getOffset() + dde1.getLength() == dde2.getOffset())) {
                            return true;
                        }
                    } else if (deletePreviousEdits.contains(dde1) && deletePreviousEdits.contains(dde2)) {
                        if ((dde1type == DocumentEvent.EventType.REMOVE)
                                && (dde1.getOffset() - dde1.getLength() == dde2.getOffset())) {
                            return true;
                        }
                    }
                }
            }

            return false;
        }

    }

    private enum OS { WINDOWS, MAC, OTHER }
    private static final OS os = getOS();
    private static OS getOS() {
        String osName = System.getProperty("os.name");
        if (osName.startsWith("Windows")) {
            return OS.WINDOWS;
        } else if (osName.startsWith("Mac")) {
            return OS.MAC;
        } else {
            return OS.OTHER;
        }
    }

    /*
    the platform-specific keystroke shortcuts

    the reasoning behind these shortcuts:
    Windows: "ctrl Z and alt backspace" for undo are common, for example Notepad.exe
    Mac: in Java Swing's AquaLookAndFeel, AquaKeyBindings defines copy as "meta C" and paste as "meta V", which is why
         here undo follows that pattern: "meta Z"
     */
    private static class PlatformKeyStrokes {
        final KeyStroke[] undo, redo, pasteAsPlainText;
        PlatformKeyStrokes(KeyStroke[] undo, KeyStroke[] redo, KeyStroke[] pasteAsPlainText) {
            this.undo = undo;
            this.redo = redo;
            this.pasteAsPlainText = pasteAsPlainText;
        }
    }
    private static final PlatformKeyStrokes platformKeyStrokes = getPlatformKeyStrokes();
    private static PlatformKeyStrokes getPlatformKeyStrokes() {
        KeyStroke[] undo, redo, pasteAsPlainText;
        switch (os) {
            case WINDOWS:
                undo = getKeyStrokes("ctrl Z", "alt BACK_SPACE", "UNDO");
                redo = getKeyStrokes("ctrl shift Z", "ctrl Y");
                pasteAsPlainText = getKeyStrokes("ctrl shift V");
                break;
            case MAC:
                undo = getKeyStrokes("meta Z", "UNDO");
                redo = getKeyStrokes("meta shift Z", "meta Y");
                pasteAsPlainText = getKeyStrokes("meta shift V");
                break;
            case OTHER:
                undo = getKeyStrokes("ctrl Z", "UNDO");
                redo = getKeyStrokes("ctrl shift Z", "ctrl Y");
                pasteAsPlainText = getKeyStrokes("ctrl shift V");
                break;
            default: throw new AssertionError();
        }
        return new PlatformKeyStrokes(undo, redo, pasteAsPlainText);
    }
    private static KeyStroke[] getKeyStrokes(String... keyStrokes) {
        return Arrays.stream(keyStrokes)
                .map(KeyStroke::getKeyStroke)
                .map(Objects::requireNonNull)
                .toArray(KeyStroke[]::new);
    }

    public static KeyStroke[] getPlatformUndoKeyStrokes() {
        return platformKeyStrokes.undo.clone();
    }

    public static KeyStroke[] getPlatformRedoKeyStrokes() {
        return platformKeyStrokes.redo.clone();
    }

    public static KeyStroke[] getPlatformPasteAsPlainTextKeyStrokes() {
        return platformKeyStrokes.pasteAsPlainText.clone();
    }

    private static CompoundUndoManager getOrAddCompoundUndoManager(JTextComponent jtc, boolean addCompoundUndoManager) {
        Document doc = jtc.getDocument();
        if (!(doc instanceof AbstractDocument)) return null;
        AbstractDocument adoc = (AbstractDocument) doc;
        CompoundUndoManager match = Arrays.stream(adoc.getUndoableEditListeners())
                .filter(d -> d instanceof CompoundUndoManager && ((CompoundUndoManager)d).jtc == jtc)
                .map(d -> (CompoundUndoManager)d)
                .findFirst().orElse(null);
        if (match != null) {
            return match;
        } else {
            if (addCompoundUndoManager) {
                CompoundUndoManager cum = new CompoundUndoManager(jtc);
                adoc.addUndoableEditListener(cum);
                return cum;
            } else {
                return null;
            }
        }
    }

    private static final UndoAction undoAction = new UndoAction();
    private static final RedoAction redoAction = new RedoAction();
    private static final PasteAsPlainTextAction pasteAsPlainTextAction = new PasteAsPlainTextAction();
    private static void addEnhancementActions(JTextComponent jtc) {
        InputMap im = jtc.getInputMap();
        ActionMap am = jtc.getActionMap();
        for (KeyStroke keyStroke: platformKeyStrokes.undo) {
            if (im.get(keyStroke) == null)
                im.put(keyStroke, undo);
            am.put(undo, undoAction);
        }
        for (KeyStroke keyStroke: platformKeyStrokes.redo) {
            if (im.get(keyStroke) == null)
                im.put(keyStroke, redo);
            am.put(redo, redoAction);
        }
        for (KeyStroke keyStroke: platformKeyStrokes.pasteAsPlainText) {
            if (im.get(keyStroke) == null)
                im.put(keyStroke, pasteAsPlainText);
            am.put(pasteAsPlainText, pasteAsPlainTextAction);
        }
    }

    private static class ReplacementAction {
        final Action defaultAction, xAction;
        ReplacementAction(Action defaultAction, Action xAction) {
            this.defaultAction = defaultAction;
            this.xAction = xAction;
        }
    }
    private static final Map<String, ReplacementAction> replacementActionMap = createReplacementActionMap();
    private static Map<String, ReplacementAction> createReplacementActionMap() {
        Map<String, Action> defaultActionMap =
                Arrays.stream(new DefaultEditorKit().getActions())
                .collect(Collectors.toMap(a -> (String)a.getValue(Action.NAME), a -> a));
        Map<String, Action> xActionMap =
                Arrays.stream(new XDefaultEditorKit().getActions())
                .collect(Collectors.toMap(a -> (String)a.getValue(Action.NAME), a -> a));
        Set<String> actionNames = Stream.concat(
                defaultActionMap.keySet().stream(), xActionMap.keySet().stream())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        HashMap<String, ReplacementAction> result = new HashMap<>();
        for (String actionName: actionNames) {
            Action defaultAction = defaultActionMap.get(actionName);
            Action xAction = xActionMap.get(actionName);
            if (defaultAction != null && xAction != null)
                result.put(actionName, new ReplacementAction(defaultAction, xAction));
        }
        return Collections.unmodifiableMap(result);
    }

    private static void replaceXDefaultEditorKitActions(JTextComponent jtc) {
        replaceXDefaultEditorKitActionMap(jtc.getActionMap());
        Keymap km = jtc.getKeymap();
        if (km != null) {
            Action kmDefaultAction = km.getDefaultAction();
            Action kmReplacementAction = findReplacementAction(kmDefaultAction);
            if (kmReplacementAction != null) km.setDefaultAction(kmReplacementAction);
        }
    }

    private static void replaceXDefaultEditorKitActionMap(ActionMap actionMap) {
        ActionMap current = actionMap;
        while (current != null) {
            Object[] keys = current.keys();
            if (keys != null) {
                for (Object key: keys) {
                    Action action = current.get(key);
                    Action replacementAction = findReplacementAction(action);
                    if (replacementAction != null) current.put(key, replacementAction);
                }
            }
            current = current.getParent();
        }
    }

    private static Action findReplacementAction(Action action) {
        if (action != null) {
            Object actionNameObj = action.getValue(Action.NAME);
            if (actionNameObj instanceof String) {
                String actionName = (String) actionNameObj;
                ReplacementAction replacementAction = replacementActionMap.get(actionName);
                if (replacementAction != null && action.getClass() == replacementAction.defaultAction.getClass())
                    return replacementAction.xAction;
            }
        }
        return null;
    }

    private static final Map<JTextComponent, Runnable> componentBeepRunnables = new WeakIdentityHashMap<>();
    private static void setBeepFunction(JTextComponent jtc, Runnable beepFunction) {
        componentBeepRunnables.put(jtc, beepFunction);
    }

    static void beepForJTextComponent(JTextComponent jtc) {//called from XDefaultEditorKit
        Runnable r = componentBeepRunnables.get(jtc);
        if (r != null) r.run();
    }

    static void undoForJTextComponent(JTextComponent jtc) {
        CompoundUndoManager cum = getOrAddCompoundUndoManager(jtc, false);
        if (cum != null && cum.canUndo())
            cum.undo();
    }

    static void redoForJTextComponent(JTextComponent jtc) {
        CompoundUndoManager cum = getOrAddCompoundUndoManager(jtc, false);
        if (cum != null && cum.canRedo())
            cum.redo();
    }

    static void pasteAsPlainTextForJTextComponent(JTextComponent jtc) {
        if (isPlainTextContentType(jtc)) {
            jtc.paste();
        } else {
            String str = getClipboardText();
            if (str != null) jtc.replaceSelection(str);
        }
    }

    private static boolean isPlainTextContentType(JTextComponent jtc) {
        if (jtc instanceof JEditorPane) {
            JEditorPane jep = (JEditorPane)jtc;
            return "text/plain".equals(jep.getContentType());
        } else {
            return !(jtc.getUI().getEditorKit(jtc) instanceof StyledEditorKit);
        }
    }

    private static String getClipboardText() {
        try {
            Object o = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            if (o instanceof String) return (String)o;
        } catch (UnsupportedFlavorException | IOException ignore) { }
        return null;
    }

    private static void setClipboardText(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }

    private static class JTCEPopupMenu extends JPopupMenu {

        private static class JTCEMenuItem extends JMenuItem {

            ActionListener itemAction;
            
            {
                addActionListener(e -> {
                    if (itemAction != null)
                        itemAction.actionPerformed(e);
                });
            }

            void reinitWithBinding(String text, InputMap im, ActionMap am, Object binding) {
                setText(text);
                setAccelerator(getDisplayKeyStroke(getInputMapKeyStrokesForValue(im, binding)));
                itemAction = am.get(binding);
                setEnabled(itemAction != null);
            }

            void reinitWithAction(String text, Runnable action) {
                setText(text);
                setAccelerator(null);
                itemAction = action == null ? null : e -> action.run();
                setEnabled(itemAction != null);
            }

            //accelerator for display only, non-functional keystroke
            @Override
            public void setAccelerator(KeyStroke keyStroke) {
                super.setAccelerator(keyStroke);
                InputMap im = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
                while (im != null) {
                    im.clear();
                    im = im.getParent();
                }
            }

        }

        final JTextComponent jtc;
        final JTCEMenuItem cutItem = new JTCEMenuItem();
        final JTCEMenuItem copyItem = new JTCEMenuItem();
        final JTCEMenuItem pasteItem = new JTCEMenuItem();
        final JTCEMenuItem pasteAsPlainTextItem = new JTCEMenuItem();
        final JTCEMenuItem selectAllItem = new JTCEMenuItem();
        final JTCEMenuItem undoItem = new JTCEMenuItem();
        final JTCEMenuItem redoItem = new JTCEMenuItem();
        final JTCEMenuItem activateLinkItem = new JTCEMenuItem();
        final JTCEMenuItem copyLinkItem = new JTCEMenuItem();

        JTCEPopupMenu(JTextComponent jtc) {
            this.jtc = jtc;
        }

        @Override
        public void show(Component invoker, int x, int y) {
            removeAll();

            InputMap im = jtc.getInputMap();
            ActionMap am = jtc.getActionMap();
            boolean keyboardInvoked = checkKeyboardInvoked();
            boolean jtcEnabled = jtc.isEnabled();
            boolean jtcEditable = jtc.isEditable();
            boolean jtcHasText = jtc.getDocument().getLength() > 0;
            boolean jtcHasSelection = jtcHasText && jtc.getSelectionStart() != jtc.getSelectionEnd();
            boolean jtcPlainText = isPlainTextContentType(jtc);
            CompoundUndoManager jtcCum = getOrAddCompoundUndoManager(jtc, false);
            int jtcIndex = keyboardInvoked ? jtc.getCaret().getDot() : jtc.viewToModel(new Point(x, y));
            String jtcHyperlink = getHyperlinkAtIndex(jtc, jtcIndex);

            cutItem.reinitWithBinding("Cut", im, am, "cut-to-clipboard");
            if (!jtcEnabled || !jtcEditable || !jtcHasSelection)
                cutItem.setEnabled(false);
            add(cutItem);

            copyItem.reinitWithBinding("Copy", im, am, "copy-to-clipboard");
            if (!jtcHasSelection)
                copyItem.setEnabled(false);
            add(copyItem);

            pasteItem.reinitWithBinding("Paste", im, am, "paste-from-clipboard");
            if (!jtcEnabled || !jtcEditable)
                pasteItem.setEnabled(false);
            add(pasteItem);

            if (!jtcPlainText) {
                pasteAsPlainTextItem.reinitWithBinding("Paste as plain text", im, am, pasteAsPlainText);
                if (!jtcEnabled || !jtcEditable)
                    pasteAsPlainTextItem.setEnabled(false);
                add(pasteAsPlainTextItem);
            }

            selectAllItem.reinitWithBinding("Select all", im, am, "select-all");
            if (!jtcEnabled || !jtcHasText)
                selectAllItem.setEnabled(false);
            add(selectAllItem);

            if (jtcCum != null) {
                undoItem.reinitWithBinding("Undo", im, am, undo);
                if (!jtcEnabled || !jtcEditable || !jtcCum.canUndo())
                    undoItem.setEnabled(false);
                add(undoItem);

                redoItem.reinitWithBinding("Redo", im, am, redo);
                if (!jtcEnabled || !jtcEditable || !jtcCum.canRedo())
                    redoItem.setEnabled(false);
                add(redoItem);
            }

            if (jtcHyperlink != null) {
                addSeparator();

                activateLinkItem.reinitWithAction("Activate link", () -> fireHyperlink(jtc, jtcHyperlink, jtcIndex));
                if (!jtcEnabled)
                    activateLinkItem.setEnabled(false);
                add(activateLinkItem);

                copyLinkItem.reinitWithAction("Copy link", () -> setClipboardText(jtcHyperlink));
                if (!jtcEnabled)
                    copyLinkItem.setEnabled(false);
                add(copyLinkItem);
            }

            if (!keyboardInvoked) {
                super.show(invoker, x, y);
            } else {
                Point p = getKeyboardInvokedShowLocation();
                super.show(invoker, p.x, p.y);
            }
        }

        private boolean checkKeyboardInvoked() {
            AWTEvent awtEvent = EventQueue.getCurrentEvent();
            if (awtEvent == null) return false;
            if (!(awtEvent instanceof KeyEvent)) return false;
            KeyEvent keyEvent = (KeyEvent)awtEvent;
            if (keyEvent.getSource() != jtc) return false;
            KeyEventInfo kei = KeyEventInfo.getCurrentKeyEventInfo();
            return kei != null && kei.nameEquals("postPopup");
        }

        private static String getHyperlinkAtIndex(JTextComponent jtc, int index) {
            Document d = jtc.getDocument();
            if (!(d instanceof HTMLDocument)) return null;
            HTMLDocument hd = (HTMLDocument)jtc.getDocument();
            HTMLDocument.Iterator docit = hd.getIterator(HTML.Tag.A);
            if (docit == null) return null;
            while (docit.isValid()) {
                if (docit.getStartOffset() <= index && docit.getEndOffset() > index) {
                    AttributeSet as = docit.getAttributes();
                    if (as != null) {
                        Enumeration<?> en = as.getAttributeNames();
                        if (en != null) {
                            while (en.hasMoreElements()) {
                                if (HTML.Attribute.HREF.equals(en.nextElement())) {
                                    Object attr = as.getAttribute(HTML.Attribute.HREF);
                                    if (attr instanceof String) {
                                        return (String)attr;
                                    }
                                }
                            }
                        }
                    }
                }
                docit.next();
            }
            return null;
        }

        /*
        sorting the keystrokes is a good way to get the proper display keystroke.
        for example, for the paste action, instead of the "Ctrl V" key, it would be the "VK_PASTE" key,
        which is an uncommon key that not many keyboards have.
         */
        private static KeyStroke getDisplayKeyStroke(KeyStroke[] keyStrokes) {
            if (keyStrokes == null || keyStrokes.length == 0) return null;
            KeyStroke[] sortedKeyStrokes = keyStrokes.clone();
            Arrays.sort(sortedKeyStrokes, Comparator.comparing(AWTKeyStroke::getModifiers).reversed());
            Arrays.sort(sortedKeyStrokes, Comparator.comparing(ks -> ks.getKeyCode() < '0' || ks.getKeyCode() > 'Z'));
            return sortedKeyStrokes[0];
        }

        private static KeyStroke[] getInputMapKeyStrokesForValue(InputMap im, Object value) {
            if (im == null) return null;
            ArrayList<KeyStroke> keyStrokes = new ArrayList<>();
            KeyStroke[] imKeys = im.allKeys();
            if (imKeys == null) return null;
            for (KeyStroke imKey: imKeys)
                if (value.equals(im.get(imKey)))
                    keyStrokes.add(imKey);
            return keyStrokes.isEmpty() ? null : keyStrokes.toArray(new KeyStroke[0]);
        }

        private Point getKeyboardInvokedShowLocation() {
            Point p = getCaretPosition(jtc);
            FontMetrics fm = jtc.getFontMetrics(jtc.getFont());
            int fontHeight = fm.getHeight();
            int fontMaxDescent = fm.getMaxDescent();
            return new Point(p.x, p.y + fontHeight + fontMaxDescent);
        }

        private static Point getCaretPosition(JTextComponent jtc) {
            Point p = jtc.getCaret().getMagicCaretPosition();
            if (p != null) {
                return new Point(p.x, p.y);
            } else {
                try {
                    Rectangle rect = jtc.modelToView(jtc.getSelectionEnd());
                    return new Point(rect.x, rect.y);
                } catch (BadLocationException e) { throw new Error(e); }
            }
        }

        private static void fireHyperlink(JTextComponent jtc, String href, int offset) {
            if (!(jtc instanceof JEditorPane)) return;
            JEditorPane jep = (JEditorPane)jtc;
            Document d = jep.getDocument();
            if (!(d instanceof StyledDocument)) return;
            StyledDocument sd = (StyledDocument)jep.getDocument();
            Object page = sd.getProperty(Document.StreamDescriptionProperty);
            URL url = null;
            try {
                url = page instanceof URL ? new URL((URL)page, href) : null;
            } catch (MalformedURLException ignore) { }
            HyperlinkEvent event = new HyperlinkEvent(
                    jep, HyperlinkEvent.EventType.ACTIVATED, url, href, sd.getCharacterElement(offset));
            jep.fireHyperlinkUpdate(event);
        }

    }

}
