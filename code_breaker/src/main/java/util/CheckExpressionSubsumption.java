package util;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Iterator;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.ibm.wala.util.collections.HashSetFactory;

public class CheckExpressionSubsumption {

	private static Set<String> getCode(JSONArray stuff) {
		Set<String> code = HashSetFactory.make();
		for(int i = 0; i < stuff.length(); i++) {
			code.add(stuff.getJSONObject(i).getString("code"));
		}
		return code;
	}
	
	public static void main(String[] args) throws JSONException, FileNotFoundException {
		JSONArray newExprs = 
			new JSONArray(
				new JSONTokener(
					new FileInputStream(args[0])));

		JSONArray oldExprs = 
				new JSONArray(
					new JSONTokener(
						new FileInputStream(args[1])));
		
		if (args.length > 2) {
			JSONArray usedOldExprs = 
					new JSONArray(
						new JSONTokener(
							new FileInputStream(args[2])));
			Set<Object> keep = HashSetFactory.make(usedOldExprs.toList());
			Iterator<Object> elts = oldExprs.iterator();
			while (elts.hasNext()) {
				JSONObject elt = (JSONObject) elts.next();
				if (! keep.contains(elt.get("expr_name"))) {
					elts.remove();
				}
			}
		}
		
		Set<String> newCode = getCode(newExprs);
		Set<String> oldCode = getCode(oldExprs);

		System.err.println(oldCode.size());

		oldCode.removeAll(newCode);
		
		System.err.println(oldCode.size());
		for(String s : oldCode) {
			System.err.println(s);
		}
	}

}
