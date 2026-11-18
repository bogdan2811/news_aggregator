import java.util.HashSet;
import java.util.Set;

public class Helpers {

    public static String normalizeCategory(String category) {
        category = category.trim();
        category = category.replace(",", "");
        category = category.replaceAll("\\s+", "_");
        category = category.concat(".txt");
        return category;
    }

    public static String normalizeLanguage(String language) {
        return language.concat(".txt");
    }

    public static Set<String> transformText(String text) {
        Set<String> words = new HashSet<String>();
        text = text.toLowerCase();
        String[] wordArray = text.split("\\s+");
        for (String word : wordArray) {
            word = word.replaceAll("[^a-z]", "");
            if (!word.isEmpty() && !NewsAggregator.linkingWordsSet.contains(word)) {
                words.add(word);
            }
        }
        return words;
    }
}
