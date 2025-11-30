import java.io.*;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;


/**
 * Main.java
 * Simulador de Sistema de Arquivos com Journaling (simples, educativo)
 *
 * Uso:
 *   - Compilar: javac Main.java
 *   - Executar: java Main
 *
 * Comandos no shell:
 *   mkdir <path>            - cria diretório
 *   rmdir <path>            - remove diretório (deve estar vazio)
 *   touch <path>            - cria arquivo vazio
 *   rm <path>               - remove arquivo
 *   cp <src> <dest>         - copia arquivo (ou diretório recursivo)
 *   mv <src> <dest>         - renomeia/move arquivo ou diretório
 *   write <path> <content>  - cria/escreve (substitui) conteúdo do arquivo
 *   cat <path>              - exibe conteúdo do arquivo
 *   ls <path>               - lista diretório (padrão "/")
 *   journal show            - mostra journal
 *   journal clear           - limpa journal (cuidado)
 *   persist                 - força salvar estado FS em disco
 *   exit                    - sai do shell
 */
public class Main {

    public static void main(String[] args) throws Exception {
        FileSystemSimulator fs = FileSystemSimulator.loadOrCreate("fs.data", "journal.log");
        System.out.println("Simulador de Sistema de Arquivos com Journaling");
        System.out.println("Digite 'help' para ver comandos.");
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("fs> ");
                if (!scanner.hasNextLine()) break;
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) continue;
                String[] parts = splitArgs(line);
                String cmd = parts[0].toLowerCase(Locale.ROOT);

                try {
                    switch (cmd) {
                        case "help":
                            printHelp();
                            break;
                        case "mkdir":
                            requireArgs(parts, 2);
                            fs.createDirectory(normalizePath(parts[1]));
                            break;
                        case "rmdir":
                            requireArgs(parts, 2);
                            fs.removeDirectory(normalizePath(parts[1]));
                            break;
                        case "touch":
                            requireArgs(parts, 2);
                            fs.createFile(normalizePath(parts[1]));
                            break;
                        case "rm":
                            requireArgs(parts, 2);
                            fs.deleteFile(normalizePath(parts[1]));
                            break;
                        case "cp":
                            requireArgs(parts, 3);
                            fs.copy(normalizePath(parts[1]), normalizePath(parts[2]));
                            break;
                        case "mv":
                            requireArgs(parts, 3);
                            fs.rename(normalizePath(parts[1]), normalizePath(parts[2]));
                            break;
                        case "write":
                            // Accept cases where splitArgs failed to keep the 3rd token
                            if (parts.length < 2) throw new FSException("Argumentos insuficientes.");
                            String pathArg = parts[1];
                            // Extract content from original line after the path occurrence
                            String content = "";
                            int idx = line.indexOf(pathArg);
                            if (idx >= 0) {
                                int start = idx + pathArg.length();
                                if (start < line.length()) {
                                    content = line.substring(start).trim();
                                    // remove surrounding quotes if present
                                    if (content.length() >= 2 && content.startsWith("\"") && content.endsWith("\"")) {
                                        content = content.substring(1, content.length() - 1);
                                    }
                                }
                            }
                            if (content.isEmpty()) throw new FSException("Argumentos insuficientes.");
                            fs.writeFile(normalizePath(pathArg), content);
                            break;
                        case "cat":
                            requireArgs(parts, 2);
                            System.out.println(fs.readFile(normalizePath(parts[1])));
                            break;
                        case "ls":
                            String p = parts.length >= 2 ? normalizePath(parts[1]) : "/";
                            fs.listDirectory(p);
                            break;
                        case "journal":
                            requireArgs(parts, 2);
                            if (parts[1].equalsIgnoreCase("show")) fs.journal.showJournal();
                            else if (parts[1].equalsIgnoreCase("clear")) {
                                System.out.println("Limpando journal (atenção!)...");
                                fs.journal.clear();
                            } else System.out.println("Subcomando journal desconhecido.");
                            break;
                        case "persist":
                            fs.save();
                            System.out.println("Estado persistido.");
                            break;
                        case "exit":
                            fs.shutdown();
                            System.out.println("Saindo...");
                            return;
                        default:
                            System.out.println("Comando desconhecido. Digite 'help'.");
                    }
                } catch (FSException e) {
                    System.out.println("Erro: " + e.getMessage());
                } catch (Exception e) {
                    System.out.println("Erro inesperado: " + e.getMessage());
                    e.printStackTrace(System.out);
                }
            }
        }
    }

    private static void printHelp() {
        System.out.println("Comandos disponíveis:");
        System.out.println("  mkdir <path>");
        System.out.println("  rmdir <path>");
        System.out.println("  touch <path>");
        System.out.println("  rm <path>");
        System.out.println("  cp <src> <dest>");
        System.out.println("  mv <src> <dest>");
        System.out.println("  write <path> <content>");
        System.out.println("  cat <path>");
        System.out.println("  ls <path>");
        System.out.println("  journal show|clear");
        System.out.println("  persist");
        System.out.println("  exit");
    }

    private static void requireArgs(String[] parts, int n) throws FSException {
        if (parts.length < n) throw new FSException("Argumentos insuficientes.");
    }

    // Normalize paths: ensure absolute starting with "/"
    private static String normalizePath(String p) {
        if (p == null || p.isEmpty()) return "/";
        if (!p.startsWith("/")) p = "/" + p;
        if (p.length() > 1 && p.endsWith("/")) p = p.substring(0, p.length() - 1);
        return p;
    }

    // Very simple argument splitter: splits into up to 3 parts (command, arg1, arg2-rest)
    private static String[] splitArgs(String line) {
        // Allow quotes for the third argument
        List<String> out = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < line.length(); ++i) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }
            if (!inQuotes && Character.isWhitespace(c)) {
                if (cur.length() > 0) {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
            } else {
                cur.append(c);
            }
            // optimization: keep only first 3 tokens merged
            if (i == line.length() - 1 && cur.length() > 0) out.add(cur.toString());
        }
        if (out.isEmpty()) return new String[]{line};
        // If more than 3 tokens, merge tokens from 3..end into token3 (to allow spaces in content)
        if (out.size() > 3) {
            StringBuilder b = new StringBuilder(out.get(2));
            for (int i = 3; i < out.size(); ++i) {
                b.append(" ").append(out.get(i));
            }
            List<String> small = new ArrayList<>();
            small.add(out.get(0));
            small.add(out.size() >= 2 ? out.get(1) : "");
            small.add(b.toString());
            return small.toArray(new String[0]);
        } else {
            return out.toArray(new String[0]);
        }
    }
}

