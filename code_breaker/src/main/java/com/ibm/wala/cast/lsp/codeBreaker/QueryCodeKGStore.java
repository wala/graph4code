package com.ibm.wala.cast.lsp.codeBreaker;


import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashSet;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

public class QueryCodeKGStore {
    private String codeAPILocation = "http://localhost:5000/search";

    public Iterable<String> getSuggestion(String line) {
        // parse line starting with the term Watson
        System.err.println("LINE: " + line);
        line = line.substring(line.indexOf("Watson") + "Watson ".length());
        StringBuilder content = new StringBuilder();
        Set<String> results = new HashSet<String>();
        try {
            URL url = new URL(codeAPILocation);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setDoOutput(true);
            DataOutputStream out = new DataOutputStream(connection.getOutputStream());
            out.writeBytes("query=" + URLEncoder.encode(line, "UTF-8"));
            out.flush();
            out.close();
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(connection.getInputStream()));
            String l = null;
            StringBuffer buf = new StringBuffer();
            while ((l = in.readLine()) != null) {
                buf.append(l);
            }
            JSONArray obj = new JSONArray(buf.toString());
            for (Object c : obj) {
                JSONObject o = (JSONObject) c;
                String class_name = o.getString("class");
                class_name = class_name.replaceAll("[ '<>]+", "");
                class_name = class_name.replaceAll("class", "");
                results.add(class_name);
                if (results.size() > 2) {
                	break;
                }
            }

            in.close();

        } catch(Exception e) {
            e.printStackTrace();
        }
        return results;
    }

    public static void main(String[] args) {
        QueryCodeKGStore store = new QueryCodeKGStore();
        System.err.println(store.getSuggestion("PCA.decomposition.sklearn # Watson \"memory efficient\""));
    }

    public class ClassInfo {
        String clazz;
        String class_name;
        String method_name;
    }

}
