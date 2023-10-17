package util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.Stack;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.collections.Iterator2Collection;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.graph.labeled.LabeledGraph;
import com.ibm.wala.util.graph.labeled.SlowSparseNumberedLabeledGraph;

public class ExpressionPrinter {

	static int exprNumber = 0;

	public static void main(String[] args) throws JSONException, IOException {
		JSONArray exprs = new JSONArray();
		
		JSONTokener toks = new JSONTokener(new FileInputStream(args[0]));
		toks.nextValue();
		toks.nextValue();

		while (toks.more()) {
			JSONObject expressionData = (JSONObject) toks.nextValue();

			if (((JSONObject)expressionData).has("expressions")) {
				File f = new File(((JSONObject)expressionData).getString("analysis_file"));	
				File file = System.getProperty("data_dir") != null?
					new File(System.getProperty("data_dir") + File.separator + f.getName()):
					f;
				
				if (file.exists()) {
					JSONObject data = 
						new JSONObject(
							new JSONTokener(
								new BZip2CompressorInputStream(
									new FileInputStream(file))));
					
					JSONArray turtles = data.getJSONArray("turtle_analysis");

					LabeledGraph<JSONObject,Either<Integer,String>> turtleGraph = new SlowSparseNumberedLabeledGraph<>();
					for(int i = 0; i < turtles.length(); i++) {
						if (! turtles.isNull(i)) {
							JSONObject o = turtles.getJSONObject(i);
							if (o != null) {
								if (! turtleGraph.containsNode(o)) {
									turtleGraph.addNode(o);
								}

								JSONObject e = o.getJSONObject("edges");
								if (e.has("flowsTo")) {
									JSONObject ft = e.getJSONObject("flowsTo");
									ft.keySet().forEach(ns -> { 
										Either<Integer, String> label = getLabel(ns);

										JSONArray targets = ft.getJSONArray(ns);
										for(int j = 0; j < targets.length(); j++) {
											JSONObject target = turtles.getJSONObject(targets.getInt(j));
											if (! turtleGraph.containsNode(target)) {
												turtleGraph.addNode(target);
											}
											turtleGraph.addEdge(o, target, label);
										}
									});
								}
							}
						}
					}
					((JSONObject)expressionData).getJSONArray("expressions").forEach(e -> { 
						try {
						JSONObject eo = (JSONObject)e;
						int idx = eo.getInt("nodeNumber");
						if (! turtles.isNull(idx)) {
							JSONObject n = turtles.getJSONObject(idx);
							Pair<Set<String>, String[]> exps = printExpression(n, turtleGraph, new Stack<>());
							for (String s : exps.snd) {
								JSONObject exp = new JSONObject();
								exp.put("json_file",  file);
								exp.put("node_number", n.getNumber("nodeNumber"));
								
								if (n.has("sourceText")) {
									exp.put("source_code", n.get("sourceText"));
									exp.put("source_file", data.getString("filename"));	
								}

								exp.put("fields", exps.fst);
								exp.put("code",  "lambda df: " + s);
								
								exp.put("expr_name", "expr_" + (exprNumber++));
								exprs.put(exp);
							}
						}
						} catch (OutOfMemoryError ex) {
							
						}
					});
				}
			}
		}
		
		try (PrintWriter x = new PrintWriter(System.out)) {
			exprs.write(x, 2, 0);
		}
	}

	private static Either<Integer, String> getLabel(String ns) {
		Either<Integer,String> label; 
		try {
			Integer n = Integer.valueOf(ns);
			label = Either.forLeft(n);
		} catch (NumberFormatException nfe) {
			label = Either.forRight(ns);
		}
		return label;
	}

