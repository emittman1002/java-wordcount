package net.emittman;

import lombok.Getter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WordCount {
    @Getter
    private ConcurrentSkipListMap<String, Integer> wordCounts;

    public WordCount() {
        wordCounts = new ConcurrentSkipListMap<String, Integer>();
    }

    public void addWord(String word) {
        Integer ct = wordCounts.get(word);
        if (ct != null) {
            wordCounts.put(word, ++ct);
        }
        else {
            wordCounts.put(word, 1);
        }
    }

    public void removeWord(String word) {
        Integer ct = wordCounts.get(word);
        if (ct != null) {
            if (--ct > 0) {
                wordCounts.put(word, --ct);
            }
            else {
                wordCounts.remove(word);
            }
        }
    }

    public static void main(String[] args) {
        int ret = 0;

        System.out.println("args.length = " + args.length);
        try {
            URL url;
            if (args.length > 0) {
                url = new URL(args[0]);
            } else {
                url = new URL("https://www.cnn.com");
            }

            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");

            if (connection.getResponseCode() == 200) {
                // Calculate the count
                System.out.println("Counting words from " + url.toString() + "...");
                WordCount wordCount = new WordCount();
                final Pattern WORD_CHARS = Pattern.compile("[a-zA-Z0-9_.-]+");
                String line;
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                while ((line = reader.readLine()) != null) {
                    line = line.replaceAll("\s+", " ")
                            .replaceAll(". ", " ");
                    for (String chars : line.split("\s")) {
                        Matcher matcher = WORD_CHARS.matcher(chars);
                        int start = 0;
                        int len = chars.length();
                        while (start < len && matcher.find(start)) {
                            String word = matcher.group();
                            wordCount.addWord(word);
                            start = matcher.end();
                        }
                    }
                }

                // Output the results
                System.out.println("\nResults:");
                Map<String, Integer> wordCounts = wordCount.getWordCounts();
                List<Map.Entry<String, Integer>> entries = new ArrayList();
                entries.addAll(wordCounts.entrySet());
                // Sort highest to lowest
                entries.sort((e1, e2) -> e2.getValue() - e1.getValue());
                for (Map.Entry<String, Integer> entry : entries) {
                    System.out.println("  " + entry.getKey() + ": " + entry.getValue());
                }
            } else {
                System.err.println("HTTPS returned " + connection.getResponseCode());
                System.err.println(connection.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
            ret = 1;
        }
        System.exit(ret);
    }
}
