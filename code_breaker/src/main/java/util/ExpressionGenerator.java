package util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.Stack;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.ibm.wala.util.collections.HashMapFactory;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.collections.Iterator2Collection;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.graph.labeled.LabeledGraph;
import com.ibm.wala.util.graph.labeled.SlowSparseNumberedLabeledGraph;
import com.ibm.wala.util.graph.traverse.DFS;
import com.ibm.wala.util.intset.IntSetUtil;
import com.ibm.wala.util.intset.MutableIntSet;

public class ExpressionGenerator {

	static int exprNumber = 0;

	static boolean DEBUG = true;


	public static JSONObject handleAnalysisRequest(String file_name) throws IOException, WalaException, CancelException {
		return new RunTurtleAnalysis(new File(file_name), file_name).test();
	}

	private static Iterable<JSONObject> estimateExpressions(LabeledGraph<JSONObject,Either<Integer,String>> turtleGraph, Set<String> fields, Set<String> allCsvFiles) {
		Set<JSONObject> roots = turtleGraph.stream().filter(j -> {
			if (j.has("path")) {
				JSONArray p = j.getJSONArray("path");
				//int last = p.length()-1;
				if (p.getString(0).equals("pandas") /*&&
					(p.getString(last).equals("DataFrame") ||
					 p.getString(last).equals("read_csv"))*/) {
					return true;
				}
			}
			return false;			
		}).collect(Collectors.toSet());

		roots.forEach(n-> {
			if (n.has("constant_positional_args")) {
				gatherFiles(allCsvFiles, n.getJSONObject("constant_positional_args"));
			}
			if (n.has("constant_named_args")) {
				gatherFiles(allCsvFiles, n.getJSONObject("constant_named_args"));
			}
		});

		Collection<JSONObject> allFlows = DFS.getReachableNodes(turtleGraph, roots);

		MutableIntSet containers = IntSetUtil.make();
		allFlows.forEach(n -> containers.add(n.getInt("nodeNumber")));

		Set<JSONObject> readNodes = HashSetFactory.make();
		turtleGraph.forEach(n -> {
			if (n.has("reads")) {
				JSONArray reads = n.getJSONArray("reads");
				for(int i = 0; i < reads.length(); i++) {
					JSONObject obj = reads.getJSONObject(i);
					JSONArray cc = obj.getJSONArray("container");
					for(int j = 0; j < cc.length(); j++) {
						if (containers.contains(cc.getInt(j))) {
							readNodes.add(n);
							if (obj.has("field") && obj.get("field") instanceof String) {
								String field = obj.getString("field");
								if (!field.startsWith("[") && !field.equals("loc")) {
									fields.add(field);
								}
							}
						}
					}
				}
			}
		});

		// System.err.println(readNodes.size());

		Set<JSONObject> allReadNodes = DFS.getReachableNodes(turtleGraph, readNodes);

		/*
		for(JSONObject r : allReadNodes) {
			System.err.println(r.getInt("nodeNumber"));
		}
		 */

		allReadNodes.stream()
				.filter(n -> n.has("path_end") && n.getString("path_end").equals("rename"))
				.forEach(n -> {
					if (n.has("constant_named_args")) {
						JSONObject args = n.getJSONObject("constant_named_args");
						if (args.has("columns")) {
							JSONObject columns = args.getJSONObject("columns");
							columns.keys().forEachRemaining(k -> {
								fields.add(k);
								if (columns.get(k) instanceof String) {
									fields.add(columns.getString(k));
								}
							});
						}
					}
				});

		return allReadNodes
				.stream()
				.filter(n -> isWrite.test(n) || 
						turtleGraph.getSuccNodeCount(n)>0 ||
						n.has("updates") ||
						( turtleGraph.getPredNodeCount(n, Either.forLeft(0)) == 1 &&
						  turtleGraph.getPredNodes(n, Either.forLeft(0)).next().has("reads") &&
						  turtleGraph.getPredNodeCount(n) > 1))						  
				.collect(Collectors.toSet());
	}
	
