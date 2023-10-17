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

public class CSVMerges {

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
						field = field.trim().replaceAll("\\[|\\]|'", "");
						
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
		
		System.err.println(csvToColumns);
		
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
		
		Map<Collection<String>,Set<String>> columnsToCsvs = HashMapFactory.make();
		
		for (Entry<String, Set<String>> leftCsvCols : csvToColumns.entrySet()) {
			String leftCsv = leftCsvCols.getKey();
			Set<String> leftCols = leftCsvCols.getValue();
			for (Entry<String, Set<String>> rightCsvCols : csvToColumns.entrySet()) {
				String rightCsv = rightCsvCols.getKey();
				Set<String> rightCols = rightCsvCols.getValue();
				if (leftCsv.compareTo(rightCsv) > 0) {
					Set<String> both = HashSetFactory.make(leftCols);
					both.retainAll(rightCols);
					if (! both.isEmpty()) {
						if (! columnsToCsvs.containsKey(both)) {
							columnsToCsvs.put(both, HashSetFactory.make());
						}
						columnsToCsvs.get(both).add(leftCsv);
						columnsToCsvs.get(both).add(rightCsv);
					}
				}
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
							joinCols.containsAll(sharedFields)
							    &&
							 !(sharedFields.containsAll(leftFields) 
									 &&
							   sharedFields.containsAll(rightFields))) {
							
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
							
							JSONObject join = new JSONObject();
							join.put("left", leftCsv);
							join.put("right", rightCsv);
							join.put("on",  joinCols);
							join.put("id",  id++);
							joins.put(join);
							if (! enabled.isEmpty()) {
								join.put("exprs",  enabled);
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
