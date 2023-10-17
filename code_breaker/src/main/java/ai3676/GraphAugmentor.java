package ai3676;

import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.ibm.wala.cast.java.ecj.util.SourceDirCallGraph;
import com.ibm.wala.cast.java.ecj.util.SourceDirCallGraph.Processor;
import com.ibm.wala.cast.loader.AstMethod;
import com.ibm.wala.cast.loader.AstMethod.DebuggingInformation;
import com.ibm.wala.cast.tree.CAstSourcePositionMap.Position;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAPhiInstruction;
import com.ibm.wala.util.collections.HashMapFactory;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.graph.AbstractGraph;
import com.ibm.wala.util.graph.EdgeManager;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.NodeManager;

public class GraphAugmentor {

	public static void main(String... args) throws ClassHierarchyException, IllegalArgumentException, CallGraphBuilderCancelException, IOException {
		Map<Integer, Pair<Integer, Integer>> tokenMap = HashMapFactory.make();
		CSVParser csvParser = CSVFormat.DEFAULT.withHeader().parse(new FileReader(System.getProperty("tokenFile")));
		for (CSVRecord token : csvParser) {
			int id = Integer.valueOf(token.get("seqnr"));
			int startOffset = Integer.valueOf(token.get("start"));
			int endOffsetInclusive = Integer.valueOf(token.get("stop"));
			tokenMap.put(id, Pair.make(startOffset, endOffsetInclusive));
		}
		
		JSONObject parseTreeJson = 
			(JSONObject)new JSONTokener(new FileInputStream(System.getProperty("parseTreeFile")))
			.nextValue();

		Graph<JSONObject> parseTree = new AbstractGraph<JSONObject>() {

			NodeManager<JSONObject> nodes = new NodeManager<JSONObject>() {
				private Set<JSONObject> a = HashSetFactory.make();
				
				{
					parseTreeJson
						.getJSONObject("graph")
						.getJSONArray("nodes")
						.forEach(n -> a.add((JSONObject)n));
				}

				@Override
				public Stream<JSONObject> stream() {
					return a.stream();
				}

				@Override
				public int getNumberOfNodes() {
					return a.size();
				}

				@Override
				public void addNode(JSONObject n) {
					throw new UnsupportedOperationException();
				}

				@Override
				public void removeNode(JSONObject n) throws UnsupportedOperationException {
					throw new UnsupportedOperationException();
				}

				@Override
				public boolean containsNode(JSONObject n) {
					return a.contains(n);
				}
				
			};
			
			@Override
			protected NodeManager<JSONObject> getNodeManager() {
				return nodes;
			}

			private EdgeManager<JSONObject> edges = new EdgeManager<JSONObject>() {
				private Map<JSONObject,Set<JSONObject>> forward = HashMapFactory.make();
				private Map<JSONObject,Set<JSONObject>> backward = HashMapFactory.make();
				
				{
					Map<Integer,JSONObject> idToNode = HashMapFactory.make();
					JSONArray nodes = parseTreeJson
						.getJSONObject("graph")
						.getJSONArray("nodes");
					for(int i = 0; i < nodes.length(); i++) {
						JSONObject node = nodes.getJSONObject(i);
						idToNode.put(node.getInt("id"), node);
					}
					parseTreeJson
						.getJSONObject("graph")
						.getJSONArray("edges")
						.forEach(n -> {
							JSONObject e = (JSONObject)n;
							JSONArray edge = e.getJSONArray("between");
							JSONObject src = idToNode.get(edge.getInt(0));
							JSONObject dst = idToNode.get(edge.getInt(1));
							if (! forward.containsKey(src)) {
								forward.put(src, HashSetFactory.make());
							}
							forward.get(src).add(dst);
							if (! backward.containsKey(dst)) {
								backward.put(dst, HashSetFactory.make());
							}
							backward.get(dst).add(src);
						});
				}

				@Override
				public Iterator<JSONObject> getPredNodes(JSONObject n) {
					return backward.get(n).iterator();
				}

				@Override
				public int getPredNodeCount(JSONObject n) {
					return backward.get(n).size();
				}

				@Override
				public Iterator<JSONObject> getSuccNodes(JSONObject n) {
					return forward.get(n).iterator();
				}

				@Override
				public int getSuccNodeCount(JSONObject N) {
					return forward.get(N).size();
				}

				@Override
				public void addEdge(JSONObject src, JSONObject dst) {
					throw new UnsupportedOperationException();
				}

				@Override
				public void removeEdge(JSONObject src, JSONObject dst) throws UnsupportedOperationException {
					throw new UnsupportedOperationException();
				}

				@Override
				public void removeAllIncidentEdges(JSONObject node) throws UnsupportedOperationException {
					throw new UnsupportedOperationException();
				}

				@Override
				public void removeIncomingEdges(JSONObject node) throws UnsupportedOperationException {
					throw new UnsupportedOperationException();
				}

				@Override
				public void removeOutgoingEdges(JSONObject node) throws UnsupportedOperationException {
					throw new UnsupportedOperationException();
				}

				@Override
				public boolean hasEdge(JSONObject src, JSONObject dst) {
					return forward.get(src).contains(dst);
				}
				
			};
			
			@Override
			protected EdgeManager<JSONObject> getEdgeManager() {
				return edges;
			}
		
		};
		
		(new SourceDirCallGraph()).doit(args, new Processor() {
			
			private Map<JSONObject, Pair<Integer, Integer>> locations = HashMapFactory.make();
			private SortedMap<Integer,Set<JSONObject>> offsetToNodes = new TreeMap<>(new Comparator<Integer>() {
				@Override
				public int compare(Integer o1, Integer o2) {
					return o1 - o2;
				}
			});
			
			private Pair<Integer,Integer> location(JSONObject node) {
				Pair<Integer,Integer> result;
				if (locations.containsKey(node)) {
					return locations.get(node);
				} else {
					if (node.getString("node-type").equals("Token")) {
						result = tokenMap.get(node.getInt("id"));
					} else {
						int start = Integer.MAX_VALUE;
						int end = Integer.MIN_VALUE;
						Iterator<JSONObject> ss = parseTree.getSuccNodes(node);
						while (ss.hasNext()) {
							Pair<Integer,Integer> s = location(ss.next());
							if (s.fst < start) {
								start = s.fst;
							}
							if (s.snd > end) {
								end = s.snd;
							}
						}
						result = Pair.make(start, end);
					}
					locations.put(node, result);
					for(int i = result.fst; i <= result.snd; i++) {
						if (! offsetToNodes.containsKey(i)) {
							offsetToNodes.put(i,  HashSetFactory.make());
						}
						
						offsetToNodes.get(i).add(node);
					}
					return result;
				}
			}
			
			@Override
			public void process(CallGraph cg, CallGraphBuilder<?> builder, long time) {
				parseTree.forEach(jsonNode -> { 
					location(jsonNode);
				});
				
				for (CGNode n : cg) {
					if (n.getMethod() instanceof AstMethod) {
						DefUse DU = n.getDU();
						DebuggingInformation DI = ((AstMethod)n.getMethod()).debugInfo();
						Set<Pair<JSONObject,JSONObject>> df = HashSetFactory.make();
						n.getIR().iterateAllInstructions().forEachRemaining(inst -> { 
							if (inst.iIndex() >= 0 && inst.getDef() > 0) {
								Position src = DI.getInstructionPosition(inst.iIndex());
								if (src != null) {
									for(int i = src.getFirstOffset(); i <= src.getLastOffset(); i++) {
										if (offsetToNodes.containsKey(i)) {											
											offsetToNodes.get(i).forEach(srcNode -> {
												if ("expression".equals(srcNode.get("type-rule-name"))) {
													JSONArray dataflowTo;
													if (srcNode.has("dataflow")) {
														dataflowTo = srcNode.getJSONArray("dataflow");
													} else {
														dataflowTo = new JSONArray();
														srcNode.put("dataflow", dataflowTo);
													}
													DU.getUses(inst.getDef()).forEachRemaining(new Consumer<SSAInstruction>() {
														private final Set<SSAInstruction> history = HashSetFactory.make();

														@Override
														public void accept(SSAInstruction succ) {
															if (! history.contains(succ)) {
																history.add(succ);
																if (succ instanceof SSAPhiInstruction) {
																	DU.getUses(succ.getDef()).forEachRemaining(ss -> accept(succ));
																}  else {
																	Position dst = DI.getInstructionPosition(succ.iIndex());
																	if (dst != null) {
																		for(int j = dst.getFirstOffset(); j <= dst.getLastOffset(); j++) {
																			if (offsetToNodes.containsKey(j)) {
																				offsetToNodes.get(j).forEach(dstNode -> {
//																					if ("expression".equals(dstNode.get("type-rule-name"))) {
																						Pair<JSONObject, JSONObject> key = Pair.make(srcNode,  dstNode);
																						if (! df.contains(key)) {
																							df.add(key);
																							dataflowTo.put(dstNode.getInt("id"));
																						}
//																					}
																				});
																			}
																		}
																	}
																}
															}
														}	
													});
												}
											});
										}
									}
								}
							}
						});
					}
				}
				
				try (Writer out = new FileWriter(System.getProperty("parseTreeFile").substring(0, System.getProperty("parseTreeFile").length()-4) + ".df.json")) {
					parseTreeJson.write(out, 4, 0);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});
	}
}
