import java.io.IOException;
import java.util.Scanner;

public class Main {

	public static void main(String[] args) throws IOException {		
		String tmpthe = "";
		String tmpthis = "";
		String tmpthat = "";
		int shift_num = 0;

		Scanner scanner = new Scanner(System.in);
		scanner.useDelimiter("\\n");
		while(scanner.hasNext()){
			String input = scanner.next();
			for(int i=0; i<26; i++){
				tmpthe = shift("the", i);
				tmpthis = shift("this", i);
				tmpthat = shift("that", i);
				if((input.indexOf(tmpthe) != -1) || (input.indexOf(tmpthis) != -1) || (input.indexOf(tmpthat) != -1)){
					shift_num = i;
					break;
				}
			}
			System.out.println(shiftR(input, shift_num));
			tmpthat = "";
			tmpthis = "";
			tmpthat = "";
			shift_num=0;
		}
	}

	public static String shift(String s, int num){
		String tmp = "";
		for(int i=0; i<s.length(); i++){
				if((s.charAt(i)+num%26) > 'z'){
					tmp = tmp + String.valueOf((char)(s.charAt(i)+(num%26)-'z'+'a'-1));
				}else{
					tmp = tmp + String.valueOf((char)((s.charAt(i)+num%26)));
				}
		}
		return tmp;
	}

	public static String shiftR(String s, int num){
		String tmp ="";
		for( int i=0; i<s.length(); i++){
			if(s.charAt(i) >= 'a' && s.charAt(i) <= 'z'){
				if(s.charAt(i)-num < 'a'){
					tmp = 	tmp + String.valueOf((char)(s.charAt(i)-num+26));
				}else{
					tmp = tmp + String.valueOf((char)((s.charAt(i)-num)));
				}
			}else{
				tmp = tmp + String.valueOf((char)(s.charAt(i)));
			}
		}
		return tmp;
	}
}