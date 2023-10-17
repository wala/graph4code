import java.io.*;
import java.util.StringTokenizer;

class Team {
	int n,score;

	Team(int n) {
		this.n = n;
	}

	void AddScore(int s) {
		this.score = s;
	}

	int match(int m[]) {
		for (int i=0;i<100;i++)
			if (this.score==m[i]) return i+1;
		return -1;
	}
}

class Main {
	public static void main(String args[]) {
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		String buf;

		try {
			Team team[] = new Team[101];
			int m[] = new int[101];
			int tp = 0;
			int mp = 0;
			for (int i=0;i<=100;i++) m[i] = 0;
			while ((buf = br.readLine()).equals("0,0")==false) {
				StringTokenizer st = new StringTokenizer(buf,",");

				team[tp] = new Team(Integer.parseInt(st.nextToken()));
				int d = Integer.parseInt(st.nextToken());
				team[tp++].AddScore(d);
				if (mp==0) {
					m[0] = d;
					mp++;
				}
				for (int i=0;i<=mp;i++) {
					if (d>m[i]) {
						for (int j=mp;j>i;j--) {
							int t = m[j-1];
							m[j-1] = m[j];
							m[j] = t;
						}
						m[i] = d;
						mp++;
						break;
					} else if (d==m[i]) break;
				}
			}
			while ((buf = br.readLine())!=null) {
				for (int i=0;i<=100;i++) {
					if (team[i].n==Integer.parseInt(buf)) {
						System.out.println(team[i].match(m));
						break;
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}