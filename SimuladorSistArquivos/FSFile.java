import java.io.Serializable;

class FSFile extends FSNode {
    private static final long serialVersionUID = 1L;
    private String content;

    FSFile(String name, String content) {
        super(name);
        this.content = content == null ? "" : content;
    }

    String getContent() { return content; }
    void setContent(String c) { content = c == null ? "" : c; }
    int getSize() { return content.getBytes().length; }

    FSFile copy() {
        return new FSFile(name, content);
    }

    @Override
    boolean isDirectory() { return false; }
}
