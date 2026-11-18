import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

public class MyThread extends Thread {
    private int id, nrThreads, uniqueSize;
    private CyclicBarrier barrier;
    private ConcurrentHashMap<String, List<Article>> idHashMap;
    private ConcurrentHashMap<String, List<Article>> titleHashMap;
    private Article[] uniqueArticles;
    private ConcurrentHashMap<String, List<String>> categoryHashMap;
    private ConcurrentHashMap<String, List<String>> languagesHashMap;
    private List<Result.Pair> pairs;
    private ConcurrentHashMap<String, Integer> keywordHashMap;
    private AtomicInteger totalArticles;

    public MyThread(int id) {
        this.id = id;
        barrier = NewsAggregator.barrier;
        nrThreads = NewsAggregator.THREADS;
        idHashMap = NewsAggregator.idHashMap;
        titleHashMap = NewsAggregator.titleHashMap;
        categoryHashMap = NewsAggregator.categoryHashMap;
        languagesHashMap = NewsAggregator.languagesHashMap;
        pairs = NewsAggregator.pairs;
        keywordHashMap = NewsAggregator.keywordHashMap;
        totalArticles = NewsAggregator.totalArticles;
    }

    public void parseFiles() {
        String[] files = NewsAggregator.articleFiles;
        int filesSize = files.length;
        int start = (int) (id * (double) filesSize / nrThreads);
        int end = Math.min((int) ((id + 1) * (double) filesSize / nrThreads) ,  filesSize);
        int count = 0;

        ObjectMapper mapper = NewsAggregator.mapper;
        for (int i = start; i < end; i++) {
            try {
                List<Article> articles = mapper.readValue(new File(files[i]),
                        new TypeReference<List<Article>>() {});
                for (Article article : articles) {
                    idHashMap.computeIfAbsent(article.getUuid(),
                                    k -> Collections.synchronizedList(new ArrayList<>()))
                            .add(article);
                    titleHashMap.computeIfAbsent(article.getTitle(),
                                    k -> Collections.synchronizedList(new ArrayList<>()))
                            .add(article);
                }
                count += articles.size();
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
        }
        totalArticles.addAndGet(count);
    }


    public void removeDuplicates() {
        Set<String> toRemove = new HashSet<>();

        for (Map.Entry<String, List<Article>> entry : titleHashMap.entrySet()) {
            List<Article> articles = entry.getValue();
            if (articles.size() > 1) {
                for (Article article : articles) {
                    toRemove.add(article.getUuid());
                }
            }
        }

        for (Map.Entry<String, List<Article>> entry : idHashMap.entrySet()) {
            List<Article> articles = entry.getValue();
            if (articles.size() > 1) {
                for (Article article : articles) {
                    toRemove.add(article.getUuid());
                }
            }
        }

        List<Article> aux = new ArrayList<>();
        for (Map.Entry<String, List<Article>> entry : idHashMap.entrySet()) {
            if (!toRemove.contains(entry.getKey())) {
                aux.add(entry.getValue().getFirst());
            }
        }

        NewsAggregator.uniqueArticles = aux.toArray(new Article[aux.size()]);
    }

    public void generateOutputParallel() {
        String[] categories = NewsAggregator.categories;
        int categoriesSize = NewsAggregator.NR_CATEGORIES;
        List<Result> resultsCat = NewsAggregator.categoryResult;
        int start = (int) (id * (double) categoriesSize / nrThreads);
        int end = Math.min((int) ((id + 1) * (double) categoriesSize / nrThreads) , categoriesSize);
        for (int i = start; i < end; i++) {
            String current = categories[i];
            List<String> ids = categoryHashMap.get(current);
            if (ids != null) {
                resultsCat.add(new Result(Helpers.normalizeCategory(current), ids));
            }
        }

        String[] languages = NewsAggregator.languages;
        int languagesSize = NewsAggregator.NR_LANGUAGES;
        List<Result> resultsLg = NewsAggregator.languageResult;
        start = (int) (id * (double) languagesSize / nrThreads);
        end = Math.min((int) ((id + 1) * (double) languagesSize / nrThreads) , languagesSize);
        for (int i = start; i < end; i++) {
            String current = languages[i];
            List<String> ids = languagesHashMap.get(current);
            if (ids != null) {
                resultsLg.add(new Result(Helpers.normalizeLanguage(current), ids));
            }
        }
    }

    public void generateOutputSecv() {
        NewsAggregator.allResult = new Result("all_articles.txt", pairs, false);
        List<Result.Pair> keywordPairs = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : keywordHashMap.entrySet()) {
            keywordPairs.add(new Result.Pair(entry.getKey(), entry.getValue()));
        }
        NewsAggregator.keywordResult = new Result("keywords_count.txt", keywordPairs, true);

        HashMap<String, Integer> authorHashMap =  new HashMap<>();
        for (Article article : uniqueArticles) {
            authorHashMap.merge(article.getAuthor(), 1, Integer::sum);
        }

        int duplicateCount = totalArticles.get() - uniqueSize;

        int maxCountAuthor = -1;
        String bestAuthor = null;
        for (Map.Entry<String, Integer> entry : authorHashMap.entrySet()) {
            int count = entry.getValue();
            String name = entry.getKey();
            if (count > maxCountAuthor ||
                    (count == maxCountAuthor && name.compareTo(bestAuthor) < 0)) {
                maxCountAuthor = count;
                bestAuthor = name;
            }
        }

        int maxCountLanguage = -1;
        String topLanguage = null;
        for (Map.Entry<String, List<String>> entry : languagesHashMap.entrySet()) {
            String language = entry.getKey();
            List<String> languages = entry.getValue();
            if (languages.size() > maxCountLanguage ||
                    (languages.size() == maxCountLanguage && language.compareTo(topLanguage) < 0)) {
                topLanguage = language;
                maxCountLanguage = languages.size();
            }
        }

        int maxCountCategory = -1;
        String topCategory = null;
        for (Map.Entry<String, List<String>> entry : categoryHashMap.entrySet()) {
            String category = entry.getKey();
            List<String> categories = entry.getValue();
            if (categories.size() > maxCountCategory ||
                    (categories.size() == maxCountCategory && category.compareTo(topCategory) < 0)) {
                topCategory = category;
                maxCountCategory = categories.size();
            }
        }

        String idRecent = NewsAggregator.allResult.getPairs().getFirst().getField();
        Article recent = idHashMap.get(idRecent).getFirst();

        Result.Pair keywordPair = NewsAggregator.keywordResult.getPairs().getFirst();

        StringBuilder result = new StringBuilder();
        result.append("duplicates_found - ").append(duplicateCount).append("\n");
        result.append("unique_articles - ").append(uniqueSize).append("\n");
        result.append("best_author - ").append(bestAuthor).append(" ")
                .append(maxCountAuthor).append("\n");
        result.append("top_language - ").append(topLanguage).append(" ")
                .append(maxCountLanguage).append("\n");
        result.append("top_category - ").append(Helpers.normalizeCategory(topCategory)
                .replace(".txt", "")).append(" ").append(maxCountCategory)
                .append("\n");
        result.append("most_recent_article - ").append(recent.getPublished()).append(" ")
                .append(recent.getUrl()).append("\n");
        result.append("top_keyword_en - ").append(keywordPair.getField()).append(" ")
                .append(keywordPair.getNumber()).append("\n");
        NewsAggregator.report = result.toString();
    }

