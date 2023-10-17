


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
 

 class Main{
             
     static int[] ints=new int[10];
     public static void main(String[] args) throws IOException {
        BufferedReader reader =
                new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            String input = reader.readLine();
            if (input == null || input.equals("0")) {
                break;
            }
            long value = Long.parseLong(input);
            int count = 0;
            for (long i = 5; i <= value; i *= 5) {
                count += value / i;
            }
            System.out.println(count);
        }
    }
     public static void main2(String args[]){
         BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
         
         try{
             String line;
             while((line=br.readLine())!= null){if(line.isEmpty())break;
             int nn = Integer.parseInt(line), count=0;
             if(nn==0)break;
                for(int i=5; i<nn;i*=5){
                    count+=nn/i;
                }
                System.out.println(count);
             }
             
         }catch(Exception e){}         
     }
   
 }