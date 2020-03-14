package unused;

import org.chocosolver.solver.search.strategy.selectors.values.SetValueSelector;
import org.chocosolver.solver.variables.SetVar;

import java.util.*;
import java.util.stream.Collectors;

public class SetDomainRandom implements SetValueSelector {

    private final Random rand;
    Map<SetVar, List<Integer>> valueMap;

    public SetDomainRandom(long seed) {
        this.rand = new Random(seed);
        this.valueMap = new HashMap<>();
    }

    @Override
    public int selectValue(SetVar v) {
        if(!valueMap.containsKey(v)) {
            List<Integer> values = Arrays.stream(v.getUB().toArray()).filter(a -> !v.getLB().contains(a)).boxed().collect(Collectors.toList());
            valueMap.put(v, values);
        }
        List<Integer> values = valueMap.get(v);
        int i = rand.nextInt(values.size());
        Integer value = values.get(i);
        values.remove(value);
        values.add(value);
        System.out.println("Returning " + value + " from " + values + " for" + v);
        return value;
    }
}
