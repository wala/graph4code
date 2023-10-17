package ai3693;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import org.antlr.runtime.ANTLRFileStream;
import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.Token;
import org.antlr.runtime.TokenRewriteStream;
import org.json.JSONArray;
import org.json.JSONObject;
import org.python.antlr.PythonLexer;
import org.python.antlr.PythonTokenSource;

public class JythonTokenizer {

	public static void main(String... args) throws IOException {
		File fileName = new File(args[0]);
        ANTLRFileStream charStream = new org.antlr.runtime.ANTLRFileStream(fileName.getAbsolutePath());
        PythonLexer lexer = new PythonLexer(charStream);
        CommonTokenStream tokens = new TokenRewriteStream(lexer);
        PythonTokenSource indentedSource = new PythonTokenSource(tokens, args[0], true);
        tokens = new CommonTokenStream(indentedSource);

        tokens.fill();
        
        JSONArray toks = new JSONArray();
        tokens.getTokens().forEach(n -> { 
        	Token tok = (Token)n;
        	JSONObject t = new JSONObject();
        	t.put("text", tok.getText());
        	t.put("line", tok.getLine());
        	t.put("column", tok.getCharPositionInLine());
        	t.put("index", tok.getTokenIndex());
        	toks.put(t); 
        });
        
        try (PrintWriter f = new PrintWriter(System.out)) {
        	toks.write(f, 2, 0);
        }
	}
	
}
