import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Article {
    private String uuid, title, author, url, text,
            published, language;
    private List<String> categories;
    public Article() {
    }


    public String getUuid() {
        return uuid;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public String getUrl() {
        return url;
    }

    public String getText() {
        return text;
    }

    public String getPublished() {
        return published;
    }

    public String getLanguage() {
        return language;
    }

    public List<String> getCategories() {
        return categories;
    }
}
