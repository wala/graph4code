import java.io.*;
import java.util.StringTokenizer;
import java.util.ArrayList;
import java.util.Collections;

class Index {
	String name;
	ArrayList<Integer> a = new ArrayList<Integer>();

	Index(String name,int page) {
		this.name = name;
		this.a.add(page);
	}

	void AddToIndex(ArrayList<Index> id,Index next) {
		boolean Not_Add = true;
		for (int i=0;i<id.size();i++) {
			if (id.get(i).name.compareTo(next.name)>0) {
				id.add(i,next);
				Not_Add = false;
				break;
			} else if (id.get(i).name.compareTo(next.name)==0) {
				id.get(i).a.add(next.a.get(0));
				Not_Add = false;
				break;
			}
		}
		if (Not_Add) id.add(next);
	}

	void printIndex() {
		System.out.println(this.name);
		Collections.sort(this.a);
		System.out.print(this.a.get(0));
		for (int i=1;i<a.size();i++) {
			System.out.print(" "+this.a.get(i));
		}
		System.out.println();
	}
}

class Main {
	public static void main(String args[]) {
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		String buf;
		ArrayList<Index> id = new ArrayList<Index>();
		boolean ff = true;

		try {

		while ((buf = br.readLine())!=null) {
			StringTokenizer st = new StringTokenizer(buf);
			String name = st.nextToken();
			int page = Integer.parseInt(st.nextToken());
			if (ff) {
				id.add(new Index(name,page));
				ff = false;
			} else {
				Index t = new Index(name,page);
				id.get(0).AddToIndex(id,t);
			}
		}
		for (int i=0;i<id.size();i++) {
			id.get(i).printIndex();
		}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}