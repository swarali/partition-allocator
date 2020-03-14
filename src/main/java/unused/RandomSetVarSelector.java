package unused;

import org.chocosolver.solver.search.strategy.selectors.variables.RandomVar;
import org.chocosolver.solver.search.strategy.selectors.variables.VariableSelector;
import org.chocosolver.solver.variables.SetVar;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RandomSetVarSelector implements VariableSelector<SetVar> {

    private java.util.Random random;

    public RandomSetVarSelector(long seed) {
        random = new java.util.Random(seed);
    }

    @Override
    public SetVar getVariable(SetVar[] variables) {
        List<Integer> sets = new ArrayList();
        for (int idx = 0; idx < variables.length; idx++) {
            if (!variables[idx].isInstantiated()) {
                sets.add(idx);
            }
        }
        if (sets.size() > 0) {
            int rand_idx = sets.get(random.nextInt(sets.size()));
            //System.out.println("Selecting variable: " + variables[rand_idx].getName());
            return variables[rand_idx];
        } else return null;
    }
}

