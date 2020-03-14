package allocator;

import org.chocosolver.solver.Model;
import org.chocosolver.solver.exception.ContradictionException;
import org.chocosolver.solver.search.loop.monitors.IMonitorRestart;
import org.chocosolver.solver.variables.IVariableMonitor;
import org.chocosolver.solver.variables.SetVar;
import org.chocosolver.solver.variables.Variable;
import org.chocosolver.solver.variables.events.IEventType;
import org.chocosolver.util.objects.setDataStructures.ISetIterator;

import java.util.*;

class VariableMonitor implements IVariableMonitor<SetVar> {

    Set<Integer> lastLB;
    Set<Integer> lastUB;

    static Map<SetVar, VariableMonitor> variableMonitorMap = new LinkedHashMap<>();

    public VariableMonitor() {
        lastLB = new LinkedHashSet<>();
        lastUB = new LinkedHashSet<>();
    }

    @Override
    public void onUpdate(SetVar s, IEventType evt) {

        Set<Integer> newLB = Util.isetToSet(s.getLB());

        Set<Integer> addedLB = new LinkedHashSet<>(newLB);
        Set<Integer> removedLB = new LinkedHashSet<>(lastLB);
        addedLB.removeAll(lastLB);
        removedLB.removeAll(newLB);

        Set<Integer> newUB = Util.isetToSet(s.getUB());

        Set<Integer> addedUB = new LinkedHashSet<>(newUB);
        Set<Integer> removedUB = new LinkedHashSet<>(lastUB);
        addedUB.removeAll(lastUB);
        removedUB.removeAll(newUB);

        if(addedLB.isEmpty() && removedLB.isEmpty() && addedUB.isEmpty()) return;

        System.out.format("%s: +[%s(%d) / %s(%d)] | -[%s(%d) / %s(%d)] (%d/%d) (%d/%d) updated due to %s\n",
                s.getName(), Util.sequenceNum(addedLB), addedLB.size(), Util.sequenceNum(addedUB), addedUB.size(),
                Util.sequenceNum(removedLB), removedLB.size(), Util.sequenceNum(removedUB), removedUB.size(),
                s.getCard().getLB(), s.getCard().getUB(), newLB.size(), newUB.size(), evt);

        lastLB = newLB;
        lastUB = newUB;
    }

    public static void logOnUpdate(List< ? extends Variable> variableList) {

        for(Variable v: variableList) {
            try {
                SetVar var = v.asSetVar();
                VariableMonitor variableMonitor = new VariableMonitor();
                var.addMonitor(variableMonitor);
                variableMonitorMap.put(var, variableMonitor);
            } catch (ClassCastException ex) {
                v.addMonitor(new IVariableMonitor() {
                    @Override
                    public void onUpdate(Variable var, IEventType evt) throws ContradictionException {
                        System.out.println("Var " + Util.toPrettyVal(v) + " updated due to " + evt);
                    }
                });
            }
        }

        Model model = variableList.get(0).getModel();
        model.getSolver().plugMonitor(new IMonitorRestart() {
            @Override
            public void beforeRestart() {

                variableMonitorMap.forEach((s, variableMonitor) -> {
                    variableMonitor.lastLB.clear();
                    variableMonitor.lastUB.clear();
                });
            }
        });


    }
}

