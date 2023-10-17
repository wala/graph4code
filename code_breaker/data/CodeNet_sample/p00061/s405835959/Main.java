import static java.util.Arrays.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.StringTokenizer;

public class Main {

	static void tr(Object... os) {
		System.err.println(deepToString(os));
	}

	void solve() {
		T[] ts = new T[100];
		int n = 0;
		for (;;) {
			String[] ss = sc.next().split(",");
			if (ss[0].equals("0")) break;
			T t = new T();
			t.id = Integer.parseInt(ss[0]);
			t.v = Integer.parseInt(ss[1]);
			ts[n++] = t;
		}
		ts = Arrays.copyOfRange(ts, 0, n);
		sort(ts);
		ts[0].rank = 1;
		for (int i = 1; i < ts.length; i++) {
			if (ts[i-1].v == ts[i].v) {
				ts[i].rank = ts[i-1].rank;
			} else {
				ts[i].rank = ts[i-1].rank + 1;
			}
		}
		for (;sc.hasNext();) {
			int x = sc.nextInt();
			for (T t : ts) if (t.id == x) out.println(t.rank);
			out.flush();
		}
	}

	class T implements Comparable<T> {
		int id;
		int v;
		int rank;
		@Override
		public int compareTo(T o) {
			return o.v - v;
		}
	}

	public static void main(String[] args) throws Exception {
		new Main().run();
	}

	MyScanner sc = null;
	PrintWriter out = null;
	public void run() throws Exception {
		sc = new MyScanner(System.in);
		out = new PrintWriter(System.out);
		solve();
		out.flush();
		out.close();
	}

	class MyScanner {
		String line;
		BufferedReader reader;
		StringTokenizer tokenizer;

		public MyScanner(InputStream stream) {
			reader = new BufferedReader(new InputStreamReader(stream));
			tokenizer = null;
		}
		public void eat() {
			while (tokenizer == null || !tokenizer.hasMoreTokens()) {
				try {
					line = reader.readLine();
					if (line == null) {
						tokenizer = null;
						return;
					}
					tokenizer = new StringTokenizer(line);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		}
		public String next() {
			eat();
			return tokenizer.nextToken();
		}
		public String nextLine() {
			try {
				return reader.readLine();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		public boolean hasNext() {
			eat();
			return (tokenizer != null && tokenizer.hasMoreElements());
		}
		public int nextInt() {
			return Integer.parseInt(next());
		}
		public long nextLong() {
			return Long.parseLong(next());
		}
		public double nextDouble() {
			return Double.parseDouble(next());
		}
		public int[] nextIntArray(int n) {
			int[] a = new int[n];
			for (int i = 0; i < n; i++) a[i] = nextInt();
			return a;
		}
	}
}