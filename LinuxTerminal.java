// ...existing code...
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class LinuxTerminal {
    private static String currentDir = System.getProperty("user.dir");
    private static String previousDir = currentDir;

    public static void main(String[] args) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        while (true) {
            try {
                System.out.print(currentDir + " $ "); // prompt like a terminal
                String commandLine = reader.readLine();

                if (commandLine == null) {
                    break;
                }

                commandLine = commandLine.trim();
                if (commandLine.isEmpty()) {
                    continue; // ignore empty input
                }

                if (commandLine.equalsIgnoreCase("exit")) {
                    break;
                }

                try {
                    executeCommand(commandLine);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    System.err.println("Command interrupted");
                    break;
                }
            } catch (IOException e) {
                e.printStackTrace();
                break;
            }
        }
    }

    private static void executeCommand(String commandLine) throws IOException, InterruptedException {
        // Handle `cd` manually (supports spaces, "~", and "cd -")
        if (commandLine.startsWith("cd")) {
            String arg = commandLine.length() > 2 ? commandLine.substring(2).trim() : "";

            if (arg.isEmpty()) {
                previousDir = currentDir;
                currentDir = System.getProperty("user.home");
                return;
            }

            if (arg.equals("-")) {
                // swap with previousDir
                String temp = currentDir;
                currentDir = previousDir;
                previousDir = temp;
                return;
            }

            if (arg.startsWith("~")) {
                String home = System.getProperty("user.home");
                arg = home + arg.substring(1);
            }

            java.io.File newDir = new java.io.File(currentDir, arg);
            if (!newDir.isAbsolute()) {
                newDir = newDir.getCanonicalFile();
            }

            if (newDir.exists() && newDir.isDirectory()) {
                previousDir = currentDir;
                currentDir = newDir.getCanonicalPath();
            } else {
                System.out.println("cd: no such file or directory: " + arg);
            }
            return;
        }

        // Handle `mkdir` as a built-in (supports -p and multiple paths)
        String[] parts = commandLine.trim().split("\\s+");
        if (parts.length > 0 && parts[0].equals("mkdir")) {
            boolean parents = false;
            List<String> paths = new ArrayList<>();
            for (int i = 1; i < parts.length; i++) {
                if ("-p".equals(parts[i])) {
                    parents = true;
                } else {
                    paths.add(parts[i]);
                }
            }

            if (paths.isEmpty()) {
                System.out.println("mkdir: missing operand");
                return;
            }

            for (String p : paths) {
                if (p.startsWith("~")) {
                    p = System.getProperty("user.home") + p.substring(1);
                }

                java.io.File dir = new java.io.File(p);
                if (!dir.isAbsolute()) {
                    dir = new java.io.File(currentDir, p);
                }

                boolean ok;
                if (parents) {
                    ok = dir.mkdirs();
                } else {
                    ok = dir.mkdir();
                }

                if (!ok) {
                    if (dir.exists() && dir.isDirectory()) {
                        System.out.println("mkdir: cannot create directory '" + p + "': File exists");
                    } else {
                        System.out.println("mkdir: failed to create '" + p + "'");
                    }
                }
            }
            return;
        }

        // Handle `cat` as a built-in (reads one or more files; '-' reads stdin)
        if (parts.length > 0 && parts[0].equals("cat")) {
            if (parts.length == 1) {
                // No args: read from stdin and echo to stdout until EOF
                try {
                    copyStream(System.in);
                } catch (IOException e) {
                    System.out.println("cat: error reading stdin: " + e.getMessage());
                }
                return;
            }

            boolean readStdin = false;
            for (int i = 1; i < parts.length; i++) {
                String arg = parts[i];
                if ("-".equals(arg)) {
                    // Read from stdin once
                    try {
                        copyStream(System.in);
                    } catch (IOException e) {
                        System.out.println("cat: error reading stdin: " + e.getMessage());
                    }
                    // continue to next file (note: stdin may be exhausted)
                    readStdin = true;
                    continue;
                }

                if (arg.startsWith("~")) {
                    arg = System.getProperty("user.home") + arg.substring(1);
                }

                Path path = Paths.get(arg);
                if (!path.isAbsolute()) {
                    path = Paths.get(currentDir).resolve(arg).normalize();
                }

                if (!Files.exists(path)) {
                    System.out.println("cat: " + parts[i] + ": No such file or directory");
                    continue;
                }
                if (Files.isDirectory(path)) {
                    System.out.println("cat: " + parts[i] + ": Is a directory");
                    continue;
                }

                try (InputStream in = Files.newInputStream(path)) {
                    Files.copy(path, System.out);
                } catch (IOException e) {
                    System.out.println("cat: " + parts[i] + ": " + e.getMessage());
                }
            }

            // If user requested only "-" (read stdin) and there were no other files,
            // we've already read from stdin above.
            return;
        }

        // Build and run the process for other commands
        List<String> command = new ArrayList<>();
        String os = System.getProperty("os.name").toLowerCase();
        boolean isWindows = os.contains("win");

        if (isWindows) {
            command.add("cmd.exe");
            command.add("/c");
            command.add(commandLine);
        } else {
            command.add("bash");
            command.add("-c");
            command.add(commandLine);
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(new java.io.File(currentDir)); // set working directory
        pb.redirectErrorStream(true); // merge stderr into stdout to avoid deadlocks
        Process process = pb.start();

        try (BufferedReader stdOut = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = stdOut.readLine()) != null) {
                System.out.println(line);
            }
        }
        
    }

    private static void copyStream(InputStream in) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            System.out.write(buffer, 0, read);
        }
        System.out.flush();
    }
}