package topology;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Host extends AllocationUnit {
    public static final int LEVEL = 3;

    public Zone zone;
    public Rack rack;
    public Chassis chassis;
    public List<Disk> diskList;
    public List<Location> locationList;

    public int diskCapacity;
    public int capacity;

    private static int nextGlobalIndex = 0;

    public Host(String hostName, int hostIndex, Chassis parentChassis, List<Integer> hostData) {
        name = hostName;
        index = hostIndex;
        globalIndex = nextGlobalIndex++;

        zone = parentChassis.zone;
        rack = parentChassis.rack;
        parent = chassis = parentChassis;

        childList = diskList = new ArrayList<>();
        int diskIndex = 0;
        for(int diskCapacity: hostData) {
            diskList.add(new Disk(diskIndex, this, diskCapacity));
            diskIndex++;
        }

        diskCapacity = diskList.size();
        locationList = diskList.stream().flatMap(d -> d.locationList.stream()).collect(Collectors.toList());
        capacity = locationList.size();
    }

    @Override
    public Host next() {
        return (Host) super.next();
    }
}
