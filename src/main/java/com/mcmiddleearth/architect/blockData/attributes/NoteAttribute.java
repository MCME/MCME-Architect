/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mcmiddleearth.architect.blockData.attributes;

import java.util.HashSet;
import java.util.Set;
import org.bukkit.Note;
import org.bukkit.Note.Tone;
import org.bukkit.block.data.type.NoteBlock;
import org.bukkit.configuration.ConfigurationSection;

/**
 *
 * @author Eriol_Eandur
 */
public class NoteAttribute extends Attribute {

    public NoteAttribute(String name) {
        super(name, NoteBlock.class);
    }

    @Override
    public void cycleState() {
        if(blockData instanceof NoteBlock) {
            NoteBlock noteBlockData = (NoteBlock) blockData;
            Note note = noteBlockData.getNote();
            boolean newSharped = false;
            int newOctave = note.getOctave();
            Tone newTone = note.getTone();
            boolean found = false;
            for(Tone search: Tone.values()) {
                if(found) {
                    newTone = search;
                    found = false;
                    break;
                }
                if(search.equals(note.getTone())) {
                    if(!note.isSharped() && note.getTone().isSharpable()) {
                        newSharped = true;
                        if(newTone.equals(Tone.F)) {
                            newOctave++;
                        }
                        break;
                    } else {
                        found = true;
                    }
                }
            }
            if(found) {
                newTone = Tone.values()[0];
            }
            if(newOctave==2 && newTone.equals(Tone.G)) {
                newOctave = 0;
                newSharped = true;
                newTone = Tone.F;
            }
            noteBlockData.setNote(new Note(newOctave, newTone, newSharped));
        }
    }
    
    @Override
    public int countSubAttributes() {
        return 1;
    }

    @Override
    public int countStates() {
        return 25;
    }
    
    @Override
    public String getState() {
        if(!(blockData instanceof NoteBlock)) {
            return null;
        }
        Note note = ((NoteBlock)blockData).getNote();
        //return note.getTone().name()+note.getOctave()+(note.isSharped()?"#":"");
        return ""+getId(note);
    }

    @Override
    public void setState(Object newValue) {
        if((blockData instanceof NoteBlock) && (newValue instanceof Note)) {
            ((NoteBlock)blockData).setNote((Note)newValue);
        }
    }

    @Override
    public void saveToConfig(ConfigurationSection config) {
        if(blockData instanceof NoteBlock) {
            config.set(name, getState());
        }
    }
    
    @Override
    public void loadFromConfig(ConfigurationSection config) {
        /*String input = config.getString(name);
        boolean sharp = input.length()>2 && input.charAt(2)=='#';
        int octave = input.charAt(1);
        Note.Tone tone = Note.Tone.valueOf(""+input.charAt(0));*/
        int input = config.getInt(name);
        int octave = input / 12;
        Tone tone = getTone(input%12);
        boolean sharp = getSharp(input%12);
        Note note = new Note(octave, tone, sharp);
        setState(note);
        
    }
    
    private boolean getSharp(int id) {
        return (id==0) || (id==2) || (id==4) || (id==7) || (id==8);
    }
    
    private Tone getTone(int id){
        switch(id) {
            case 11:
            case 0: 
                return Tone.F;
            case 1:
            case 2:
                return Tone.G;
            case 3:
            case 4:
                return Tone.A;
            case 5:
                return Tone.B;
            case 6:
            case 7:
                return Tone.C;
            case 8:
            case 9:
                return Tone.D;
            case 10:
                return Tone.E;
            default: return null;
        }
    }
    
    private int getId(Note note) {
        int id = 0;
        switch(note.getTone()) {
            case F:
                if(note.isSharped()) id=-1;
                else id = 11; break;
            case G:
                id = 1; break;
            case A:
                id = 3; break;
            case B:
                id = 5; break;
            case C:
                id = 6; break;
            case D:
                id = 8; break;
            case E:
                id = 10; break;
        }
        if(note.isSharped()) id++;
        return id + note.getOctave() * 12;
    }
    /*private static final Set<Integer> sharpIds = new HashSet<>();

    static{
        sharpIds.add(0);
        sharpIds.add(2);
        sharpIds.add(4);
        sharpIds.add(7);
        sharpIds.add(9);
    }*/

}