	private static Pair<Set<String>,String[]> back(Either<Integer,String> label, JSONObject node, LabeledGraph<JSONObject,Either<Integer,String>> turtles, Stack<JSONObject> stack) {
		String[] cnst = getConstant(label, node);
		if (cnst != null) {
			return Pair.make(Collections.emptySet(), cnst);
		}
		
		Set<String> result = HashSetFactory.make();
		Set<String> fields = HashSetFactory.make();
		
		if (turtles.getPredNodeCount(node, label) > 5) {
			return Pair.make(Collections.emptySet(), new String[] {  "UNKNOWN" });
		}
		
		turtles.getPredNodes(node , label).forEachRemaining(p -> {
			if (! stack.contains(p)  && stack.size() < 6) {
				stack.push(p);
				Pair<Set<String>, String[]> c = printExpression(p, turtles, stack);
				fields.addAll(c.fst);
				for(String x : c.snd) {
					result.add(x);
				}
				stack.pop();
			}
		});
		
		Set<String> result2 = result;
		if (label.isRight()) {
			result2 = result2.stream().map(s -> label.getRight() + "=" + s).collect(Collectors.toSet());
		}
	
		return Pair.make(fields, result2.toArray(new String[ result2.size()]));
	}

	private static String dump(Object constant) {
		if (constant instanceof Boolean) {
			return ((Boolean)constant)? "True": "False";
		} else if (constant instanceof String) {
			return "\"" + constant + '"';
		} else if (constant == null) {
			return "None";
		} else {
			return "" + constant;
		}
	}
	
	private static String[] getConstant(Either<Integer, String> label, JSONObject node) {
		if (label.isLeft() && node.has("constant_positional_args")) {
			JSONObject cpa = node.getJSONObject("constant_positional_args");
			String ll = "" + label.getLeft();
			if (cpa.has(ll) && !((cpa.get(ll) instanceof JSONArray) && cpa.getJSONArray(ll).length() == 0)) {
				return new String[] { dump(cpa.get(ll)) };
			}
		}

		if (label.isRight() && node.has("constant_named_args")) {
			JSONObject cpa = node.getJSONObject("constant_named_args");
			String rl = label.getRight();
			if (cpa.has(rl) && !((cpa.get(rl) instanceof JSONArray) && cpa.getJSONArray(rl).length() == 0)) {
				return new String[] { rl + "=" + dump(cpa.get(rl)) };
			}
		}
		
		return null;
	}
	
	private static String decodeOp(String op) {
		switch (op) {
		case "add": return "+";	
		case "sub": return "-";
		case "minus": return "-";
		case "mul": return "*";
		case "div": return "/";
		case "rem": return "%";
		case "lt": return "<";
		case "le": return "<=";
		case "gt": return ">";
		case "ge": return ">=";
		case "eq": return  "==";
		case "ne": return  "!=";
		case "and": return "and";
		default:
			assert false : "unknown op " + op;
			return null;
		}
	}
	
