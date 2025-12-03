package com.ibm.watson.graphcode;

import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void thing1( String[] args )
    {
        BidiMap<Integer,Integer> map = new DualHashBidiMap<>();
        for(int i = 0; i < 10; i++) {
        	map.put(i, 10-i);
        }
        System.err.println(map.size());
    }

    public static void thing2( String[] args )
    {
        BidiMap<Double,Double> map = new DualHashBidiMap<>();
        for(int i = 0; i < 10; i++) {
        	map.put(i*1.0, (10-i)*1.0);
        }
        System.err.println(map.size());
    }
    
    public static void main(String... args) {
    	thing1(args);
    	thing2(args);
    }
}
