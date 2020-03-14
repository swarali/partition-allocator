package allocator;

import org.apache.commons.cli.*;
import org.apache.commons.io.FilenameUtils;
import topology.Disk;
import topology.Topology;
import topology.Zone;

import java.io.FileNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Context {
    static final int REPLICATION_FACTOR = 3;

    final String topologyFile;
    final double fillLevel;
    final public Topology topology;

    final public Zone zone;

    final String allocationFile;
    final public Allocation allocation;

    final String newAllocationFile;

    public Context(String[] args) throws ParseException, FileNotFoundException {
        Options options = new Options();
        options.addOption("t", "topology-file", true, "Topology file");
        options.addOption("i", "partitions-input-file", true, "Existing allocations file");
        options.addOption("o", "partitions-output-file", true, "New allocations file");
        options.addOption("f", "fill-level", true, "Fill level (default=0.8)");
        options.addOption("p", "print", false, "Only print data about the existing allocation");

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);

        // Read topology of the zone and adjust used capacity by fill-level
        topologyFile = cmd.getOptionValue("topology-file");
        fillLevel = Double.parseDouble(cmd.getOptionValue("fill-level", "0.8"));
        topology = new Topology(topologyFile, fillLevel, REPLICATION_FACTOR);

        // Create zone object from topology
        String zoneName = Topology.getZoneNameFromFileName(topologyFile);
        zone = new Zone(zoneName, topology.dataUsed);

        allocationFile = cmd.getOptionValue("partitions-input-file", "");

        // Read already existing allocation else set to empty allocation
        String defaultNewAllocFile = "new_alloc.part";
        if(!allocationFile.isEmpty()) {
            String defaultNewAllocFileName = "new_" + FilenameUtils.getName(allocationFile);
            defaultNewAllocFile = FilenameUtils.concat(FilenameUtils.getPath(allocationFile), defaultNewAllocFileName);
        }
        newAllocationFile = cmd.getOptionValue("partitions-output-file", defaultNewAllocFile);

        allocation = new Allocation(zone, allocationFile);

        boolean onlyPrint = cmd.hasOption("print");
        if (onlyPrint) {
            if(allocation.existingPartitionList.isEmpty()) {
                System.out.println("Empty partition data. Cannot print allocation statistics");
                System.exit(1);
            }
            Allocator.printAllocationStatistics(allocation, zone);
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

    }
}
