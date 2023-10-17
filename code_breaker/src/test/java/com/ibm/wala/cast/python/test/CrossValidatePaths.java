package com.ibm.wala.cast.python.test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.jena.query.Dataset;
import org.apache.jena.riot.RDFDataMgr;

import com.ibm.wala.codeBreaker.turtle.TurtleGraphUtil;
import com.ibm.wala.util.collections.HashMapFactory;


public class CrossValidatePaths {

	private static final int PATH_LENGTH_LIMIT = 5;
	
    public static void main(String[] args) {
        Dataset trainGraph = RDFDataMgr.loadDataset(args[0]);
        File testFilesDirectory = new File(args[1]);
        String[] test_files = testFilesDirectory.list();
        int totalNoPaths = 0;
        int successfulPaths = 0;
        int maxPathLength = 0;
        List<Integer> numOut = new ArrayList<>();
        Map<Integer,List<Integer>> numOutContext = HashMapFactory.make();

        for (int i = 1; i < 2; i++) {
 
        	System.out.println(testFilesDirectory + "/" + test_files[i]);
        	Dataset testGraph = RDFDataMgr.loadDataset(testFilesDirectory + "/" + test_files[i]);
             // find all the nodes with no outgoing edges in the test graph
            // these are the leaf nodes we will start with to stitch a path together
            List<String> nodes = TurtleGraphUtil.getAllNodes(testGraph);
            System.out.println("Leaf nodes:" + nodes);
            Set<List<String>> seen_paths = new HashSet<>();
            for (String n : nodes) {
                Collection<List<String>> paths = TurtleGraphUtil.backwardPaths(testGraph, n);
                int nodeOutgoingEdges = TurtleGraphUtil.checkNodeOutgoingEdges(testGraph, trainGraph, n);
                System.out.println("number of node outgoing edges:" + nodeOutgoingEdges);
                if (nodeOutgoingEdges > 0) {
                    numOut.add(nodeOutgoingEdges);
                }

                for (List<String> p : paths) {
                    if (seen_paths.contains(p) || p.size() > PATH_LENGTH_LIMIT) {
                        continue;
                    }
                    seen_paths.add(p);
                    int pathLength = p.size();
					maxPathLength = Math.max(pathLength, maxPathLength);

                    if (TurtleGraphUtil.checkPath(trainGraph, p)) {
                        successfulPaths++;
                        if (pathLength > 1) {
                            int pathOutgoingEdges = TurtleGraphUtil.checkPathOutgoingEdges(trainGraph, p);
                            System.out.println("number of path outgoing edges:" + pathOutgoingEdges);
                            if (! numOutContext.containsKey(pathLength)) {
                            	numOutContext.put(pathLength, new ArrayList<>());
                            }
                            numOutContext.get(pathLength).add(pathOutgoingEdges);
                        }

                    } else {
                        System.out.println("Failed to find path:" + p);
                    }
                    
                    totalNoPaths++;
                }

            }
        }

        System.out.println("max path length:" + maxPathLength);
        System.out.println("total number of paths:" + totalNoPaths);
        System.out.println("successful paths:" + successfulPaths);
        System.out.println("number of out edges with no context:" + sum(numOut));
        System.out.println("mean number of paths with no context:" + sum(numOut)/numOut.size());
        for(Integer i : numOutContext.keySet()) {
        	System.out.println("number of out edges with context " + i + ":" + sum(numOutContext.get(i)));
        	System.out.println("mean number of paths with context " + i + ":" + sum(numOutContext.get(i))/numOutContext.get(i).size());
        }
    }

    public static int sum(List<Integer> arr) {
        int sum = 0;
        for (int i : arr) {
            sum += i;
        }
        return sum;
    }

}
