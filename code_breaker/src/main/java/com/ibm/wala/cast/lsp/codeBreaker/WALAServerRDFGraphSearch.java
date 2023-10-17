package com.ibm.wala.cast.lsp.codeBreaker;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import com.ibm.wala.cast.lsp.AnalysisError;
import com.ibm.wala.cast.lsp.ClientDriver;
import com.ibm.wala.cast.lsp.WALAServerCore;
import com.ibm.wala.cast.lsp.codeBreaker.WALATurtleServer.Comments;
import com.ibm.wala.codeBreaker.turtle.PythonTurtleAnalysisEngine.TurtlePath;

public class WALAServerRDFGraphSearch extends ClientDriver {
	private class RDFGraphSearchServer extends WALATurtleServer {
		private final QueryStaticAnalysisStore store = new QueryStaticAnalysisStore();

		private RDFGraphSearchServer(Comments comments) {
			super(comments);
		}

		@Override
		protected AnalysisError create(TurtlePath turtle, Comments comments) {
			return new RDFSearchSuggestion(turtle, comments);
		}

		class RDFSearchSuggestion extends TurtleHandler {
			
			protected RDFSearchSuggestion(TurtlePath turtle, Comments comments) {
				super(turtle, comments);
			}

			@Override
			public Kind kind() {
				return Kind.Hover;
			}

			@Override
			public String toString(boolean useMarkdown) {
				String path = super.toString(useMarkdown);
				StringBuffer sb = new StringBuffer();
				store.getSuggestion(path).forEachRemaining((row) -> {
					sb.append(row.get("z").toString()).append("\n");
				});
				
				return sb.toString();
			}

		}

		@Override
		public CompletableFuture<Object> shutdown() {
			return CompletableFuture.completedFuture(new Object());
		}
	}

	public WALAServerRDFGraphSearch() throws IOException {
		super();
	}

	@Override
	protected WALAServerCore createServer() {
		return new RDFGraphSearchServer(Comments.Ignore);
	}

	public static void main(String[] args) throws IOException {
		new WALAServerRDFGraphSearch().createServer().launchOnStdio();
	}

}