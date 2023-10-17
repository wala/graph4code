import java.util.Scanner;
public class Main {
	public static void main(String[] args){
		Scanner sc=new Scanner(System.in);
		int[] team=new int[100];
		int[] data=new int[100];
		int ptr=0;
		while(true){
			String str=sc.next();
			if(str.equals("0,0")){
				break;
			}
			team[ptr]=Integer.parseInt(str.split(",")[0]);
			data[ptr]=Integer.parseInt(str.split(",")[1]);
			ptr++;
		}
		for(int i=0;i<ptr;i++){
			for(int j=ptr-1;j>=i+1;j--){
				if(data[j]>data[j-1]){
					int box=data[j];
					data[j]=data[j-1];
					data[j-1]=box;
					box=team[j];
					team[j]=team[j-1];
					team[j-1]=box;
				}
			}
		}
		int[] ranking=new int[ptr];
		int checkRank=data[0];
		int rank=1;
		for(int i=0;i<ptr;i++){
			if(data[i]!=checkRank){
				checkRank=data[i];
				rank++;
			}
			ranking[i]=rank;
		}
		while(sc.hasNext()){
			int searchTeam=sc.nextInt();
			for(int i=0;i<ptr;i++){
				if(team[i]==searchTeam){
					System.out.println(ranking[i]);
					break;
				}
			}
		}
	}
}