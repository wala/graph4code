package com.ibm.wala.cast.lsp.codeBreaker;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import com.ibm.wala.cast.lsp.AnalysisError;
import com.ibm.wala.cast.lsp.ClientDriver;
import com.ibm.wala.cast.lsp.WALAServerCore;
import com.ibm.wala.cast.lsp.codeBreaker.WALATurtleServer.Comments;
import com.ibm.wala.codeBreaker.turtle.PythonTurtleAnalysisEngine.TurtlePath;

public class WALAServerCodeSearch extends ClientDriver {

	public static class CodeSearchServer extends WALATurtleServer {
		private final QueryCodeKGStore store = new QueryCodeKGStore();

		public CodeSearchServer(Comments comments) {
			super(comments);
		}

		@Override
		protected AnalysisError create(TurtlePath turtle, Comments comments) {
			return new CodeSearchSuggestion(turtle, comments);
		}

		public class CodeSearchSuggestion extends TurtleHandler {

			protected CodeSearchSuggestion(TurtlePath turtle, Comments comments) {
				super(turtle, comments);
			}

			@Override
			public Kind kind() {
				return Kind.Hover;
			}

			@Override
			public String toString(boolean useMarkdown) {
				String path = super.toString(useMarkdown);
				String msg = "[";
				for(String s : store.getSuggestion(path)) {
					msg += " " + s;
				}
				return msg + "]";
			}

		}

		@Override
		public CompletableFuture<Object> shutdown() {
			return CompletableFuture.completedFuture(new Object());
		}
	}

	public static void main(String[] args) throws IOException {
		new WALAServerCodeSearch().createServer().launchOnStdio();
	}

	@Override
	protected WALAServerCore createServer() {
		return new CodeSearchServer(Comments.Require);
	}

	public WALAServerCodeSearch() throws IOException {
		super();
	}

}