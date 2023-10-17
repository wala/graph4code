import java.util.Scanner;

public class Main {

	public static void main(String[] args) {
		Scanner sc = new Scanner(System.in);
		while(true){
			int n = sc.nextInt();
			if(n==0)break;
			int c = 0;
			while(n>=5){
				c += n/5;
                                n= n/5;
			}
			System.out.println(c);
		}
	}
}