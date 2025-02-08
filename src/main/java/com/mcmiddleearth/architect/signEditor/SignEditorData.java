/*
 * Copyright (C) 2017 MCME
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.mcmiddleearth.architect.signEditor;

import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.pluginutil.message.FancyMessage;
import com.mcmiddleearth.pluginutil.message.MessageType;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;

/**
 *
 * @author Eriol_Eandur
 */
public class SignEditorData {
    
    private final static Map<Player,Block> signEditors = new HashMap<>();
    
    public static void putEditor(Player editor, Block signBlock) {
        signEditors.put(editor, signBlock);
    }
    
    public static boolean isEditor(Player editor) {
        return signEditors.containsKey(editor);
    }
    
    public static void sendSignMessage(Player editor) {
        Block signBlock = signEditors.get(editor);
        if(signBlock==null || !(signBlock.getState() instanceof Sign)) {
            return;
        }
        Sign sign = (Sign) signBlock.getState();
        FancyMessage message = new FancyMessage(MessageType.INFO,PluginData.getMessageUtil());
        message.addSimple("Click at a line to edit it.\\n");
        String[] lines = sign.getLines();
        for(int i = 0; i<4;i++) {
            String line = "<empty Line>";
            String lineEdit="";
            if(i<lines.length) {
                line = lines[i];
                lineEdit = line.replace('§','&');
            }
            message.addFancy("["+(i+1)+"] "+line+"\\n",
                             "/sign "+(i+1)+" "+lineEdit, 
                             "Click to edit. Don't change the leading '/sign <line index> '.");
        }
        message.send(editor);
    }
    
    public static boolean editSign(Player player, int line, String newText) {
        Block signBlock = signEditors.get(player);
        if(line<1||line>4) {
            return false;
        }
        if(signBlock==null || !(signBlock.getState() instanceof Sign)) {
            signEditors.remove(player);
            return false;
        }
        Sign sign = (Sign) signBlock.getState();
        char[] chars = newText.toCharArray();
        CharPage charPage = CharPage.LATIN;
        newText = "";
        for(int i=0; i < chars.length; i++) {
            if(chars[i] == '#') {
                if(i+1 < chars.length) {
                    switch(chars[i+1]) {
                        case '#':
                        case ' ':
                            newText = newText+"#";
                            break;
                        case 'L':
                            charPage = CharPage.LATIN;
                            i++;
                            break;
                        case 'T':
                            charPage = CharPage.TENGWAR;
                            i++;
                            break;
                        case 'A':
                            charPage = CharPage.ANGERTHAS;
                            i++;
                            break;
                        default:
                            newText = newText+"§";
                    }
                }
            } else {
                newText = newText+((char)(chars[i]+charPage.shift));
            }
        }
        sign.setLine(line-1, newText);//.replace('#','§'));
        sign.update(true, false);
        return true;
    }
}
