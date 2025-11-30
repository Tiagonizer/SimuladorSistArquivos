import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

class FileSystemSimulator implements Serializable {
    private static final long serialVersionUID = 1L;

    private FSDirectory root;
    transient Journal journal; // transient because Journal manages its own file
    transient Path storagePath;

    // for generating unique journal entry ids
    transient AtomicLong journalSeq = new AtomicLong(0);

    FileSystemSimulator() {
        this.root = new FSDirectory("/");
    }

    // load existing FS state or create new, and attach journaling mechanism
    public static FileSystemSimulator loadOrCreate(String storageFilename, String journalFilename) throws IOException, ClassNotFoundException {
        Path storagePath = Paths.get(storageFilename);
        FileSystemSimulator fs;
        if (Files.exists(storagePath)) {
            try (ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(storagePath))) {
                Object obj = ois.readObject();
                if (!(obj instanceof FileSystemSimulator)) {
                    System.out.println("Arquivo de estado inválido. Criando novo sistema.");
                    fs = new FileSystemSimulator();
                } else {
                    fs = (FileSystemSimulator) obj;
                    System.out.println("Estado do sistema carregado de " + storageFilename);
                }
            } catch (Exception e) {
                System.out.println("Falha ao carregar estado: " + e.getMessage());
                fs = new FileSystemSimulator();
            }
        } else {
            fs = new FileSystemSimulator();
            System.out.println("Novo sistema de arquivos criado.");
        }

