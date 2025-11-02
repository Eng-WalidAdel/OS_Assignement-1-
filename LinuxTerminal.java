import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

class Parser {
    String commandName;
    String[] args;

    public boolean parse(String input) {
        if (input == null || input.trim().isEmpty()) {
            return false;
        }

        String[] parts = input.trim().split("\\s+");
        if (parts.length == 0) {
            return false;
        }

        commandName = parts[0];
        if (parts.length > 1) {
            args = new String[parts.length - 1];
            System.arraycopy(parts, 1, args, 0, parts.length - 1);
        } else {
            args = new String[0];
        }

        return true;
    }

    public String getCommandName() {
        return commandName;
    }

    public String getArgs() {
        if (args == null || args.length == 0) {
            return "";
        }
        return String.join(" ", args);
    }
}

public class LinuxTerminal {
    
    private static String currentDir = System.getProperty("user.dir");
    Parser parser;

    public LinuxTerminal() {
        this.parser = new Parser();
    }

    public static void main(String[] args) {
        LinuxTerminal terminal = new LinuxTerminal();
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        while (true) {
            try {
                System.out.print(currentDir + " $ ");
                String commandLine = reader.readLine();

                if (commandLine == null) {
                    break;
                }

                commandLine = commandLine.trim();
                if (commandLine.isEmpty()) {
                    continue;
                }

                if (commandLine.equalsIgnoreCase("exit")) {
                    break;
                }

                terminal.processCommand(commandLine);
            } catch (IOException e) {
                e.printStackTrace(System.err);
                break;
            }
        }
    }

    public void processCommand(String input) {
        // Handle redirection first
        if (input.contains(">>")) {
            handleRedirection(input, true);
            return;
        } else if (input.contains(">")) {
            handleRedirection(input, false);
            return;
        }

        if (parser.parse(input)) {
            chooseCommandAction();
        } else {
            System.out.println("Invalid command");
        }
    }

    public void chooseCommandAction() {
        String command = parser.getCommandName();
        String argsString = parser.getArgs();
        String[] args = argsString.isEmpty() ? new String[0] : argsString.split("\\s+");

        switch (command.toLowerCase()) {
            case "pwd" -> System.out.println(pwd());
            case "cd" -> cd(args);
            case "ls" -> ls(args);
            case "mkdir" -> mkdir(args);
            case "rmdir" -> rmdir(args);
            case "touch" -> touch(args);
            case "cp" -> cp(args);
            case "rm" -> rm(args);
            case "cat" -> cat(args);
            case "wc" -> wc(args);
            case "zip" -> zip(args);
            case "unzip" -> unzip(args);
            default -> System.out.println("Command not found: " + command);
        }
    }