    public void organizeArticles() {

        int start = (int) (id * (double) uniqueSize / nrThreads);
        int end = Math.min((int) ((id + 1) * (double) uniqueSize / nrThreads) , uniqueSize);

        for (int i = start; i < end; i++) {
            Article current = uniqueArticles[i];

            Set<String> uniqueCategories = new HashSet<>(current.getCategories());
            for (String category : uniqueCategories) {
                categoryHashMap.computeIfAbsent(category,
                                k -> Collections.synchronizedList(new ArrayList<>()))
                        .add(current.getUuid());
            }

            languagesHashMap.computeIfAbsent(current.getLanguage(),
                            k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(current.getUuid());

            pairs.add(new Result.Pair(current.getUuid(), current.getPublished()));

            if (current.getLanguage().equals("english")) {
                Set<String> words = Helpers.transformText(current.getText());
                for (String word : words) {
                    keywordHashMap.merge(word, 1, Integer::sum);
                }
            }
        }
    }

    public void printOutput() {
        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter("reports.txt"));
            bw.write(NewsAggregator.report);
            bw.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        for (Result res : NewsAggregator.categoryResult) {
            res.createFile();
        }
        for (Result res : NewsAggregator.languageResult) {
            res.createFile();
        }
        NewsAggregator.allResult.createFile();
        NewsAggregator.keywordResult.createFile();
    }


    @Override
    public void run() {
        parseFiles();

        try {
            barrier.await();
        } catch (BrokenBarrierException | InterruptedException e) {
            e.printStackTrace();
        }

        if (id == 0) {
            removeDuplicates();
        }

        try {
            barrier.await();
        } catch (BrokenBarrierException | InterruptedException e) {
            e.printStackTrace();
        }

        uniqueArticles = NewsAggregator.uniqueArticles;
        uniqueSize = uniqueArticles.length;

        organizeArticles();

        try {
            barrier.await();
        } catch (BrokenBarrierException | InterruptedException e) {
            e.printStackTrace();
        }

        generateOutputParallel();

        try {
            barrier.await();
        } catch (BrokenBarrierException | InterruptedException e) {
            e.printStackTrace();
        }

        if (id == 0) {
            generateOutputSecv();
            printOutput();
        }
    }
}