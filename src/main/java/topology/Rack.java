package topology;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Rack extends AllocationUnit {
    public static final int LEVEL = 1;

    public Zone zone;
    public List<Chassis> chassisList;
    public List<Host> hostList;
    public List<Disk> diskList;
    public List<Location> locationList;

    public int chassisCapacity;
    public int hostCapacity;
    public int diskCapacity;
    public int capacity;

    private static int nextGlobalIndex = 0;

    public Rack(String rackName, int rackIndex, Zone parentZone, Map<String, Map<String, List<Integer>>> rackData) {
        name = rackName;
        index = rackIndex;
        globalIndex = nextGlobalIndex++;

        parent = zone = parentZone;

        childList = chassisList = new ArrayList<>();
        int chassisIndex = 0;
        for (Map.Entry<String, Map<String, List<Integer>>> entry : rackData.entrySet()) {
            String chassisName = entry.getKey();
            Map<String, List<Integer>> chassisData = entry.getValue();
            chassisList.add(new Chassis(chassisName, chassisIndex, this, chassisData));
            chassisIndex++;
        }

        chassisCapacity = chassisList.size();
        hostList = chassisList.stream().flatMap(c -> c.hostList.stream()).collect(Collectors.toList());
        hostCapacity = hostList.size();
        diskList = hostList.stream().flatMap(h -> h.diskList.stream()).collect(Collectors.toList());
        diskCapacity = diskList.size();
        locationList = diskList.stream().flatMap(d -> d.locationList.stream()).collect(Collectors.toList());
        capacity = locationList.size();
    }

    @Override
    public Rack next() {
        return (Rack) super.next();
    }
}
