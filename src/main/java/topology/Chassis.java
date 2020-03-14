package topology;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Chassis extends AllocationUnit {
    public static final int LEVEL = 2;

    public Zone zone;
    public Rack rack;

    public List<Host> hostList;
    public List<Disk> diskList;
    public List<Location> locationList;

    public int hostCapacity;
    public int diskCapacity;
    public int capacity;

    private  int nextIndex = 0;
    private static int nextGlobalIndex = 0;

    public Chassis(String chassisName, int chassisIndex, Rack parentRack, Map<String, List<Integer>> chassisData) {
        name = chassisName;
        index = chassisIndex;
        globalIndex = nextGlobalIndex++;

        zone = parentRack.zone;
        parent = rack = parentRack;

        childList = hostList = new ArrayList<>();
        int hostIndex = 0;
        for(Map.Entry<String, List<Integer>> entry: chassisData.entrySet()) {
            String hostName = entry.getKey();
            List<Integer> hostData = entry.getValue();
            hostList.add(new Host(hostName, hostIndex, this, hostData));
            hostIndex++;
        }

        hostCapacity = hostList.size();
        diskList = hostList.stream().flatMap(h -> h.diskList.stream()).collect(Collectors.toList());
        diskCapacity = diskList.size();
        locationList = diskList.stream().flatMap(d -> d.locationList.stream()).collect(Collectors.toList());
        capacity = locationList.size();
    }

    @Override
    public Chassis next() {
        return (Chassis) super.next();
    }
}
