
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class Main {
    public static void main(String args[]){
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        try{
            String line;
            HashMap<String, Integer> hm = new HashMap<String,Integer>();
            
            while((line=br.readLine())!=null){
                if(line.equals(""))break;
                String[] splited = line.split(" ");
                for(String str:splited){
                    if(hm.containsKey(str))hm.put(str,hm.get(str)+1);
                    else hm.put(str,1);
                }
                String mostFreq="", mostLong="";
                int freq=0;
                for(String key:hm.keySet()){
                    if(hm.get(key)>freq){freq=hm.get(key);mostFreq=key;}
                    if(key.length()>mostLong.length()) mostLong=key;
                }
                System.out.println(mostFreq+" "+mostLong);
            }
        }catch(Exception e){e.printStackTrace();}
    }
}