	private static Predicate<JSONObject> isWrite = n -> (n.has("writes") && n.getJSONArray("writes").length()>0); 

	private static void gatherFiles(Set<String> allCsvFiles, JSONObject cpa) {
		Consumer<String> f = k -> {
			Object v = cpa.get(k);
			if (v instanceof String) {
				String str = (String) v;
				if (str.endsWith(".csv") || str.endsWith(".xls") || str.endsWith(".txt")) {
					allCsvFiles.add(str);
				}
			}
		};
		cpa.keys().forEachRemaining(f);
	}

	public static void main(String... args) throws JSONException, IOException {
		JSONObject csv2scripts =
				new JSONObject(
						new JSONTokener(
										new FileInputStream(args[0])));

		JSONObject indexedExprsByDataset = new JSONObject();

		csv2scripts.keySet().forEach(keyStr ->
		{
			JSONArray arr = new JSONArray();
			indexedExprsByDataset.put(keyStr, arr);
			JSONArray scripts = (JSONArray) csv2scripts.get(keyStr);
			scripts.forEach(script ->
			{
				try {
					String str_script = (String) script;
					JSONObject data = handleAnalysisRequest(str_script);
					JSONObject indexedExprs = indexCodeByField(index(data, str_script, keyStr), str_script);
					arr.put(indexedExprs);
				} catch (Exception e) {
					e.printStackTrace();
				}
			});

		});

		try (FileWriter w = new FileWriter(args[1])) {
			indexedExprsByDataset.write(w, 3, 0);
			w.flush();
		}
		System.err.println(indexedExprCount + " expressions found");
		System.exit(0);
	}

