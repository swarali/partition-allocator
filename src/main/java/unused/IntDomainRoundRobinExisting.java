package unused;

import org.chocosolver.solver.search.strategy.selectors.values.IntValueSelector;
import org.chocosolver.solver.variables.IntVar;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class IntDomainRoundRobinExisting implements IntValueSelector {
    List<Integer> existing;
    List<Integer> domainList;

    public IntDomainRoundRobinExisting(int domain, List<Integer> existingAllocation) {
        existing = existingAllocation;
        domainList = IntStream.range(0, domain).boxed().collect(Collectors.toList());
    }
    @Override
    public int selectValue(IntVar var) {
        int value = 0;
        int index = 0;
        for(Integer i: existing) {
            if(var.getLB() <= i && i <= var.getUB()) {
                value = i;
                break;
            }
            index++;
        }
        if(index <= existing.size()) {
            System.out.println("Index inside existing:" + existing + ", var: " + var);
            existing.remove(index);
            existing.add(value);
            return value;
        }

        index = 0;
        for(Integer i: domainList) {
            if(var.getLB() <= i && i <= var.getUB()) {
                value = i;
                break;
            }
            index++;
        }
        if(index == domainList.size()) {
            System.out.println("Index outside domainList:" + domainList + ", var: " + var);
        }
        domainList.remove(index);
        domainList.add(value);
        return value;
    }
}