        fs.storagePath = storagePath;
        fs.journal = new Journal(Paths.get(journalFilename));
        fs.journalSeq = new AtomicLong(fs.journal.currentMaxId());
        // Recovery: replay unfinished operations
        fs.recoverFromJournal();
        return fs;
    }

    // Save state to disk
    public synchronized void save() throws IOException {
        // persist to temporary file and move to avoid corruption
        Path tmp = storagePath.resolveSibling(storagePath.getFileName().toString() + ".tmp");
        try (ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(tmp))) {
            oos.writeObject(this);
            oos.flush();
        }
        Files.move(tmp, storagePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public void shutdown() {
        try {
            save();
        } catch (IOException e) {
            System.out.println("Erro ao salvar estado na saída: " + e.getMessage());
        }
    }

    /* ---------- Journaling wrapper for operations ---------- */

    // Each operation writes an entry to journal with status START, then executes action, then writes COMMIT.
    private synchronized void journaledOperation(String opType, Map<String, String> params, Runnable action) throws FSException {
        long id = journalSeq.incrementAndGet();
        Journal.Entry eStart = new Journal.Entry(id, opType, params, Instant.now().toEpochMilli(), "START");
        try {
            journal.append(eStart);
            // Execute action
            action.run();
            Journal.Entry eCommit = new Journal.Entry(id, opType, params, Instant.now().toEpochMilli(), "COMMIT");
            journal.append(eCommit);
            // persist FS state after commit
            try {
                save();
            } catch (IOException ex) {
                throw new FSException("Falha ao persistir o sistema de arquivos: " + ex.getMessage());
            }
        } catch (FSException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new FSException("Erro ao executar operação: " + ex.getMessage());
        }
    }

    /* ---------- FS operations (public) ---------- */

    public void createDirectory(String path) throws FSException {
        Map<String, String> params = Map.of("path", path);
        journaledOperation("MKDIR", params, () -> {
            PathParts pp = PathParts.of(path);
            FSDirectory parent = traverseToDirectory(pp.parentPath, true);
            if (parent.hasDirectory(pp.baseName) || parent.hasFile(pp.baseName)) {
                throw new RuntimeException("Nome já existe: " + pp.baseName);
            }
            parent.addDirectory(new FSDirectory(pp.baseName));
            System.out.println("Diretório criado: " + path);
        });
    }

    public void removeDirectory(String path) throws FSException {
        Map<String, String> params = Map.of("path", path);
        journaledOperation("RMDIR", params, () -> {
            if ("/".equals(path)) throw new RuntimeException("Não é possível remover raiz.");
            PathParts pp = PathParts.of(path);
            FSDirectory parent = traverseToDirectory(pp.parentPath, true);
            FSDirectory dir = parent.getDirectory(pp.baseName);
            if (dir == null) throw new RuntimeException("Diretório não existe: " + path);
            if (!dir.isEmpty()) throw new RuntimeException("Diretório não vazio: " + path);
            parent.removeDirectory(pp.baseName);
            System.out.println("Diretório removido: " + path);
        });
    }

    public void createFile(String path) throws FSException {
        Map<String, String> params = Map.of("path", path);
        journaledOperation("TOUCH", params, () -> {
            PathParts pp = PathParts.of(path);
            FSDirectory parent = traverseToDirectory(pp.parentPath, true);
            if (parent.hasFile(pp.baseName) || parent.hasDirectory(pp.baseName))
                throw new RuntimeException("Nome já existe: " + pp.baseName);
            parent.addFile(new FSFile(pp.baseName, ""));
            System.out.println("Arquivo criado: " + path);
        });
    }

    public void deleteFile(String path) throws FSException {
        Map<String, String> params = Map.of("path", path);
        journaledOperation("RM", params, () -> {
            PathParts pp = PathParts.of(path);
            FSDirectory parent = traverseToDirectory(pp.parentPath, true);
            if (!parent.hasFile(pp.baseName)) throw new RuntimeException("Arquivo não existe: " + path);
            parent.removeFile(pp.baseName);
            System.out.println("Arquivo removido: " + path);
        });
    }

    public void writeFile(String path, String content) throws FSException {
        Map<String, String> params = Map.of("path", path, "content", content);
        journaledOperation("WRITE", params, () -> {
            PathParts pp = PathParts.of(path);
            FSDirectory parent = traverseToDirectory(pp.parentPath, true);
            FSFile file = parent.getFile(pp.baseName);
            if (file == null) {
                file = new FSFile(pp.baseName, content);
                parent.addFile(file);
            } else {
                file.setContent(content);
            }
            System.out.println("Conteúdo escrito em: " + path);
        });
    }

    public String readFile(String path) throws FSException {
        PathParts pp = PathParts.of(path);
        FSDirectory parent = traverseToDirectory(pp.parentPath, true);
        FSFile file = parent.getFile(pp.baseName);
        if (file == null) throw new FSException("Arquivo não encontrado: " + path);
        return file.getContent();
    }

    public void listDirectory(String path) throws FSException {
        FSDirectory dir = traverseToDirectory(path, true);
        System.out.println("Conteúdo de " + path + ":");
        dir.getDirectories().forEach(d -> System.out.println("[DIR]  " + d.getName()));
        dir.getFiles().forEach(f -> System.out.println("[FILE] " + f.getName() + " (" + f.getSize() + " bytes)"));
    }

    public void copy(String src, String dest) throws FSException {
        Map<String, String> params = Map.of("src", src, "dest", dest);
        journaledOperation("CP", params, () -> {
            FSNode srcNode = getNodeByPath(src);
            if (srcNode == null) throw new RuntimeException("Origem não encontrada: " + src);

            PathParts destParts = PathParts.of(dest);
            FSDirectory destParent = traverseToDirectory(destParts.parentPath, true);
            String destName = destParts.baseName;

            FSNode destNode = getNodeByPath(dest);
            if (destNode instanceof FSDirectory) {
                FSDirectory targetDir = (FSDirectory) destNode;
                String newName = srcNode.getName();
                if (targetDir.hasDirectory(newName) || targetDir.hasFile(newName)) {
                    throw new RuntimeException("Destino já contém um item com nome: " + newName);
                }
                if (srcNode instanceof FSDirectory) {
                    targetDir.addDirectory(((FSDirectory) srcNode).deepCopy());
                } else {
                    targetDir.addFile(((FSFile) srcNode).copy());
                }
            } else {
                if (destParent.hasDirectory(destName) || destParent.hasFile(destName)) {
                    throw new RuntimeException("Destino já existe: " + dest);
                }
                if (srcNode instanceof FSDirectory) {
                    FSDirectory copy = ((FSDirectory) srcNode).deepCopy();
                    copy.setName(destName);
                    destParent.addDirectory(copy);
                } else {
                    FSFile copy = ((FSFile) srcNode).copy();
                    copy.setName(destName);
                    destParent.addFile(copy);
                }
            }
            System.out.println("Cópia realizada: " + src + " -> " + dest);
        });
    }

    public void rename(String src, String dest) throws FSException {
        Map<String, String> params = Map.of("src", src, "dest", dest);
        journaledOperation("MV", params, () -> {
            if (src.equals("/")) throw new RuntimeException("Não é possível renomear raiz.");
            FSNode srcNode = getNodeByPath(src);
            if (srcNode == null) throw new RuntimeException("Origem não encontrada: " + src);

            PathParts destParts = PathParts.of(dest);
            FSDirectory destParent = traverseToDirectory(destParts.parentPath, true);
            String destName = destParts.baseName;

            if (destParent.hasDirectory(destName) || destParent.hasFile(destName)) {
                throw new RuntimeException("Nome de destino já existe: " + dest);
            }

            PathParts srcParts = PathParts.of(src);
            FSDirectory srcParent = traverseToDirectory(srcParts.parentPath, true);
            if (srcNode instanceof FSDirectory) {
                srcParent.removeDirectory(srcNode.getName());
                ((FSDirectory) srcNode).setName(destName);
                destParent.addDirectory((FSDirectory) srcNode);
            } else {
                srcParent.removeFile(srcNode.getName());
                ((FSFile) srcNode).setName(destName);
                destParent.addFile((FSFile) srcNode);
            }
            System.out.println("Movido/renomeado: " + src + " -> " + dest);
        });
    }

    /* ---------- Helpers ---------- */

    private FSDirectory traverseToDirectory(String path, boolean mustExist) {
        if (path == null || path.isEmpty()) path = "/";
        if (!path.startsWith("/")) path = "/" + path;
        if ("/".equals(path)) return root;
        String[] parts = path.substring(1).split("/");
        FSDirectory cur = root;
        for (String p : parts) {
            if (p.isEmpty()) continue;
            FSDirectory next = cur.getDirectory(p);
            if (next == null) {
                if (mustExist) throw new RuntimeException("Diretório não encontrado: " + path);
                next = new FSDirectory(p);
                cur.addDirectory(next);
            }
            cur = next;
        }
        return cur;
    }

    private FSNode getNodeByPath(String path) {
        if (path == null || path.isEmpty()) path = "/";
        if (!path.startsWith("/")) path = "/" + path;
        if ("/".equals(path)) return root;
        PathParts pp = PathParts.of(path);
        FSDirectory parent;
        try {
            parent = traverseToDirectory(pp.parentPath, true);
        } catch (RuntimeException e) {
            return null;
        }
        if (parent.hasFile(pp.baseName)) return parent.getFile(pp.baseName);
        if (parent.hasDirectory(pp.baseName)) return parent.getDirectory(pp.baseName);
        return null;
    }

    private void recoverFromJournal() {
        try {
            List<Journal.Entry> starts = journal.uncommittedStarts();
            if (starts.isEmpty()) {
                System.out.println("Journal limpo: sem operações pendentes.");
                return;
            }
            System.out.println("Recuperando " + starts.size() + " operação(ões) pendente(s) do journal...");
            for (Journal.Entry e : starts) {
                System.out.println("Reaplicando: id=" + e.id + " op=" + e.opType + " params=" + e.params);
                try {
                    switch (e.opType) {
                        case "MKDIR":
                            safeRun(() -> createDirectoryNoJournal(e.params.get("path")));
                            break;
                        case "RMDIR":
                            safeRun(() -> removeDirectoryNoJournal(e.params.get("path")));
                            break;
                        case "TOUCH":
                            safeRun(() -> createFileNoJournal(e.params.get("path")));
                            break;
                        case "RM":
                            safeRun(() -> deleteFileNoJournal(e.params.get("path")));
                            break;
                        case "WRITE":
                            safeRun(() -> writeFileNoJournal(e.params.get("path"), e.params.get("content")));
                            break;
                        case "CP":
                            safeRun(() -> copyNoJournal(e.params.get("src"), e.params.get("dest")));
                            break;
                        case "MV":
                            safeRun(() -> renameNoJournal(e.params.get("src"), e.params.get("dest")));
                            break;
                        default:
                            System.out.println("Operação de journal desconhecida: " + e.opType);
                    }
                    Journal.Entry commit = new Journal.Entry(e.id, e.opType, e.params, Instant.now().toEpochMilli(), "COMMIT");
                    journal.append(commit);
                } catch (Exception ex) {
                    System.out.println("Erro ao reaplicar operação id=" + e.id + ": " + ex.getMessage());
                }
            }
            try {
                save();
            } catch (IOException ex) {
                System.out.println("Falha ao persistir após recuperação: " + ex.getMessage());
            }
            System.out.println("Recuperação concluída.");
        } catch (IOException e) {
            System.out.println("Erro ao ler journal para recuperação: " + e.getMessage());
        }
    }

    private void safeRun(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            // ignore during recovery — best effort
        }
    }

    /* ---------- Non-journal versions used during recovery ---------- */
    private void createDirectoryNoJournal(String path) {
        PathParts pp = PathParts.of(path);
        FSDirectory parent = traverseToDirectory(pp.parentPath, false);
        if (parent.hasDirectory(pp.baseName) || parent.hasFile(pp.baseName)) return;
        parent.addDirectory(new FSDirectory(pp.baseName));
    }

    private void removeDirectoryNoJournal(String path) {
        if ("/".equals(path)) return;
        PathParts pp = PathParts.of(path);
        FSDirectory parent = traverseToDirectory(pp.parentPath, true);
        FSDirectory dir = parent.getDirectory(pp.baseName);
        if (dir == null) return;
        if (!dir.isEmpty()) return;
        parent.removeDirectory(pp.baseName);
    }

    private void createFileNoJournal(String path) {
        PathParts pp = PathParts.of(path);
        FSDirectory parent = traverseToDirectory(pp.parentPath, false);
        if (parent.hasFile(pp.baseName) || parent.hasDirectory(pp.baseName)) return;
        parent.addFile(new FSFile(pp.baseName, ""));
    }

    private void deleteFileNoJournal(String path) {
        PathParts pp = PathParts.of(path);
        FSDirectory parent = traverseToDirectory(pp.parentPath, true);
        parent.removeFile(pp.baseName);
    }

    private void writeFileNoJournal(String path, String content) {
        PathParts pp = PathParts.of(path);
        FSDirectory parent = traverseToDirectory(pp.parentPath, false);
        FSFile file = parent.getFile(pp.baseName);
        if (file == null) {
            parent.addFile(new FSFile(pp.baseName, content));
        } else {
            file.setContent(content);
        }
    }

    private void copyNoJournal(String src, String dest) {
        FSNode srcNode = getNodeByPath(src);
        if (srcNode == null) return;
        PathParts destParts = PathParts.of(dest);
        FSDirectory destParent = traverseToDirectory(destParts.parentPath, false);
        String destName = destParts.baseName;
        FSNode destNode = getNodeByPath(dest);
        if (destNode instanceof FSDirectory) {
            FSDirectory targetDir = (FSDirectory) destNode;
            String newName = srcNode.getName();
            if (targetDir.hasDirectory(newName) || targetDir.hasFile(newName)) return;
            if (srcNode instanceof FSDirectory) targetDir.addDirectory(((FSDirectory) srcNode).deepCopy());
            else targetDir.addFile(((FSFile) srcNode).copy());
        } else {
            if (destParent.hasDirectory(destName) || destParent.hasFile(destName)) return;
            if (srcNode instanceof FSDirectory) {
                FSDirectory copy = ((FSDirectory) srcNode).deepCopy();
                copy.setName(destName);
                destParent.addDirectory(copy);
            } else {
                FSFile copy = ((FSFile) srcNode).copy();
                copy.setName(destName);
                destParent.addFile(copy);
            }
        }
    }

    private void renameNoJournal(String src, String dest) {
        if (src.equals("/")) return;
        FSNode srcNode = getNodeByPath(src);
        if (srcNode == null) return;
        PathParts destParts = PathParts.of(dest);
        FSDirectory destParent = traverseToDirectory(destParts.parentPath, false);
        String destName = destParts.baseName;
        if (destParent.hasDirectory(destName) || destParent.hasFile(destName)) return;
        PathParts srcParts = PathParts.of(src);
        FSDirectory srcParent = traverseToDirectory(srcParts.parentPath, true);
        if (srcNode instanceof FSDirectory) {
            srcParent.removeDirectory(srcNode.getName());
            ((FSDirectory) srcNode).setName(destName);
            destParent.addDirectory((FSDirectory) srcNode);
        } else {
            srcParent.removeFile(srcNode.getName());
            ((FSFile) srcNode).setName(destName);
            destParent.addFile((FSFile) srcNode);
        }
    }
}
