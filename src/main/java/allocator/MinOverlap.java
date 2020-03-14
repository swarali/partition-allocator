package allocator;

import org.chocosolver.solver.constraints.Constraint;
import org.chocosolver.solver.constraints.Propagator;
import org.chocosolver.solver.constraints.PropagatorPriority;
import org.chocosolver.solver.exception.ContradictionException;
import org.chocosolver.solver.search.loop.monitors.IMonitorRestart;
import org.chocosolver.solver.variables.SetVar;
import org.chocosolver.solver.variables.delta.ISetDeltaMonitor;
import org.chocosolver.solver.variables.events.SetEventType;
import org.chocosolver.util.ESat;
import topology.Disk;
import topology.Zone;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Propagator to ensure that variables from VARS do not overlap on more than OVERLAP value.
 */
class MinOverlap extends Propagator<SetVar> {

    public static final String NAME = "MINOVERLAP";

    final int overlap;
    final Context context;
    final Zone zone;
    final List<Integer> domain;

    final Map<SetVar, Disk> setVarToDiskMap = new LinkedHashMap<>();
    final List<ISetDeltaMonitor> sdm = new ArrayList<>();

    Map<Disk, Set<Disk>> alreadyClashingLocations = new LinkedHashMap<>();
    Map<Integer, Set<Disk>> alreadyClashingPartitions = new LinkedHashMap<>();
    Map<Integer, Set<Disk>> alreadyFulfilledPartitions = new LinkedHashMap<>();

    boolean restart = false;

    public MinOverlap(SetVar[] vars, int overlap, Context context){
        super(vars, PropagatorPriority.LINEAR, true);

        this.overlap = overlap;
        this.context = context;

        this.zone = context.zone;
        this.domain = context.allocation.partitionList;

        for(Disk disk: zone.diskList) {
            SetVar setVar = vars[disk.globalIndex];
            setVarToDiskMap.put(setVar, disk);
            sdm.add(setVar.monitorDelta(this));

            alreadyClashingLocations.put(disk, new LinkedHashSet<>());
        }

        for(int partition: domain) {
            alreadyClashingPartitions.put(partition, new LinkedHashSet<>());
        }

        model.getSolver().plugMonitor(new IMonitorRestart() {
            @Override
            public void beforeRestart() {
                System.out.println("Reset Minimum Overlap propagator");
                restart = true;
            }

        });

    }

    public static Constraint createConstraint(SetVar[] vars, int overlap, Context context) {
        return new Constraint(NAME, new MinOverlap(vars, overlap, context));
    }

    public void reset() {
        for(Disk disk: zone.diskList) {
            alreadyClashingLocations.get(disk).clear();
        }

        for(int partition: domain) {
            alreadyClashingPartitions.put(partition, new LinkedHashSet<>());
        }

        alreadyFulfilledPartitions.clear();
    }

    public SetVar diskToSetVar(Disk disk) {
        return vars[disk.globalIndex];
    }

    public Disk setVarToDisk(SetVar setVar) {
        return setVarToDiskMap.get(setVar);
    }

    @Override
    public void propagate(int mask) throws ContradictionException {
        if(restart) {
            reset();
            restart = false;
        }

        //printStats();
        alreadyClashingPartitions.forEach((partition, disks) -> {
            List<Disk> otherDisks = new ArrayList<>(disks);
            for(Disk disk: disks) {
                otherDisks.remove(disk);
                alreadyClashingLocations.get(disk).addAll(otherDisks);
                otherDisks.add(disk);
            }

            if (disks.size() == 3) {
                alreadyFulfilledPartitions.put(partition, disks);
            }
        });

        alreadyFulfilledPartitions.forEach((partition, disks) -> {
            alreadyClashingPartitions.remove(partition);
        } );


        for(SetVar setVar: vars) {
            Disk disk = setVarToDisk(setVar);
            Set<Integer> setVarLB = Util.isetToSet(setVar.getLB());

            Set<Disk> clashingLocationsForLocation = alreadyClashingLocations.get(disk);
            for(Disk clashingDisk: clashingLocationsForLocation) {
                SetVar clashingDiskVar = diskToSetVar(clashingDisk);
                for(int val: setVarLB) {
                    Set<Disk> disksWithVal = alreadyClashingPartitions.getOrDefault(val, alreadyFulfilledPartitions.get(val));
                    if(!disksWithVal.contains(clashingDisk)) {
                        clashingDiskVar.remove(val, this);
                    }
                }
            }
        }

    }

