package copy;
/*
 * This file is part of choco-solver, http://choco-solver.org/
 *
 * Copyright (c) 2019, IMT Atlantique. All rights reserved.
 *
 * Licensed under the BSD 4-clause license.
 *
 * See LICENSE file in the project root for full license information.
 */

import org.chocosolver.util.objects.setDataStructures.ISet;
import org.chocosolver.util.objects.setDataStructures.ISetIterator;
import org.chocosolver.util.objects.setDataStructures.SetType;

import java.util.BitSet;

/**
 * BitSet implementation for a set of integers
 * Supports negative numbers when using int... constructor
 *
 * @author Jean-Guillaume Fages, Xavier Lorca
 */
public class Set_BitSet implements ISet {

    //***********************************************************************************
    // VARIABLES
    //***********************************************************************************

    protected int card;
    protected int offset;  // allow using negative numbers
    protected BitSet values = new BitSet();
    private ISetIterator iter = newIterator();

    //***********************************************************************************
    // ITERATOR
    //***********************************************************************************

    @Override
    public ISetIterator iterator(){
        System.out.println("Iterator");
        iter.reset();
        return iter;
    }

    @Override
    public ISetIterator newIterator(){
        return new ISetIterator() {
            private int current = -1;
            @Override
            public void reset() {
                current = -1;
            }
            @Override
            public boolean hasNext() {
                return values.nextSetBit(current+1) >= 0;
            }
            @Override
            public int nextInt() {
                current = values.nextSetBit(current + 1);
                return current+offset;
            }
        };
    }

    //***********************************************************************************
    // CONSTRUCTOR
    //***********************************************************************************

    /**
     * Creates an empty bitset having numbers greater or equal than <code>offSet</code> (possibly < 0)
     *
     * @param offSet minimum value in the set
     */
    public Set_BitSet(int offSet) {
        super();
        card = 0;
        offset = offSet;
    }

    //***********************************************************************************
    // METHODS
    //***********************************************************************************

    @Override
    public boolean add(int element) {
        if(element < offset) throw new IllegalStateException("Cannot add "+element+" to set of offset "+offset);
        if (values.get(element-offset)) {
            return false;
        }else{
            card++;
            values.set(element-offset);
            return true;
        }
    }

    @Override
    public boolean remove(int element) {
        if(contains(element)) {
            values.clear(element - offset);
            card--;
            return true;
        }else{
            return false;
        }
    }

    @Override
    public boolean contains(int element) {
        return element >= offset && values.get(element - offset);
    }

    @Override
    public int size() {
        return card;
    }

    @Override
    public void clear() {
        card = 0;
        values.clear();
    }

    @Override
    public int min() {
        if(isEmpty()) throw new IllegalStateException("cannot find minimum of an empty set");
        return offset+values.nextSetBit(0);
    }

    @Override
    public int max() {
        if(isEmpty()) throw new IllegalStateException("cannot find maximum of an empty set");
        return offset+values.previousSetBit(values.length());
    }

    @Override
    public SetType getSetType(){
        return SetType.BITSET;
    }

    @Override
    public String toString() {
        StringBuilder st = new StringBuilder("{");
        ISetIterator iter = newIterator();
        while (iter.hasNext()) {
            st.append(iter.nextInt()).append(", ");
        }
        st.append("}");
        return st.toString().replace(", }","}");
    }
}
