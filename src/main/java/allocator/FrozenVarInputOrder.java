package allocator;

import org.chocosolver.solver.Model;
import org.chocosolver.solver.search.strategy.selectors.variables.InputOrder;
import org.chocosolver.solver.variables.SetVar;
import topology.Disk;

import java.util.Arrays;
import java.util.List;

public class FrozenVarInputOrder extends InputOrder<SetVar> {

    final List<Disk> frozenVars;
    int frozenVarIdx;
    int idx;

    /**
     * Input Order on frozenVars first and then on the rest
     * @param model reference to the model (does not define the variable scope)
     */
    public FrozenVarInputOrder(Model model, List<Disk> frozenVars) {
        super(model);
        this.frozenVars = frozenVars;
        this.frozenVarIdx = 0;
        this.idx = 0;
    }

    @Override
    public SetVar getVariable(SetVar[] variables) {
        for (int idx = frozenVarIdx; idx < frozenVars.size(); idx++) {
            Disk disk = frozenVars.get(idx);
            SetVar s = Arrays.stream(variables).filter(v -> v.getName().equals(disk.name)).findAny().orElse(null);
            if(s == null) return null;
            if (!s.isInstantiated()) {
                frozenVarIdx = idx;
                //System.out.println(s.getCard().getUB() != s.getCard().getLB());
                //System.out.println("Selecting f-var: " + s.getName() + ":"
                //        + s.getCard().getLB() + "/" + s.getCard().getUB() + " | "
                //        + s.getLB().size() + "/" + s.getUB().size() + "-" + s.getCard().isInstantiated());
                return s;
            } else {
                //System.out.println(s.getName() + ": " + Util.toPrettyVal(s));
            }
        }
        frozenVarIdx = frozenVars.size();

        for(int i = idx; i < variables.length; i++) {
            SetVar s = variables[i];
            if (!s.isInstantiated()) {
                idx = i;
                //System.out.println(s.getCard().getUB() != s.getCard().getLB());
                //System.out.println("Selecting var: " + s.getName() + ":"
                //        + s.getCard().getLB() + "/" + s.getCard().getUB() + " | "
                //        + s.getLB().size() + "/" + s.getUB().size() + "-" + s.getCard().isInstantiated());
                return s;
            } else {
                //System.out.println(s.getName() + ": " + Util.toPrettyVal(s));
            }
        }
        idx = variables.length;
        return null;
        //return super.getVariable(variables);
    }
}