    @Override
    public void propagate(int idxVarInProp, int mask) throws ContradictionException {

        if(restart) {
            reset();
            restart = false;
        }

        sdm.get(idxVarInProp).freeze();
        sdm.get(idxVarInProp).forEach(val -> this.elementRemoved(idxVarInProp, val), SetEventType.REMOVE_FROM_ENVELOPE);
        sdm.get(idxVarInProp).forEach(val -> this.elementForced(idxVarInProp, val), SetEventType.ADD_TO_KER);
        sdm.get(idxVarInProp).unfreeze();
    }

    public void elementForced(int idxVarInProp, int val) throws ContradictionException {

        SetVar setVar = vars[idxVarInProp];
        Disk disk = zone.diskList.get(idxVarInProp);
        //printStats();

        Set<Disk> clashingLocationsForPartition = alreadyClashingPartitions.get(val);
        Set<Disk> clashingLocationsForLocation = alreadyClashingLocations.get(disk);

        //System.out.println("Add: " + disk.name + ": " + val + ", location: " + clashingLocationsForLocation + ", partitions: " + clashingLocationsForPartition);
        //System.out.println("Remaining partitions: " + allocator.Util.sequenceNum(alreadyClashingPartitions.keySet().stream().sorted().collect(Collectors.toList())));

        if(clashingLocationsForPartition == null) {
            System.out.println("Partition " + val + " not found in " + alreadyClashingPartitions);
            throw new ContradictionException().set(this, setVar, "Val " + val + " is already located to 3 locations");
        }

        Map<Disk, Set<Integer>> toRemove = new LinkedHashMap<>();

        Set<Integer> setVarLB = Util.isetToSet(setVar.getLB());
        setVarLB.remove(val);
        for(Disk clashingDisk: clashingLocationsForLocation) {
            if(clashingDisk == disk) continue;

            SetVar clashingDiskVar = diskToSetVar(clashingDisk);
            Set<Integer> clashingSetVarLB = Util.isetToSet(clashingDiskVar.getLB());

            Set<Integer> intersection = new HashSet<>(clashingSetVarLB);
            intersection.retainAll(setVarLB);
            if(intersection.size() > overlap) {
                throw new ContradictionException()
                        .set(this, setVar, "While adding " + val + " to " + disk + " intersection with " + clashingDisk + " contains: " + intersection);
            } else if(intersection.size() == overlap) {
                Util.getOrCreateSet(toRemove, clashingDisk).add(val);
            }
        }

        for (Disk clashingDisk: clashingLocationsForPartition) {
            alreadyClashingLocations.get(clashingDisk).add(disk);

            SetVar clashingDiskVar = diskToSetVar(clashingDisk);
            Set<Integer> clashingSetVarLB = Util.isetToSet(clashingDiskVar.getLB());
            clashingSetVarLB.remove(val);

            Set<Integer> intersection = new HashSet<>(clashingSetVarLB);
            intersection.retainAll(setVarLB);
            if(intersection.size() >= overlap) {
                throw new ContradictionException()
                        .set(this, setVar, "While adding " + val + " to " + disk + " intersection with " + clashingDisk + " is already overlapping: " + intersection);
            } else if(intersection.size() == overlap - 1) {
                Util.getOrCreateSet(toRemove, clashingDisk).addAll(setVarLB.stream()
                        .filter(i -> !intersection.contains(i)).collect(Collectors.toList()));
                Util.getOrCreateSet(toRemove, disk).addAll(clashingSetVarLB.stream()
                        .filter(i -> !intersection.contains(i)).collect(Collectors.toList()));
            }
        }

        clashingLocationsForLocation.addAll(clashingLocationsForPartition);
        clashingLocationsForPartition.add(disk);

        if (clashingLocationsForPartition.size() == 3) {
            Set<Disk> disks = alreadyClashingPartitions.remove(val);
            alreadyFulfilledPartitions.put(val, disks);

            for(Disk d: zone.diskList) {
                SetVar s = vars[d.globalIndex];
                if(!disks.contains(d)) {
                    if(s.getLB().contains(val)) {
                        throw new ContradictionException()
                                .set(this, setVar, "While adding " + val + " to " + disk + " found extra in " + d + " + " + disks);
                    }

                    Util.getOrCreateSet(toRemove, d).add(val);
                }
            }
        }

        for(Map.Entry<Disk, Set<Integer>> e: toRemove.entrySet()) {
            SetVar s = vars[e.getKey().globalIndex];
            for(int v: e.getValue()) {
                try {
                    //System.out.println("Removing: " + v + " from " + s.getName());
                    s.remove(v, this);
                } catch (ContradictionException cex) {
                    System.out.println("Contradiction on " + cex.v + " on removing "
                            + v + " inside " + setVar + " adding val: " + val);
                    throw cex;
                }
            }
        }
    }

