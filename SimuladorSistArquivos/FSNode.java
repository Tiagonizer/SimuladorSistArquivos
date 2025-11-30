import java.io.Serializable;
import java.time.Instant;

abstract class FSNode implements Serializable {
    private static final long serialVersionUID = 1L;
    protected String name;
    protected long createdAt;

    FSNode(String name) {
        this.name = name;
        this.createdAt = Instant.now().toEpochMilli();
    }

    String getName() { return name; }
    void setName(String name) { this.name = name; }

    abstract boolean isDirectory();
}
