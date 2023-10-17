import java.util.Scanner;


public class Main {

	public static void main(String[] args) {
		Scanner sc = new Scanner(System.in);
		
		while(sc.hasNext()){
			String[] str = sc.nextLine().split("[\\s]");
			boolean flag = false;
			
			for(int i = 0; i < 26; i++){
				
				for(int j = 0; j < str.length; j++){
					char[] c = str[j].toCharArray();
					
					for(int k = 0; k < c.length; k++){
						if(Character.isLowerCase(c[k])){
							if(c[k] == 'z')
								c[k] = 'a';
							else
								c[k] = (char)(c[k] + 1);
						}
					}
					str[j] = new String(c);
					if(str[j].equals("this") || str[j].equals("the") || str[j].equals("that"))
						flag = true;
				}
				if(flag)
					break;
			}
			int i = 0;
			for(String tmp:str){
				System.out.print(tmp);
				if(i == (str.length) - 1)
					break;
				System.out.print(" ");
				i++;
			}
			System.out.println();
		}
	}
}