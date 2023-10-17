package com.ibm.wala.codeBreaker.turtle;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.sparql.resultset.ResultsFormat;

import com.ibm.wala.util.collections.HashSetFactory;

public class TurtleGraphUtil {

	private static File pythonBin = new File(System.getProperty("pythonBin", "../venv36/bin/python"));
	
	private static File scriptDir = new File(System.getProperty("testDir", "../examples/github_good"));

	@FunctionalInterface
	public interface EdgeConsumer {
		void edge(String nodeid);
	}


	public static void stepBackEdge(Dataset rdf, String dsturi, EdgeConsumer f) {
		String queryStr = "select ?s where { "
					+ "?s <http://edge/dataflow> <" + dsturi + "> . "
				+ "}";

		QueryExecution exec = QueryExecutionFactory.create(queryStr, rdf);
		ResultSet results = exec.execSelect();

		results.forEachRemaining((qs) -> {
			f.edge(qs.get("s").toString());
		});
	}

	public static String findNodeUri(Dataset rdf, String srcSource, String srcPos) {
		String queryStr = "select ?s where { "
				+ "?s <http://source> \"" + srcSource + "\" . "
				+ "?s <http://turtle_info> ?sp . "
				+ "FILTER(CONTAINS(?sp,\"" + srcPos + "\")) "
				+ "}";

		QueryExecution exec = QueryExecutionFactory.create(queryStr, rdf);
		ResultSet results = exec.execSelect();

		return results.next().get("s").toString();
	}

	public static String findNodePath(Dataset rdf, String src) {
		String queryStr = "select ?path where { "
				+ "<" + src + "> <http://path> ?path . "
				+ "}";

		QueryExecution exec = QueryExecutionFactory.create(queryStr, rdf);
		ResultSet results = exec.execSelect();

		return results.next().get("path").asLiteral().getString();
	}

	public static Collection<List<String>> backwardPaths(Dataset rdf, String dstSource, String dstPos) {
		Collection<List<String>> newPaths = Arrays.asList(Arrays.asList(findNodeUri(rdf, dstSource,  dstPos)));
		return findBackwardPaths(rdf, newPaths);
	}

	public static Collection<List<String>> backwardPaths(Dataset rdf, String nodeURI) {
		Collection<List<String>> newPaths = Arrays.asList(Arrays.asList(nodeURI));
		return findBackwardPaths(rdf, newPaths);
	}

	private static Collection<List<String>> findBackwardPaths(Dataset rdf, Collection<List<String>> newPaths) {
		Set<List<String>> paths = HashSetFactory.make();

		while (! newPaths.isEmpty()) {
			Set<List<String>> x = HashSetFactory.make();

			newPaths.forEach((p) -> {
				if (! paths.contains(p)) {
					stepBackEdge(rdf, p.get(0), (src) -> {
						if (! p.contains(src)) {
							List<String> np = new LinkedList<>(p);
							np.add(0, src);
							if (! paths.contains(np)) {
								x.add(np);
							}
						}

					});
				}
			});

			paths.addAll(newPaths);
			newPaths = x;
		}

		Set<List<String>> newTurtlePaths = HashSetFactory.make();

		Map<String, String> all_nodes = new HashMap<String, String>();
		paths.forEach((l)-> {
			List<String> turtlePath = new ArrayList<>();
			l.forEach((s)->{
				if (! all_nodes.containsKey(s)) {
					String path = findNodePath(rdf, s);
					all_nodes.put(s, path);
				}
				turtlePath.add(all_nodes.get(s));
			});
			newTurtlePaths.add(turtlePath);
		});

		return newTurtlePaths;
	}

	public static boolean checkPath(Dataset rdf, List<String> path) {
		StringBuffer buf = new StringBuffer("ASK {");
		if (path.size() == 1) {
			return true;
		}
		for (int i = 0; i < path.size(); i++) {
			buf.append("?s" + i + " <http://path> \"" + path.get(i) + "\" . ");
			if (i < path.size() - 1) {
				buf.append("?s" + i + " <http://edge/dataflow> ?s" + (i + 1) + " . ");
			}
		}

		buf.append("}");
		System.out.println(buf.toString());

		QueryExecution exec = QueryExecutionFactory.create(buf.toString(), rdf);
		boolean result = exec.execAsk();
		return result;
	}

