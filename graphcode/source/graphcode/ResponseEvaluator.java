package graphcode;

import java.io.FileInputStream;
import java.io.IOException;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

public class ResponseEvaluator {

	public static MethodDeclaration findMethodByLine(CompilationUnit compilationUnit, int targetLine) {
		// 1. Get the character offset for the target line.
		// The API uses 1-based indexing for lines in getStartPosition
		int offset = compilationUnit.getPosition(targetLine, 0);

		if (offset < 0) {
			// Line number is out of bounds
			return null;
		}

		// A more direct way to find the node at the position:
		ASTNode nodeAtOffset = NodeFinder.perform(compilationUnit, offset, 0);

		// 3. Traverse up the tree to find the enclosing MethodDeclaration.
		while (nodeAtOffset != null) {
			if (nodeAtOffset instanceof MethodDeclaration) {
				return (MethodDeclaration) nodeAtOffset;
			}
			nodeAtOffset = nodeAtOffset.getParent();
		}

		// No enclosing method found (e.g., the line is in an import statement or class
		// declaration)
		return null;
	}

	public static String getPrefix(String sourceCode, CompilationUnit cu, MethodDeclaration md, int lastLine) {
		int start = md.getStartPosition();
		int end = cu.getPosition(lastLine+1, 0);
		return sourceCode.substring(start, end);
	}

	public static CompilationUnit createCompilationUnitFromString(String sourceCode) {
		ASTParser parser = ASTParser.newParser(AST.JLS21);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(sourceCode.toCharArray());

		// setResolveBindings(true) can be used, but generally requires
		// the code to be part of an actual Eclipse workspace project
		// to work correctly and provide meaningful results.
		// parser.setResolveBindings(true);

		// Create the AST (passing null for IProgressMonitor)
		CompilationUnit compilationUnit = (CompilationUnit) parser.createAST(null);

		return compilationUnit;
	}

	public static void main(String[] args) {
		try (BZip2CompressorInputStream in = new BZip2CompressorInputStream(new FileInputStream(args[0]))) {
			JSONArray original = (JSONArray) new JSONTokener(in).nextValue();
			JSONTokener gen = new JSONTokener(new FileInputStream(args[1]));
			while (gen.more()) {
				JSONObject elt = null;
				try {
					elt = (JSONObject) gen.nextValue();
				} catch (JSONException e) {
					break;
				}
				JSONObject orig = original.getJSONObject(elt.getInt("index"));
				JSONObject edges = orig.getJSONObject("context").getJSONObject("edges");
				String className = edges.getJSONObject("ROOT").getJSONArray("class").getString(0);
				String clsSourceString = edges.getJSONObject(className).getJSONArray("text").getString(0);
				CompilationUnit origClass = createCompilationUnitFromString(clsSourceString);

				int line = orig.getInt("line");

				MethodDeclaration methodToHack = findMethodByLine(origClass, line);
				if (methodToHack != null) {
					String prefix = getPrefix(clsSourceString, origClass, methodToHack, line);

					String response = elt.getString("model_response");
					int startPos = response.indexOf("```java") + 7;
					int endPos = response.indexOf("```", startPos);
					if (endPos > startPos) {
						String completion = response.substring(startPos, endPos);

						System.err.println("for " + elt.getInt("index") + "\n" + prefix + "// generated completion follows\n" + completion + "----------");
					} else {
						System.err.println("could not find end in " + response);
					}
				} else {
					System.err.println("could not find method for " + className + " at line " + line);
				}
			}
		} catch (IOException e) {
			assert false : e;
		}

	}

}
