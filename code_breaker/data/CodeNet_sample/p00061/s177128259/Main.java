
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Arrays;
 

public class Main{
          
     
     public static void main(String args[]){
         BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
         
         try{
             String line;
            int scores[] = new int[101];// team 1 score -> scores[1]
            int ranks[] = new int[31]; // ranks[score] -> ranking 
            while((line=br.readLine())!= null){if(line.isEmpty())break;
                String[] spl=line.split(",");
                int n1 = Integer.parseInt(spl[0]);
                int n2 = Integer.parseInt(spl[1]); if(n1==0&&n2==0)break;
                scores[n1]+=n2;                
             }
             //System.out.println(Arrays.toString(scores));
             ranks[30]=1;ranks[29]=(getNum(scores,30)>=1&&getNum(scores,29)>=1)? 2: 1;
             for(int i=28; i>=0;i--){
                 ranks[i]=(getNum(scores,i)>=1)? ranks[i+1]+1:ranks[i+1];
             }
             //System.out.println(Arrays.toString(ranks));
             while((line=br.readLine())!= null){if(line.isEmpty())break;
                String[] spl=line.split(" ");
                int n1 = Integer.parseInt(spl[0]);
                 System.out.println(ranks[scores[n1]]);
             }
             br.close();
             
         }catch(Exception e){e.printStackTrace();}         
     }
   static int getNum(int[] array, int score){
       int count=0;
       for(int i=0; i<array.length; i++){
           if(array[i]==score){count++;
           break;}
       }
       return count;
   }
 }