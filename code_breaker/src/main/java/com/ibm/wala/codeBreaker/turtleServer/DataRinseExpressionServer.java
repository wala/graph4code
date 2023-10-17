package com.ibm.wala.codeBreaker.turtleServer;

import static spark.Spark.post;
import static spark.Spark.port;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import spark.Request;

public class DataRinseExpressionServer {

	// dataset -> column -> script -> list of expr
	private JSONObject exprs;
	
	public void run(String... args) {
		try (FileInputStream in = new FileInputStream(args[0])) {
				exprs = new JSONObject(new JSONTokener(in));
			} catch (IOException e) {
				e.printStackTrace();
				assert false : e;
			}
		
		port(6660);
		post("/expressions", (request, response) -> serveExprs(request));
	}
	
	JSONObject serveExprs(Request req) {
		JSONTokener tokener = new JSONTokener(new StringReader(req.body()));
		JSONObject obj = new JSONObject(tokener);
		String dataset = obj.getString("dataset");
		JSONArray columns = obj.getJSONArray("columns");
		columns.put("all");
		
		JSONObject datasetExprs = exprs.getJSONObject(dataset);
		JSONObject result = new JSONObject();
		for(int i = 0; i < columns.length(); i++) {
			if (datasetExprs.has(columns.getString(i))) {
				JSONObject exprs = datasetExprs.getJSONObject(columns.getString(i));
				result.put(columns.getString(i), exprs);
			}
		}
		
		return result;
	}
	
	public static void main(String... args) {
		new DataRinseExpressionServer().run(args);
	}
}
