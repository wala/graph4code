package com.ibm.wala.cast.python.test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.function.Consumer;

import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.junit.Test;

import com.ibm.wala.cast.lsp.AnalysisError;
import com.ibm.wala.cast.lsp.WALAServerCore;
import com.ibm.wala.cast.lsp.codeBreaker.WALAServerCodeSearch;
import com.ibm.wala.cast.lsp.codeBreaker.WALATurtleServer.Comments;
import com.ibm.wala.codeBreaker.turtle.PythonTurtleAnalysisEngine.TurtlePath;

public class TestCodeBreakerCodeSearchLSP extends WALAServerCodeSearch {
	private static final String fake_uri = "file:///some/fake/file";
	
	private TestCodeBreakerCodeSearchLSP client;
	
	public TestCodeBreakerCodeSearchLSP() throws IOException {
		super();
	}

	@Test
	public void test() throws IOException {
		client = new TestCodeBreakerCodeSearchLSP();
		client.start();
	}

	@Override
	public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
		diagnostics.getDiagnostics().forEach((diag) -> {
			System.err.println(diag.getRange() + ": " + diag.getMessage());
		});
	}

	@Override
	protected WALAServerCore createServer() {
		return new CodeSearchServer(Comments.Require) {
			
			@Override
			public void analyze(String language) {
				super.analyze(language);
				MessageParams mp = new MessageParams();
				mp.setMessage("analysis done");
				client.showMessage(mp);
			}

			@Override
			protected void handle(Comments comments, Consumer<AnalysisError> callback, TurtlePath turtle) {
				super.handle(comments, callback, turtle);
			}
		};
	}

	@Override
	public void showMessage(MessageParams msg) {
		System.err.println(msg.getMessage());
		if ("analysis done".equals(msg.getMessage())) {
			TextDocumentPositionParams pos = new TextDocumentPositionParams();
			TextDocumentIdentifier uri = new TextDocumentIdentifier();
			uri.setUri(fake_uri);
			pos.setTextDocument(uri);
			Position position = new Position();
			position.setCharacter(15);
			position.setLine(3);
			pos.setPosition(position);
			server.getTextDocumentService().hover(pos).thenAccept((h) -> {
				System.err.println(h.toString());
			});
		}
	}

	@Override
	protected void start() {
		super.start();

		try {
			DidOpenTextDocumentParams params = new DidOpenTextDocumentParams();
			TextDocumentItem doc = new TextDocumentItem();
			doc.setLanguageId("python");

			BufferedReader r = new BufferedReader(new InputStreamReader(getClass().getClassLoader().getResourceAsStream("sampletextsearch.py")));
			String line;
			StringBuffer file = new StringBuffer();
			while ((line = r.readLine()) != null) {
				file.append(line).append("\n");
			}

			doc.setText(file.toString());
			doc.setUri(fake_uri);
			params.setTextDocument(doc);
			server.getTextDocumentService().didOpen(params);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
