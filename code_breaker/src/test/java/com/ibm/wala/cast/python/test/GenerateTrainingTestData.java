package com.ibm.wala.cast.python.test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IntSummaryStatistics;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;

import com.ibm.wala.util.collections.FilterIterator;
import com.ibm.wala.util.collections.HashMapFactory;
import com.ibm.wala.util.collections.IteratorUtil;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.graph.AbstractGraph;
import com.ibm.wala.util.graph.EdgeManager;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.GraphSlicer;
import com.ibm.wala.util.graph.NodeManager;
import com.ibm.wala.util.graph.impl.GraphInverter;
import com.ibm.wala.util.graph.impl.SlowSparseNumberedGraph;
import com.ibm.wala.util.graph.traverse.BFSPathFinder;
import com.ibm.wala.util.graph.traverse.DFS;

public class GenerateTrainingTestData {

    private List<Integer> edgeCounts = new LinkedList<>();
    private List<Integer> leafCounts = new LinkedList<>();
    private Map<Integer, Integer> pathLengthsForGraphs = new HashMap<>();

    private static Graph<String> snip(Graph<String> G, String predToSnip, String succToSnip) {
    	if (G.getPredNodeCount(succToSnip) == 1) {
    		return GraphSlicer.prune(G, n -> !n.equals(succToSnip));
    	} else {
    		return new AbstractGraph<String>() {

				@Override
				protected NodeManager<String> getNodeManager() {
					return G;
				}

				@Override
				protected EdgeManager<String> getEdgeManager() {
					return new EdgeManager<String>() {

						@Override
						public Iterator<String> getPredNodes(String n) {
							if (succToSnip.equals(n)) {
								return new FilterIterator<>(G.getPredNodes(n), x -> !predToSnip.equals(x));
							} else {
								return G.getPredNodes(n);
							}
						}

						@Override
						public int getPredNodeCount(String n) {
							return IteratorUtil.count(getPredNodes(n));
						}

						@Override
						public Iterator<String> getSuccNodes(String n) {
							if (predToSnip.equals(n)) {
								return new FilterIterator<>(G.getSuccNodes(n), x -> !succToSnip.equals(x));
							} else {
								return G.getSuccNodes(n);
							}
						}

						@Override
						public int getSuccNodeCount(String N) {
							return IteratorUtil.count(getSuccNodes(N));
						}

						@Override
						public void addEdge(String src, String dst) {
							assert false;
						}

						@Override
						public void removeEdge(String src, String dst) throws UnsupportedOperationException {
							throw new UnsupportedOperationException();
						}

						@Override
						public void removeAllIncidentEdges(String node) throws UnsupportedOperationException {
							throw new UnsupportedOperationException();
						}

						@Override
						public void removeIncomingEdges(String node) throws UnsupportedOperationException {
							throw new UnsupportedOperationException();
						}

						@Override
						public void removeOutgoingEdges(String node) throws UnsupportedOperationException {
							throw new UnsupportedOperationException();
						}

						@Override
						public boolean hasEdge(String src, String dst) {
							if (src.equals(predToSnip) && dst.equals(succToSnip)) {
								return false;
							} else {
								return G.hasEdge(src, dst);
							}
						}
					};
				}   			
    		};
    	}
    }
    
    
    public <T> int snippedGraphs(Graph<String> G, Function<Pair<Graph<String>, Graph<String>>,T> f, int limit) {
    	int count = 0;
    	for(String n : G) {
    		if (count > limit) {
    			break;
    		}
    		if (G.getSuccNodeCount(n) == 0) {
    			Set<String> slice = GraphSlicer.slice(G, l -> n.equals(l));
    			Graph<String> S = GraphSlicer.prune(G, l -> slice.contains(l));
    			for (Iterator<String> preds = S.getPredNodes(n); preds.hasNext(); ) {
    				String p = preds.next();
    				if (S.getPredNodeCount(p) > 0) {
    					Graph<String> snipped = snip(S, p, n);
    					f.apply(Pair.make(S, snipped));
    					count++;
    					count += snippedGraphs(snipped, f, limit - count);
    				}
    			};
    		}
    	}
    	return count;
    }
    
    class Box {
    	long i;
    }

    private Box b = new Box();

    private Map<String, String> nodeToPath = HashMapFactory.make();
    