	private static Pair<Set<String>,String[]> printExpression(JSONObject node, LabeledGraph<JSONObject,Either<Integer,String>> turtles, Stack<JSONObject> stack) {
		if (node.has("reads") && node.getJSONArray("reads").length() > 0) {
			String f = node.getJSONArray("reads").getJSONObject(0).getString("field");
			return Pair.make(Collections.singleton(f), new String[]{"df[ '" + f + "' ]"});
		
		} else if (node.has("op")) {
			Pair<Set<String>, String[]> l = back(Either.forLeft(0), node, turtles, stack);
			if (turtles.getPredNodeCount(node, Either.forLeft(1)) > 0 || getConstant(Either.forLeft(1), node) != null) {
				Pair<Set<String>, String[]> r = back(Either.forLeft(1), node, turtles, stack);
				String[] result = new String[ l.snd.length*r.snd.length ];
				for(int i = 0; i < l.snd.length; i++) {
					for(int j = 0; j < r.snd.length; j++) {
						result[i] = "(" + l.snd[i] + " " + decodeOp(node.getString("op")) + " " + r.snd[j] + ")";
					}
				}
				Set<String> fields = HashSetFactory.make(l.fst);
				fields.addAll(r.fst);
				
				return Pair.make(fields, result);
			} else {
				String[] result = new String[ l.snd.length ];
				for(int i = 0; i < l.snd.length; i++) {
					result[i] = decodeOp(node.getString("op")) + " " + l.snd[i];
				}
				return Pair.make(l.fst, result);
			}

		} else if (node.has("is_import") && node.getBoolean("is_import")) {
			JSONArray path = node.getJSONArray("path");
			String f = path.join(".");
			return Pair.make(Collections.emptySet(), new String[] { f });
		 
		} else {
			String[][] stuff = null;
			Set<String> fields = HashSetFactory.make();
			
			String pkg = node.has("path")? '"'+node.getJSONArray("path").getString(0)+'"': null;
			
			Comparator<Either<Integer, String>> order = (l, r) -> {
				if (l.isLeft()) {
					if (r.isLeft()) {
						return r.getLeft() - l.getLeft();
					} else {
						return 100;
					}
				} else {
					if (r.isLeft()) {
						return -100;
					} else {
						return r.getRight().hashCode() - l.getRight().hashCode();
					}
				}	
			};
		
			SortedSet<Either<Integer, String>> flowPreds = new TreeSet<>(order);
			
			turtles.getPredLabels(node).forEachRemaining(x -> flowPreds.add(x));
			int named = 0;
			if (node.has("constant_named_args")) {
				Iterator<String> namedKeys = node.getJSONObject("constant_named_args").keys();
				while (namedKeys.hasNext()) {
					String k = namedKeys.next();
					flowPreds.add(getLabel(k));
					named++;
				}
			}
			if (node.has("constant_positional_args")) {
				List<String> pos = Iterator2Collection.toList(node.getJSONObject("constant_positional_args").keys());
				for(int i = 0; i < pos.size() - named; i++) {
					flowPreds.add(getLabel(pos.get(i)));
				}
			}
		
			boolean call = false;
			Iterator<? extends Either<Integer, String>> preds = flowPreds.iterator();
			while (preds.hasNext()) {
				Either<Integer, String> pred = preds.next();
				Pair<Set<String>, String[]> b = back(pred, node, turtles, stack);
				fields.addAll(b.fst);
				if (stuff == null) {
					stuff = Arrays.stream(b.snd).map(s -> new String[] {s}).toArray(a -> new String[a][]);
				} else {
					int o = 0;
					boolean last = !preds.hasNext();
					String[][] newStuff = new String[ stuff.length * b.snd.length][];
					for (int i = 0; i < b.snd.length; i++) {
						for (int j = 0; j < stuff.length; j++) {
							if (last && pkg != null && pkg.equals(b.snd[i])) {
								call = true;
								newStuff[o++] = combine(new String[] {(String)node.getJSONArray("path").toList().stream().reduce((l, r) -> l+"."+r).get()}, stuff[j]);
							} else {
								newStuff[o++] = combine(new String[] {b.snd[i]}, stuff[j]);
							}
						}
					}
					stuff = newStuff;
					if (stuff.length > 100) {
						return Pair.make(Collections.emptySet(), new String[] { "UNKNOWN" });
					}
				}
			}
			if (stuff == null) {
				// no back edges...  perhaps it is a fake 'turtle' call
				if (node.has("path")) {
					JSONArray path = node.getJSONArray("path");
					if ("turtle".equals(path.get(0))) {
						String f = path.join(".");
						return Pair.make(Collections.emptySet(), new String[] { f });
					}
				}
				
				//assert stuff !=  null : node;
				return Pair.make(Collections.emptySet(), new String[] {"UNKNOWN"});
			}
			
			String[] exprs = null;
			if (node.has("path_end") && !call) {
				String method = node.getString("path_end");
				exprs = Arrays.stream(stuff).map(s -> { 
					String r = s[0] + "." + method + "(";
					if (s.length > 1) {
						r += Arrays.stream(s).skip(1).reduce((a, b) -> a + "," + b).get(); 
					}					
					r += ")";
					return r;
				}).toArray(a -> new String[a]);
			} else if (call) {
					exprs = Arrays.stream(stuff).map(s -> { 
						String r = s[0] + "(";
						if (s.length > 1) {
							r += Arrays.stream(s).skip(1).reduce((a, b) -> a + "," + b).get(); 
						}					
						r += ")";
						return r;
					}).toArray(a -> new String[a]);
			} else {
				exprs = Arrays.stream(stuff).map(s -> 
					Arrays.stream(s).reduce((a, b) -> a + " " + b).get()
				).toArray(a -> new String[a]);
			}
			
			return Pair.make(fields, exprs);
		}
	}
	
	private static String[] combine(String[] a, String [] b) {
		String[] r = new String[ a.length+b.length ];
		System.arraycopy(a, 0, r, 0, a.length);
		System.arraycopy(b, 0, r, a.length, b.length);
		return r;
	}
}
