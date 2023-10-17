import java.util.Scanner;

public class Main {
	public static void main(String[] args) {
		int i=0;
		String[] s = new String[1000];
		int[] count = new int[1000];
		Scanner sc = new Scanner(System.in);
		
		while( sc.hasNext() ){
			boolean flag=true;
			String tmp = sc.next();
			
			if(i==0){
				s[i]=tmp;
				count[i]=1;
				i++;
				continue;
			}
			
			for(int j=0; j<i; j++) {
				if(s[j].equals(tmp)){
					count[j]++;
					flag=false;
					break;
				}
			}
			
			if(flag){
				s[i]=tmp;
				count[i]=1;
				i++;
			}
		}
		
		int max = count[0];
		int length = s[0].length();
		String str = s[0];
		String str2 = s[0];
		for(int j=0; j<i; j++) {
			//System.out.println("count[" + j + "] = "+count[j]);
			//System.out.println("s[" + j + "] = "+s[j]);
		
		
			if(max<count[j]) {
				max = count[j];
				str = s[j];
			}
			if(s[j].length()>length){
				length = s[j].length();
				str2 = s[j];
			}
		}
		
		System.out.println(str + " " + str2);
			
		
	}
}