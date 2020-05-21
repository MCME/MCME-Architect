/*
 * Copyright (C) 2019 Eriol_Eandur
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
package com.mcmiddleearth.architect.copyPaste;

import com.mcmiddleearth.pluginutil.plotStoring.IStoragePlot;

/**
 *
 * @author Eriol_Eandur
 */
public class UndoData extends Clipboard {
    
    public UndoData(IStoragePlot plot) throws CopyPasteException{
        super(plot.getLowCorner().clone(),plot.getLowCorner().clone(),plot.getHighCorner().clone());
        if(!super.copyToClipboard()) {
            throw new CopyPasteException("Undo data creation failed.");
        }
    }
    
    public boolean undo() {
        rotation = 0;
        return paste(getLowCorner().clone(), true, true);
    }
}
