package topology;

import java.util.ArrayList;
import java.util.List;

public class Disk extends AllocationUnit{
    public static final int LEVEL = 4;

    public Zone zone;
    public Rack rack;
    public Chassis chassis;
    public Host host;
    public List<Location> locationList;

    public int capacity;

    private static int nextGlobalIndex = 0;

    public Disk(int diskIndex, Host parentHost, int diskCapacity) {
        name = parentHost.name+"["+(diskIndex+1)+"]";
        index = diskIndex;
        globalIndex = nextGlobalIndex++;

        zone = parentHost.zone;
        rack = parentHost.rack;
        chassis = parentHost.chassis;
        parent = host = parentHost;

        childList = locationList = new ArrayList<>();
        for(int locationIndex = 0; locationIndex < diskCapacity; locationIndex++) {
            locationList.add(new Location(locationIndex, this));
        }

        capacity = locationList.size();
    }

    @Override
    public Disk next() {
        return (Disk) super.next();
    }
}
