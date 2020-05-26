Partition allocator
==

Tool for creating an efficient allocation for storage partitions on a given datacenter topology.

Uses [Choco-solver](https://github.com/chocoteam/choco-solver) as a constraint solver

The final allocation tries to optimize the following in decreasing order of priority:
- 3 partition replicas for every partition
- Minimize duplicate partitions stored in the same component - disk(high priority) -> host -> chassis -> rack(low priority)
- Minimize partition overlaps between components - disk(high priority) -> host -> chassis -> rack(low priority)
- (almost) Uniform partition intersection between 2-tuple and 3-tuple of disks, hosts, chassis 
- Minimize movement of partitions across components during topology changes (zone expansion, chassis failures etc)

Build prerequisites:
==
* JDK (1.9+)
* Maven (3.6.3)

To package to a jar:
```$bash
mvn package
```

Run:
==
Options to run the allocation can be seen as follows:
```$bash
$ java -jar target/part-allocator-1.0-SNAPSHOT.jar --help

usage: partalloc [-f <arg>] [-h] [-i <arg>] [-o <arg>] [-p] [-t <arg>]
       [-v]
 -f,--fill-level <arg>               Fill level (default=0.8)
 -h,--help                           Show this message
 -i,--partitions-input-file <arg>    Existing allocations file
 -o,--partitions-output-file <arg>   New allocations file
 -p,--print                          Print data about the existing
                                     allocation
 -t,--topology-file <arg>            Topology file
 -v,--verbose                        Verbose output
```

For a fresh allocation:
-
```$bash
$ java -jar target/part-allocator-1.0-SNAPSHOT.jar -t <topology-file>
```

\<topology-file> can be a yaml file or a top file.
Check out example files from the data directory

For modifying an existing allocation
-
Modify the <topology-file> and run the following:
```$bash
$ java -jar target/part-allocator-1.0-SNAPSHOT.jar -t <topology-file> -i <input-allocation-file> -o <output-allocation-file> -f <fill-level>
```
\<input-allocation-file\> and \<output-allocation-file\> are typically yaml files with extension '.part'

For displaying statistics about an existing allocation
-
```$bash
$ java -jar target/part-allocator-1.0-SNAPSHOT.jar -p -t <topology-file> -i <input-allocation-file>
```
This prints information about the existing allocation

Motivation
==
Allocation of cloud resources like VMs over hypervisors, storage partitions over disks in
the cloud environment need to satisfy various interrelated conditions to ensure reliability
and performance. These problems can be efficiently modeled and solved with constraint
programming. In this project we model the storage partition allocation problem in datacenter
and study strategies to improve it. We use constraint programming for allocation of storage
partitions in the data center, expansion of datacenters and to optimize reliability and recovery
(in case of disk/host failures) while minimizing the impact on network and existing allocation.

The existing allocator at Exoscale uses a combination of constraint programming and
randomization to allocated storage partitions. By formulating a custom search strategy on the
constraint solver and leveraging uniformity at low-levels of topology hierarchy, we designed
a storage partition allocator integrated completely with the constraint solver. We compare
the performance of the allocator with the existing allocator in terms of run-time, quality of
solution on datacenter topologies at Exoscale. The storage partition allocator is significantly
faster than the existing allocator at exoscale for large topology changes in the zone. It generates a fairer
distribution of partitions across all hierarchies of the datacenter topology.

More details can be found in this [report](report.pdf)

