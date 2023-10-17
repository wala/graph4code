package util;

import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.rdfconnection.RDFConnectionFactory;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;


import com.ibm.wala.util.io.Streams;


public class GetCandidateClassesForMethods {
	public static void main(String... args) throws IllegalArgumentException, IOException, ParseException {
		RDFConnection kg = RDFConnectionFactory.connect(args[0]);

		JSONArray methods = (JSONArray) new JSONParser().parse(new FileReader(args[1]));
		String query = new String(Streams.inputStream2ByteArray(new FileInputStream(args[2])));

		org.json.JSONArray results = new org.json.JSONArray();
		methods.forEach((m) -> {
			StringBuffer values = new StringBuffer();

			JSONObject meth = (JSONObject) m;
			String method = (String) meth.keySet().iterator().next();
			String[] arr = method.split("[.]");
			
			String modQuery = query.replace("REPLACE_MODULE", arr[0]);

			Set<String> flowsUniq = new HashSet<String>();
			meth.keySet().forEach((flow) -> 
			{
				JSONObject f = (JSONObject) meth.get(flow);
				f.keySet().forEach((p)-> {
					if (!flowsUniq.contains(p)) {
						flowsUniq.add((String) p);
						values.append(" \"").append((String) p)
						.append("\" ");}
					});
			});
			modQuery = modQuery.replace("REPLACE", values.toString());
			org.json.JSONObject m_json = new org.json.JSONObject();
			m_json.put("method", method);
			results.put(m_json);
			org.json.JSONArray possible_types = new org.json.JSONArray();
			m_json.put("possible_types", possible_types);
			System.out.println("Starting method:" + method);
			System.out.println(modQuery);

			kg.querySelect(modQuery, (qs) -> {
				String clazz = qs.getResource("class").getURI();
				int count = qs.getLiteral("count").getInt();
				String superCl =  qs.getResource("super").getURI();					
				org.json.JSONObject r = new org.json.JSONObject();
				r.put("class", clazz);
				r.put("count", count);
				r.put("superclass", superCl);
				System.out.println("Classes for " + method + " " + clazz + " "  + count);
				possible_types.put(r);
			});
		});
		
		FileWriter out = new FileWriter(args[3]);
		results.write(out, 2, 0);
		out.flush();
		out.close();

	}
}