	private static void index(File file, String indexName) {
		try {
			JSONObject data =
					new JSONObject(
							new JSONTokener(
									new BZip2CompressorInputStream(
											new FileInputStream(file))));
			indexCodeByField(index(data, file.toString(), indexName), indexName);
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	private static Pattern f = Pattern.compile("\\[ \\'([^\\']*)\\' \\]");
	
	private static Set<String> fields(JSONArray code) {
		int start = 0;
		Set<String> fields = HashSetFactory.make();
		for(int i = 0; i < code.length(); i++) {
			String line = code.getString(i);
			Matcher m = f.matcher(line);
			while (start < line.length() && m.find(start)) {
				String field = m.group(1);
				fields.add(field);
				start = m.end();
			}
		}
		return fields;
	}
	
	private static int indexedExprCount = 0;
	private static final Map<String,Integer> n = HashMapFactory.make();

	
	private static JSONObject indexCodeByField(JSONArray sortedExprs, String url) {
		JSONObject indexedExprs = new JSONObject();
		Set<Pair<Integer,Integer>> ranges = HashSetFactory.make();
		sortedExprs.forEach(e -> { 
			JSONObject s = (JSONObject)e;
			if (s.has("sourceLocation") ) {
				JSONObject pos = s.getJSONObject("sourceLocation");
				Pair<Integer, Integer> p = Pair.make(pos.getInt("firstOffset"), pos.getInt("lastOffset"));
				ranges.add(p);
				System.err.println(s.get("rawCode") + ": " + p);
			}
		});
		
		
		exprs: for(int i = 0; i < sortedExprs.length(); i++) {
			JSONObject expr = sortedExprs.getJSONObject(i);
			
			if (expr.has("sourceLocation")) {
			JSONObject myPos = expr.getJSONObject("sourceLocation");
			int myStart = myPos.getInt("firstOffset");
			int myEnd = myPos.getInt("lastOffset");
			for(Pair<Integer,Integer> r : ranges) {
				System.err.println (myStart + " " + myEnd + " " + r.fst + " " + r.snd);
				if ((myStart > r.fst && myEnd <= r.snd) || (myStart >= r.fst && myEnd < r.snd)) {
					System.err.println("killing " + expr.get("rawCode"));
					
					continue exprs;
				}
			}
			}
			
			JSONArray code = expr.getJSONArray("rawCode");
			Set<String> fields = fields(code);
			String f;
			if (fields.isEmpty()) {
				f = "none";
			} else if (fields.size() > 1) {
				f = "all";
			} else {
				f = fields.iterator().next();
			}
		
			if (! indexedExprs.has(f)) {
				indexedExprs.put(f,  new JSONObject());
			}
			JSONObject urls = indexedExprs.getJSONObject(f);
			if (! urls.has(url)) {
				urls.put(url, new JSONObject());
			}
			
			int idx = n.containsKey(f)? n.get(f): 0;
			
			String fn = f.replaceAll(" ", "_") + "_" + idx;
			JSONArray dependencies = new JSONArray();
			if (fields.size() > 1) {
				for(String d : fields) {
					if (indexedExprs.has(d) && indexedExprs.getJSONObject(d).has(url)) {
						JSONObject exps = indexedExprs.getJSONObject(d).getJSONObject(url);
						Iterator<String> dnms = exps.keys();
						exps: while (dnms.hasNext()) {
							String dnm = dnms.next();
							if (exps.getJSONObject(dnm).has("writtenFields")) {
								exp: {
									JSONArray x = exps.getJSONObject(dnm).getJSONArray("writtenFields");
									for(int j = 0; j < x.length(); j++) {
										if (d.equals(x.getString(j))) {
											break exp;
										}
									}
									continue exps;
								}
							}
							dependencies.put(dnm);
						}
					}
				} 
			}
			
			JSONArray text = new JSONArray();
			if (expr.has("writtenFields") && expr.getJSONArray("writtenFields").length() == 1) {
				assert code.length() == 1;
				text.put("df[ '" + expr.getJSONArray("writtenFields").get(0) + "' ] = " + code.getString(0));
			} else {
				text = code;
			}
			
			JSONObject obj = new JSONObject();
			obj.put("code",  text);
			if (dependencies.length() > 0) {
				obj.put("dependencies", dependencies);
			}
			if (expr.has("writtenFields")) {
				obj.put("writtenFields", expr.get("writtenFields"));
			}
			
			urls.getJSONObject(url).put(fn, obj); 
			indexedExprCount++;
			
			n.put(f, idx + 1);
		}
		
		return indexedExprs;
	}
	
	private static JSONArray turtles;
	
	public static JSONArray index(JSONObject data, String file, String indexName) {
		JSONArray exprs = new JSONArray();
		ElasticSearchClient cl = DEBUG? null: new ElasticSearchClient();
		Map<String,Set<JSONObject>> groups = HashMapFactory.make();

		try {

			turtles = data.getJSONArray("turtle_analysis");

			LabeledGraph<JSONObject, Either<Integer, String>> turtleGraph = new SlowSparseNumberedLabeledGraph<>();
			for (int i = 0; i < turtles.length(); i++) {
				if (!turtles.isNull(i)) {
					JSONObject o = turtles.getJSONObject(i);
					if (o != null) {
						if (!turtleGraph.containsNode(o)) {
							turtleGraph.addNode(o);
						}

						JSONObject e = o.getJSONObject("edges");
						if (e.has("flowsTo")) {
							JSONObject ft = e.getJSONObject("flowsTo");
							ft.keySet().forEach(ns -> {
								Either<Integer, String> label = getLabel(ns);

								JSONArray targets = ft.getJSONArray(ns);
								for (int j = 0; j < targets.length(); j++) {
									JSONObject target = turtles.getJSONObject(targets.getInt(j));
									if (!turtleGraph.containsNode(target)) {
										turtleGraph.addNode(target);
									}
									turtleGraph.addEdge(o, target, label);
								}
							});
						}
					}
				}
			}

			Set<String> code = HashSetFactory.make();
			Set<String> fields = HashSetFactory.make();
			Set<String> allCsvFiles = HashSetFactory.make();
			Iterable<JSONObject> stuff = estimateExpressions(turtleGraph, fields, allCsvFiles);
			stuff.forEach(x -> System.err.println("estimated expression " + x.getInt("nodeNumber")));
			
			Function<? super JSONObject, List<JSONObject>> handleExprs = e -> {
				List<JSONObject> myExprs = new LinkedList<>();
				try {
					JSONObject eo = (JSONObject) e;
					int idx = eo.getInt("nodeNumber");
					if (!turtles.isNull(idx)) {
						JSONObject n = turtles.getJSONObject(idx);
						Pair<Set<String>, StringTree[]> exps = printExpression(n, turtleGraph, new Stack<>());
						//System.err.println(idx + ":" + exps)
						if (exps.fst.isEmpty()) {
							return Collections.emptyList();
						}
						exprs:
						for (StringTree s : exps.snd) {
							if (code.contains(s.toString())) {
								continue exprs;
							}
							code.add(s.toString());

							Pattern simpleRead = Pattern.compile("^df\\[ \\'[-a-zA-Z0-9_]*\\' \\]$");
							if (s == null || s.toString().length() < 1 || simpleRead.matcher(s.toString()).matches()) {
								// System.err.println("skipping " + s);
								continue exprs;
							}

							String sourceCode = null;
							if (n.has("sourceText")) {
								sourceCode = n.getString("sourceText");
								for (String field : exps.fst) {
									if (!sourceCode.contains(field)) {
										continue exprs;
									}
								}
							}

							JSONObject exp = new JSONObject();
							// exp.put("json_file", file);
							exp.put("node_number", n.getNumber("nodeNumber"));

							if (n.has("sourceText")) {
								exp.put("source_code", sourceCode);
							}
							JSONArray x = new JSONArray();
							x.put(s.toString());
							exp.put("rawCode", x);
			
							if (n.has("sourceLocation")) {
								exp.put("sourceLocation",  n.get("sourceLocation"));
							}
							
							exp.put("source_file", file);
							exp.put("code", "lambda df: " + s);

							exp.put("expr_name", "expr_" + (exprNumber++));
							
							if (isWrite.test(n)) {
								Set<String> writtenFields = HashSetFactory.make();
								JSONArray writes = n.getJSONArray("writes");
								for(int i = 0; i <  writes.length(); i++) {
									JSONObject write = writes.getJSONObject(i);
									Object o = write.get("field");
									if (o instanceof String) {
										writtenFields.add((String)o);
									}
								}
								if (fields.size() > 0) {
									exp.put("writtenFields", writtenFields);
								}
							}

							exp.put("fields", fields);
							exp.put("csvfiles", allCsvFiles);
							myExprs.add(exp);
						}
					}
					return myExprs;
				} catch (OutOfMemoryError ex) {
					ex.printStackTrace();
					return Collections.emptyList();
				}
			};
			
			
			stuff.forEach(s -> handleExprs.apply(s).forEach(x -> { 
				if (isWrite.test(x)) {
					x.put("objectsWritten", x.getJSONArray("writes").getJSONObject(0).get("container"));
				}
				exprs.put(x); 
			}));
			
			stuff.forEach(s -> {
				if (s.has("updates")) {
					JSONArray updates = s.getJSONArray("updates");
					for (int i = 0; i < updates.length(); i++) {
						JSONArray update = updates.getJSONArray(i);
						System.err.println("update from stuff " + update);
						if (update.get(0) instanceof JSONArray) {
							JSONArray fieldsAndGuards = update.getJSONArray(0);
							if (fieldsAndGuards.get(0) instanceof Number && fieldsAndGuards.get(1) instanceof String) {
								System.err.println("turtle string pair " + updates);
								 List<JSONObject> tests = handleExprs.apply(turtles.getJSONObject(fieldsAndGuards.getInt(0)));
								 if (tests.size() > 0) {
								 JSONObject test = tests.get(tests.size()-1);
								 String field = fieldsAndGuards.getString(1);

								 if (update.get(1) instanceof JSONArray) {
									 System.err.println("turtle value " + update.get(1));
									 JSONArray val = update.getJSONArray(1);
									 for(int j = 0; j < val.length(); j++) {
										 List<JSONObject> rvals = handleExprs.apply(turtles.getJSONObject(val.getInt(j)));
										 System.err.println("turtle exprs " + rvals);
										 for(JSONObject rval : rvals) {
											 rval.put("sourceLocation", test.get("sourceLocation"));
											 rval.put("fieldWritten", field);
											 rval.put("objectsWritten", Collections.singletonList(s));
											 rval.put("guard", test);
										 }
									 }
								 } else {
									 System.err.println("object value " + update.get(1));
									 JSONObject rval = new JSONObject();
									 rval.put("sourceLocation", test.get("sourceLocation"));
									 String expr = "df.loc[" + test.getJSONArray("rawCode").getString(0).replace(" and ", " & ") + ", '" + field + "'] = " + update.get(1);
									 JSONArray x = new JSONArray();
									 x.put(expr);
									 rval.put("rawCode", x);
									 rval.put("objectsWritten", Collections.singletonList(s.get("nodeNumber")));
									 
									 if (! groups.containsKey(field)) {
										 groups.put(field, HashSetFactory.make());
									 }
									 groups.get(field).add(rval);
								 }
								 }
							}
							
						}
					}
				}	
			});
			
			groups.entrySet().forEach(gs -> {
				JSONObject loc = null;
				String field = gs.getKey();
				
				JSONArray rawCode = new JSONArray();
				for(JSONObject obj : gs.getValue()) {
					obj.getJSONArray("rawCode").forEach(l -> rawCode.put(l));
					if (obj.has("sourceLocation")) {
						JSONObject ol = obj.getJSONObject("sourceLocation");
						if (loc == null) {
							loc = new JSONObject(ol, JSONObject.getNames(ol));
						} else {
							if (loc.getInt("firstOffset") > ol.getInt("firstOffset")) {
								loc.put("firstOffset", ol.getInt("firstOffset"));
							}
							if (loc.getInt("lastOffset") < ol.getInt("lastOffset")) {
								loc.put("lastOffset", ol.getInt("lastOffset"));
							}
						}
					}
				}
				
				JSONObject o = new JSONObject();
				o.put("sourceLocation", loc);
				o.put("field", field);
				o.put("rawCode", rawCode);
				exprs.put(o);
			});

		} catch (Exception e) {
			e.printStackTrace();
		}

		if (ExpressionGenerator.DEBUG==true) {
			System.err.println(exprs);
		} else {
			for (int i = 0; i < exprs.length(); i++) {
				cl.insert(exprs.getJSONObject(i), indexName);
			}
		}

		SortedSet<JSONObject> sort = new TreeSet<>((a, b) -> {
			if (!a.has("sourceLocation")) {
				if (b.has("sourceLocation")) {
					return -1;
				} else {
					return a.toString().compareTo(b.toString());
				}
			} else if (! b.has("sourceLocation")) {
				return  1;
			}
			JSONObject ap = a.getJSONObject("sourceLocation");
			JSONObject bp = b.getJSONObject("sourceLocation");
			int s = ap.getInt("firstOffset") - bp.getInt("firstOffset");
			return s!=0? s: ap.getInt("lastOffset") - bp.getInt("lastOffset");
		});
		for(int i = 0; i < exprs.length(); i++) {
			sort.add(exprs.getJSONObject(i));
		}
		JSONArray exprs2 = new JSONArray();
		sort.forEach(x -> exprs2.put(x));

		/*
		try (PrintWriter pw = new PrintWriter(System.out)) {
			exprs2.write(pw, 3, 0);
		}
		*/
		
		return exprs2;
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

	private static Pair<Set<String>,StringTree[]> back(Either<Integer,String> label, JSONObject node, LabeledGraph<JSONObject,Either<Integer,String>> turtles, Stack<JSONObject> stack) {
		String[] cnst = getConstant(label, node);
		if (cnst != null) {
			return Pair.make(Collections.emptySet(), Arrays.asList(cnst).stream().map(x -> new StringLeaf(x)).collect(Collectors.toList()).toArray(new StringTree[cnst.length]));
		}

		Set<StringTree> result = HashSetFactory.make();
		Set<String> fields = HashSetFactory.make();

		if (turtles.getPredNodeCount(node, label) > 5) {
			return Pair.make(Collections.emptySet(), new StringTree[] {  new StringLeaf("UNKNOWN") });
		}

		turtles.getPredNodes(node , label).forEachRemaining(p -> {
			if (! stack.contains(p)  && stack.size() < 15) {
				stack.push(p);
				Pair<Set<String>, StringTree[]> c = printExpression(p, turtles, stack);
				fields.addAll(c.fst);
				for(StringTree x : c.snd) {
					result.add(x);
				}
				stack.pop();
			}
		});

		Set<StringTree> result2 = result;
		if (label.isRight()) {
			result2 = result2.stream().map(s -> new StringNode(new StringLeaf(label.getRight()), new StringLeaf("="), s)).collect(Collectors.toSet());
		}

		return Pair.make(fields, result2.toArray(new StringTree[ result2.size()]));
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
		case "or": return "or";
		case "bitnot": return "~";
			default:
			assert false : "unknown op " + op;
			return null;
		}
	}

	interface StringTree {
		
	}
	
	static class StringLeaf implements StringTree {
		private final String text;
		
		public StringLeaf(String string) {
			text = string;
		}

		public String toString() {
			return text;
		}
	}
	
	static class StringNode implements StringTree {
		protected String separator = " ";
		private final List<StringTree> children;
		
		public StringNode(StringTree... children) {
			this.children = Arrays.asList(children);
		}
		
		public String toString() {
			if (children.size() > 1) {
				return "(" + children.stream().map(c -> c.toString()).reduce((l, r) -> l + separator + r).get() + ")";
			} else if (children.size() > 0) {
				return children.stream().map(c -> c.toString()).reduce((l, r) -> l + separator + r).get();
			} else {
				return "()";
			}
		}
	}
	
	static class StringCall extends StringNode {
		private final StringTree call;
		private final String method;
		
		StringCall(String method, StringTree call, StringTree... args) {
			super(args);
			this.separator = ", ";
			this.method = method;
			this.call = call;
		}

		StringCall(StringTree call, StringTree... args) {
			this(null, call, args);
		}
		
		public String toString() {
			return call.toString() + (method!=null? "." + method: "") + "(" + super.toString() + "))";
		}
	}
	
	private static Pair<Set<String>,StringTree[]> printExpression(JSONObject node, LabeledGraph<JSONObject,Either<Integer,String>> turtles, Stack<JSONObject> stack) {		
		if (node.has("sourceText") && node.getString("sourceText").contains("income_cat")) {
			System.err.println(node);
		}
		
		int d = -1;
		if ((d = isImport(node)) >= 0) {
			System.err.println(d);
			JSONArray path = node.getJSONArray("path");
			/*
			if (node.has("sourceLines") && node.has("path_end")) {
				String pathEnd = node.getString("path_end");
				boolean from = false;
				JSONArray lines = node.getJSONArray("sourceLines");
				for(int i = 0; i < lines.length(); i++) {
					if (lines.getString(i).startsWith("from") && lines.getString(i).endsWith(pathEnd)) {
						from = true;
					}
				}
				if (from) {
					path.remove(path.length()-1);
				}
  			}
  			*/
			String f = path.join(".");
			f = f.replaceAll("^\"|\"$", "");
			f = f.replaceAll("\".\"", ".");
			System.err.println(f);
			return Pair.make(Collections.emptySet(), new StringTree[] { new StringLeaf(f) });

		} else if (node.has("reads") && node.getJSONArray("reads").length() > 0) {
			for(int i = 0; i < node.getJSONArray("reads").length(); i++) {
				JSONObject o = node.getJSONArray("reads").getJSONObject(i);
				if (o.get("field") instanceof String) {		
					String f = o.getString("field");
					if ("str".equals(f)) {
						JSONArray cturtles = o.getJSONArray("container");
						for(int ci = 0; ci < cturtles.length(); ci++) {
							int turtle = cturtles.getInt(ci);
							JSONObject realRead = ExpressionGenerator.turtles.getJSONObject(turtle);
							for(int ri = 0; ri < realRead.getJSONArray("reads").length(); ri++) {
								JSONObject ro = realRead.getJSONArray("reads").getJSONObject(ri);
								if (ro.get("field") instanceof String) {		
									String rf = ro.getString("field");
									return Pair.make(Collections.singleton(rf), new StringTree[]{new StringLeaf("df[ '" + rf + "' ].str")});
								}
							}
						}
					} else {
						return Pair.make(Collections.singleton(f), new StringTree[]{new StringLeaf("df[ '" + f + "' ]")});
					}
				}
			}

			return Pair.make(Collections.emptySet(), new StringTree[0]);
		} else if (node.has("op")) {
			Pair<Set<String>, StringTree[]> l = back(Either.forLeft(0), node, turtles, stack);
			String operator = decodeOp(node.getString("op"));
			if (turtles.getPredNodeCount(node, Either.forLeft(1)) > 0 || getConstant(Either.forLeft(1), node) != null) {
				Pair<Set<String>, StringTree[]> r = back(Either.forLeft(1), node, turtles, stack);
				StringTree[] result = new StringTree[ l.snd.length*r.snd.length ];
				for(int i = 0, k = 0; i < l.snd.length; i++) {
					for(int j = 0; j < r.snd.length; j++) {
						result[k++] = expression(l.snd[i], new StringLeaf(operator), r.snd[j]);
					}
				}
				Set<String> fields = HashSetFactory.make(l.fst);
				fields.addAll(r.fst);

				return Pair.make(fields, result);
			} else {
				StringTree[] result = new StringTree[ l.snd.length ];
				for(int i = 0; i < l.snd.length; i++) {
					result[i] = new StringNode(new StringLeaf(operator), l.snd[i]);
				}
				return Pair.make(l.fst, result);
			}

		} else {
			StringTree[][] stuff = null;
			Set<String> fields = HashSetFactory.make();

			String pkg = node.has("path")? node.getJSONArray("path").getString(0): null;

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
				Pair<Set<String>, StringTree[]> b = back(pred, node, turtles, stack);
				fields.addAll(b.fst);
				if (stuff == null || stuff.length == 0) {
					stuff = Arrays.stream(b.snd).map(s -> new StringTree[] {s}).toArray(a -> new StringTree[a][]);
				} else {
					int o = 0;
					boolean last = !preds.hasNext();
					StringTree[][] newStuff = new StringTree[ stuff.length * b.snd.length][];
					for (int i = 0; i < b.snd.length; i++) {
						for (int j = 0; j < stuff.length; j++) {
							if (last && pkg != null && b.snd[i].toString().startsWith(pkg)) {
								call = true;
								newStuff[o++] = combine(new StringTree[] {new StringLeaf((String)node.getJSONArray("path").toList().stream().reduce((l, r) -> l+"."+r).get())}, stuff[j]);
							} else {
								newStuff[o++] = combine(new StringTree[] {b.snd[i]}, stuff[j]);
							}
								}
					}
					
					stuff = newStuff;
					if (stuff.length > 100) {
						return Pair.make(Collections.emptySet(), new StringTree[] { new StringLeaf("UNKNOWN") });
					}
				}
			}
			if (stuff == null) {
				// no back edges...  perhaps it is a fake 'turtle' call
				if (node.has("path")) {
					JSONArray path = node.getJSONArray("path");
					if ("turtle".equals(path.get(0))) {
						String f = path.join(".");
						return Pair.make(Collections.emptySet(), new StringTree[] { new StringLeaf(f) });
					}
				}

				//assert stuff !=  null : node;
				return Pair.make(Collections.emptySet(), new StringTree[] {new StringLeaf("UNKNOWN")});
			}

			StringTree[] exprs = null;
			if (node.has("path_end") && !call) {
				String method = node.getString("path_end");
				exprs = Arrays.stream(stuff).map(si -> { 
					StringTree[] s = Arrays.stream(si).filter(n -> n != null).toArray(n -> new StringTree[n]);
					if (s.length == 0) {
						return null;
					}
					StringTree[] args = Arrays.stream(s).skip(1).toArray(n -> new StringTree[n]);
					if (method.equals(s[0].toString())) {
						return new StringCall(s[0], args);		
					} else {
						boolean number = false;
						try {
							Integer.parseInt(method);
							number = true;
						} catch (NumberFormatException e) {
							// not a number
						}
						if (number) {
							return new StringNode(s[0], new StringLeaf("[" + method + "]"));
						} else {
							return new StringCall(method, s[0], args);
						}
					}
				}).filter(n -> n != null).toArray(a -> new StringTree[a]);
			} else if (call) {
				exprs = Arrays.stream(stuff).map(si -> { 
					StringTree[] s = Arrays.stream(si).filter(n -> n != null).toArray(n -> new StringTree[n]);
					if (s.length == 0) {
						return null;
					}
					StringTree[] args = Arrays.stream(s).skip(1).toArray(n -> new StringTree[n]);
					return new StringCall(s[0], args);
				}).toArray(a -> new StringTree[a]);
			} else {
				exprs = Arrays.stream(stuff).map(s -> new StringNode(s)).toArray(n -> new StringTree[n]);
			}

			return Pair.make(fields, exprs);
		}
	}

	private static int isImport(JSONObject node) {
		if (node.has("is_import") && node.getBoolean("is_import")) {
			return 0;
		} else if (node.has("reads")) {
			JSONArray reads = node.getJSONArray("reads");
			for(int i = 0; i < reads.length(); i++) {
				JSONObject read = reads.getJSONObject(i);
				if (read.has("container")) {
					JSONArray cs = read.getJSONArray("container");
					for(int j = 0; j < cs.length(); j++) {
						JSONObject c = turtles.getJSONObject(cs.getInt(j));
						int cn = isImport(c);
						if (cn >= 0) {
							return cn+1; 
						}
					}
				}
			}
		} 
		
		return -1;
	}

	private static StringTree expression(StringTree left, StringLeaf op, StringTree right) {
		String operator = op.text;
		List<StringTree> pieces = new ArrayList<>();
		switch (operator) {
		case "+":
		case "*":
			gatherPieces(pieces, left, operator);
			gatherPieces(pieces, right, operator);
			pieces.sort((l, r) -> l.toString().compareTo(r.toString()));
			break;
		case "-":
		case "/":
			gatherPieces(pieces, left, operator);
			StringTree x = pieces.remove(0);
			gatherPieces(pieces, right, operator);
			pieces.sort((l, r) -> l.toString().compareTo(r.toString()));
			pieces.add(0, x);
			break;
		default:
			pieces.add(left);
			pieces.add(right);
		}
		
		
		boolean first = true;
		List<StringTree> parts = new ArrayList<>();
		for(StringTree part : pieces) {
			if (first) {
				first = false;
			} else {
				parts.add(new StringLeaf(operator));
			}
			parts.add(part);
		}
		
		return new StringNode(parts.toArray(n -> new StringTree[n]));
	}

	private static void gatherPieces(List<StringTree> pieces, StringTree piece, String operator) {
		if (piece instanceof StringNode) {
			StringNode p = (StringNode) piece;
			if (p.children.size() > 2) {
				StringTree c1 = p.children.get(1);
				if (c1 instanceof StringLeaf) {
					if (((StringLeaf)c1).text.equals(operator)) {
						gatherPieces(pieces, p.children.get(0), operator);					}
						gatherPieces(pieces, p.children.get(2), operator);	
						return;
				}
			}
		}
		pieces.add(piece);
	}

	private static StringTree[] combine(StringTree[] a, StringTree[] b) {
		StringTree[] r = new StringTree[ a.length+b.length ];
		System.arraycopy(a, 0, r, 0, a.length);
		System.arraycopy(b, 0, r, a.length, b.length);
		return r;
	}
}
