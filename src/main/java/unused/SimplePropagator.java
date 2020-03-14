package unused;

import org.chocosolver.solver.constraints.Propagator;
import org.chocosolver.solver.exception.ContradictionException;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.util.ESat;

import java.util.Arrays;

public class SimplePropagator extends Propagator<IntVar> {

    public SimplePropagator(IntVar[] vars) {
        super(vars);
    }

    private int[] getPartitionAllocationMap() throws ContradictionException {
        int[] allottedValues = new int[vars.length];
        Arrays.fill(allottedValues, 0);
        for (IntVar var: this.vars) {
            if(var.isInstantiated()) {
                allottedValues[var.getValue()]++;
                if(allottedValues[var.getValue()] > 3) {
                    throw new ContradictionException().set(this, var, "More than 3 values set to " + var.getValue());
                }
            }
        }
        return allottedValues;
    }

    @Override
    public void propagate(int evtmask) throws ContradictionException {
        try {
            // System.out.println("Propagate");
            int[] allottedValues = getPartitionAllocationMap();

            int varLB = 0;
            for(int i=0; i < allottedValues.length; i++) {
                if(allottedValues[i] < 3)
                {
                    varLB = i;
                    break;
                }
            }

            // System.out.println("varLB: "+ varLB + " count: " + allottedValues[varLB]);
            for (IntVar var : this.vars) {
                if (!var.isInstantiated()) {
                    // System.out.println("Updating lower bound of " + var + " to " + varLB);
                    var.updateLowerBound(varLB, this); }
            }
        } catch (ContradictionException e) {
            System.out.println("Caught exception: " + e);
            throw e;
        }
    }

    @Override
    public ESat isEntailed() {
        // System.out.println("Checking isEntailed?");
        try {
            int[] allottedValues = getPartitionAllocationMap();

            for (int val : allottedValues) {
                if (val < 3)
                    return ESat.UNDEFINED;
            }

            return ESat.TRUE;
        } catch (ContradictionException e) {
            System.out.println("Not Entailed");
            return ESat.FALSE;
        }
    }
}
