package unused;

import org.chocosolver.solver.constraints.Propagator;
import org.chocosolver.solver.exception.ContradictionException;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.util.ESat;

import java.util.Arrays;

public class FullLBPropagator extends Propagator<IntVar> {

    int maxLB;
    public FullLBPropagator(IntVar[] vars) {
        super(vars);
        maxLB = vars.length / 3;
    }

    private IntVar[] getVarsWithValue(int val) {
        return Arrays.stream(vars).filter(v -> v.isInstantiated() && v.getValue() == val).toArray(IntVar[]::new);
    }

    @Override
    public void propagate(int evtmask) throws ContradictionException {
        int newLB = 0;
        while (newLB < maxLB) {
            IntVar[] intVarsWithLB = getVarsWithValue(newLB);
            if (intVarsWithLB.length > 3) {
                throw new ContradictionException().set(this, intVarsWithLB[3], "More than 3 values set to " + intVarsWithLB[3].getValue());
            } else if (intVarsWithLB.length == 3){
                newLB++;
            } else {
                break;
            }
        }

        // System.out.println("varLB: "+ varLB + " count: " + allottedValues[varLB]);
        for (IntVar var : this.vars) {
            if (!var.isInstantiated()) {
                // System.out.println("Updating lower bound of " + var + " to " + varLB);
                var.updateLowerBound(newLB, this); }
        }
    }

    @Override
    public ESat isEntailed() {
        // System.out.println("Checking isEntailed?");
        try {
            for(int val = 0; val < maxLB; val++) {
                IntVar[] intVarsWithLB = getVarsWithValue(val);
                if (intVarsWithLB.length > 3) {
                    throw new ContradictionException().set(this, intVarsWithLB[3], "More than 3 values set to " + intVarsWithLB[3].getValue());
                } else if (intVarsWithLB.length < 3){
                    return ESat.UNDEFINED;
                }
            }

            return ESat.TRUE;
        } catch (ContradictionException e) {
            System.out.println("Not Entailed");
            return ESat.FALSE;
        }
    }
}
