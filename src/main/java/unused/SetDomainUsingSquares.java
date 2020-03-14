package unused;

import allocator.Allocator;
import allocator.Util;
import org.chocosolver.solver.search.strategy.selectors.values.SetValueSelector;
import org.chocosolver.solver.variables.SetVar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class SetDomainUsingSquares implements SetValueSelector {

    List<Integer> domain;
    Allocator allocator;
    List<Integer> values;
    int index;

    public SetDomainUsingSquares(List<Integer> d, Allocator a){
        domain = d;
        allocator = a;
        int numberOfPartitions = domain.size();
        //int numberOfPartitionsSqrt = (int)Math.sqrt(numberOfPartitions);
        //assert numberOfPartitions == numberOfPartitionsSqrt*numberOfPartitionsSqrt;
        List<Integer> SqrtList = getSqrtList(numberOfPartitions);
        int horizontalLength = getGCD();
        int verticalLength = numberOfPartitions / horizontalLength;
        System.out.println("Rectangle: " + horizontalLength + " x " + verticalLength);
        if(verticalLength < horizontalLength) {
            System.out.println("VerticalLength is <= HorizontalLength");
            //System.exit(1);
        }

        values = new ArrayList<>();
        // Horizontal iterator
        List<Integer> horizontalDomain = new ArrayList<>();
        for (int i = 0; i < horizontalLength; i++) {
            for (int j = 0; j < verticalLength; j++) {
                int index =  i * verticalLength + j;
                horizontalDomain.add(domain.get(index));
            }
        }
        values.addAll(horizontalDomain);
        System.out.println(Util.sequenceNum(horizontalDomain));

        // Vertical iterator
        List<Integer> verticalDomain = new ArrayList<>();
        for (int i = 0; i < verticalLength; i++) {
            for (int j = 0; j < horizontalLength; j++) {
                int index =  i + j * verticalLength;
                verticalDomain.add(domain.get(index));
            }
        }
        values.addAll(verticalDomain);
        System.out.println(Util.sequenceNum(verticalDomain));

        // Diagonal iterator
        List<Integer> diagonalDomain = new ArrayList<>();
        for (int i = 0; i < verticalLength; i++) {
            int index = i;
            for (int j = 0; j < horizontalLength; j++) {
                diagonalDomain.add(domain.get(index));
                if((index % verticalLength) == 0) {
                    index+=(verticalLength + verticalLength -1);
                } else {
                    index+=(verticalLength -1);
                }
            }
        }
        values.addAll(diagonalDomain);
        System.out.println(Util.sequenceNum(diagonalDomain));

        System.out.println(values);
        index = 0;
    }

    public int getGCD() {
        int gcd = domain.size();

        List<Integer> diskCapacities = allocator.context.zone.diskList.stream().map(d -> d.capacity).collect(Collectors.toList());
        for(int diskCapacity: diskCapacities) {
            int num1 = diskCapacity;
            int num2 = gcd;
            while(num1 != num2) {
                if(num1 > num2) {
                    num1-=num2;
                } else {
                    num2-=num1;
                }
            }
            gcd = num1;
        }
        return gcd;
    }
    public List<Integer> getSqrtList(int n) {
        int a, b, c, d, na, nb, nc, nd;
        int count = 0;

        for (a = 0, na = n; a * a <= na; a++) {
            for (b = 0, nb = na - a * a; b * b <= nb; b++) {
                for (c = 0, nc = nb - b * b; c * c <= nc; c++) {
                    for (d = 0, nd = nc - c * c; d * d <= nd; d++) {
                        if (d * d == nd) {
                            System.out.println(a +", " + b + ", " +  c + ", " + d);
                            return Arrays.asList(a, b, c, d);
                        }
                    }
                }
            }
        }
        return null;
    }

    @Override
    public int selectValue(SetVar v) {
       int val = values.get(index % values.size());
       index++;
       return val;
    }
}
