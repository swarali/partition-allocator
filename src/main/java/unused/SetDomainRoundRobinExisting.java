package unused;

import allocator.Allocator;
import org.chocosolver.solver.search.strategy.selectors.values.SetValueSelector;
import org.chocosolver.solver.variables.SetVar;
import topology.Disk;

import java.util.*;

public class SetDomainRoundRobinExisting implements SetValueSelector {
    final Random rd;
    final Allocator allocator;
    final List<Integer> domain;


    long restartCount;
    List<Integer> existing;
    List<Integer> shuffledDomain;

    public SetDomainRoundRobinExisting(List<Integer> d, List<Integer> existingValues, Allocator a) {
        rd = new Random();
        allocator = a;
        existing = existingValues;
        domain = d;

        restartCount = -1;
    }

    public List<Integer> getShuffledDomain() {
        List<Integer> dList = new ArrayList<>(domain);
        Collections.shuffle(dList, rd);
        //System.out.println("Shuffling domain from: " + domain + " to " + dList);
        return dList;
    }

    @Override
    public int selectValue(SetVar var) {
        int value = 0;
        int index = 0;
        for(Integer i: existing) {
            if(var.getUB().contains(i) && !var.getLB().contains(i)) {
                value = i;
                break;
            }
            index++;
        }
        if(index < existing.size()) {
            System.out.println("Index inside existing:" + existing + ", var: " + var + ", index: " + index);
            existing.remove(index);
            existing.add(value);
            return value;
        }

        if(allocator.model.getSolver().getRestartCount() != restartCount) {
            shuffledDomain = getShuffledDomain();
            restartCount = allocator.model.getSolver().getRestartCount();
            for(SetVar v: allocator.latestSolution.retrieveSetVars()) {
                for(int val: allocator.latestSolution.getSetVal(v)) {
                    index = shuffledDomain.indexOf(val);
                    shuffledDomain.remove(index);
                    shuffledDomain.add(val);
                }
            }
        }

        index =0;
        for(Integer i: shuffledDomain) {
            if(var.getUB().contains(i) && !var.getLB().contains(i)) {
                value = i;
                break;
            }
            index++;
        }
        if(index == shuffledDomain.size()) {
            System.out.println("Index outside domainList:" + shuffledDomain + ", var: " + var );
        }
        shuffledDomain.remove(index);
        shuffledDomain.add(value);
        return value;
    }
}
