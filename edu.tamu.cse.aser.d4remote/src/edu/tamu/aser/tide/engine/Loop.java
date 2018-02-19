package edu.tamu.aser.tide.engine;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import com.ibm.wala.ssa.SSACFG.BasicBlock;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.util.graph.Graph;

public class Loop {

    protected final BasicBlock header;
    protected final BasicBlock backJump;
    protected final List<BasicBlock> loopBlocks;
    protected List<SSAInstruction> loopInstructions;

    protected final Graph g;
    protected Collection<BasicBlock> loopExists;

    /**
     * Creates a new loop. Expects that the last statement in the list is the loop head
     * and the second-last statement is the back-jump to the head. {@link LoopFinder} will
     * normally guarantee this.
     * @param head the loop header
     * @param loopBlocks an ordered list of loop statements, ending with the header
     * @param g the unit graph according to which the loop exists
     */
    Loop(BasicBlock head, List<BasicBlock> loopBlocks, Graph g) {
        this.header = head;
        this.g = g;

        //put header to the top
        loopBlocks.remove(head);
        loopBlocks.add(0, head);

        //last statement
        this.backJump = loopBlocks.get(loopBlocks.size()-1);

        assert g.hasEdge(this.backJump,head); //must branch back to the head

        this.loopBlocks = loopBlocks;
    }

    /**
     * @return the loop head
     */
    public BasicBlock getHead() {
        return header;
    }

    /**
     * Returns the statement that jumps back to the head, thereby constituing the loop.
     */
    public BasicBlock getBackJumpBlock() {
        return backJump;
    }

    /**
     * @return all statements of the loop, including the header;
     * the header will be the first element returned and then the
     * other statements follow in the natural ordering of the loop
     */
    public List<SSAInstruction> getLoopInstructions() {
    	if(loopInstructions==null)
    	{
    		loopInstructions = new ArrayList<SSAInstruction>();
            for(BasicBlock iblock: loopBlocks)
            {
            	loopInstructions.addAll((iblock).getAllInstructions());
            }
    	}
        return loopInstructions;
    }

    /**
     * Returns all loop exists.
     * A loop exit is a statement which has a successor that is not contained in the loop.
     */
    public Collection<BasicBlock> getLoopExits() {
        if(loopExists==null) {
            loopExists = new HashSet<BasicBlock>();
            for (BasicBlock s : loopBlocks) {
            	Iterator<BasicBlock> succs =  g.getSuccNodes(s);
                while (succs.hasNext()) {
                    if(!loopBlocks.contains(succs.next())) {
                        loopExists.add(s);
                    }
                }
            }
        }
        return loopExists;
    }

    /**
     * Computes all targets of the given loop exit, i.e. statements that the exit jumps to but which are not
     * part of this loop.
     */
    public Collection<SSAInstruction> targetsOfLoopExit(SSAInstruction loopExit) {
        assert getLoopExits().contains(loopExit);
        Iterator<SSAInstruction> succs =  g.getSuccNodes(loopExit);
        Collection<SSAInstruction> res = new HashSet<SSAInstruction>();
        while (succs.hasNext()) {
            SSAInstruction s = succs.next();
            res.add(s);
        }
        res.removeAll(loopBlocks);
        return res;
    }

    /**
     * Returns <code>true</code> if this loop certainly loops forever, i.e. if it has not exit.
     * @see #getLoopExits()
     */
    public boolean loopsForever() {
        return getLoopExits().isEmpty();
    }

    /**
     * Returns <code>true</code> if this loop has a single exit statement.
     * @see #getLoopExits()
     */
    public boolean hasSingleExit() {
        return getLoopExits().size()==1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((header == null) ? 0 : header.hashCode());
        result = prime * result
                + ((loopBlocks == null) ? 0 : loopBlocks.hashCode());
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final Loop other = (Loop) obj;
        if (header == null) {
            if (other.header != null)
                return false;
        } else if (!header.equals(other.header))
            return false;
        if (loopBlocks == null) {
            if (other.loopBlocks != null)
                return false;
        } else if (!loopBlocks.equals(other.loopBlocks))
            return false;
        return true;
    }

}