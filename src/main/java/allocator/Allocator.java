package allocator;

import org.apache.commons.cli.*;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.util.Combinations;
import org.chocosolver.memory.ICondition;
import org.chocosolver.solver.*;
import org.chocosolver.solver.constraints.Constraint;
import org.chocosolver.solver.exception.ContradictionException;
import org.chocosolver.solver.search.loop.monitors.*;
import org.chocosolver.solver.search.strategy.Search;
import org.chocosolver.solver.search.strategy.selectors.values.SetDomainMin;
import org.chocosolver.solver.search.strategy.selectors.values.SetValueSelector;
import org.chocosolver.solver.search.strategy.selectors.variables.VariableSelector;
import org.chocosolver.solver.search.strategy.strategy.SetStrategy;
import org.chocosolver.solver.trace.IMessage;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.solver.variables.SetVar;
import org.chocosolver.solver.variables.Variable;
import org.chocosolver.util.criteria.Criterion;
import org.testng.util.Strings;
import topology.*;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Allocator {

    static final int[] EMPTY_VALUES = new int[0];

    final public Context context;

    final public int[] partitions;

    final public Model model;
    final public SetVar zoneVar;
    final public List<SetVar> rackVarList;
    final public List<SetVar> chassisVarList;
    final public List<SetVar> hostVarList;
    final public List<SetVar> diskVarList;
    final public IntVar objective;

    final public Solution latestSolution;

    static public List<Disk> frozenVars = new ArrayList<>();;

    Allocation latestAllocation;
    int latestObjective;
    int overlap;
    int maxDisjointLevel;

    boolean shouldStop = false;

    public Allocator(Context context, Allocation latestAllocation, int latestObjective, int overlap, int maxDisjointLevel) {

        this.context = context;
        Zone zone = context.zone;
        Allocation allocation = context.allocation;

        this.latestAllocation = latestAllocation;
        this.latestObjective = latestObjective;
        this.overlap = overlap;
        this.maxDisjointLevel = maxDisjointLevel;

        // Create model, variables and constraints
        this.partitions = IntStream.range(0, allocation.partitionList.size()).toArray();

        boolean zoneDisjoint = (Zone.LEVEL > maxDisjointLevel);
        boolean racksInZoneDisjoint = (Rack.LEVEL > maxDisjointLevel);
        boolean chassisInRackDisjoint = (Chassis.LEVEL > maxDisjointLevel);
        boolean hostsInChassisDisjoint = (Host.LEVEL > maxDisjointLevel);
        boolean disksInHostDisjoint = (Disk.LEVEL > maxDisjointLevel);
        boolean locationsInDiskDisjoint = (Location.LEVEL > maxDisjointLevel);

        if(zoneDisjoint) {
            System.out.println("Zone cannot be disjoint due partition replication");
            System.exit(1);
        }
        if(!locationsInDiskDisjoint) {
            System.err.println("Locations in the disks must always be disjoint");
            System.exit(1);
        }

        model = new Model(zone.name);
        // Do not record history. Required to speed-up the allocation.
        model.getSettings().setEnvironmentHistorySimulationCondition(ICondition.FALSE);

        rackVarList = new ArrayList<>();
        chassisVarList = new ArrayList<>();
        hostVarList = new ArrayList<>();
        diskVarList = new ArrayList<>();

        zoneVar = model.setVar(zone.name, EMPTY_VALUES, partitions);
        IntVar zoneVarCapacity = zoneVar.getCard();
        model.arithm(zoneVarCapacity, "<=", zone.capacity).post();

        SetVar[] rackVars = zone.rackList.stream()
                .map(r -> model.setVar(r.name, EMPTY_VALUES, partitions)).toArray(SetVar[]::new);
        if(racksInZoneDisjoint) {
            model.partition(rackVars, zoneVar).post();
        } else {
            model.union(rackVars, zoneVar).post();
        }
        rackVarList.addAll(Arrays.asList(rackVars));
        for(Rack rack: zone.rackList) {
            SetVar rackVar = rackVars[rack.index];
            IntVar rackCapacityVar = rackVar.getCard();
            model.arithm(rackCapacityVar, "<=", rack.capacity).post();

            SetVar[] chassisVars = rack.chassisList.stream()
                    .map(c -> model.setVar(c.name, EMPTY_VALUES, partitions)).toArray(SetVar[]::new);
            if (chassisInRackDisjoint) {
                model.partition(chassisVars, rackVar).post();
            } else {
                model.union(chassisVars, rackVar).post();
            }
            chassisVarList.addAll(Arrays.asList(chassisVars));
            for(Chassis chassis: rack.chassisList) {
                SetVar chassisVar = chassisVars[chassis.index];
                IntVar chassisCapacityVar = chassisVar.getCard();
                model.arithm(chassisCapacityVar, "<=", chassis.capacity).post();

                SetVar[] hostVars = chassis.hostList.stream()
                        .map(h -> model.setVar(h.name, EMPTY_VALUES, partitions)).toArray(SetVar[]::new);
                if(hostsInChassisDisjoint) {
                    model.partition(hostVars, chassisVar).post();
                } else {
                    model.union(hostVars, chassisVar).post();
                }
                hostVarList.addAll(Arrays.asList(hostVars));
                for(Host host: chassis.hostList) {
                    SetVar hostVar = hostVars[host.index];
                    IntVar hostCapacityVar = hostVar.getCard();
                    model.arithm(hostCapacityVar, "<=", host.capacity).post();

                    SetVar[] diskVars = host.diskList.stream()
                            .map(d -> model.setVar(d.name, EMPTY_VALUES, partitions)).toArray(SetVar[]::new);
                    if(disksInHostDisjoint) {
                        model.partition(diskVars, hostVar).post();
                    } else {
                        model.union(diskVars, hostVar).post();
                    }
                    diskVarList.addAll(Arrays.asList(diskVars));
                    for(Disk disk: host.diskList) {
                        SetVar diskVar = diskVars[disk.index];
                        IntVar diskCapacityVar = diskVar.getCard();
                        model.arithm(diskCapacityVar, "<=", disk.capacity).post();
                    }
                }
            }
        }


        SetVar[] diskVars = diskVarList.toArray(SetVar[]::new);

        /*
        int[] disks = IntStream.range(0, diskVars.length).toArray();
        SetVar[] partitionVarArray = model.setVarArray("P", partitions.length, EMPTY_VALUES, disks);
        partitionList = Arrays.asList(partitionVarArray);
        model.inverseSet(partitionVarArray, diskVars, 0, 0).post();

        for(SetVar partition: partitionList) {
            IntVar partitionCard = partition.getCard();
            model.arithm(partitionCard, "<=", Context.REPLICATION_FACTOR).post();
        }
         */

        // Objective to maximize sum of diskVarList cardinality
        List<SetVar> maxCardList = null;
        switch (maxDisjointLevel) {
            case Rack.LEVEL:
                maxCardList = rackVarList;
                break;
            case Chassis.LEVEL:
                maxCardList = chassisVarList;
                break;
            case Host.LEVEL:
                maxCardList = hostVarList;
                break;
            case Disk.LEVEL:
                maxCardList = diskVarList;
                break;
        }

        objective = model.intVar("Objective", latestObjective, zone.capacity);
        IntVar[] diskVarCard = maxCardList.stream().map(v -> v.getCard()).toArray(IntVar[]::new);
        model.sum(diskVarCard, "=", objective).post();

        Solver solver = model.getSolver();
        // UnSatisfiedSetNeighbourhood myLNS = new UnSatisfiedSetNeighbourhood(diskVars, frozenVars);
        // solver.setLNS(myLNS);

        // Add custom constraint to initialize allocation
        InitialProp.createConstraint(objective, diskVars, latestAllocation, maxDisjointLevel, frozenVars, context).post();

        // Add custom constraint to minimize disk overlaps
        MinOverlap.createConstraint(diskVars, overlap, context).post();

        // Set solver options

        //IObjectiveManager<IntVar> objectiveManager = ObjectiveFactory.makeObjectiveManager(objective, ResolutionPolicy.MAXIMIZE);
        //solver.setObjectiveManager(objectiveManager);

        // Set Search strategy
        VariableSelector<SetVar> variableSelector = new FrozenVarInputOrder(model, frozenVars);
        SetValueSelector valueSelector = new SetDomainMin() {
            //@Override
            //public int selectValue(SetVar s) {
            //    int val = super.selectValue(s);
            //    System.out.println("Selecting val: " + val + " for " + s.getName());
            //    return val;
            //}
        };
        SetVar[] diskVarsOrdered =  zone.diskList.stream()
                        .collect(Collectors.groupingBy(d -> d.index,
                                Collectors.groupingBy(d -> d.host.index,
                                        Collectors.groupingBy(d -> d.chassis.index,
                                                Collectors.groupingBy(d -> d.rack.index)))))
                        .values().stream()
                        .flatMap(m1 -> m1.values().stream()
                                .flatMap(m2 -> m2.values().stream()
                                        .flatMap(m3 -> m3.values().stream()
                                                .flatMap(m4 -> m4.stream().map(d -> diskVarList.get(d.globalIndex))))))
                        .collect(Collectors.toList()).toArray(SetVar[]::new);


        SetStrategy setStrategy = Search.setVarSearch(
                variableSelector,
                valueSelector,
                true, diskVarsOrdered);

        solver.setSearch(setStrategy);

        // Set LNS on the latest solution;
        Variable[] variablesToRecord = new Variable[diskVarList.size()+1];
        for (int i = 0; i < diskVarList.size(); i++) {
            variablesToRecord[i] = diskVarList.get(i);
        }
        variablesToRecord[diskVarList.size()] = objective;
        latestSolution = new Solution(model, variablesToRecord);
        model.getSolver().attach(latestSolution);

    }

    public void outputSearchTree(int iteration) {
        Solver solver = model.getSolver();

        Closeable closeable = new GraphvizGenerator(context.zone.name + "-" + iteration + "-search-tree.gv", solver);

        solver.plugMonitor(new IMonitorClose() {
            @Override
            public void afterClose() {
                System.out.println("Exiting output search tree");
                try {
                    closeable.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void outputOnEveryUpdate() {
        List<Variable> vars = new ArrayList<>();
        vars.addAll(diskVarList);
        vars.addAll(hostVarList);
        vars.addAll(chassisVarList);
        vars.addAll(rackVarList);
        vars.addAll(vars.stream().map(v -> v.asSetVar().getCard()).collect(Collectors.toList()));

        vars.add(objective);
        VariableMonitor.logOnUpdate(diskVarList);
    }

    public void setStopCriterion() {
        Solver solver = model.getSolver();
        solver.addStopCriterion(new Criterion() {
            @Override
            public boolean isMet() {
                if(shouldStop){
                    System.out.println("Stop criterion met");
                }
                return shouldStop;
            }
        });

        model.getSolver().plugMonitor(new IMonitorContradiction() {
            @Override
            public void onContradiction(ContradictionException cex) {
                cex.printStackTrace(System.out);
                System.out.println(cex.v + ":"+cex.c + cex.getMessage());

                System.out.println("Allocation: " + Util.toPrettyValMap(diskVarList));

                Map<Disk, Set<Integer>> disks = context.zone.rackList.get(0).chassisList.get(1).diskList.stream()
                                    .collect(Collectors.toMap(d -> d, d -> Util.isetToSet(diskVarList.get(d.globalIndex).getLB())));
                printCombinationStatistics(disks);

                if(model.getSolver().getFailCount() >= 1) {
                    shouldStop = true;
                }
            }
        });
    }

    public static void main(String[] args) throws ParseException, FileNotFoundException {
        // Set commandline options
        Context context = new Context(args);
        Zone zone = context.zone;

        /*
        if(!allocation.locationToPartitionMap.isEmpty()) {
            System.out.println("Loading existing allocation");
            Map<Disk, Integer> usedDiskCapacity = Util.deepCopy(allocation.data).entrySet().stream()
                    .map(Util.mapMapEntry(s -> zone.getDiskFromName(s), l -> l.size()))
                    .collect(Util.toMap());

            Map<String, Map<String, Map<String, List<Integer>>>> usedTopologyCapacity = zone.rackList.stream()
                    .map(r -> Map.entry(r.name, r.chassisList.stream()
                            .map(c -> Map.entry(c.name, c.hostList.stream()
                                    .map(h -> Map.entry(h.name, h.diskList.stream()
                                            .map(d -> usedDiskCapacity.getOrDefault(d, 0))
                                            .collect(Collectors.toList())))
                                    .collect(Util.toMap())))
                            .collect(Util.toMap())))
                    .collect(Util.toMap());

            String file = FilenameUtils.concat(FilenameUtils.getPath(topologyFile), "used_" + FilenameUtils.getBaseName(topologyFile)+".yaml");
            Topology.writeToFile(file, usedTopologyCapacity);
            System.exit(0);
        }
         */

        List<String> solutionList = new ArrayList<>();
        List<Integer> objectiveList = new ArrayList<>();

        Allocation latestAllocation = context.allocation;
        int latestObjective = 0;
        int overlap = 2;
        int maxDisjointLevel = Rack.LEVEL;
        boolean reLoadOnce = false;


        Allocator allocator;

        for (int iteration = 0;; iteration++) {

            // Create model and variables for the allocator
            allocator = new Allocator(context, latestAllocation, latestObjective, overlap, maxDisjointLevel);

            // Options for debugging
            // allocator.logEveryDecision();
            // allocator.outputOnEveryUpdate();
            allocator.outputSearchTree(iteration);
            allocator.setStopCriterion();

            Solver solver = allocator.model.getSolver();
            // Solve and print solution
            if (solver.solve()) {
                reLoadOnce = true;
                Solution latestSolution = allocator.latestSolution;

                try {
                    latestObjective = latestSolution.getIntVal(allocator.objective);

                    solutionList.add(latestSolution.toString());
                    objectiveList.add(latestObjective);

                    latestAllocation = new Allocation(zone, allocator.diskVarList);

                } catch (Exception e) {
                    System.out.println("Cannot retrieve solution since initialized from existing alloc: " + e);
                    continue;
                }

                System.out.println("******Solution " + iteration + "*****: ");

                List<Integer> partitionAllocation = allocator.diskVarList.stream()
                        .map(v -> latestSolution.getSetVal(v))
                        .flatMap(values -> Arrays.stream(values).boxed())
                        .sorted()
                        .collect(Collectors.toList());
                System.out.println("Partitions:" + Util.sequenceNum(partitionAllocation));

                if (partitionAllocation.size() != zone.capacity) {
                    System.out.println("Partition allocation size: " + partitionAllocation.size() + " is not equal to total capacity: " + zone.capacity);
                }

                printAllocationStatistics(latestAllocation, zone);

                latestAllocation.writeToFile(context.newAllocationFile);

                allocator.model.getSolver().printStatistics();

                System.out.println("Objective values: " + objectiveList);
                System.out.println("Overlap: " + overlap);
                System.out.println("Max disjoint: " + maxDisjointLevel);

                if(latestObjective >= zone.capacity) { break; }

                latestObjective++;

            } else {
                System.out.println("Failed with disk overlap: " + overlap + " and disjoint on level: " + maxDisjointLevel);
                if(reLoadOnce) {
                   reLoadOnce = false;
                }else {
                    reLoadOnce = true;
                    if(maxDisjointLevel < Disk.LEVEL) {
                        frozenVars.clear();
                        latestObjective = 0;
                        maxDisjointLevel++;
                    } else {
                        overlap++;
                        latestObjective--;
                    }
                }
                if (overlap > 2) break;
            }

            System.out.println("******Restart*****");

        }
        System.out.println("**Done**");

        if(!solutionList.isEmpty()) {
            System.out.println("Found at least one solution: ");

            //printAllocationStatistics(latestAllocation, zone);

            System.out.println("Objective values: " + objectiveList);
            System.out.println("Overlap: " + overlap);

            context.allocation.convertTo(latestAllocation);
            //System.out.println("Partition alloc: " + allocator.lastSolution);
        }


    }

    // To print changes and statistics
    public static <T extends AllocationUnit, C extends Collection<Integer>> void printCombinationStatistics(Map<T, C> allocationUnitToPartitionListMap) {
        printCombinationStatistics(allocationUnitToPartitionListMap, 3, false);
    }

    public static <T extends AllocationUnit, C extends Collection<Integer>> void printCombinationStatistics(Map<T, C> allocationUnitToPartitionListMap, int k, boolean printVal) {

        List<AllocationUnit> auList = new ArrayList(allocationUnitToPartitionListMap.keySet());

        if(auList.size() < k) {
            return;
        }
        Combinations combinations = new Combinations(auList.size(), k);
        Map<String, List<Integer>> intersectionMap = new HashMap<>();
        for (int [] combination: combinations) {
            // combination length must be k
            AllocationUnit[] disks = new AllocationUnit[k];
            for (int i = 0; i < k; i++) {
                disks[i] = auList.get(combination[i]);
            }
            String name = Arrays.stream(disks).map(d -> d.name).collect(Collectors.joining("_"));

            List<Integer> intersectionValue = new ArrayList<>(allocationUnitToPartitionListMap.get(disks[0]));
            for (int i = 1; i < k; i++) {
                intersectionValue.retainAll(allocationUnitToPartitionListMap.get(disks[i]));
            }

            if(!intersectionValue.isEmpty()) {
                intersectionMap.put(name, intersectionValue);
            }
        }
        if(printVal)
            System.out.println("Intersection Val: " + intersectionMap.entrySet().stream().map(Util.mapMapValue(v -> v.size())) .collect(Util.toMap()));

        Map<Integer, Long> intersectionValMap = intersectionMap.values().stream()
                .collect(Collectors.groupingBy(v -> v.size(), Collectors.counting()));

        System.out.println("Intersection Load on " + intersectionValMap);

    }

    public static void printAllocationStatistics(Allocation allocation, Zone zone) {

        Map<Integer, List<Disk>> partitionToDiskListMap = allocation.getPartitionToDiskListMap();
        Map<Disk, List<Integer>> diskToPartitionMap = allocation.getDiskToPartitionListMap();

        System.out.println("Disk: " + diskToPartitionMap.entrySet().stream().collect(Collectors.toMap(e->e.getKey(), e-> e.getValue().size())));

        Map<Integer, Integer> partitionReplicas = partitionToDiskListMap.entrySet().stream()
                .map(Util.mapMapValue(diskList -> diskList.size()))
                .collect(Util.toMap());

        System.out.println("Partition replicas: " + partitionReplicas);

        Map<Integer, List<Disk>> unSatisfiedReplicas = partitionToDiskListMap.entrySet().stream()
                .filter(e -> e.getValue().size()!=3)
                .collect(Util.toMap());

        System.out.println("Unsatisfied replicas: " + unSatisfiedReplicas.keySet());

        Set<Disk> unSatisfyingLocations = unSatisfiedReplicas.values().stream()
                .flatMap(k -> k.stream())
                .sorted()
                .collect(Collectors.toCollection(LinkedHashSet::new));

        System.out.println("Unsatisfied Locations:" + unSatisfyingLocations);

        System.out.println("For disk: ");
        printCombinationStatistics(diskToPartitionMap);

        Map<Disk, Long> diskCard = diskToPartitionMap.entrySet().stream()
                .map(Util.mapMapValue(l -> l.stream().distinct().count()))
                .collect(Util.toMap());
        System.out.println("Unsatisfied disk Cardinality: " + diskCard.entrySet().stream()
                .filter(e -> e.getKey().capacity != e.getValue())
                .map(e -> Map.entry(e.getKey().name, e.getValue()+"/"+e.getKey().capacity))
                .collect(Util.toMap()));
        System.out.println("Disk card sum: " + diskCard.values().stream().mapToLong(Long::longValue).sum());

        Map<Host, List<Integer>> hostToPartitionListMap = allocation.getHostToPartitionListMap();

        System.out.println("For host: ");
        printCombinationStatistics(hostToPartitionListMap);

        Map<Host, Long> hostCard = hostToPartitionListMap.entrySet().stream()
                .map(Util.mapMapValue(l -> l.stream().distinct().count()))
                .collect(Util.toMap());

        System.out.println("Unsatisfied host Cardinality: " + hostCard.entrySet().stream()
                .filter(e -> e.getKey().capacity != e.getValue())
                .map(e -> Map.entry(e.getKey().name, e.getValue()+"/"+e.getKey().capacity))
                .collect(Util.toMap()));
        System.out.println("Host card sum: " + hostCard.values().stream().mapToLong(Long::longValue).sum());

        Map<Chassis, List<Integer>> chassisToPartitionListMap = allocation.getChassisToPartitionListMap();

        System.out.println("For chassis: ");
        printCombinationStatistics(chassisToPartitionListMap, 3, true);

        Map<Chassis, Long> chassisCard = chassisToPartitionListMap.entrySet().stream()
                .map(Util.mapMapValue(l -> l.stream().distinct().count()))
                .collect(Util.toMap());

        System.out.println("Unsatisfied chassis cardinality: " + chassisCard.entrySet().stream()
                .filter(e -> e.getKey().capacity != e.getValue())
                .map(e -> Map.entry(e.getKey().name, e.getValue()+"/"+e.getKey().capacity))
                .collect(Util.toMap()));
        System.out.println("Chassis card sum: " + chassisCard.values().stream().mapToLong(Long::longValue).sum());

        Map<Rack, List<Integer>> rackToPartitionListMap = allocation.getRackToPartitionListMap();

        System.out.println("For rack: ");
        printCombinationStatistics(rackToPartitionListMap, 3, true);

        Map<Rack, Long> rackCard = rackToPartitionListMap.entrySet().stream()
                .map(Util.mapMapValue(l -> l.stream().distinct().count()))
                .collect(Util.toMap());

        System.out.println("Unsatisfied rack cardinality: " + rackCard.entrySet().stream()
                .filter(e -> e.getKey().capacity != e.getValue())
                .map(e -> Map.entry(e.getKey().name, e.getValue()+"/"+e.getKey().capacity))
                .collect(Util.toMap()));
        System.out.println("Rack card sum: " + rackCard.values().stream().mapToLong(Long::longValue).sum());

        System.out.println("Total partition capacity: " + zone.capacity);
    }

    // Unused methods

    public static void printTrace() {
        System.out.println("Printing stack trace:");
        StackTraceElement[] elements = Thread.currentThread().getStackTrace();
        for (int i = 1; i < elements.length; i++) {
            StackTraceElement s = elements[i];
            System.out.println("\tat " + s.getClassName() + "." + s.getMethodName()
                    + "(" + s.getFileName() + ":" + s.getLineNumber() + ")");
        }
    }

    public void getChassisCombinations() {
        Combinations chassisCombinations = new Combinations(context.zone.chassisCapacity, Context.REPLICATION_FACTOR);
        List<Set<Integer>> chassisTriplets = new ArrayList<>();
        for (int [] combination: chassisCombinations) {
            Integer[] a = ArrayUtils.toObject(combination);
            chassisTriplets.add(new HashSet<Integer>(Arrays.asList(a)));
            System.out.println(Arrays.toString(combination));
        }
        int numOfTriplets = chassisTriplets.size();
        System.out.println(numOfTriplets);

        IntVar[] tripletLoad = model.intVarArray("tripletLoad", numOfTriplets, 0, context.zone.capacity);
        IntVar[] partitionCapacity = model.intVarArray("partitionCapacity", context.zone.chassisCapacity, 0, context.zone.capacity / Context.REPLICATION_FACTOR);

        int cIndex = 0;
        for(Chassis chassis: context.zone.chassisList)
        {
            int chassisCapacity = chassis.capacity;
            Constraint c1 = model.arithm(partitionCapacity[cIndex],"=", chassisCapacity);
            c1.post();
            System.out.println(c1);

            int finalCIndex = cIndex;
            IntVar[] a = IntStream.range(0, numOfTriplets)
                    .filter(i -> chassisTriplets.get(i).contains(finalCIndex))
                    .mapToObj(i -> chassisTriplets.get(i))
                    .toArray(IntVar[]::new);

            Constraint c2 = model.sum(a, "=", chassisCapacity);
            c2.post();
            System.out.println(c2);
            cIndex++;
        }

        for (int i = 0; i < numOfTriplets; i++)
        {
            Set<Integer> triplet = chassisTriplets.get(i);
            int minChassisCapacity = triplet
                    .stream()
                    .map(x -> context.zone.chassisList.get(x).capacity)
                    .min(Integer::compareTo)
                    .orElse(0);
            Constraint c = model.arithm(tripletLoad[i], "<=", minChassisCapacity);
            c.post();
 //         System.out.println(c);
        }
    }

    public void logEveryDecision() {
        class MyDecisionMessage implements IMessage {

            Map<SetVar, Set<Integer>> lastLB;
            Map<SetVar, Set<Integer>> lastUB;
            List<SetVar> setVars;

            public MyDecisionMessage() {
                lastLB = new LinkedHashMap<>();
                lastUB = new LinkedHashMap<>();

                setVars = new ArrayList<>();
                setVars.addAll(rackVarList);
                setVars.addAll(chassisVarList);
                setVars.addAll(hostVarList);
                setVars.addAll(diskVarList);
                for(SetVar s: setVars) {
                    lastLB.put(s, new HashSet<>());
                    lastUB.put(s, new HashSet<>());
                }
            }

            @Override
            public String print() {

               // Map<String, String> changedDecision = new LinkedHashMap<>();
                List<String> changedDecision = new LinkedList<>();

                for(SetVar s: setVars) {
                    Set<Integer> newLB = new LinkedHashSet<>();
                    s.getLB().forEach(v -> newLB.add(v));

                    Set<Integer> addedLB = new LinkedHashSet<>(newLB);
                    Set<Integer> removedLB = new LinkedHashSet<>(lastLB.get(s));
                    addedLB.removeAll(lastLB.get(s));
                    removedLB.removeAll(newLB);

                    Set<Integer> newUB = new LinkedHashSet<>();
                    s.getUB().forEach(v -> newUB.add(v));

                    Set<Integer> addedUB = new LinkedHashSet<>(newUB);
                    Set<Integer> removedUB = new LinkedHashSet<>(lastUB.get(s));
                    addedUB.removeAll(lastUB.get(s));
                    removedUB.removeAll(newUB);

                    if(addedLB.isEmpty() && removedLB.isEmpty() && addedUB.isEmpty()) return "";

                    System.out.format("%s: +[%s(%d) / %s(%d)] | -[%s(%d) / %s(%d)] (%d/%d) updated due to %s%n",
                            s.getName(), Util.sequenceNum(addedLB), addedLB.size(), Util.sequenceNum(addedUB), addedUB.size(),
                            Util.sequenceNum(removedLB), removedLB.size(), Util.sequenceNum(removedUB), removedUB.size(),
                            s.getCard().getLB(), s.getCard().getUB(), "Decision");

                    lastLB.put(s, newLB);
                    lastUB.put(s, newUB);
                }

                return String.join("\n", changedDecision) + "\n" + objective.toString();

            }
        }

        MyDecisionMessage myDecision = new MyDecisionMessage();

        // model.getSolver().showDecisions(new MyDecisionMessage());
        model.getSolver().plugMonitor(new IMonitorDownBranch() {

            @Override
            public void afterDownBranch(boolean left) {
               System.out.println(myDecision.print() + "\n........................................................");
            }
        });

        model.getSolver().plugMonitor(new IMonitorRestart() {
            @Override
            public void beforeRestart() {
                for(SetVar s: myDecision.setVars) {
                    myDecision.lastLB.get(s).clear();
                    myDecision.lastUB.get(s).clear();
                }
            }
        });
    }
}
