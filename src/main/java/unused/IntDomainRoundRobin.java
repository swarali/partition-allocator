package unused;

import org.chocosolver.solver.search.strategy.selectors.values.IntValueSelector;
import org.chocosolver.solver.variables.IntVar;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class IntDomainRoundRobin implements IntValueSelector {
    List<Integer> domainList;

    public IntDomainRoundRobin(int domain) {
        domainList = IntStream.range(0, domain).boxed().collect(Collectors.toList());
    }
    @Override
    public int selectValue(IntVar var) {
        int value = 0;
        int index = 0;
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
