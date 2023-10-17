package util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;


public class GatherDependencies {
	public static void main(String[] args) {
		JSONParser parser = new JSONParser();
		HashMap<String, Set<String>> dependencyMap = new HashMap<String, Set<String>>();
		String graph4codeNamespace = "http://purl.org/twc/graph4code/ontology/";
		String pythonNamespace = "http://purl.org/twc/graph4code/python/";
		try {
			File f = new File(args[0]);
			File[] fils =  f.listFiles(pathname -> pathname.getName().endsWith(".json") && pathname.length() > 0L);
			
			for (File file : fils) {
				System.out.println(file.getName());
				Object obj = parser.parse(new FileReader(file));
				JSONArray packagesList = (JSONArray) obj;
	 
				Iterator<JSONObject> iterator = packagesList.iterator();
				while (iterator.hasNext()) {
					JSONObject pkg = (JSONObject) iterator.next();
					extractDependencies(pkg, dependencyMap);
				}
			}
			
			Dataset ds = DatasetFactory.create();
			
			Model model = ModelFactory.createDefaultModel();
			Property dependency = model.createProperty("http://schema.org/softwareRequirements");
			dependencyMap.entrySet().forEach(entry-> {
				Resource pkg = model.createResource(pythonNamespace + entry.getKey());
				entry.getValue().forEach(dep-> {
					Resource depcy = model.createResource(pythonNamespace + dep);
					System.out.println(depcy.toString());
					pkg.addProperty(dependency, depcy);
				});
			});
			String graphURI = graph4codeNamespace + "module_dependencies";
			ds.addNamedModel(graphURI, model);
			
			FileOutputStream os = new FileOutputStream(args[1], true);
			RDFDataMgr.write(os, ds, Lang.NQ) ;		
			os.flush();
			os.close();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void extractDependencies(JSONObject pkg, HashMap<String, Set<String>> dependencyMap) {
		String pkg_name = (String) pkg.get("package_name");
		if (dependencyMap.containsKey(pkg_name)) {
			return;
		}
		JSONArray dependencies = (JSONArray) pkg.get("dependencies");
		Set<String> deps = new LinkedHashSet<String>();
		dependencyMap.put(pkg_name, deps);
		
		dependencies.forEach(dependency-> {
			JSONObject d = (JSONObject) dependency;
			String dep = (String) d.get("package_name");
			deps.add(dep);
			extractDependencies(d, dependencyMap);
		});
	}
}
