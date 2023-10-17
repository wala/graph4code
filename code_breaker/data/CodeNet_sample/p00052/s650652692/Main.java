import java.util.Scanner;
class Main {
	public static void main(String args[]){
		Scanner scan = new Scanner(System.in);
		
		while(true){
			int num = scan.nextInt();
			if(num == 0){
				break;
			}
			int count = 0;
			int div = 5;
			
			while(num/div != 0){
				count += num/div;
				div *= 5;
			}
			
			System.out.println(count);
		}
	}
}