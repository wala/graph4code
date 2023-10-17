package util;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.ibm.wala.util.collections.HashMapFactory;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.collections.Pair;

public class EvaluateMergesForFields {

	public static void main(String... args) throws IOException {
		Pattern exprs = Pattern.compile("^expr_[0-9]*$");

		String csvsList = args[0];
		String csvColumns = args[1];

		Map<String,Set<String>> columnToCsvs = HashMapFactory.make();
		Map<String,Set<String>> csvToColumns = HashMapFactory.make();
		Map<String,Map<String,String>> csvToColumnNameMap = HashMapFactory.make();

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
						csvToColumns.get(csv).add(field.toLowerCase());
					
						if (! csvToColumnNameMap.containsKey(csv)) {
							csvToColumnNameMap.put(csv, HashMapFactory.make());
						}
						csvToColumnNameMap.get(csv).put(field.toLowerCase(), field);
					}
				}
			}
		}


		Map<String,Set<String>> fieldExprMap = HashMapFactory.make();
		String exprsJsonFile = args[2];
		try (FileInputStream ej = new FileInputStream(exprsJsonFile)) {
			JSONArray exprsJson = (JSONArray) new JSONTokener(ej).nextValue();
			for(int i = 0; i < exprsJson.length(); i++) {
				JSONObject exprJson = exprsJson.getJSONObject(i);
				JSONArray exprFields = exprJson.getJSONArray("fields");
				exprFields.forEach(f -> {
					String ff = ((String)f).toLowerCase();
					if (! fieldExprMap.containsKey(ff)) {
						fieldExprMap.put(ff, HashSetFactory.make());
					}
					fieldExprMap.get(ff).add(exprJson.getString("expr_name"));
				});
			}
		}
		
		Map<String,String> fieldsToLearn = HashMapFactory.make();
		String fieldsToLearnFile = args[3];
		try (FileReader flf = new FileReader(fieldsToLearnFile)) {
			try (BufferedReader bis = new BufferedReader(flf)) {
				for (String fieldToLearnLine = bis.readLine(); fieldToLearnLine != null; fieldToLearnLine = bis.readLine()) {
					StringTokenizer toks = new StringTokenizer(fieldToLearnLine, ",");
					String field = toks.nextToken();		
					String type = toks.nextToken();
					fieldsToLearn.put(field.toLowerCase(), type);
				} 
			}
		}

		String mergeDsfName = args[4];
		Set<String> mergeDss= HashSetFactory.make();
		try (FileReader dsf = new FileReader(mergeDsfName)) {
			try (BufferedReader dss = new BufferedReader(dsf)) {
				for(String ds = dss.readLine(); ds != null; ds = dss.readLine()) {
					mergeDss.add(ds);
				}
			}
		}

		JSONArray joins = new JSONArray();
		String mergesJsonFile = args[5];
		try (FileInputStream ej = new FileInputStream(mergesJsonFile)) {
			JSONArray mergesJson = (JSONArray) new JSONTokener(ej).nextValue();
			for(int i = 0; i < mergesJson.length(); i++) {
				JSONObject mergeJson = mergesJson.getJSONObject(i);
				String ds = "ds_" + mergeJson.getInt("id") + ".csv.bz2";
				if (mergeDss.contains(ds)) {
					for(String csv : new String[] { mergeJson.getString("left"), mergeJson.getString("right") }) { 
						if (!Collections.disjoint(fieldsToLearn.keySet(), csvToColumns.get(csv))) {
							for(String fieldToLearn : csvToColumns.get(csv)) {
								if (fieldsToLearn.containsKey(fieldToLearn)) {
									JSONObject learn = new JSONObject(mergeJson, JSONObject.getNames(mergeJson));
									learn.put("field", csvToColumnNameMap.get(csv).get(fieldToLearn));
									learn.put("type", fieldsToLearn.get(fieldToLearn));
									learn.put("mask", fieldExprMap.get(fieldToLearn));
									joins.put(learn);
								}
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
