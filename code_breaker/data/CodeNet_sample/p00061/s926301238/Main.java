import java.util.Scanner;

public class Main{
	public static void main(String[] args) {
		Scanner sc = new Scanner(System.in);
		int[] p = new int[101];
		int[] s = new int[31];
		while(sc.hasNext()) {
			String[] t = sc.next().split(",");
			if(t[0].equals("0")) break;
			p[Integer.parseInt(t[0])] = Integer.parseInt(t[1]);
			s[Integer.parseInt(t[1])]++;
		}
		int[] r = new int[31];
		int k = 1;
		for(int i = 30; i >= 0; i--) {
			r[i] = k;
			if(s[i] != 0) {
				k++;
			}
		}
//		for(int i = 30; i >= 0; i--) {
//			System.out.printf("%d %d\n", i, r[i]);
//		}
//		for(int i = 1; i <= 5; i++) {
//			System.out.println(r[p[i]]);
//		}
		while(sc.hasNext()) {
			int q = sc.nextInt();
			System.out.println(r[p[q]]);
		}
		sc.close();
	}
}
