# Parallel News Aggregator

A multithreaded Java application that parses, deduplicates and organizes large collections of news articles stored as JSON files, then generates reports grouped by category, language and keyword frequency.

The main goal of the project is **scalability**: the expensive stages (parsing JSON and organizing ~100k articles) are split across a configurable number of threads that run in synchronized phases.

## Features

- **Parallel JSON parsing** of article files using Jackson.
- **Deduplication**: any article whose `uuid` or `title` appears more than once is removed entirely (every copy is discarded).
- **Organization** of the unique articles by category, by language and by English keywords (stopwords are ignored, and each word counts at most once per article).
- **Report generation**: per-category and per-language article lists, all articles sorted by date, keyword frequencies and a summary report.

## Requirements

- **Java 21 or newer** (the code uses `List.getFirst()`)
- `make`
- Jackson 2.17.2 (already included in `lib/`)

## Build and Run

```bash
make build
make run ARGS="<threads> <articles_file> <inputs_file>"
```

Example:

```bash
make run ARGS="4 data/articles.txt data/inputs.txt"
```

`make clean` removes the compiled classes **and every `.txt` file in the project root** (the generated outputs), so keep input files in a subdirectory.

## Input Format

All paths listed inside the input files are resolved relative to the directory that contains `<articles_file>`.

**`<articles_file>`**: the number of JSON files, followed by one path per line:

```
3
articles/file1.json
articles/file2.json
articles/file3.json
```

**`<inputs_file>`**: exactly 3 auxiliary files, in this order: languages, categories, linking words:

```
3
languages.txt
categories.txt
linking_words.txt
```

Each auxiliary file contains a count followed by one entry per line:

```
3
english
romanian
german
```

**Article JSON files** contain an array of articles. Unknown fields are ignored.

```json
[
  {
    "uuid": "a1b2c3",
    "title": "Example title",
    "author": "Jane Doe",
    "url": "https://example.com/article",
    "text": "Article body...",
    "published": "2025-03-01T08:00:00",
    "language": "english",
    "categories": ["World News", "Science, Technology"]
  }
]
```

## Output

All output files are written to the current working directory.

| File | Content |
|---|---|
| `<Category>.txt` | Sorted UUIDs of the articles in that category (commas removed, spaces replaced with `_`) |
| `<language>.txt` | Sorted UUIDs of the articles in that language |
| `all_articles.txt` | `uuid published` for every unique article, newest first |
| `keywords_count.txt` | `word count` for English articles, sorted by count (descending), then alphabetically |
| `reports.txt` | Summary: duplicates found, unique articles, best author, top language, top category, most recent article, top English keyword |

## Parallelization Strategy

The program uses plain Java threads (`Thread.start()` / `join()`). Each thread runs the same pipeline, split into **phases separated by a `CyclicBarrier`**:

| Phase | Work | Executed by |
|---|---|---|
| 1. Parsing | Each thread parses its block of JSON files and indexes articles by `uuid` and `title` | All threads |
| 2. Deduplication | Builds the array of unique articles | Thread 0 |
| 3. Organization | Each thread groups its block of unique articles by category, language and keywords | All threads |
| 4. Parallel output | Each thread builds the results for its share of categories and languages | All threads |
| 5. Final output | Builds the summary report, sorts global results and writes all files | Thread 0 |

**Work distribution:** every array (files, unique articles, categories, languages) is statically partitioned into contiguous blocks, where thread `id` processes indices `[id * N / P, (id + 1) * N / P)`.

**Synchronization mechanisms:**

- **`CyclicBarrier`**: ensures all threads finish a phase before the next one starts, and makes data written by thread 0 (e.g. the unique articles) visible to the other threads.
- **`ConcurrentHashMap`** with atomic `computeIfAbsent` / `merge`: shared indexes (by id, title, category, language) and keyword counters, updated without lost writes.
- **`Collections.synchronizedList`**: lists that multiple threads append to concurrently.
- **`AtomicInteger`**: total article counter; each thread counts locally and performs a single atomic update to reduce contention.
- **Read-only arrays**: once deduplication is done, unique articles are stored in a plain array, which is safe to read concurrently without locking.

## Performance

**Test setup:** Intel i5-1135G7 @ 2.4 GHz (4 cores / 8 threads), 8 GB RAM, Ubuntu 24.04, OpenJDK 25. The dataset contains 13,789 JSON files with ~100,000 articles in total.

| Threads | 1 | 2 | 3 | 4 | 8 |
|---|---|---|---|---|---|
| Average time (3 runs) | 10.597 s | 6.965 s | 5.843 s | 5.528 s | 5.284 s |
| Speedup `S(p) = T(1) / T(p)` | 1.00 | 1.52 | 1.81 | 1.91 | 2.00 |
| Efficiency `E(p) = S(p) / p` | 1.00 | 0.76 | 0.60 | 0.47 | 0.25 |

**Analysis:**

- Execution time drops significantly from 1 to 2 threads and keeps improving, at a slower rate, up to 4 threads.
- Beyond 4 threads, gains are small. The sequential parts of the program (deduplication and final output on thread 0) limit the maximum speedup (Amdahl's law). Synchronization overhead on the shared hash maps and limited memory bandwidth also grow with the number of threads.
- The test machine has only 4 physical cores, so 4 threads is the best trade-off between speedup and resource usage on this system.

## Project Structure

| File | Description |
|---|---|
| `NewsAggregator.java` | Entry point: reads the input files, initializes shared data structures, starts and joins the threads |
| `MyThread.java` | The parallel pipeline (all phases) |
| `Article.java` | Model for a JSON article |
| `Result.java` | An output file built and sorted in memory, before it is written to disk |
| `Helpers.java` | File name normalization and keyword extraction |
| `Makefile` | Build, run and clean targets |
