package net.emittman;

import lombok.Getter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WordCount {
    @Getter
    private final ConcurrentSkipListMap<String, Integer> wordCounts;

    public WordCount() {
        wordCounts = new ConcurrentSkipListMap<>();
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

        try {
            String argValue;
            URL url;
            if (args.length >= 1) {
                argValue = args[0];
            } else {
                argValue = "https://www.cnn.com";
            }
            url = new URL(argValue);

            int numResults;
            if (args.length >= 2) {
                argValue = args[1];
                if (argValue.equals("all")) {
                    argValue = Integer.toString(Integer.MAX_VALUE);
                }
            } else {
                argValue = "20";
            }
            numResults = Integer.parseInt(argValue);

            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");

            if (connection.getResponseCode() == 200) {
                // Calculate the count
                System.out.println("Counting words from " + url + "...");
                WordCount wordCount = new WordCount();
                final Pattern WORD_CHARS = Pattern.compile("[a-zA-Z0-9_.-]+");
                String line;
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                while ((line = reader.readLine()) != null) {
                    line = line.replaceAll("\\s+", " ")
                            .replaceAll(". ", " ");
                    for (String chars : line.split("\\s")) {
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
                if (numResults != Integer.MAX_VALUE) {
                    System.out.println("Top " + numResults + " Results:");
                }
                else {
                    System.out.println("Results:");
                }
                Map<String, Integer> wordCounts = wordCount.getWordCounts();
                List<Map.Entry<String, Integer>> entries = new ArrayList<>(wordCounts.entrySet());
                // Sort highest to lowest
                entries.sort((e1, e2) -> e2.getValue() - e1.getValue());
                int ct = 0;
                for (Map.Entry<String, Integer> entry : entries) {
                    System.out.println("  " + entry.getKey() + ": " + entry.getValue());
                    if (++ct >= numResults) {
                        break;
                    }
                }
            } else {
                System.err.println("HTTPS returned " + connection.getResponseCode());
                System.err.println(connection);
            }
        } catch (Exception e) {
            e.printStackTrace();
            ret = 1;
        }
        System.exit(ret);
    }
}
