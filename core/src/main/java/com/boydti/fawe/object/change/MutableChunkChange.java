package com.boydti.fawe.object.change;

import com.boydti.fawe.Fawe;
import com.boydti.fawe.object.FaweChunk;
import com.boydti.fawe.object.extent.FastWorldEditExtent;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.history.UndoContext;
import com.sk89q.worldedit.history.change.Change;

public class MutableChunkChange implements Change {

    public FaweChunk from;
    public FaweChunk to;

    public MutableChunkChange(FaweChunk from, FaweChunk to) {
        this.from = from;
        this.to = to;
    }

    @Override
    public void undo(UndoContext context) throws WorldEditException {
        create(context, true);
    }

    @Override
    public void redo(UndoContext context) throws WorldEditException {
        create(context, false);
    }

    public void create(UndoContext context, boolean undo) {
        Extent extent = context.getExtent();
        if (extent.getClass() == FastWorldEditExtent.class) {
            FastWorldEditExtent fwee = (FastWorldEditExtent) extent;
            if (undo) {
                fwee.getQueue().setChunk(from);
            } else {
                fwee.getQueue().setChunk(to);
            }
        } else {
            Fawe.debug("FAWE doesn't support: " + context + " for " + getClass());
        }
    }
}
