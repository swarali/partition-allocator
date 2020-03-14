package topology;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Topology {

    double fillPercent;
    public Map<String, Map<String, Map<String, List<Integer>>>> data;
    public Map<String, Map<String, Map<String, List<Integer>>>> dataUsed;

    public Topology(String fileName, double fillPercent, int replicationFactor) throws FileNotFoundException {
        this.fillPercent = fillPercent;

        double carryAhead = 0;
        int capacityUsed = 0;
        data = readFromFile(fileName);

        dataUsed = new LinkedHashMap<>();
        Map<String, Map<String, List<Integer>>> rackData = null;
        Map<String, List<Integer>> chassisData = null;
        List<Integer> hostData = null;

        for(Map.Entry<String, Map<String, Map<String, List<Integer>>>> zEntry: data.entrySet()) {
            String rack = zEntry.getKey();
            rackData = new LinkedHashMap<>();
            for (Map.Entry<String, Map<String, List<Integer>>> rEntry: zEntry.getValue().entrySet()) {
                String chassis = rEntry.getKey();
                chassisData = new LinkedHashMap<>();
                for(Map.Entry<String, List<Integer>> cEntry: rEntry.getValue().entrySet()) {
                    String host = cEntry.getKey();
                    hostData = new ArrayList<>();
                    for (int diskCapacity: cEntry.getValue()) {
                        double diskCapacityDouble = (diskCapacity * fillPercent) + carryAhead;
                        int diskCapacityUsed = (int) diskCapacityDouble;
                        carryAhead = diskCapacityDouble - (double) diskCapacityUsed;
                        capacityUsed += diskCapacityUsed;
                        hostData.add(diskCapacityUsed);
                    }
                    chassisData.put(host, hostData);
                }
                rackData.put(chassis, chassisData);
            }
            dataUsed.put(rack, rackData);
        }

        if(capacityUsed % replicationFactor != 0) {
            System.out.format("Capacity %d is not divisible by %d. Fixing last capacity", capacityUsed, replicationFactor);
            int lastCapacity = hostData.get(hostData.size()-1);
            lastCapacity-= capacityUsed % replicationFactor;
            hostData.set(hostData.size()-1, lastCapacity);
        }

    }

    public static String getZoneNameFromFileName(String fileName) {
        Pattern topFileNamePattern = Pattern.compile("zone-(.*).top");
        Pattern yamlFileNamePattern = Pattern.compile("topo-(.*).yaml");
        Matcher m;

        m = topFileNamePattern.matcher(fileName);
        if(m.find()) {
            return m.group(1);
        }
        m = yamlFileNamePattern.matcher(fileName);
        if(m.find()) {
            return m.group(1);
        }

        return "default-zone";
    }

    public static Map<String, Map<String, Map<String, List<Integer>>>> readFromFile(String fileName) throws FileNotFoundException {
        if(fileName.endsWith(".yaml")) {
            InputStream inputStream = new FileInputStream(fileName);
            return new Yaml().load(inputStream);
        } else if (fileName.endsWith(".top")){
            return readFromTopFile(fileName);
        }
        return null;
    }

    private static Map<String, Map<String, Map<String, List<Integer>>>> readFromTopFile(String fileName) throws FileNotFoundException {
        Map<String, Map<String, Map<String, List<Integer>>>> zoneTopology = new LinkedHashMap<>();

        InputStream inputStream = new FileInputStream(fileName);
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        String rack = "default-rack";
        String chassis = "default-chassis";
        String line;
        Pattern rackLinePattern = Pattern.compile("# rack (.*) chassis (.*)");
        Pattern hostLinePattern = Pattern.compile("host (\\w*) \\w* ([ \\d*]*)");
        try {
            while ((line = bufferedReader.readLine()) != null) {
                if(line.isEmpty()) {
                    continue;
                }

                Matcher rackLineMatcher = rackLinePattern.matcher(line);
                if(rackLineMatcher.find()) {
                    rack = rackLineMatcher.group(1);
                    chassis = rackLineMatcher.group(2);

                    if (!zoneTopology.containsKey(rack)) {
                        zoneTopology.put(rack, new LinkedHashMap<>());
                    }

                    Map<String, Map<String, List<Integer>>> rackTopology = zoneTopology.get(rack);

                    if (!rackTopology.containsKey(chassis)) {
                        rackTopology.put(chassis, new LinkedHashMap<>());
                    }
                    Map<String, List<Integer>> chassisTopology = rackTopology.get(chassis);
                    continue;
                }

                Matcher hostLineMatcher = hostLinePattern.matcher(line);
                if(hostLineMatcher.find()) {
                    String host = hostLineMatcher.group(1);
                    List<Integer> hostDiskCapacity = Arrays.stream(hostLineMatcher.group(2).trim().split("\\s+"))
                            .mapToInt(v -> Integer.parseInt(v))
                            .boxed()
                            .collect(Collectors.toList());

                    if(!zoneTopology.containsKey(rack)) {
                        zoneTopology.put(rack, new LinkedHashMap<>());
                    }

                    if(!zoneTopology.get(rack).containsKey(chassis)) {
                        zoneTopology.get(rack).put(chassis, new LinkedHashMap<>());
                    }

                    if(zoneTopology.get(rack).get(chassis).containsKey(host)) {
                        String message = "Zone topology contains duplicates entries for host: " + host + ". Only keeping the first entry.";
                        System.err.println(message);
                    }
                    zoneTopology.get(rack).get(chassis).put(host, hostDiskCapacity);
                }
            }
        } catch (IOException e) {
            System.out.println("Unable to read topology file: " + fileName);
            e.printStackTrace();
        }
        System.out.println("Zone topology: " + zoneTopology);
        return zoneTopology;
    }

    public static void writeToFile(String fileName, Map<String, Map<String, Map<String, List<Integer>>>> data) {
        DumperOptions options = new DumperOptions();
        //options.setDefaultFlowStyle(DumperOptions.FlowStyle.FLOW);
        //options.setSplitLines(false);

        String yaml = new Yaml(options).dump(data).replace("\'", "\"");
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
