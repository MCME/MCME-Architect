/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mcmiddleearth.architect.displayEntity;

/**
 *
 * @author Eriol_Eandur
 */
public enum DisplayEntityEditorMode {
    XMOVE       ("mx","",": Selects x-movement mode"),
    YMOVE       ("my","",": Selects y-movement mode"),
    ZMOVE       ("mz","",": Selects z-movement mode"),
    MOVE        ("mo","",": Selects move left/right mode"),
    XROTATE     ("x"," [part]",": Selects x-axis rotation mode"),
    YROTATE     ("y"," [part]",": Selects y-axis rotation mode"),
    ZROTATE     ("z"," [part]",": Selects z-axis rotation mode"),
    ROTATE      ("r"," [part]",": Selects line of sight rotation mode"),
    TURN        ("t","",": Selects turn armor stand mode"),
    MARKER      ("ma","",": Selects switch marker mode"),
    STRAIGHT    ("str", "[part]", ": Selects straighten mode."),
    SIZE        ("si","",": Selects switch size mode"),
    SCALE        ("sc","",": Selects change scale mode"),
    VISIBLE     ("v","",": Selects switch visibility mode"),
    GRAVITY     ("g","",": Selects switch gravity mode"),
    PASTE       ("p","",": Selects paste mode"),
    COPY        ("c","",": Selects copy mode"),
    LOCK        ("l","",": Selects switch lock mode"),
    ROLLBACK    ("rollback","",": Not implemented");
    
    private final String name;
    private final String helpText;
    private final String arguments;

    DisplayEntityEditorMode(String name, String arguments, String helpText) {
        this.name = name;
        this.helpText = helpText;
        this.arguments = arguments;
    }
    
    public static DisplayEntityEditorMode getEditorMode(String name) {
        for(DisplayEntityEditorMode type: DisplayEntityEditorMode.values()) {
            if(name.toLowerCase().startsWith(type.name)) {
                return type;
            }
        }
        return null;
    }

    public String getName() {
        return name;
    }

    public String getHelpText() {
        return helpText;
    }

    public String getArguments() {
        return arguments;
    }
}
