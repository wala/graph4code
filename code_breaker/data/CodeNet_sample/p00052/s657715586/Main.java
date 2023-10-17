import java.util.*;

public class Main {
	public static void main(String[] args) {
		Scanner in = new Scanner(System.in);
		while(true) {
			int n = in.nextInt();
			if(n==0) break;
			long result=1;
			int count=0;
			for(int i=n; i>1; i--) {
				result *= i;
				if(result > 1000000000) result %= 1000000000;
				while(result%10 == 0) {
//					System.out.println("result:" + result + " i:" + i + " count:" + count);
					count++;
					result /= 10;
				}
			}
			System.out.println(count);
		}
	}
}