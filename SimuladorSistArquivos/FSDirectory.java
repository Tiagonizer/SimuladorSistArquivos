import java.io.Serializable;
import java.util.*;

class FSDirectory extends FSNode {
    private static final long serialVersionUID = 1L;
    private List<FSDirectory> directories = new ArrayList<>();
    private List<FSFile> files = new ArrayList<>();

    FSDirectory(String name) { super(name); }

    List<FSDirectory> getDirectories() { return Collections.unmodifiableList(directories); }
    List<FSFile> getFiles() { return Collections.unmodifiableList(files); }

    void addDirectory(FSDirectory d) { directories.add(d); }
    void removeDirectory(String name) { directories.removeIf(d -> d.getName().equals(name)); }
    void addFile(FSFile f) { files.add(f); }
    void removeFile(String name) { files.removeIf(f -> f.getName().equals(name)); }

    FSDirectory getDirectory(String name) {
        for (FSDirectory d : directories) if (d.getName().equals(name)) return d;
        return null;
    }

    FSFile getFile(String name) {
        for (FSFile f : files) if (f.getName().equals(name)) return f;
        return null;
    }

    boolean hasDirectory(String name) { return getDirectory(name) != null; }
    boolean hasFile(String name) { return getFile(name) != null; }

    boolean isEmpty() { return directories.isEmpty() && files.isEmpty(); }

    @Override
    boolean isDirectory() { return true; }

    // Deep copy for recursive copy
    FSDirectory deepCopy() {
        FSDirectory copy = new FSDirectory(this.name);
        for (FSFile f : files) copy.addFile(f.copy());
        for (FSDirectory d : directories) copy.addDirectory(d.deepCopy());
        return copy;
    }

    void setName(String name) { this.name = name; }
}
