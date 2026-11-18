import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

public class NewsAggregator {
    public static int THREADS, NR_FILES, NR_LANGUAGES, NR_CATEGORIES;
    public static String[] articleFiles, languages, categories;
    public static Set<String> linkingWordsSet;
    public static CyclicBarrier barrier;
    public static ObjectMapper mapper;
    public static AtomicInteger totalArticles;
    public static ConcurrentHashMap<String, List<Article>> idHashMap;
    public static ConcurrentHashMap<String, List<Article>> titleHashMap;
    public static ConcurrentHashMap<String, List<String>> categoryHashMap;
    public static ConcurrentHashMap<String, List<String>> languagesHashMap;
    public static ConcurrentHashMap<String, Integer> keywordHashMap;
    public static Article[] uniqueArticles;
    public static List<Result.Pair> pairs;
    public static List<Result> categoryResult, languageResult;
    public static Result allResult, keywordResult;
    public static String report;


    public static String[] processFile(BufferedReader br) throws IOException {
        String line = br.readLine().trim();
        int size = Integer.parseInt(line);
        String[] array = new String[size];
        for (int i = 0; i < size; i++) {
            array[i] = br.readLine().trim();
        }
        return array;
    }

    public static void processInput(String[] args) {
        THREADS = Integer.parseInt(args[0]);
        File baseDir = new File(args[1]).getParentFile();
        try {
            BufferedReader br = new BufferedReader(new FileReader(args[1]));
            String[] files = processFile(br);
            articleFiles = new String[files.length];
            NR_FILES = articleFiles.length;
            for (int i = 0; i < NR_FILES; i++) {
                articleFiles[i] = new File(baseDir, files[i]).getCanonicalPath();
            }
            br.close();
        } catch (FileNotFoundException e) {
            System.out.println("File not found");
            System.exit(1);
        } catch (IOException e) {
            System.out.println("Error reading file");
            System.exit(1);
        }

        try {
            BufferedReader br = new BufferedReader(new FileReader(args[2]));
            String line = br.readLine().trim();
            if (Integer.parseInt(line) != 3) {
                System.out.println("Not enough auxiliary files");
                System.exit(1);
            }
            // line = path languages
            line = br.readLine().trim();
            line = new File(baseDir, line).getCanonicalPath();
            BufferedReader br1 = new BufferedReader(new FileReader(line));
            languages = processFile(br1);
            NR_LANGUAGES = languages.length;
            br1.close();

            // line = path categories
            line =  br.readLine().trim();
            line = new File(baseDir, line).getCanonicalPath();
            br1 =  new BufferedReader(new FileReader(line));
            categories = processFile(br1);
            NR_CATEGORIES = categories.length;
            br1.close();

            // line = path words
            line =  br.readLine().trim();
            line = new File(baseDir, line).getCanonicalPath();
            br1 =  new BufferedReader(new FileReader(line));

            linkingWordsSet = new HashSet<>(Arrays.asList(processFile(br1)));
            br1.close();
            br.close();
        } catch (FileNotFoundException e) {
            System.out.println("File not found");
            System.exit(1);
        } catch (IOException e) {
            System.out.println("Error reading file");
            System.exit(1);
        }
    }

    public static void main(String[] args) {
        processInput(args);

        MyThread[] threads = new MyThread[THREADS];
        barrier = new CyclicBarrier(THREADS);
        mapper = new ObjectMapper();
        idHashMap = new ConcurrentHashMap<>();
        titleHashMap = new ConcurrentHashMap<>();
        categoryHashMap = new ConcurrentHashMap<>();
        languagesHashMap = new ConcurrentHashMap<>();
        categoryResult = Collections.synchronizedList(new ArrayList<>());
        languageResult = Collections.synchronizedList(new ArrayList<>());
        pairs = Collections.synchronizedList(new ArrayList<>());
        keywordHashMap = new ConcurrentHashMap<>();
        totalArticles = new AtomicInteger();

        for (int i = 0; i < THREADS; i++) {
            threads[i] = new MyThread(i);
            threads[i].start();
        }

        for (int i = 0; i < THREADS; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }


}