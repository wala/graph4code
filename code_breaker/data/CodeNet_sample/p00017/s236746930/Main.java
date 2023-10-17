import java.util.Scanner;
class Main {
	public static void main(String args[]){
		Scanner scan = new Scanner(System.in);
		
		while(scan.hasNextLine()){
			String line = scan.nextLine();
			String[] cipher = line.split(" ");
			char[][] ch = new char[cipher.length][];
			int i,j;
			
			for(i=0;i<cipher.length;i++){
				ch[i] = cipher[i].toCharArray();
			}
			
			String[] str = new String[cipher.length];
			String s = "";
			out : while(true){
				for(i=0;i<cipher.length;i++){
					for(j=0;j<cipher[i].length();j++){
						if(ch[i][j]!='.' && ch[i][j]!=' '){
							if(ch[i][j] == 'z'){
								ch[i][j] = 'a';
							}else{
								ch[i][j] = (char)(ch[i][j]+1);
							}
						}
					}
					str[i] = String.valueOf(ch[i]);
				}
				
				for(i=0;i<cipher.length;i++){
					if(str[i].equals("the") || str[i].equals("this") || str[i].equals("that")
							|| str[i].equals("the.") || str[i].equals("this.") || str[i].equals("that.")){
						break out;
					}
				}
			}
			
			for(i=0;i<cipher.length-1;i++){
				s+=(str[i]+" ");
			}
			s+=str[cipher.length-1];
			System.out.println(s);
		}
	}
}