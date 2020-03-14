package unused;

import org.chocosolver.solver.search.strategy.selectors.values.SetValueSelector;
import org.chocosolver.solver.variables.SetVar;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class SetDomainRoundRobin implements SetValueSelector {
    List<Integer> domainList;

    public SetDomainRoundRobin(int[] domain) {
        domainList = Arrays.stream(domain).boxed().collect(Collectors.toList());
        Collections.shuffle(domainList);
    }

    @Override
    public int selectValue(SetVar var) {
        int value =0;
        int index =0;
        for(Integer i: domainList) {
            if(var.getUB().contains(i) && !var.getLB().contains(i)) {
                value = i;
                break;
            }
            index++;
        }
        if(index == domainList.size()) {
            System.out.println("Index outside domainList:" + domainList + ", var: " + var );
        }
        domainList.remove(index);
        domainList.add(value);
        return value;
    }
}