    public void elementRemoved(int idxVarInProp, int val) throws ContradictionException {

        SetVar setVar = vars[idxVarInProp];
        Disk disk = zone.diskList.get(idxVarInProp);
        //printStats();

        Set<Disk> clashingLocationsForPartition = alreadyClashingPartitions.get(val);
        Set<Disk> clashingLocationsForLocation = alreadyClashingLocations.get(disk);

        if(clashingLocationsForPartition == null) {
            clashingLocationsForPartition = alreadyFulfilledPartitions.remove(val);
            alreadyClashingPartitions.put(val, clashingLocationsForPartition);
        }

        if(!clashingLocationsForPartition.contains(disk)) {
            return;
        }

        //System.out.println("Rem: " + disk.name + ": " + val);

        clashingLocationsForPartition.remove(disk);
        clashingLocationsForLocation.removeAll(clashingLocationsForPartition);

        Set<Integer> setVarLB = Util.isetToSet(setVar.getLB());
        setVarLB.remove(val);
        for (Disk clashingDisk: clashingLocationsForPartition) {
            alreadyClashingLocations.get(clashingDisk).remove(disk);

            SetVar clashingDiskVar = vars[clashingDisk.globalIndex];
            Set<Integer> clashingSetVarLB = Util.isetToSet(clashingDiskVar.getLB());
            clashingSetVarLB.remove(val);

            Set<Integer> intersection = new HashSet<>(clashingSetVarLB);
            intersection.retainAll(setVarLB);
            if(!intersection.isEmpty()) {
                throw new ContradictionException()
                        .set(this, setVar, "Intersection of " + setVar + " and " + clashingDiskVar + " is not empty: " + intersection);
            }
        }

        //System.out.println("Remaining partitions: " + allocator.Util.sequenceNum(alreadyClashingPartitions.keySet().stream().sorted().collect(Collectors.toList())));

        //System.out.println("Exit  Rem: " + disk.name + ": " + val);
        //System.out.println("Clashing locations for " + disk.name + ": " + clashingLocationsForLocation);
        //printStats();
    }

    @Override
    public ESat isEntailed() {
        return ESat.UNDEFINED;
    }

    /* Print stats during propagation loop. Caution: May slowdown performance on large allocation */
    public void printStats() {

        int constraintSize = alreadyClashingLocations.values().stream()
                .mapToInt(disks -> disks.size())
                .sum();

        int depth = alreadyClashingPartitions.values().stream()
                .mapToInt(disks -> disks.size())
                .sum();
        depth+= 3 * alreadyFulfilledPartitions.size();

        System.out.format("%5d(remaining) + %5d(done) | Size: %5d Depth: %6d%n",
                alreadyClashingPartitions.size(), alreadyFulfilledPartitions.size(), constraintSize, depth);
    }
}
