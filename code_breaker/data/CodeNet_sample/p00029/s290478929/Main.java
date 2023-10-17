import java.util.Scanner;

class Main {
	public static void main(String args[]){
		Scanner scan = new Scanner(System.in);
		String[] splitLine;
		String[] sentence = new String[32];
		String line = new String();
		int[] num = new int[32];
		int max = 0,maxLen=0;
		int i;
		int current=0;
		boolean flag;
		
		line = scan.nextLine();
		splitLine = line.split(" ");
		
		for(i=0;i<32;i++){
			num[i] = 1;
		}
		
		for(i=0;i<splitLine.length;i++){
			if(current == 0){
				sentence[0] = splitLine[0];
				current++;
			}else{
				flag = true;
				for(int j=0;j<current;j++){
					if(sentence[j].equals(splitLine[i])){
						num[j]++;
						flag = false;
					}
				}
				if(flag){
					sentence[current] = splitLine[i];
					current++;
				}
			}
		}
		
		for(i=1;i<current;i++){
			if(num[max] < num[i]){
				max = i;
			}
			if(sentence[maxLen].length() < sentence[i].length()){
				maxLen = i;
			}
		}
		
		System.out.println(sentence[max] + " " + sentence[maxLen]);
		scan.close();
	}
}