package topology;

public class Location extends AllocationUnit{
    public static final int LEVEL = 5;

    public Zone zone;
    public Rack rack;
    public Chassis chassis;
    public Host host;
    public Disk disk;

    private static int nextGlobalIndex = 0;

    public Location(int locationIndex, Disk parentDisk) {
        name = parentDisk.name+"["+(locationIndex+1)+"]";
        index = locationIndex;
        globalIndex = nextGlobalIndex++;

        zone = parentDisk.zone;
        rack = parentDisk.rack;
        chassis = parentDisk.chassis;
        host = parentDisk.host;
        parent = disk = parentDisk;
        childList = null;
    }

    @Override
    public Location next() {
        return (Location) super.next();
    }

}
