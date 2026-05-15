package SeWeb_hw2;

/**
 * Data Transfer Object for carrying Book data between the Model/Service and View.
 */
public class BookDto {
    private String title;
    private String author;
    private String themes;
    private String readingLevel;
    private String uri;

    // Constructors
    public BookDto() {}

    public BookDto(String title, String author, String themes, String readingLevel, String uri) {
        this.title = title;
        this.author = author;
        this.themes = themes;
        this.readingLevel = readingLevel;
        this.uri = uri;
    }

    // Getters and Setters
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getThemes() { return themes; }
    public void setThemes(String themes) { this.themes = themes; }

    public String getReadingLevel() { return readingLevel; }
    public void setReadingLevel(String readingLevel) { this.readingLevel = readingLevel; }

    public String getUri() { return uri; }
    public void setUri(String uri) { this.uri = uri; }
}