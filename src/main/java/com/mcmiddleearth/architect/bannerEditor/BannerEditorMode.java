/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mcmiddleearth.architect.bannerEditor;

/**
 *
 * @author Eriol_Eandur
 */
public enum BannerEditorMode {
    ORIENTATION ("o","",": Selects orientation mode."),
    LIST    ("l","",": Selects list patterns mode."),
    TEXTURE ("t","",": Selects cycle texture mode."),
    COLOR   ("c","",": Selects cycle color mode."),
    ADD     ("a","",": Selects add pattern mode."),
    REMOVE  ("r","",": Selects remove pattern mode."),
    SHIELD  ("s","",": Selects banner to shield mode."),
    GET     ("g","",": Selects get banner mode.");
    
    private final String name;
    private final String arguments;
    private final String helpText;
    
    BannerEditorMode(String name, String arguments, String helpText) {
        this.name = name;
        this.helpText = helpText;
        this.arguments = arguments;
    }
    
    public static BannerEditorMode getEditorMode(String name) {
        for(BannerEditorMode type: BannerEditorMode.values()) {
            if(type.name.equalsIgnoreCase(name.substring(0, 1))) {
                return type;
            }
        }
        return null;
    }

    public String getName() {
        return name;
    }

    public String getArguments() {
        return arguments;
    }

    public String getHelpText() {
        return helpText;
    }
}
