import java.util.Scanner;

public class Main{
	public static void main(String[] args) {
		Scanner sc = new Scanner(System.in);
		int max = 20000;
		long[][] cnt = new long[max + 1][2];

		for(int i = 2; i <= max; i++) {
			int t = i;
			while(t % 2 == 0) {
				t /= 2;
				cnt[i][0]++;
			}
			t = i;
			while(t % 5 == 0) {
				t /= 5;
				cnt[i][1]++;
			}
			cnt[i][0] += cnt[i - 1][0];
			cnt[i][1] += cnt[i - 1][1];
		}
		//System.out.println(Math.min(cnt[12][0], cnt[12][1]));
		while(sc.hasNext()) {
			int n = sc.nextInt();
			if(n == 0) break;
			System.out.println(cnt[n][1]);
		}
		sc.close();
	}
}
