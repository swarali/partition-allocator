package unused;

import allocator.Allocation;
import allocator.Context;
import org.chocosolver.solver.Solution;
import org.chocosolver.solver.exception.ContradictionException;
import org.chocosolver.solver.search.loop.lns.neighbors.INeighbor;
import org.chocosolver.solver.variables.SetVar;
import topology.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class UnSatisfiedSetNeighbourhood implements INeighbor {

    static Zone zone;

    final SetVar[] vars;
    final List<Disk> frozenVars;
    final Map<SetVar, Disk> setVarToDiskMap = new LinkedHashMap<>();
    final int maxDisjointLevel;

    int nbCall;
    int nbFixedVariables;
    private Allocation latestAllocation;

    public UnSatisfiedSetNeighbourhood(SetVar[] vars, List<Disk> frozenVars, Allocation latestAllocation, int maxDisjointLevel,  Context context){
        this.vars = vars;
        this.frozenVars = frozenVars;
        for(Disk disk: zone.diskList) {
            SetVar setVar = vars[disk.globalIndex];
            setVarToDiskMap.put(setVar, disk);
        }
        this.latestAllocation = latestAllocation;
        this.maxDisjointLevel = maxDisjointLevel;

        this.zone = context.zone;
    }

    @Override
    public void recordSolution() {
        System.out.println("Recording Solution " + nbCall++);

    }

    @Override
    public void fixSomeVariables() throws ContradictionException {

        System.out.println("******Restart*****");

        Map<Integer, List<Disk>> partitionToDiskListMap = latestAllocation.getPartitionToDiskListMap();
        Map<Disk, List<Integer>> diskToPartitionMap = latestAllocation.getDiskToPartitionListMap();

        Function<Disk, List<Disk>> diskToDisjointDiskList = null;
        switch (maxDisjointLevel) {
            case Zone.LEVEL:
                throw new ContradictionException().set(this, null, "Cannot be disjoint in the zone");
            case Rack
                    .LEVEL:
                diskToDisjointDiskList = d -> d.rack.diskList;
                break;
            case Chassis
                    .LEVEL:
                diskToDisjointDiskList = d -> d.chassis.diskList;
                break;
            case Host
                    .LEVEL:
                diskToDisjointDiskList = d -> d.host.diskList;
                break;
            case Disk.LEVEL:
                diskToDisjointDiskList = d -> Collections.singletonList(d);
                break;
        }

        List<Disk> unSatisfiedDisks = diskToPartitionMap.keySet().stream()
                .filter(disk -> diskToPartitionMap.get(disk).size() != disk.capacity)
                .collect(Collectors.toList());

        for(Disk disk: unSatisfiedDisks) {
            SetVar setVar = diskToSetVar(disk);
            if(frozenVars.contains(disk)) {
                //frozenVars.remove(disk);
                //throw new ContradictionException().set(this, setVar, "Disk "+ disk + " is already frozen");
            } else {
                frozenVars.add(disk);
            }
        }

        List<Integer> unSatisfiedReplicas = partitionToDiskListMap.keySet().stream()
                .filter(p -> partitionToDiskListMap.get(p).size() < 3)
                .flatMap(p -> Collections.nCopies(3 - partitionToDiskListMap.get(p).size(), p).stream())
                .collect(Collectors.toList());

        //Allocator.zone.diskList.stream()
        //        .filter(disk -> !frozenVars.contains(disk))
        //        .forEach(disk -> {
        //            List<Integer> partitions = diskToPartitionMap.remove(disk);
        //            partitions.forEach(p -> {
        //                partitionToDiskListMap.get(p).remove(disk);
        //            });
        //        });

        Function<Disk, List<Integer>> diskNeighbour = (d -> diskToPartitionMap.get(d));
        Function<Integer, List<Disk>> partitionNeighbour = (p -> partitionToDiskListMap.get(p));
        Function<List<Disk>, List<Integer>> diskListNeighbour = (dList -> dList.stream()
                .flatMap(d -> diskNeighbour.apply(d).stream()).distinct().sorted().collect(Collectors.toList()));
        Function<List<Integer>, List<Disk>> partitionListNeighbour = (pList -> pList.stream()
                .flatMap(p -> partitionNeighbour.apply(p).stream()).distinct().sorted().collect(Collectors.toList()));

        for(Integer p1: unSatisfiedReplicas) {

            Disk d1 = null;
            Integer p2 = null;
            Disk d2 = null;

            List<Disk> p1N1 = partitionNeighbour.apply(p1);
            List<Integer> p1N2 = diskListNeighbour.apply(p1N1);
            List<Disk> p1N3 = partitionListNeighbour.apply(p1N2);

            boolean found = false;
            for(Disk d: unSatisfiedDisks) {

                //System.out.println("Disk " + d + " must be in the 3rd neighbourhood of " + p1 + " : " + p1N3.contains(d));

                List<Integer> d1N1 = diskNeighbour.apply(d);
                List<Disk> d1N2 = partitionListNeighbour.apply(d1N1);
                List<Integer> d1N3 = diskListNeighbour.apply(d1N2);

                List<Disk> d1Disjoint = diskToDisjointDiskList.apply(d);

                for(Integer pi: latestAllocation.partitionList) {
                    if(d1N3.contains(pi)) continue;

                    List<Disk> piN1 = partitionNeighbour.apply(pi);

                    if(piN1.isEmpty()) continue;

                    for(Disk di: piN1) {
                        if(p1N3.contains(di)) continue;

                        if(piN1.stream().filter(dj -> dj != di).anyMatch(dj -> d1Disjoint.contains(dj))) continue;

                        List<Disk> diDisjoint = diskToDisjointDiskList.apply(di);
                        if(p1N1.stream().anyMatch(dj -> diDisjoint.contains(dj))) continue;

                        found = true;
                        d1 = d;
                        p2 = pi;
                        d2 = di;
                        break;
                    }

                    if(found) break;
                }

                // p2 = latestAllocation.partitionList.stream()
                //         .filter(pi -> !d1N3.contains(pi))
                //         .filter(pi -> !partitionToDiskListMap.get(pi).isEmpty())
                //         .filter(pi -> partitionToDiskListMap.get(pi).stream()
                //                         .anyMatch(di -> !p1N3.contains(di)))
                //         .findFirst()
                //         .orElse(null);

                //if(p2 == null) {
                //    continue;
                //}

                //d2 = partitionToDiskListMap.get(p2).stream()
                //        .filter(di -> !p1N3.contains(di))
                //        .findFirst()
                //        .orElse(null);


                if(found) {

                    System.out.println("Disks for " + p1 + ": " + p1N1);
                    System.out.println("Disks for " + p2 + ": " + partitionToDiskListMap.get(p2));
                    System.out.println("Partitions in " + d1 + " : " + d1N1);
                    System.out.println("Partitions in " + d2  + " : " + diskToPartitionMap.get(d2));
                    break;
                }

                //System.out.println("Cannot find unclashing partition for " + p1 + " and " + d);

            }

            if(!found) {
                System.out.println("No appropriate d1 found for " + p1);
                continue;
            }

            // Make smarter choice of partition here.
            List<Disk> disks1 = partitionToDiskListMap.get(p1);
            List<Disk> disks2 = partitionToDiskListMap.get(p2);

            partitionToDiskListMap.get(p2).remove(d2);
            diskToPartitionMap.get(d2).remove(p2);

            partitionToDiskListMap.get(p1).add(d2);
            diskToPartitionMap.get(d2).add(p1);

            partitionToDiskListMap.get(p2).add(d1);
            diskToPartitionMap.get(d1).add(p2);

            System.out.println( p1 + " in " + partitionToDiskListMap.get(p1) + ", "
                                + p2 + " in: " + partitionToDiskListMap.get(p2));

            if(diskToPartitionMap.get(d1).size() == d1.capacity) {
                unSatisfiedDisks.remove(d1);
            }
        }

        List<Disk> diskList;
        if(unSatisfiedDisks.isEmpty()) {
            diskList = zone.diskList;
        } else {
            diskList = frozenVars;
        }

        for(Disk disk: diskList) {
            SetVar s = diskToSetVar(disk);
            List<Integer> setVal = diskToPartitionMap.getOrDefault(disk, new ArrayList<>());

            System.out.println("Initialize " + disk + " " + s.getName() + " to " + setVal);
            for(int val: setVal) {
                try {
                    s.force(val, this);

                } catch (ContradictionException cex) {
                    System.out.println("Contradiction on " + cex + " while adding value " + val);
                    System.out.println("Size of " + cex.v + ": " + cex.v.asSetVar().getValue().size());
                    throw cex;
                }
            }
        }

        nbFixedVariables = frozenVars.size();

        System.out.println("Unsatisfied disks: " + unSatisfiedDisks);
        System.out.println("Frozen fragments: " + frozenVars + ", size: " + nbFixedVariables + "/" + vars.length);

    }

    public SetVar diskToSetVar(Disk disk) {
        return vars[disk.globalIndex];
    }

    public Disk setVarToDisk(SetVar setVar) {
        return setVarToDiskMap.get(setVar);
    }

    @Override
    public void loadFromSolution(Solution solution) {
        nbCall = 0;
    }
}
