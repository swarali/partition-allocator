package allocator;

import org.chocosolver.solver.Solver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.nio.file.StandardOpenOption.APPEND;

public class GraphvizGenerator extends org.chocosolver.solver.trace.GraphvizGenerator {

    private final Path instance;

    private static final String ROOT = "\t-1 [label=\"ROOT\", shape = doublecircle, color = gray];\n";
    private static final String NODE = "\t%d [label = \"%s\" shape = circle];\n";
    private static final String ROOTEDGE = "\t%d -> %d;\n";

    public GraphvizGenerator(String gvFile, Solver aSolver) {
        super(gvFile, aSolver);
        instance = Paths.get(gvFile);
    }
    @Override
    protected void sendNode(int nc, int pid, int alt, int kid, int rid, String label, String info) {
        if(pid == -1){
            try {
                if(nc == 0) {
                    Files.write(instance, ROOT.getBytes(), APPEND);
                }
                Files.write(instance, String.format(NODE, nc, label).getBytes(), APPEND);
                Files.write(instance, String.format(ROOTEDGE, pid, nc).getBytes(), APPEND);
            } catch (IOException e) {
                System.err.println("Unable to write to GEXF file. No information will be sent.");
                connected = false;
            }
        }else{
            super.sendNode(nc, pid, alt, kid, rid, label, info);
        }
    }
}
