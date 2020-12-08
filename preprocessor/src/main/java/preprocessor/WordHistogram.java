package preprocessor;

import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

public class WordHistogram {
    private final Map<String, Long> hist = new HashMap<>();

    public void count(String word) {
        long count = hist.getOrDefault(word, 0L);
        hist.put(word, count + 1);
    }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner("\n");
        for (Map.Entry<String, Long> en : hist.entrySet()) {
            joiner.add(en.getKey() + " " + en.getValue());
        }
        return joiner.toString();
    }
}
