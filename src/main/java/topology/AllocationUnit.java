package topology;

import java.util.List;

public abstract class AllocationUnit implements Comparable<AllocationUnit> {

    public String name;
    public int index;
    public int globalIndex;

    public AllocationUnit parent;
    public List<? extends AllocationUnit> childList;

    @Override
    public String toString() {
        return name;
    }

    @Override
    public int compareTo(AllocationUnit allocationUnit) {
        if(this.getClass() != allocationUnit.getClass()) {
            throw new ClassCastException(this + " and " + allocationUnit + " are of different allocation types");
        }
        return this.globalIndex - allocationUnit.globalIndex;
    }

    public AllocationUnit findName(List<? extends AllocationUnit> l, String name) {
        return l.stream().filter(au -> au.name.equals(name)).findFirst().orElse(null);
    }

    public AllocationUnit next() {
        int nextIndex = index+1;

        if(nextIndex == parent.childList.size()) return  null;

        return parent.childList.get(nextIndex);
    }
}
