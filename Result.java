import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Result {
    private String fileName;
    private List<String> ids;
    private List<Pair> pairs;
    private Type type;

    public enum Type {
        ONE_FIELD,
        TWO_FIELDS,
    }

    public static class Pair {
        private String field, date;
        private int number;

        public Pair(String field, String date) {
            this.field = field;
            this.date = date;
        }

        public Pair(String field, int number) {
            this.field = field;
            this.number = number;
        }

        public String getField() {
            return field;
        }

        public int getNumber() {
            return number;
        }
    }

    public Result(String fileName, List<String> field) {
        this.fileName = fileName;
        this.ids = new ArrayList<>(field);
        Collections.sort(this.ids);
        this.type = Type.ONE_FIELD;
    }

    public Result(String fileName, List<Pair> pairs, boolean isNumber) {
        this.fileName = fileName;
        this.type = Type.TWO_FIELDS;
        this.pairs = new ArrayList<>(pairs);

        if (isNumber) {
            this.pairs.sort((a, b) -> {
                int cmp = b.number - a.number;
                if (cmp != 0) {
                    return cmp;
                } else {
                    return a.field.compareTo(b.field);
                }
            });
        } else {
            this.pairs.sort((a, b) -> {
                int cmp = b.date.compareTo(a.date);
                if (cmp != 0) {
                    return cmp;
                } else {
                    return a.field.compareTo(b.field);
                }
            });
        }
    }

    public void createFile() {
        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter(fileName));
            if (type == Type.ONE_FIELD) {
                for (String id : ids) {
                    bw.write(id);
                    bw.newLine();
                }
            } else {
                for (Pair pair : pairs) {
                    if (pair.date == null) {
                        bw.write(pair.field + " " +  pair.number);
                    } else {
                        bw.write(pair.field + " " +  pair.date);
                    }
                    bw.newLine();
                }
            }
            bw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public List<Pair> getPairs() {
        return pairs;
    }
}
