import java.util.*;
public class Main {

	public static void main(String[] args) {
	Scanner cin = new Scanner(System.in);
	while(true){
		int n,c=0;
		n=cin.nextInt();
		if(n==0){
			break;
		}
		for(int i=1;i<n+1;i++){
			int q=i;
			while(true){
				if(q%5==0){
					c=c+1;
					q=q/5;
				}
				if(q%5!=0){
					break;
				}
		    }
		}
		System.out.println(c);
	}
	}
}