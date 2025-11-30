class PathParts {
    final String parentPath;
    final String baseName;

    private PathParts(String parentPath, String baseName) {
        this.parentPath = parentPath;
        this.baseName = baseName;
    }

    static PathParts of(String absPath) {
        if (absPath == null || absPath.isEmpty()) absPath = "/";
        if (!absPath.startsWith("/")) absPath = "/" + absPath;
        if ("/".equals(absPath)) return new PathParts("/", "/");
        int idx = absPath.lastIndexOf('/');
        String parent = (idx == 0) ? "/" : absPath.substring(0, idx);
        String base = absPath.substring(idx + 1);
        return new PathParts(parent, base);
    }
}
