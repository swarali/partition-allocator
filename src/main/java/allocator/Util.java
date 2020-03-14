package allocator;

import org.apache.commons.lang3.SerializationUtils;
import org.chocosolver.solver.variables.Variable;
import org.chocosolver.util.objects.setDataStructures.ISet;
import org.chocosolver.util.objects.setDataStructures.ISetIterator;

import java.io.Serializable;
import java.util.*;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class Util {

    public static List<Integer> arrayToList(int[] array) {
        return Arrays.stream(array).boxed().collect(Collectors.toList());
    }

    public static int[] listToArray(List<Integer> list) {
        return list.stream().mapToInt(i -> i).toArray();
    }

    private static void appendRange(StringBuilder sb, int begin, int end) {
        sb.append(", ").append(begin);
        if (end != begin)
            sb.append("-").append(end);
    }

    public static <T extends Iterable<Integer>> String sequenceNum(T numbers) {
        StringBuilder sb = new StringBuilder();
        Iterator<Integer> iterator = numbers.iterator();
        if (!iterator.hasNext()) return sb.toString();
        int begin = numbers.iterator().next(); int end = begin;
        for (int cur: numbers) {
            if (cur == end || cur == end + 1)
                end = cur;
            else {
                appendRange(sb, begin, end);
                begin = end = cur;
            }
        }
        appendRange(sb, begin, end);
        return sb.substring(1);
    }

    public static String sequenceNum(int[] numbers) {
        return sequenceNum(arrayToList(numbers));
    }

    public static <V extends Serializable> V deepCopy(V v) {
        return SerializationUtils.clone(v);
    }

    public static <K, V> Map<K, V> deepCopy(Map<K, V> m) {
        return m.entrySet().stream()
                .map(Util.mapMapValue(v ->{
                    if(v instanceof Iterable) {
                        return (V) deepCopy((Iterable) v);
                    } else if(v instanceof Map){
                        return (V) deepCopy((Map)v);
                    } else {
                        return v;
                    }
                }))
                .collect(Util.toMap());
    }

    public static <V> Iterable<V> deepCopy(Iterable<V> l) {
        List<V> newL = new LinkedList<>();

        l.forEach(v -> {
            if(v instanceof Iterable) {
                newL.add((V) deepCopy((Iterable)v));
            } else {
                newL.add(v);
            }
        });
        return newL;
    }

    public static Set<Integer> isetToSet(ISet iSet) {
        ISetIterator iterator = iSet.newIterator();
        Set<Integer> isetValues = new LinkedHashSet<>();
        while(iterator.hasNext()) {
            isetValues.add(iterator.next());
        }
        return isetValues;
    }
    /* Helper functions to work on streams */
    public static <K, V> Collector<Map.Entry<K, V>, ?, Map<K,V>> toMap() {
        return Collectors.toMap(Map.Entry<K, V>::getKey, Map.Entry<K, V>::getValue, (v1, v2) -> v1,
                LinkedHashMap::new);
    }

    public static <K, V> Stream<Map.Entry<K, V>> flatMapEntry(Map.Entry<K, List<V>> entry) {
        return entry.getValue().stream().map(v -> Map.entry(entry.getKey(), v));
    }

    public static <K1, K2, V1, V2> Function<Map.Entry<K1, V1>, Map.Entry<K2, V2>> mapMapEntry(Function<K1, K2> mapper1, Function<V1, V2> mapper2) {
        return entry -> Map.entry(mapper1.apply(entry.getKey()), mapper2.apply(entry.getValue()));
    }

    public static <K1, K2, V> Function<Map.Entry<K1, V>, Map.Entry<K2, V>> mapMapKey(Function<K1, K2> mapper) {
        return entry -> Map.entry(mapper.apply(entry.getKey()), entry.getValue());
    }

    public static <K1, V1, V2> Function<Map.Entry<K1, V1>, Map.Entry<K1, V2>> mapMapValue(Function<V1, V2> mapper) {
        return new Function<Map.Entry<K1, V1>, Map.Entry<K1, V2>>() {
            @Override
            public Map.Entry<K1, V2> apply(Map.Entry<K1, V1> k1V1Entry) {
                return Map.entry(k1V1Entry.getKey(), mapper.apply(k1V1Entry.getValue()));
            }
        };
    }

    public static <K1, V1, V2> Function<Map.Entry<K1, List<V1>>, Map.Entry<K1, List<V2>>> mapMapValueList(Function<V1, V2> mapper) {
        return entry -> Map.entry(entry.getKey(), entry.getValue().stream().map(mapper).collect(Collectors.toList()));
    }

    public static <T, K> Collector<Map.Entry<K, T>, ?, Map<K, List<T>>> groupByEntryKeyList() {
        return Collectors.groupingBy(Map.Entry::getKey, LinkedHashMap::new, Collectors.mapping(Map.Entry<K, T>::getValue, Collectors.toList()));
    }

    public static <T, K> Collector<Map.Entry<K, T>, ?, Map<T, List<K>>> groupByEntryValList() {
        return Collectors.groupingBy(Map.Entry::getValue, LinkedHashMap::new, Collectors.mapping(Map.Entry<K, T>::getKey, Collectors.toList()));
    }

    public static <T, C extends Collection<T>> Collection<T> intersect(Stream<C> stream) {
        final Iterator<C> allLists = stream.iterator();

        if (!allLists.hasNext()) return Collections.emptySet();

        final Set<T> result = new HashSet<>(allLists.next());
        while (allLists.hasNext()) {
            result.retainAll(new HashSet<>(allLists.next()));
        }
        return result;
    }

    public static <V> List<V> getOrCreateList(List<List<V>> list, int index) {

        while(index >= list.size()) {
            list.add(new ArrayList<>());
        }
        List<V> valueList = list.get(index);

        return valueList;
    }

    public static <T, V> List<V> getOrCreateList(Map<T, List<V>> map, T key) {
        List<V> valueList = map.getOrDefault(key, new ArrayList());

        if(valueList.isEmpty()) {
            map.put(key, valueList);
        }
        return valueList;
    }

    public static <T, K, V>  Map<K, V> getOrCreateMap(Map<T, Map<K, V>> map, T key) {
        Map<K, V> valueMap = map.getOrDefault(key, new LinkedHashMap<>());
        if(valueMap.isEmpty()) {
            map.put(key, valueMap);
        }
        return valueMap;
    }

    public static <T, V>  Set<V> getOrCreateSet(Map<T, Set<V>> map, T key) {
        Set<V> valueSet = map.getOrDefault(key, new LinkedHashSet<>());
        if(valueSet.isEmpty()) {
            map.put(key, valueSet);
        }
        return valueSet;
    }

    public static String toPrettyVal(Variable var) {
        if(var.isInstantiated()) {
            switch (var.getTypeAndKind() & Variable.KIND) {
                case Variable.INT:
                    return Integer.toString(var.asIntVar().getValue());
                case Variable.SET:
                    ISet setValue = var.asSetVar().getValue();
                    return sequenceNum(setValue) + "[" + setValue.size() + "]";
                default:
                    throw new IllegalStateException("Unexpected value: " + var + (var.getTypeAndKind() & Variable.KIND));
            }
        } else {
            switch (var.getTypeAndKind() & Variable.KIND) {
                case Variable.INT:
                    return var.asIntVar().getLB() + ":" + var.asIntVar().getUB();
                case Variable.SET:
                    ISet setValueLB = var.asSetVar().getLB();
                    ISet setValueUB = var.asSetVar().getUB();
                    return sequenceNum(setValueLB) + "[" + setValueLB.size() + "]:" + sequenceNum(setValueUB) + "[" + setValueUB.size() + "]";
                default:
                    throw new IllegalStateException("Unexpected value: " + (var.getTypeAndKind() & Variable.KIND));
            }
        }
    }

    public static Map<String, String> toPrettyValMap(Iterable<? extends Variable> vars) {
        Map<String, String> initialisedValues = new LinkedHashMap<String, String>();

        for(Variable var: vars) {
            String val = toPrettyVal(var);
            initialisedValues.put(var.getName(), val);
        }
        return initialisedValues;
    }

    public static Map<String, String> toPrettyValMap(Variable var) {
        ArrayList<Variable> list = new ArrayList<>();
        list.add(var);
        return toPrettyValMap(list);
    }
}