    public void gatherStatistics(String fileName) {
        Model m = ModelFactory.createDefaultModel();
        m.read(fileName);

        StmtIterator it = m.listStatements(null, m.getProperty("http://edge/dataflow"), (RDFNode) null);
        int i = 0;
        SlowSparseNumberedGraph<String> graph = SlowSparseNumberedGraph.make();

        Set<String> nodes = new HashSet<String>();

        while(it.hasNext()) {
            Statement s = it.nextStatement();
            StmtIterator sit = ((Model) m).listStatements(s.getSubject(), m.getProperty("http://path"), (RDFNode) null);
            String subj = sit.nextStatement().getObject().toString();

            sit = ((Model) m).listStatements((Resource) s.getObject(), m.getProperty("http://path"), (RDFNode) null);
            String obj = sit.nextStatement().getObject().toString();

            if (!nodes.contains(s.getSubject().toString())) {
                graph.addNode(s.getSubject().toString());
                nodeToPath.put(s.getSubject().toString(), subj);
            }
            if (!nodes.contains(s.getObject().toString())) {
                graph.addNode(s.getObject().toString());
                nodeToPath.put(s.getObject().toString(), obj);
            }
            graph.addEdge(s.getSubject().toString(), s.getObject().toString());
            i++;
        }

        snippedGraphs(graph, g -> { b.i++; return null; }, 10);
        
        int leafNodeCount = 0;
        Graph<String> iGraph = GraphInverter.invert(graph);

        Map<Integer, Integer> pathLengthsForPreds = new HashMap<Integer, Integer>();

        for (String node: graph) {
            if (!graph.getSuccNodes(node).hasNext()) {
                leafNodeCount++;
                Set<String> preds = DFS.getReachableNodes(iGraph, Collections.singleton(node));
                Set<String> rootPreds = new HashSet<String>();
                for (String n : preds) {
                    if (graph.getPredNodeCount(n) == 0) {
                        rootPreds.add(n);
                    }
                }

                for (String n : rootPreds) {
                    BFSPathFinder<String> pathFinder = new BFSPathFinder<String>(graph, n, node);
                    List<String> path = pathFinder.find();
					int pathLength = path.size();
                    if (!pathLengthsForPreds.containsKey(pathLength)) {
                        pathLengthsForPreds.put(pathLength, 0);
                    }
                    pathLengthsForPreds.put(pathLength, pathLengthsForPreds.get(pathLength) + 1);
                    if (pathLength > 36) {
                    	System.err.println(path);
                    }
                    if (!pathLengthsForGraphs.containsKey(pathLength)) {
                        pathLengthsForGraphs.put(pathLength, 0);
                    }
                    pathLengthsForGraphs.put(pathLength, pathLengthsForGraphs.get(pathLength) + 1);
                }
            }
        }

        int maxPathLength = 0;

        for (Integer pathLength: pathLengthsForPreds.keySet()) {
            if (pathLength > maxPathLength) {
                maxPathLength = pathLength;
            }
        }
        System.out.println("File:" + fileName);
        System.out.println("edge count:" + i);
        System.out.println("leaf_node_count:" + leafNodeCount);
        System.out.println("max path length:" + maxPathLength);
        System.out.println("path lengths -> frequency" + pathLengthsForPreds);
        edgeCounts.add(i);
        leafCounts.add(leafNodeCount);

    }

    private void printSummaryStatistics(List<Integer> counts) {
        int[] eCounts = counts.stream().mapToInt(Integer::intValue).toArray();
        IntStream stream = IntStream.of(eCounts);
        IntSummaryStatistics summary_data = stream.summaryStatistics();
        System.out.println(summary_data);
    }

    private void printSummaryStatistics() {
        System.out.println("Overall edge count statistics");
        printSummaryStatistics(edgeCounts);
        System.out.println("Overall leaf node count statistics");
        printSummaryStatistics(leafCounts);
        System.out.println("Overall path length count statistics");
        for (Integer pathLength: pathLengthsForGraphs.keySet()) {
            System.out.println("Path length:" + pathLength + " count:" + pathLengthsForGraphs.get(pathLength));
        }
        System.out.println("graphs for training: " + b.i);
  }

    public static void main(String[] args) throws Exception {
        List<Path> files = Files.walk(Paths.get(args[0]))
                //use to string here, otherwise checking for path segments
                .filter(p -> p.toString().endsWith(".ttl"))
                .collect(Collectors.toList());
        GenerateTrainingTestData trainer = new GenerateTrainingTestData();
        for (Path p : files) {
            trainer.gatherStatistics(p.toString());
        }
        trainer.printSummaryStatistics();

    }
}
