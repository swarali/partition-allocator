package allocator;

import org.chocosolver.solver.variables.SetVar;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import topology.*;

import java.io.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Allocation {
    public Zone zone;
    public Map<String, List<Integer>> data;

    public Map<Location, Integer> locationToPartitionMap;
    public Map<Integer, List<Location>> partitionToLocationListMap;

    public List<Location> unAllocatedLocationList;
    public Map<String, List<Integer>> overAllocatedPartitions;

    public List<Integer> existingPartitionList;
    public List<Integer> partitionList;

    public Allocation(Zone zone, String fileName) {
        System.out.println("Reading allocation from file:" + fileName);

        Map<String, List<Integer>> diskNameToPartitionListMap = new LinkedHashMap<>();

        if(fileName != null && !fileName.isEmpty()) {
            readFromFile(fileName).forEach((hostName, hostData) ->
                    hostData.forEach((diskLocalName, diskData) ->
                            diskNameToPartitionListMap.put(hostName + "[" + diskLocalName + "]", diskData)));
        }
        setFromData(zone, diskNameToPartitionListMap);
    }

    public Allocation(Zone zone, List<SetVar> diskVarList) {
        Map<String, List<Integer>> diskNameToPartitionListMap = diskVarList.stream()
                .collect(Collectors.toMap(var -> var.getName(), var -> Arrays.stream(var.getValue().toArray()).boxed().collect(Collectors.toList())));

        setFromData(zone, diskNameToPartitionListMap);
    }

    private void setFromData(Zone zone, Map<String, List<Integer>> diskNameToPartitionListMap) {
        this.data = diskNameToPartitionListMap;
        this.zone = zone;
        int partitionCapacity = zone.capacity / Context.REPLICATION_FACTOR;

        existingPartitionList = diskNameToPartitionListMap.values().stream()
                .flatMap(v -> v.stream())
                .sorted()
                .distinct()
                .collect(Collectors.toList());

        int smallestPartition = 0;
        int largestPartition = partitionCapacity - 1;
        if (existingPartitionList.size() > 0) {
            smallestPartition = existingPartitionList.get(0);
            largestPartition = smallestPartition + partitionCapacity - 1;
        }
        partitionList = IntStream.range(smallestPartition, smallestPartition + partitionCapacity).boxed().collect(Collectors.toList());

        locationToPartitionMap = new LinkedHashMap<>();
        partitionToLocationListMap = new LinkedHashMap<>();

        unAllocatedLocationList = new ArrayList<>();
        overAllocatedPartitions = Util.deepCopy(diskNameToPartitionListMap);

        for (Disk disk: zone.diskList) {
            List<Integer> partitionList = diskNameToPartitionListMap.getOrDefault(disk.name, new ArrayList<>());
            for(Location location: disk.locationList) {
                if(location.index < partitionList.size() && (partitionList.get(location.index) <= largestPartition)) {
                    Integer partition = partitionList.get(location.index);
                    locationToPartitionMap.put(location, partition);
                    Util.getOrCreateList(partitionToLocationListMap, partition).add(location);

                    overAllocatedPartitions.get(disk.name).remove(partition);
                } else {
                    unAllocatedLocationList.add(location);
                }
            }
        }
        overAllocatedPartitions = overAllocatedPartitions.entrySet().stream().filter(e -> !e.getValue().isEmpty()).collect(Util.toMap());

        System.out.println("Unallocated locations: " + unAllocatedLocationList.size());
        System.out.println("OverAllocated partitions: " + overAllocatedPartitions);

    }

    public static <K, V, T> Map<K, List<T>> mapMapValueList(Map<K, List<V>> m, Function<V, T> mapper) {
        return m.entrySet().stream()
                .map(Util.mapMapValueList(mapper))
                .collect(Util.toMap());
    }

    public static <K, V, T> Map<T, List<V>> mapMapKeyAndAggregate(Map<K, V> m, Function<K, T> mapper) {
        return m.entrySet().stream()
                .map(Util.mapMapKey(mapper))
                .collect(Util.groupByEntryKeyList());
    }

    public Map<Integer, List<Disk>> getPartitionToDiskListMap() {
        return mapMapValueList(partitionToLocationListMap, location -> location.disk);
    }

    public Map<Disk, List<Integer>> getDiskToPartitionListMap() {
        return mapMapKeyAndAggregate(locationToPartitionMap, location -> location.disk);
    }

    public Map<Integer, List<Host>> getPartitionToHostListMap() {
        return mapMapValueList(partitionToLocationListMap, location -> location.host);
    }

    public Map<Host, List<Integer>> getHostToPartitionListMap() {
        return mapMapKeyAndAggregate(locationToPartitionMap, location -> location.host);
    }

    public Map<Integer, List<Chassis>> getPartitionToChassisListMap() {
        return mapMapValueList(partitionToLocationListMap, location -> location.chassis);
    }

    public Map<Chassis, List<Integer>> getChassisToPartitionListMap() {
        return mapMapKeyAndAggregate(locationToPartitionMap, location -> location.chassis);
    }

    public Map<Integer, List<Rack>> getPartitionToRackListMap() {
        return mapMapValueList(partitionToLocationListMap, location -> location.rack);
    }

    public Map<Rack, List<Integer>> getRackToPartitionListMap() {
        return mapMapKeyAndAggregate(locationToPartitionMap, location -> location.rack);
    }

    public static Map<String, Map<String, List<Integer>>> readFromFile(String fileName){
        //InputStream inputStream = allocator.Allocation.class.getClassLoader().getResourceAsStream(fileName);
        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream(fileName);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return new Yaml().load(inputStream);
    }


    public void convertTo(Allocation newAllocation) {

        Map<Disk, List<Integer>> oldDiskToPartitionMap = getDiskToPartitionListMap();
        Map<Disk, List<Integer>> newDiskToPartitionMap = newAllocation.getDiskToPartitionListMap();

        Set<Disk> disks = new HashSet<>();
        disks.addAll(oldDiskToPartitionMap.keySet());
        disks.addAll(newDiskToPartitionMap.keySet());

        for(Disk disk: disks) {
            List<Integer> oldDiskList = oldDiskToPartitionMap.getOrDefault(disk, null);
            List<Integer> newDiskList = newDiskToPartitionMap.getOrDefault(disk, null);

            if(oldDiskList == null) {
                System.out.println("Add " + disk + " : " + Util.sequenceNum(newDiskList));
                continue;
            }

            if(newDiskList == null) {
                System.out.println("Del " + disk + " : " + Util.sequenceNum(oldDiskList));
                continue;
            }

            List<Integer> movedAway = oldDiskList.stream().filter(p -> !newDiskList.contains(p)).collect(Collectors.toList());
            List<Integer> movedIn = newDiskList.stream().filter(p -> !oldDiskList.contains(p)).collect(Collectors.toList());

            if(!movedAway.isEmpty())
                System.out.println("Mv out " + disk + " : " + Util.sequenceNum(movedAway));

            if(!movedIn.isEmpty())
            System.out.println("Mv in " + disk + " : " + Util.sequenceNum(movedIn));
        }
    }

    public void writeToFile(String fileName) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.FLOW);
        options.setSplitLines(false);

        Map<Disk, List<Integer>> diskToPartitionMap = getDiskToPartitionListMap();
        Map<String, Map<String, List<Integer>>> fileData = zone.hostList.stream()
                .collect(Collectors.toMap(h->h.name,
                        h-> h.diskList.stream()
                                .collect(Collectors.toMap(d -> String.valueOf(d.index+1), d -> diskToPartitionMap.getOrDefault(d, new ArrayList<>())))));

        String yaml = new Yaml(options).dump(fileData).replace("\'", "\"");
        try {
            System.out.println("Writing to file: " + fileName);
            FileWriter writer = new FileWriter(fileName);
            writer.write(yaml);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
