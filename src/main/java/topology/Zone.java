package topology;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Zone extends AllocationUnit {
    public static final int LEVEL = 0;

    final public List<Rack> rackList;
    final public List<Chassis> chassisList;
    final public List<Host> hostList;
    final public List<Disk> diskList;
    final public List<Location> locationList;

    final public int rackCapacity;
    final public int chassisCapacity;
    final public int hostCapacity;
    final public int diskCapacity;
    final public int capacity;

    private static int nextGlobalIndex = 0;

    public Zone(String zoneName, Map<String, Map<String, Map<String, List<Integer>>>> zoneData) {
        name = zoneName;

        globalIndex = nextGlobalIndex++;

        parent = null;
        childList = rackList = new ArrayList<>();
        int rackIndex = 0;
        for(Map.Entry<String, Map<String, Map<String, List<Integer>>>> entry: zoneData.entrySet()) {
            String rackName = entry.getKey();
            Rack rack = new Rack(rackName, rackIndex, this, entry.getValue());
            rackList.add(rack);
            rackIndex++;
        }

        rackCapacity = rackList.size();
        chassisList = rackList.stream().flatMap(r -> r.chassisList.stream()).collect(Collectors.toList());
        chassisCapacity = chassisList.size();
        hostList = chassisList.stream().flatMap(c -> c.hostList.stream()).collect(Collectors.toList());
        hostCapacity = hostList.size();
        diskList = hostList.stream().flatMap(h -> h.diskList.stream()).collect(Collectors.toList());
        diskCapacity = diskList.size();
        locationList = diskList.stream().flatMap(d -> d.locationList.stream()).collect(Collectors.toList());
        capacity = locationList.size();
    }

    public Rack getRackFromName(String name) {
        return (Rack) findName(rackList, name);
    }

    public Disk getDiskFromName(String name) {
        return (Disk) findName(diskList, name);
    }

    @Override
    public Zone next() {
        return (Zone) super.next();
    }
}
