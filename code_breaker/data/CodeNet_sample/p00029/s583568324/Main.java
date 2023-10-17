import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class Main {

	public static void main(String[] args) {
		Scanner stdIn = new Scanner(System.in);
		Map<String, Integer> sen = new HashMap<>();
		int max = 0; //単語の登場回数のカウント変数
        int count = 0; //出力が1単語の時の例外処理用変数
        int max_width = 0; //最長の長さ
		String most_frequent_word = null; //再頻出ワード
		String longest_word = null; //最長単語

		while(stdIn.hasNext()){
			String word = stdIn.next();
            
            if(count == 0){
            	most_frequent_word = word; //出力が1単語の際のための処理
            }

			if(max_width < word.length() ){
				max_width = word.length();
				longest_word = word;
			}

			if(sen.get(word) == null){ //入力された単語がなかったら、
                
                sen.put(word, 1); //新しく、箱を作って
                
            }else{
            	int v = sen.get(word) + 1;
                sen.put(word, v);

                if(max <= v){
                	most_frequent_word = word;
                    max = v;
                }

            }
            
            
            count++;
			 
		}
        System.out.println(most_frequent_word + " " + longest_word);
       
		stdIn.close();
	}

}
