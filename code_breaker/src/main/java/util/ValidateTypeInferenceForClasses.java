package util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import org.json.simple.parser.ParseException;


/***
 * To Check with Julian - there is still a bug that somehow does not catch the class
 * buildbot.util.service.BuildbotService although one would expect it to be found.
 * @author kavithasrinivas
 *
 */
public class ValidateTypeInferenceForClasses {
	
	private static String normalizeName(String name, Map<String, String> classMap) {
        String[] name_arr = name.split("[.]");
        if (name_arr.length < 3) {
        	return name;
        }
        List<String> arr = Arrays.asList(Arrays.copyOfRange(name_arr, 0, name_arr.length - 1));
        
        String clazz = String.join(".", arr);
        if (classMap.containsKey(clazz)) {
           String cls = classMap.get(clazz);
           name = name.replace(clazz, cls);
        }
        return name.trim(); 
	}
	
    private static Double calculateJaccardSimilarity(CharSequence left, CharSequence right) {
        Set<String> intersectionSet = new HashSet<String>();
        Set<String> unionSet = new HashSet<String>();
        boolean unionFilled = false;
        int leftLength = left.length();
        int rightLength = right.length();
        if (leftLength == 0 || rightLength == 0) {
            return 0d;
        }

        for (int leftIndex = 0; leftIndex < leftLength; leftIndex++) {
            unionSet.add(String.valueOf(left.charAt(leftIndex)));
            for (int rightIndex = 0; rightIndex < rightLength; rightIndex++) {
                if (!unionFilled) {
                    unionSet.add(String.valueOf(right.charAt(rightIndex)));
                }
                if (left.charAt(leftIndex) == right.charAt(rightIndex)) {
                    intersectionSet.add(String.valueOf(left.charAt(leftIndex)));
                }
            }
            unionFilled = true;
        }
        return Double.valueOf(intersectionSet.size()) / Double.valueOf(unionSet.size());
    }
    
