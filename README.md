# sahlaysta.swing
Enhancements to Java Swing

## JTextComponentEnhancer

Enhancements to Swing text components.

- Add compound undo/redo functionality and shortcuts
- Add 'paste as plain text' option and shortcut
- Add right-click popup menu (Cut, Copy, Paste...)
    - This menu can also be activated by the 'show context menu' key (for example this is Shift+F10 on Windows)
- Adjust caret behavior while text is selected: when the left/right arrow key is pressed the caret will move to the beginning/end of the selection (this should be normal behavior in a text entry but Java Swing does not do it)
- Add hyperlink right-click support (popup menu will include options to activate/copy)
- Disable all beeps (for example, backspace in an empty text field)

Usage:

```java

//enhance a JTextComponent
JTextComponentEnhancer.enhanceJTextComponent(myJTextComponent);

//automatically enhance all JTextComponents
JTextComponentEnhancer.applyGlobalEnhancer();

```