	public static int checkPathOutgoingEdges(Dataset rdf, List<String> path) {
		StringBuffer buf = new StringBuffer("SELECT (count(distinct ?path) as ?c) where {");
		if (path.size() == 1) {
			buf.append("?s" + 0 + " <http://path> \"" + path.get(0) + "\" . ");
			buf.append("?s" + 0 + " <http://edge/dataflow> ?z . ");
		} else {
			for (int i = 0; i < path.size(); i++) {
				buf.append("?s" + i + " <http://path> \"" + path.get(i) + "\" . ");
				buf.append("?s" + i + " <http://edge/dataflow> ?s" + (i + 1) + " . ");
			}
			buf.append("?s" + path.size() + " <http://path> ?path . ");

		}

		buf.append("}");
		System.out.println(buf.toString());

		QueryExecution exec = QueryExecutionFactory.create(buf.toString(), rdf);
		ResultSet results = exec.execSelect();
		return Integer.parseInt(results.next().getLiteral("c").getString());
	}

	public static int checkNodeOutgoingEdges(Dataset testGraph, Dataset trainGraph, String node) {
		StringBuffer buf = new StringBuffer("SELECT ?path where { <" + node + "> <http://path> ?path . }");
		QueryExecution exec = QueryExecutionFactory.create(buf.toString(), testGraph);
		ResultSet results = exec.execSelect();
		String path = results.next().getLiteral("?path").getString();

		buf = new StringBuffer("SELECT (count(distinct ?path) as ?c) where {");
		buf.append("?s <http://path> \"" + path + "\" . ");
		buf.append("?s <http://edge/dataflow> ?z  . ");
		buf.append("?z <http://path> ?path .");

		buf.append("}");
		System.out.println(buf.toString());

		exec = QueryExecutionFactory.create(buf.toString(), trainGraph);
		results = exec.execSelect();
		return Integer.parseInt(results.next().getLiteral("c").getString());

	}


	public static void checkEdge(Dataset rdf, String pred, String srcSource, String srcPos, String dstSource, String dstPos) {
		String queryStr = "select ?sp ?tp where { "
				+ "?s " + pred + " " + srcSource + "\" . "
				+ "?t " + pred + " " + dstSource + "\" . "
				+ "?s <http://edge/dataflow> ?t . "
				+ "?s <http://turtle_info> ?sp . "
				+ "?t <http://turtle_info> ?tp . "
				+ "FILTER(CONTAINS(?sp,\"" + srcPos + "\")) "
				+ "FILTER(CONTAINS(?tp,\"" + dstPos + "\")) }";
		
		QueryExecution exec = QueryExecutionFactory.create(queryStr, rdf);
		ResultSet results = exec.execSelect();
		
		assert results.hasNext() : "no results for " + srcSource + " --> " + dstSource;
		
		ResultSetFormatter.output(
			System.out,
			results, 
			ResultsFormat.FMT_RDF_TURTLE);
	}


	public static List<String> getAllNodes(Dataset rdf) {
		String queryStr = "select distinct ?s where {?s ?p ?o}";
		QueryExecution exec = QueryExecutionFactory.create(queryStr, rdf);
		ResultSet results = exec.execSelect();

		List<String> nodes = new ArrayList<>();
		results.forEachRemaining((qs) -> {
			nodes.add(qs.get("s").toString());
		});
		return nodes;
	}


	public static void checkSourceEdge(Dataset rdf, String srcSource, String srcPos, String dstSource, String dstPos) {
		 checkEdge(rdf, "<http://source>", srcSource, srcPos, dstSource, dstPos);
	}

	public static Dataset toDataset(List<String> files) throws IOException, FileNotFoundException {
		File rdfGraph = File.createTempFile("rdfGraph", ".ttl");
		//rdfGraph.deleteOnExit();
		
		File jsonLog = File.createTempFile("edges", ".json");
		//jsonLog.deleteOnExit();
		
		Path dir = Files.createTempDirectory("rdf");
		for(String file : files) {
			Files.copy(new FileInputStream(new File(scriptDir, file)), Paths.get(dir.toString(), file));
		}
		
		Process python = Runtime.getRuntime().exec(new String[] {pythonBin.getAbsolutePath(), "CollectAllGraphs.py", dir.toString(), rdfGraph.getAbsolutePath(), jsonLog.getAbsolutePath()}, null, new File("../code_miner"));
		
		String line;
		BufferedReader errs = new BufferedReader(new InputStreamReader(python.getErrorStream()));
		while  ((line = errs.readLine()) != null) {
			System.err.println(line);
		}
		
		return RDFDataMgr.loadDataset(rdfGraph.getAbsolutePath());
	}
}