	// Read the results of type inference, looking for class constructors
	// which appear like classes (take a list of classes as a separate file)
	// Ensure that for each class constructor, we infer return type correctly with
	// the class
	public static void main(String... args) throws IllegalArgumentException, IOException, ParseException {
		BufferedReader inferredTypeFile = new BufferedReader(new FileReader(args[0]));
		BufferedReader classMapReader = new BufferedReader(new FileReader(args[1]));
		BufferedReader classesReader = new BufferedReader(new FileReader(args[2]));

		String line = null;

		Set<String> classes = new HashSet<String>();
		Map<String, String> classMap = new HashMap<String, String>();
		
		while ((line = classesReader.readLine()) != null) {
			String c = line.trim();
			classes.add(c);
		}

		while ((line = classMapReader.readLine()) != null) {
			StringTokenizer tokenizer = new StringTokenizer(line, " ");
			String c = tokenizer.nextToken();

			if (tokenizer.hasMoreTokens()) {
				String normalizedClass = tokenizer.nextToken();
				classMap.put(c, normalizedClass);
			}
		}

		String currentType = null;
		int maxForType = 0;
		int currentTypeCount = 0;
		int fuzzyCount = 0;
		Set<String> foundClasses = new HashSet<String>();
		Set<String> notFoundClasses = new HashSet<String>();
		Set<String> foundTypesNotInClasses = new HashSet<String>();
		Set<String> allInferredTypes = new HashSet<String>();
		Set<String> fuzzyClasses = new HashSet<String>();


		while ((line = inferredTypeFile.readLine()) != null) {
			StringTokenizer tokenizer = new StringTokenizer(line, " ");
			if (tokenizer.countTokens() < 3) {
				continue;
			}

			String clazz = tokenizer.nextToken();

			clazz = normalizeName(clazz, classMap);
			
			allInferredTypes.add(clazz);

			if (!clazz.equals(currentType)) {
				handleType(classes, currentType, maxForType, currentTypeCount, fuzzyCount, foundClasses,
						notFoundClasses, foundTypesNotInClasses, fuzzyClasses);
				currentType = clazz;
				maxForType = 0;
				currentTypeCount = 0;
				fuzzyCount = 0;
			}
			int count = Integer.parseInt(tokenizer.nextToken());
			if (tokenizer.hasMoreTokens()) {
				String type = tokenizer.nextToken();
				type = type.substring(type.lastIndexOf('/') + 1);
				
				maxForType = Math.max(count, maxForType);
				// only if we have a class constructor, then look at its possible types
				if (type.equals(clazz)) {
					currentTypeCount = count;
				} else {
					// see if type exists in the class map as a normalized class
					if (classMap.containsKey(clazz) && type.equals(classMap.get(clazz))) {
						currentTypeCount = count;
					} else {
						 {
							double score = calculateJaccardSimilarity(type, clazz);
							String[] typeBreakUp = type.split("[.]");
							String[] clazzBreakUp = clazz.split("[.]");
							String typeType = typeBreakUp[typeBreakUp.length - 1].replace('_', ' ').trim();
							String typeMod = typeBreakUp[0].replace('_', ' ').trim();
							String clazzType = clazzBreakUp[clazzBreakUp.length - 1].replace('_', ' ').trim();
							String clazzMod = clazzBreakUp[0].replace('_', ' ').trim();
							
							if (typeType.equals(clazzType) && typeMod.contentEquals(clazzMod)) {
								System.out.println("class with fuzzy match:" + type + " " + clazz);
								fuzzyCount += 1;
							}
						}
					}
				}
			}
		}
		// handle last type
		handleType(classes, currentType, maxForType, currentTypeCount, fuzzyCount, foundClasses,
				notFoundClasses, foundTypesNotInClasses, fuzzyClasses);


		if (currentType != null && currentTypeCount > 0) {
			System.out.println(
					"Class:" + currentType + " has max count: " + maxForType + " and found in : " + currentTypeCount);
			foundClasses.add(currentType);
		} else if (currentType != null && currentTypeCount == 0 && classes.contains(currentType)) {
			System.out.println("Class not found:" + currentType + " has max count: " + maxForType);
			notFoundClasses.add(currentType);
		}

		int classesNotInTypeInf = 0;
		for (String c : classes) {
			if (!foundClasses.contains(c) && !notFoundClasses.contains(c)) {
				System.out.println("CLASS NOT IN TYPE INF: " + c);
				classesNotInTypeInf += 1;
			}
		}
		

		System.out.println("Classes for which types were found:" + foundClasses.size());
		System.out.println("Classes for which types were not found:" + notFoundClasses.size());
		System.out.println("fuzzily correct classes:" + fuzzyClasses.size());
		System.out.println("All classes in class map:" + classes.size());
		System.out.println("Classes not in type inference:" + classesNotInTypeInf);
		System.out.println("Classes in type inference but not in classes:" + foundTypesNotInClasses.size());
		System.out.println("Total number of inferred types:" + allInferredTypes.size());


	}

	private static void handleType(Set<String> classes, String currentType, int maxForType, int currentTypeCount,
			int fuzzyCount, Set<String> foundClasses, Set<String> notFoundClasses, Set<String> foundTypesNotInClasses,
			Set<String> fuzzyClasses) {
		if (currentType != null && currentTypeCount > 0 && classes.contains(currentType)) {
			System.out.println("Class found:" + currentType + " has max count: " + maxForType
					+ " and found in : " + currentTypeCount);
			foundClasses.add(currentType);
		} else if (currentType != null && currentTypeCount == 0 && classes.contains(currentType)) {
			if (fuzzyCount > 0) {
				fuzzyClasses.add(currentType);
			}
			notFoundClasses.add(currentType);
			System.out.println("Class not found:" + currentType + " has max count: " + maxForType);
		} else if (currentType != null && currentTypeCount > 0  && !classes.contains(currentType)) {
			foundTypesNotInClasses.add(currentType);
		}
	}
}
