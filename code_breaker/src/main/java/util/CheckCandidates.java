package util;

import java.util.*;
import org.json.simple.*;
import org.json.simple.parser.*;
import com.ibm.wala.util.io.Streams;
import com.ibm.wala.util.collections.*;

import java.io.*;

class CheckCandidates {

    private static Map<String, Set<String>> read(JSONArray left_flows) {
        Map<String, Set<String>> names = HashMapFactory.make();	
	left_flows.forEach(lm -> {
	  JSONObject left_meth = (JSONObject) lm;
	  String left_method = (String) left_meth.get("method");
	  JSONArray lstuff = (JSONArray)left_meth.get("possible_types");
	  names.put(left_method, HashSetFactory.make());
	  lstuff.iterator().forEachRemaining(ls -> {
	    names.get(left_method).add((String) ((JSONObject)ls).get("class"));
	  });
	});
	return names;
    }
	    
    public static void main(String... args) throws Exception {

	JSONArray left_flows =
	    (JSONArray) new JSONParser().parse(new FileReader(args[0]));
	JSONArray right_flows =
	    (JSONArray) new JSONParser().parse(new FileReader(args[1]));

	Map<String, Set<String>> ls = read(left_flows);
	Map<String, Set<String>> rs = read(right_flows);

	ls.entrySet().forEach(les -> {
	  String name = les.getKey();
	  Set<String> left_callees = les.getValue();
	  Set<String> right_callees = rs.get(name);

	  Set<String> lr = HashSetFactory.make(left_callees);
	  if (right_callees != null) {
	      lr.removeAll(right_callees);
	  }
	  
	  if (! lr.isEmpty()) {
	      System.err.println(name);
	      System.err.println(lr);
	  }
	});
    }
}
