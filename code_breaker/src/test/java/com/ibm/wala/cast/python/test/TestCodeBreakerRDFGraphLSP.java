package com.ibm.wala.cast.python.test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.TextDocumentItem;
import org.junit.Test;

import com.ibm.wala.cast.lsp.codeBreaker.WALAServerRDFGraphSearch;

public class TestCodeBreakerRDFGraphLSP extends WALAServerRDFGraphSearch {

	public TestCodeBreakerRDFGraphLSP() throws IOException {
		super();
	}

	@Test
	public void test() throws IOException {
		TestCodeBreakerRDFGraphLSP client = new TestCodeBreakerRDFGraphLSP();
		client.start();
		client.server.shutdown();
	}

	@Override
	public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
		diagnostics.getDiagnostics().forEach((diag) -> {
			System.err.println(diag.getMessage());
		});
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
			doc.setUri("file:///some/fake/file");
			params.setTextDocument(doc);
			server.getTextDocumentService().didOpen(params);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
