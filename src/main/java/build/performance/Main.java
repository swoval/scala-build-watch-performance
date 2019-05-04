package build.performance;

import com.swoval.files.FileTreeViews.Observer;
import com.swoval.files.PathWatcher;
import com.swoval.files.PathWatchers;
import com.swoval.files.PathWatchers.Event;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main {
  private static final int defaultIterations = 1;
  private static final Set<String> allProjects;
  private static final Path defaultRelativeSourceDirectory =
      Paths.get("src").resolve("main").resolve("scala").resolve("sbt").resolve("benchmark");

  static {
    allProjects = new HashSet<>();
    allProjects.add("sbt-0.13.17");
    allProjects.add("sbt-1.3.0");
    allProjects.add("mill");
  }

  //  private static Process startProject(final String project) {
  //    switch (project) {
  //      case "sbt-0":
  //      case "sbt-1":
  //      case "mill":
  //    }
  //  }

  public static void main(final String[] args) throws IOException {
    var prev = '\0';
    var iterations = defaultIterations;
    var baseDirectory = Optional.<Path>empty();
    var sourceDirectory = Optional.<Path>empty();
    var projects = new ArrayList<String>();
    try (final var tempDir = new TempDirectory()) {
      for (final String arg : args) {
        switch (prev) {
          case 'b':
            baseDirectory = Optional.of(Paths.get(arg));
            prev = '\0';
            break;
          case 's':
            sourceDirectory = Optional.of(Paths.get(arg));
            prev = '\0';
            break;
          case 'i':
            iterations = Integer.valueOf(arg);
            prev = '\0';
            break;
          default:
            if (arg.equals("-b") || arg.equals("--base-directory")) {
              prev = 'b';
            } else if (arg.equals("-s") || arg.equals("--source-directory")) {
              prev = 's';
            } else if (arg.equals("-i") || arg.equals("--iterations")) {
              prev = 'i';
            } else if (allProjects.contains(arg)) {
              projects.add(arg);
            } else {
              final String[] parts = arg.split("=");
              if (parts.length == 2) {
                if (parts[0].equals("-s") || parts[0].equals("--base-directory")) {
                  sourceDirectory = Optional.of(Paths.get(parts[1]));
                } else if (parts[0].equals("-b") || parts[0].equals("--base-directory")) {
                  baseDirectory = Optional.of(Paths.get(parts[1]));
                }
              }
              prev = '\0';
            }
            break;
        }
      }
      for (final var projectName : projects) {
        final Path base = tempDir.get().resolve(projectName);
        System.out.println(base);
        setupProject(projectName, tempDir.get());
        final var process = runSbt(tempDir.get().resolve("sbt-1.3.0"), "~test");
        final var project =
            new Project(base, base.resolve(defaultRelativeSourceDirectory), process);
        try {
          run(project, iterations);
          int count = 5000;
          genSources(project, count);
          System.out.println("generated " + count + " sources");
          run(project, iterations);
        } finally {
          project.close();
        }
      }
    }
  }

  private static void run(final Project project, final int iterations) {
    try (final PathWatcher<PathWatchers.Event> watcher = PathWatchers.get(true)) {
      final var watchFile = project.getBaseDirectory().resolve("watch.out");
      watcher.register(watchFile, -1);
      final LinkedBlockingQueue<Integer> queue = new LinkedBlockingQueue<>();
      watcher.addObserver(
          new Observer<>() {
            @Override
            public void onError(final Throwable t) {}

            @Override
            public void onNext(final Event event) {
              System.out.println("Received event " + event);
              queue.offer(1);
            }
          });
      long totalElapsed = 0;
      final Path sourceFile = project.getSourceDirectory().resolve("AkkaMain.scala");
      final byte[] touchContents = Files.readAllBytes(sourceFile);
      final StringBuilder commentLines = new StringBuilder();
      commentLines.append(new String(touchContents));
      queue.clear();
      queue.poll(2, TimeUnit.MINUTES);
      for (int i = -5; i < iterations; ++i) {
        commentLines.append("//\n");
        queue.clear();
        Files.write(sourceFile, commentLines.toString().getBytes());
        queue.poll(10, TimeUnit.SECONDS);
        System.out.println(watchFile);
        long watchFileLastModified = Files.getLastModifiedTime(watchFile).toMillis();
        long touchLastModified = Files.getLastModifiedTime(sourceFile).toMillis();
        long elapsed = watchFileLastModified - touchLastModified;
        // Discard the first run that includes build tool startup
        if (i >= 0) totalElapsed += elapsed;
        System.out.println("Took " + elapsed + " ms to run task");
      }
      long average = totalElapsed / iterations;
      System.out.println("Ran " + iterations + " tests. Average latency was " + average + " ms.");
    } catch (final IOException | InterruptedException e) {
      e.printStackTrace();
    }
  }

  private static class ForkProcess implements AutoCloseable {
    final Process process;
    final Thread thread;
    final AtomicBoolean isShutdown = new AtomicBoolean(false);

    ForkProcess(final Process process) {
      this.process = process;
      thread =
          new Thread("process-io-thread") {
            @Override
            public void run() {
              final InputStream is = process.getInputStream();
              final InputStream es = process.getErrorStream();
              try {
                while (!isShutdown.get()) {
                  {
                    final StringBuilder builder = new StringBuilder();
                    while (is.available() > 0) {
                      builder.append((char) is.read());
                    }
                    if (builder.length() > 0) {
                      System.out.print(builder.toString());
                    }
                  }
                  {
                    final StringBuilder builder = new StringBuilder();
                    while (es.available() > 0) {
                      builder.append((char) es.read());
                    }
                    if (builder.length() > 0) {
                      System.out.print(builder.toString());
                    }
                  }
                }
              } catch (final IOException e) {
                // ignore
              }
            }
          };
      thread.setDaemon(true);
      thread.start();
    }

    @Override
    public void close() {
      if (isShutdown.compareAndSet(false, true)) {
        process.destroyForcibly();
        thread.interrupt();
        try {
          thread.join();
        } catch (final InterruptedException e) {
        }
      }
    }
  }

  private static class Project implements AutoCloseable {
    private final Path baseDirectory;
    private final Path sourceDirectory;
    private final Optional<ForkProcess> forkProcess;

    public Path getBaseDirectory() {
      return baseDirectory;
    }

    public Path getSourceDirectory() {
      return sourceDirectory;
    }

    Project(final Path baseDirectory, final Path sourceDirectory, final ForkProcess forkProcess) {
      this.baseDirectory = baseDirectory;
      this.sourceDirectory = sourceDirectory;
      this.forkProcess = Optional.of(forkProcess);
    }

    @Override
    public void close() {
      forkProcess.ifPresent(ForkProcess::close);
    }
  }

  private static ForkProcess runSbt(final Path tempDir, final String... commands)
      throws IOException {
    final var allCommands = new String[commands.length + 1];
    allCommands[0] = "sbt";
    for (int i = 0; i < commands.length; ++i) allCommands[i + 1] = commands[i];
    return new ForkProcess(new ProcessBuilder(allCommands).directory(tempDir.toFile()).start());
  }

  private static void genSources(final Project project, int count) throws IOException {
    final Path src = project.getSourceDirectory();
    final Path blah = src.resolve("blah");
    Files.createDirectories(blah);
    for (int i = 1; i <= count; ++i) {
      Files.write(blah.resolve("Blah" + i + ".scala"), generatedSource(i).getBytes());
    }
  }

  private static String generatedSource(final int counter) {
    final var result = new StringBuilder();
    result.append("package sbt.benchmark.blah");
    result.append('\n');
    result.append('\n');
    result.append("class Blah" + counter);
    final int lines = 75;
    for (int i = 0; i < lines; ++i) {
      result.append("// ******************************************************************");
    }
    return result.toString();
  }

  private static Path setupProject(final String project, final Path tempDir) throws IOException {
    final ClassLoader loader = Main.class.getClassLoader();
    final Enumeration<URL> urls = loader.getResources(project);
    while (urls.hasMoreElements()) {
      final URL url = urls.nextElement();
      try {
        final URI uri = url.toURI();
        Path path;
        if (uri.getScheme().equals("jar")) {
          FileSystem fileSystem = FileSystems.newFileSystem(uri, Collections.emptyMap());
          path = fileSystem.getPath("/" + project);
        } else {
          path = Paths.get(uri);
        }
        final var base = path.getParent();
        final var temp = tempDir.resolve(path.getFileName().toString());
        Files.createDirectories(temp);
        Files.walkFileTree(
            path,
            new FileVisitor<>() {
              @Override
              public FileVisitResult preVisitDirectory(
                  final Path dir, final BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(tempDir.resolve(base.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
              }

              @Override
              public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                  throws IOException {
                if (attrs.isRegularFile()) {
                  var content = Files.readAllBytes(file);
                  Files.write(tempDir.resolve(base.relativize(file).toString()), content);
                }
                return FileVisitResult.CONTINUE;
              }

              @Override
              public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
              }

              @Override
              public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                return FileVisitResult.CONTINUE;
              }
            });
      } catch (final Exception e) {
        e.printStackTrace();
      }
    }
    return tempDir.resolve(project);
  }

  private static class TempDirectory implements AutoCloseable {
    private final Path tempDir = Files.createTempDirectory("build-perf").toRealPath();

    public Path get() {
      return tempDir;
    }

    private void closeImpl(final Path directory) throws IOException {
      try {
        final Set<FileVisitOption> options = new HashSet<>();
        options.add(FileVisitOption.FOLLOW_LINKS);
        Files.walkFileTree(
            directory,
            options,
            1,
            new FileVisitor<>() {
              @Override
              public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                return FileVisitResult.CONTINUE;
              }

              @Override
              public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                  throws IOException {
                if (file != directory) {
                  if (attrs.isDirectory() && file != directory) {
                    closeImpl(file);
                  }
                  Files.deleteIfExists(file);
                }
                return FileVisitResult.CONTINUE;
              }

              @Override
              public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
              }

              @Override
              public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                  throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
              }
            });
      } catch (final DirectoryNotEmptyException e) {
        closeImpl(directory);
      }
    }

    @Override
    public void close() throws IOException {
      closeImpl(tempDir);
    }

    public TempDirectory() throws IOException {}
  }
}
