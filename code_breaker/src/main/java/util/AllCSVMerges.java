package util;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.ibm.wala.util.collections.HashMapFactory;
import com.ibm.wala.util.collections.HashSetFactory;

public class AllCSVMerges {

	public static void main(String... args) throws IOException {
		Pattern exprs = Pattern.compile("^expr_[0-9]*$");
		
		String csvsList = args[0];
		String csvColumns = args[1];
		
		Map<String,Set<String>> columnToCsvs = HashMapFactory.make();
		Map<String,Set<String>> csvToColumns = HashMapFactory.make();
		
		try (BufferedReader files = new BufferedReader(new FileReader(csvsList))) {
			try (BufferedReader columnss = new BufferedReader(new FileReader(csvColumns))) {
				String csv;
				while((csv = files.readLine()) != null) {
					String columns = columnss.readLine();
					StringTokenizer toks = new StringTokenizer(columns, ",");
					while (toks.hasMoreTokens()) {
						String field = toks.nextToken();
						
						if (exprs.matcher(field).matches()) {
							continue;
						}
						
						if (! columnToCsvs.containsKey(field)) {
							columnToCsvs.put(field, HashSetFactory.make());
						}
						columnToCsvs.get(field).add(csv);
						
						if (! csvToColumns.containsKey(csv)) {
							csvToColumns.put(csv, HashSetFactory.make());
						}
						csvToColumns.get(csv).add(field);
					}
				}
			}
		}
		
		Map<Collection<String>,Set<String>> columnsToCsvs = HashMapFactory.make();
		String mergesFile = args[2];
		try (FileReader mr = new FileReader(mergesFile)) {
			JSONTokener mt = new JSONTokener(mr);
			JSONArray merges = (JSONArray)  mt.nextValue();
			merge: for(Object merge : merges) {
				if (merge instanceof String) {
					Set<String> csvs = columnToCsvs.get(merge);
					if (columnToCsvs.containsKey(merge) && csvs.size() > 1) {
						Set<String> key = Collections.singleton((String)merge);
						if (! columnsToCsvs.containsKey(key)) {
							columnsToCsvs.put(key, HashSetFactory.make());
						}
						columnsToCsvs.get(key).addAll(csvs);
					}
				} else {
					assert merge instanceof JSONArray : merge;
					JSONArray cols = (JSONArray) merge;
					List<String> x = new LinkedList<>();
					cols.toList().forEach(o -> x.add((String)o));
					Set<String> mcsvs = HashSetFactory.make();
					if (x.size() == 0 || !columnToCsvs.containsKey(x.get(0))) {
						continue merge;
					}
					mcsvs.addAll(columnToCsvs.get(x.get(0)));
					for(int i = 1; i < x.size(); i++) {
						String col = x.get(i);
						if (!columnToCsvs.containsKey(col)) {
							continue merge;
						}
							
						mcsvs.retainAll(columnToCsvs.get(col));
						
						if (mcsvs.size() <= 1) {
							continue merge;
						}

					}

					if (mcsvs.size() <= 1) {
						continue merge;
					}

					if (! columnsToCsvs.containsKey(x)) {
						columnsToCsvs.put(x, HashSetFactory.make());
					}
					columnsToCsvs.get(x).addAll(mcsvs);
				}
			}
		}
		
		Map<Set<String>,Set<String>> exprFieldMap = HashMapFactory.make();
		String exprsJsonFile = args[2];
		try (FileInputStream ej = new FileInputStream(exprsJsonFile)) {
			JSONArray exprsJson = (JSONArray) new JSONTokener(ej).nextValue();
			for(int i = 0; i < exprsJson.length(); i++) {
				JSONObject exprJson = exprsJson.getJSONObject(i);
				JSONArray exprFields = exprJson.getJSONArray("fields");
				Set<String> exprFieldSet = HashSetFactory.make();
				exprFields.forEach(s -> exprFieldSet.add((String)s));
				if (! exprFieldMap.containsKey(exprFieldSet)) {
					exprFieldMap.put(exprFieldSet, HashSetFactory.make());
				}
				exprFieldMap.get(exprFieldSet).add(exprJson.getString("expr_name"));
			}
		}
		
		int id=0;
		JSONArray joins = new JSONArray();
		for (Entry<Collection<String>, Set<String>> mes : columnsToCsvs.entrySet()) {
			Collection<String> joinCols = mes.getKey();
			
			for(String leftCsv : mes.getValue()) {
				Set<String> leftFields = csvToColumns.get(leftCsv);
				assert leftFields.containsAll(joinCols);
				
				for(String rightCsv : mes.getValue()) {
					Set<String> rightFields = csvToColumns.get(rightCsv);
					assert rightFields.containsAll(joinCols);
					
					if (leftCsv.compareTo(rightCsv) > 0) {
						Set<String> sharedFields = HashSetFactory.make(leftFields);
						sharedFields.retainAll(rightFields);
						
						if (sharedFields.containsAll(joinCols)
								&&
							joinCols.containsAll(sharedFields)) {
							
							Set<String> leftOnly = HashSetFactory.make(leftFields);
							leftOnly.removeAll(joinCols);
							leftOnly.removeAll(sharedFields);
							Set<String> rightOnly = HashSetFactory.make(rightFields);
							rightOnly.removeAll(joinCols);
							rightOnly.removeAll(sharedFields);

							Set<String> enabled = HashSetFactory.make();
							for(Set<String> usefulFields : exprFieldMap.keySet()) {
								if (! Collections.disjoint(leftOnly, usefulFields)
										&&
									! Collections.disjoint(rightOnly, usefulFields)) {
									enabled.addAll(exprFieldMap.get(usefulFields));
								}
							}
							
							if (! enabled.isEmpty()) {
								JSONObject join = new JSONObject();
								join.put("left", leftCsv);
								join.put("right", rightCsv);
								join.put("on",  joinCols);
								join.put("exprs",  enabled);
								join.put("id",  id++);
								joins.put(join);
							}
						}
					}
				}
			}
		}

		try (PrintWriter pw = new PrintWriter(System.out)) {
			joins.write(pw, 2, 0);
		}
	}
}
