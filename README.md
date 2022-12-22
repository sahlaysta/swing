# swing
Enhancements to Java Swing

## JTextComponentEnhancer

- add compound undo/redo functionality and shortcuts
- add right-click popup menu with (Cut, Copy, Paste...)
- adjust behavior of having text selected and the right/left arrow key is pressed
- adjust right-click behavior on text selection
- add hyperlink right-click support

Usage:

```java

//enhance a JTextComponent
JTextComponentEnhancer.enhanceJTextComponent(myJTextComponent);

//automatically enhance all JTextComponents
JTextComponentEnhancer.applyGlobalEnhancer();

```
