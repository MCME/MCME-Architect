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
import com.mcmiddleearth.pluginutil.NumericUtil;
import com.mcmiddleearth.pluginutil.message.FancyMessage;
import com.mcmiddleearth.pluginutil.message.MessageType;

import java.util.*;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;

/**
 *
 * @author Eriol_Eandur
 */
public class SignEditorData {
    
    private final static Map<Player,SignData> signEditors = new HashMap<>();
    
    public static void putEditor(Player editor, Block signBlock, Side side) {
        signEditors.put(editor, new SignData(signBlock, side));
    }
    
    public static boolean isEditor(Player editor) {
        return signEditors.containsKey(editor);
    }
    
    public static void sendSignMessage(Player editor) {
        SignData signData = signEditors.get(editor);
        Block signBlock = signData.getBlock();
        if(signBlock==null || !(signBlock.getState() instanceof Sign sign)) {
            return;
        }
        FancyMessage message = new FancyMessage(MessageType.INFO,PluginData.getMessageUtil());
        message.addSimple("You are editing sign side: "+signData.getSide()+"\\n");
        message.addSimple("Click at a line to edit it.\\n");
        String[] lines = sign.getSide(signData.getSide()).getLines();
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
        SignData signData = signEditors.get(player);
        Block signBlock = signData.getBlock();
        if (line < 1 || line > 4) {
            return false;
        }
        if (signBlock == null || !(signBlock.getState() instanceof Sign sign)) {
            signEditors.remove(player);
            return false;
        }
        newText = processLineText(newText);
        sign.getSide(signData.getSide()).setLine(line-1, newText);//.replace('#','§'));
        sign.update(true, false);
        return true;
    }

    public static String processLineText(String newText) {
        char[] chars = newText.toCharArray();
        CharPage charPage = CharPage.LATIN;
        newText = "";
        for(int i=0; i < chars.length; i++) {
            if(chars[i] == '&') {
                if (i + 1 < chars.length) {
                    switch (chars[i + 1]) {
                        case '&':
                        case ' ':
                            newText = newText + "&";
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
                            newText = newText + "§";
                    }
                }
            } else {
                newText = newText + ((char)(chars[i]+charPage.shift));
            }
        }
        return newText;
    }

    public static Component parseLine(String line) {
        Component result = Component.text("");
        Format format = new Format();
        StringBuilder text = new StringBuilder();
        char[] chars = line.toCharArray();
        CharPage charPage = CharPage.LATIN;
        for(int i=0; i < chars.length; i++) {
            if (chars[i] == '&' && i < chars.length-1 && Format.isFormatting(chars[i+1])) {
                switch (chars[i + 1]) {
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
                        result = result.append(format.format(Component.text(text.toString())));
                        text = new StringBuilder();
                        format.setFormat(chars[i+1]);
                        i++;
                }
            } else if(chars[i] == '#' && i < chars.length-7 && Format.isHexString(line.substring(i,i+7))) {
                result = result.append(format.format(Component.text(text.toString())));
                text = new StringBuilder();
                format.setHexColor(line.substring(i, i+7));
                i+=6;
            } else {
                text.append((char)(chars[i]+charPage.shift));
            }
        }
        return result;
    }

    public static class Format {

        private static Set<Character> formattingChars = new HashSet<>(
                Arrays.asList('0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f','k','l','m','n','o','r'));

        private TextColor color = NamedTextColor.BLACK;
        private boolean bold = false;
        private boolean obfuscated = false;
        private boolean strikethrough = false;
        private boolean underline = false;
        private boolean italic = false;

        public void reset() {
            color = NamedTextColor.BLACK;
            bold = false;
            obfuscated = false;
            strikethrough = false;
            underline = false;
            italic = false;
        }

        public void setHexColor(String hexColorString) {
            color = TextColor.fromCSSHexString(hexColorString);
        }

        public Component format(Component component) {
            component = component.color(color);
            if(obfuscated) component = component.decorate(TextDecoration.OBFUSCATED);
            if(bold) component = component.decorate(TextDecoration.BOLD);
            if(strikethrough) component = component.decorate(TextDecoration.STRIKETHROUGH);
            if(underline) component = component.decorate(TextDecoration.UNDERLINED);
            if(italic) component = component.decorate(TextDecoration.ITALIC);
            return component;
        }

        public void setFormat(char character) {
            switch(character) {
                case 'k':
                    obfuscated = true;
                    break;
                case 'l':
                    bold = true;
                    break;
                case 'm':
                    strikethrough = true;
                    break;
                case 'n':
                    underline = true;
                    break;
                case 'o':
                    italic = true;
                    break;
                case 'r':
                    reset();
                    break;
                default:
                    color = colorFromCharacter(character);
            }
        }

        private NamedTextColor colorFromCharacter(char character) {
            return switch(character) {
                case '1' -> NamedTextColor.DARK_BLUE;
                case '2' -> NamedTextColor.DARK_GREEN;
                case '3' -> NamedTextColor.DARK_RED;
                case '4' -> NamedTextColor.DARK_AQUA;
                case '5' -> NamedTextColor.DARK_PURPLE;
                case '6' -> NamedTextColor.GOLD;
                case '7' -> NamedTextColor.GRAY;
                case '8' -> NamedTextColor.DARK_GRAY;
                case '9' -> NamedTextColor.BLUE;
                case 'a' -> NamedTextColor.GREEN;
                case 'b' -> NamedTextColor.AQUA;
                case 'c' -> NamedTextColor.RED;
                case 'd' -> NamedTextColor.LIGHT_PURPLE;
                case 'e' -> NamedTextColor.YELLOW;
                case 'f' -> NamedTextColor.WHITE;
                default -> NamedTextColor.BLACK;
            };
        }

        public static boolean isFormatting(char character) {
            return formattingChars.contains(character);
        }

        public static boolean isHexString(String hexString) {
            if(hexString.length() != 7) {
                return false;
            }
            try {
                Integer.parseInt(hexString.substring(1), 16);
                return true;
            } catch(NumberFormatException ex) {
                return false;
            }
        }
    }
}