    public void touch(String[] args) {
        if (args.length != 1) {
            System.out.println("touch: missing file operand");
            return;
        }

        String fileName = args[0];
        Path path;

        if (fileName.startsWith("~")) {
            fileName = System.getProperty("user.home") + fileName.substring(1);
        }

        if (Paths.get(fileName).isAbsolute()) {
            path = Paths.get(fileName);
        } else {
            path = Paths.get(currentDir, fileName);
        }

        try {
            if (!Files.exists(path)) {
                Files.createFile(path);
            } else {
                // Update timestamp
                Files.setLastModifiedTime(path, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()));
            }
        } catch (IOException e) {
            System.out.println("touch: cannot touch '" + fileName + "': " + e.getMessage());
        }
    }

    public void cp(String[] args) {
        if (args.length == 3 && "-r".equals(args[0])) {
            // cp -r source destination
            copyDirectory(args[1], args[2]);
        } else if (args.length == 2) {
            // cp source destination
            copyFile(args[0], args[1]);
        } else {
            System.out.println("cp: invalid arguments");
        }
    }

    private void copyFile(String source, String destination) {
        Path sourcePath = resolvePath(source);
        Path destPath = resolvePath(destination);

        if (!Files.exists(sourcePath)) {
            System.out.println("cp: cannot stat '" + source + "': No such file or directory");
            return;
        }

        if (Files.isDirectory(sourcePath)) {
            System.out.println("cp: -r not specified; omitting directory '" + source + "'");
            return;
        }

        try {
            Files.copy(sourcePath, destPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.out.println("cp: cannot copy '" + source + "' to '" + destination + "': " + e.getMessage());
        }
    }

    private void copyDirectory(String source, String destination) {
        Path sourcePath = resolvePath(source);
        Path destPath = resolvePath(destination);

        if (!Files.exists(sourcePath)) {
            System.out.println("cp: cannot stat '" + source + "': No such file or directory");
            return;
        }

        if (!Files.isDirectory(sourcePath)) {
            System.out.println("cp: '" + source + "' is not a directory");
            return;
        }

        try {
            Files.walkFileTree(sourcePath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    Path targetPath = destPath.resolve(sourcePath.relativize(dir));
                    if (!Files.exists(targetPath)) {
                        Files.createDirectories(targetPath);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.copy(file, destPath.resolve(sourcePath.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            System.out.println("cp: cannot copy directory '" + source + "': " + e.getMessage());
        }
    }

    public void rm(String[] args) {
        if (args.length != 1) {
            System.out.println("rm: missing operand");
            return;
        }

        String fileName = args[0];
        Path path = resolvePath(fileName);

        if (!Files.exists(path)) {
            System.out.println("rm: cannot remove '" + fileName + "': No such file or directory");
            return;
        }

        if (Files.isDirectory(path)) {
            System.out.println("rm: cannot remove '" + fileName + "': Is a directory");
            return;
        }

        try {
            Files.delete(path);
        } catch (IOException e) {
            System.out.println("rm: cannot remove '" + fileName + "': " + e.getMessage());
        }
    }

    public void wc(String[] args) {
        if (args.length != 1) {
            System.out.println("wc: missing operand");
            return;
        }

        String fileName = args[0];
        Path path = resolvePath(fileName);

        if (!Files.exists(path)) {
            System.out.println("wc: " + fileName + ": No such file or directory");
            return;
        }

        if (Files.isDirectory(path)) {
            System.out.println("wc: " + fileName + ": Is a directory");
            return;
        }

        try {
            List<String> lines = Files.readAllLines(path);
            long lineCount = lines.size();
            long wordCount = 0;
            long charCount = 0;

            for (String line : lines) {
                charCount += line.length() + 1; // +1 for newline
                wordCount += line.trim().isEmpty() ? 0 : line.trim().split("\\s+").length;
            }

            System.out.printf("%d %d %d %s%n", lineCount, wordCount, charCount, fileName);
        } catch (IOException e) {
            System.out.println("wc: " + fileName + ": " + e.getMessage());
        }
    }

    public void zip(String[] args) {
        if (args.length < 2) {
            System.out.println("zip: missing arguments");
            return;
        }

        String zipFileName = args[0];
        boolean recursive = false;
        List<String> filesToZip = new ArrayList<>();

        int startIndex = 1;
        if ("-r".equals(args[1])) {
            recursive = true;
            startIndex = 2;
            if (args.length < 3) {
                System.out.println("zip: missing arguments");
                return;
            }
        }

        for (int i = startIndex; i < args.length; i++) {
            filesToZip.add(args[i]);
        }

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(resolvePath(zipFileName).toFile()))) {
            for (String fileToZip : filesToZip) {
                Path path = resolvePath(fileToZip);
                if (Files.isDirectory(path) && recursive) {
                    zipDirectory(path, path.getFileName().toString(), zos);
                } else if (Files.isRegularFile(path)) {
                    zipFile(path, path.getFileName().toString(), zos);
                } else {
                    System.out.println("zip: " + fileToZip + ": No such file or directory");
                }
            }
        } catch (IOException e) {
            System.out.println("zip: " + e.getMessage());
        }
    }

    private void zipFile(Path file, String fileName, ZipOutputStream zos) throws IOException {
        ZipEntry zipEntry = new ZipEntry(fileName);
        zos.putNextEntry(zipEntry);
        Files.copy(file, zos);
        zos.closeEntry();
    }

    private void zipDirectory(Path folder, String parentFolder, ZipOutputStream zos) throws IOException {
        Files.walk(folder).forEach(path -> {
            try {
                String zipEntryName = parentFolder + "/" + folder.relativize(path).toString();
                if (Files.isDirectory(path)) {
                    if (!zipEntryName.endsWith("/")) {
                        zipEntryName += "/";
                    }
                    ZipEntry zipEntry = new ZipEntry(zipEntryName);
                    zos.putNextEntry(zipEntry);
                    zos.closeEntry();
                } else {
                    ZipEntry zipEntry = new ZipEntry(zipEntryName);
                    zos.putNextEntry(zipEntry);
                    Files.copy(path, zos);
                    zos.closeEntry();
                }
            } catch (IOException e) {
                System.out.println("zip: " + e.getMessage());
            }
        });
    }

    public void unzip(String[] args) {
        if (args.length == 0) {
            System.out.println("unzip: missing archive name");
            return;
        }

        String zipFileName = args[0];
        String extractDir = currentDir;

        // Check for -d option
        if (args.length >= 3 && "-d".equals(args[1])) {
            extractDir = args[2];
        }

        Path zipPath = resolvePath(zipFileName);
        Path extractPath = resolvePath(extractDir);

        if (!Files.exists(zipPath)) {
            System.out.println("unzip: cannot find " + zipFileName);
            return;
        }

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipPath.toFile()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path entryPath = extractPath.resolve(entry.getName());

                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    Files.copy(zis, entryPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            System.out.println("unzip: " + e.getMessage());
        }
    }

    private void handleRedirection(String input, boolean append) {
        String operator = append ? ">>" : ">";
        int operatorIndex = input.indexOf(operator);
        
        String command = input.substring(0, operatorIndex).trim();
        String fileName = input.substring(operatorIndex + operator.length()).trim();
        
        if (fileName.isEmpty()) {
            System.out.println("No output file specified");
            return;
        }

        Path outputPath = resolvePath(fileName);
        
        try {
            // Capture command output
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PrintStream originalOut = System.out;
            System.setOut(new PrintStream(baos));
            
            // Execute command
            if (parser.parse(command)) {
                chooseCommandAction();
            }
            
            System.setOut(originalOut);
            
            // Write to file
            if (append && Files.exists(outputPath)) {
                Files.write(outputPath, baos.toByteArray(), StandardOpenOption.APPEND);
            } else {
                Files.write(outputPath, baos.toByteArray(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
            
        } catch (IOException e) {
            System.out.println("Error with redirection: " + e.getMessage());
        }
    }

    private Path resolvePath(String pathStr) {
        if (pathStr.startsWith("~")) {
            pathStr = System.getProperty("user.home") + pathStr.substring(1);
        }

        Path path = Paths.get(pathStr);
        if (!path.isAbsolute()) {
            path = Paths.get(currentDir).resolve(pathStr);
        }

        return path.normalize();
    }

    public String pwd() {
        return currentDir;
    }

    public void cd(String[] args) {
        if (args.length == 0) {
            currentDir = System.getProperty("user.home");
            return;
        }

        String path = args[0];
        if ("..".equals(path)) {
            File parent = new File(currentDir).getParentFile();
            if (parent != null) {
                currentDir = parent.getAbsolutePath();
            }
            return;
        }

        if (path.startsWith("~")) {
            path = System.getProperty("user.home") + path.substring(1);
        }

        File newDir = new File(path);
        if (!newDir.isAbsolute()) {
            newDir = new File(currentDir, path);
        }

        if (newDir.exists() && newDir.isDirectory()) {
            try {
                currentDir = newDir.getCanonicalPath();
            } catch (IOException e) {
                System.out.println("cd: error resolving path: " + e.getMessage());
            }
        } else {
            System.out.println("cd: no such file or directory: " + path);
        }
    }

    public void ls(String[] args) {
        if (args.length > 0) {
            System.out.println("ls: arguments not supported in this implementation");
            return;
        }

        try {
            File currentDirectory = new File(currentDir);
            File[] files = currentDirectory.listFiles();
            
            if (files == null) {
                System.out.println("ls: cannot access directory");
                return;
            }

            Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            
            for (File file : files) {
                System.out.println(file.getName());
            }
        } catch (Exception e) {
            System.out.println("ls: " + e.getMessage());
        }
    }

    public void mkdir(String[] args) {
        if (args.length == 0) {
            System.out.println("mkdir: missing operand");
            return;
        }

        for (String dirName : args) {
            Path path = resolvePath(dirName);
            
            try {
                if (Files.exists(path)) {
                    System.out.println("mkdir: cannot create directory '" + dirName + "': File exists");
                } else {
                    Files.createDirectories(path);
                }
            } catch (IOException e) {
                System.out.println("mkdir: cannot create directory '" + dirName + "': " + e.getMessage());
            }
        }
    }

    public void rmdir(String[] args) {
        if (args.length == 0) {
            System.out.println("rmdir: missing operand");
            return;
        }

        if (args.length == 1 && "*".equals(args[0])) {
            // Remove all empty directories in current directory
            try {
                File currentDirectory = new File(currentDir);
                File[] files = currentDirectory.listFiles();
                
                if (files != null) {
                    for (File file : files) {
                        if (file.isDirectory()) {
                            String[] contents = file.list();
                            if (contents != null && contents.length == 0) {
                                if (!file.delete()) {
                                    System.out.println("rmdir: failed to remove '" + file.getName() + "'");
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("rmdir: " + e.getMessage());
            }
        } else {
            // Remove specific directory
            String dirName = args[0];
            Path path = resolvePath(dirName);
            
            if (!Files.exists(path)) {
                System.out.println("rmdir: failed to remove '" + dirName + "': No such file or directory");
                return;
            }

            if (!Files.isDirectory(path)) {
                System.out.println("rmdir: failed to remove '" + dirName + "': Not a directory");
                return;
            }

            try {
                if (Files.list(path).findAny().isPresent()) {
                    System.out.println("rmdir: failed to remove '" + dirName + "': Directory not empty");
                } else {
                    Files.delete(path);
                }
            } catch (IOException e) {
                System.out.println("rmdir: failed to remove '" + dirName + "': " + e.getMessage());
            }
        }
    }

    public void cat(String[] args) {
        if (args.length == 0) {
            System.out.println("cat: missing operand");
            return;
        }

        switch (args.length) {
            case 1 -> {
                // Print content of one file
                String fileName = args[0];
                Path path = resolvePath(fileName);
                if (!Files.exists(path)) {
                    System.out.println("cat: " + fileName + ": No such file or directory");
                    return;
                }   if (Files.isDirectory(path)) {
                    System.out.println("cat: " + fileName + ": Is a directory");
                    return;
                }   try {
                    List<String> lines = Files.readAllLines(path);
                    for (String line : lines) {
                        System.out.println(line);
                    }
                } catch (IOException e) {
                    System.out.println("cat: " + fileName + ": " + e.getMessage());
                }
            }
            case 2 -> {
                // Concatenate and print content of two files
                String fileName1 = args[0];
                String fileName2 = args[1];
                Path path1 = resolvePath(fileName1);
                Path path2 = resolvePath(fileName2);
                // Check first file
                if (!Files.exists(path1)) {
                    System.out.println("cat: " + fileName1 + ": No such file or directory");
                    return;
                }   if (Files.isDirectory(path1)) {
                    System.out.println("cat: " + fileName1 + ": Is a directory");
                    return;
                }   // Check second file
                if (!Files.exists(path2)) {
                    System.out.println("cat: " + fileName2 + ": No such file or directory");
                    return;
                }   if (Files.isDirectory(path2)) {
                    System.out.println("cat: " + fileName2 + ": Is a directory");
                    return;
                }   try {
                    // Print first file
                    List<String> lines1 = Files.readAllLines(path1);
                    for (String line : lines1) {
                        System.out.println(line);
                    }
                    
                    // Print second file
                    List<String> lines2 = Files.readAllLines(path2);
                    for (String line : lines2) {
                        System.out.println(line);
                    }
                } catch (IOException e) {
                    System.out.println("cat: " + e.getMessage());
                }
            }
            default -> System.out.println("cat: too many arguments");
        }
    }
}