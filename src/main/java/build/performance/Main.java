package build.performance;

import com.swoval.files.FileTreeViews.Observer;
import com.swoval.files.PathWatcher;
import com.swoval.files.PathWatchers;
import com.swoval.files.PathWatchers.Event;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.AccessDeniedException;
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
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main {
  private static Set<String> allProjects;
  private static final FileSystem jarFileSystem;
  private static boolean isWin = System.getProperty("os.name", "").toLowerCase().startsWith("win");

  private static Path srcDirectory(final String config) {
    return Paths.get("src").resolve(config).resolve("scala").resolve("sbt").resolve("benchmark");
  }

  static {
    allProjects = new HashSet<>();
    allProjects.add("sbt-0.13.17");
    allProjects.add("sbt-1.3.0");
    allProjects.add("gradle-5.4.1");
    if (!isWin) allProjects.add("mill-0.3.6");
    try {
      final var url = Main.class.getClassLoader().getResource("sbt-1.3.0");
      if (url == null) throw new NullPointerException();
      final var uri = url.toURI();
      jarFileSystem = FileSystems.newFileSystem(uri, Collections.emptyMap());
    } catch (final Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  public static void main(final String[] args)
      throws IOException, URISyntaxException, TimeoutException {
    var prev = '\0';
    var warmupIterations = 3;
    var iterations = 5;
    var baseDirectory = Optional.<Path>empty();
    var extraSources = 5000;
    String javaHome = "";

    var sourceDirectory = Optional.<Path>empty();
    var projects = new ArrayList<String>();
    try (final var tempDir = new TempDirectory()) {
      for (final String arg : args) {
        switch (prev) {
          case 'b':
            baseDirectory = Optional.of(Paths.get(arg));
            prev = '\0';
            break;
          case 'j':
            javaHome = arg;
            prev = '\0';
            break;
          case 'e':
            extraSources = Integer.valueOf(arg);
            prev = '\0';
            break;
          case 'i':
            iterations = Integer.valueOf(arg);
            prev = '\0';
            break;
          case 's':
            sourceDirectory = Optional.of(Paths.get(arg));
            prev = '\0';
            break;
          case 'w':
            warmupIterations = Integer.valueOf(arg);
            prev = '\0';
            break;
          default:
            if (arg.equals("-b") || arg.equals("--base-directory")) {
              prev = 'b';
            } else if (arg.equals("-e") || arg.equals("--extra-sources")) {
              prev = 'e';
            } else if (arg.equals("-s") || arg.equals("--source-directory")) {
              prev = 's';
            } else if (arg.equals("-j") || arg.equals("--java-home")) {
              prev = 'j';
            } else if (arg.equals("-i") || arg.equals("--iterations")) {
              prev = 'i';
            } else if (arg.equals("all")) {
              projects.addAll(allProjects);
            } else if (arg.equals("-w") || arg.equals("--warmup-iterations")) {
              prev = 'w';
            } else if (allProjects.contains(arg)) {
              projects.add(arg);
            } else {
              final String[] parts = arg.split("=");
              if (parts.length == 2) {
                if (parts[0].equals("-s") || parts[0].equals("--source-directory")) {
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
        final var base = tempDir.get();
        final var projectBase = base.resolve(projectName);
        setupProject(projectName, base);
        final Project project;
        if (projectName.startsWith("sbt")) {
          var binary = projectBase.resolve("bin").resolve("sbt-launch.jar").toString();
          project =
              new Project(projectBase, projectBase, javaHome, "java", "-jar", binary, "~test");
        } else if (projectName.startsWith("mill")) {
          final var binary = projectBase.resolve("bin").resolve("mill").toString();
          project =
              new Project(
                  projectBase,
                  projectBase.resolve("perf"),
                  javaHome,
                  "java",
                  "-DMILL_CLASSPATH=" + binary,
                  "-DMILL_VERSION=0.3.6",
                  "-Djna.nosys=true",
                  "-cp",
                  binary,
                  "mill.main.client.MillClientMain",
                  "-w",
                  "perf.test");
        } else if (projectName.startsWith("gradle")) {
          var binaryName = projectName.replaceAll("gradle-", "gradle-launcher-") + ".jar";
          final var binary = projectBase.resolve("lib").resolve(binaryName).toString();
          project =
              new Project(
                  projectBase,
                  projectBase,
                  javaHome,
                  "java",
                  "-Xmx64m",
                  "-Xms64m",
                  "-Dorg.gradle.appname=gradle",
                  "-classpath",
                  binary,
                  "org.gradle.launcher.GradleMain",
                  "-t",
                  "spec");
        } else {
          throw new IllegalArgumentException("Cannot create a project from name " + projectName);
        }
        try {
          run(project, iterations, warmupIterations);
          project.genSources(extraSources);
          System.out.println("generated " + extraSources + " sources");
          run(project, iterations, warmupIterations);
        } finally {
          project.close();
        }
      }
      final Path src = sourceDirectory.orElse(null);
      baseDirectory.ifPresent(dir -> System.out.println(src));
    } finally {
      System.out.println("close file system");
      jarFileSystem.close();
    }
  }

  private static void run(final Project project, final int iterations, final int warmupIterations)
      throws TimeoutException {
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
              queue.offer(1);
            }
          });
      long totalElapsed = 0;
      queue.clear();
      System.out.println("Waiting for startup");
      if (queue.poll(4, TimeUnit.MINUTES) == null)
        throw new TimeoutException("Failed to touch expected file");
      System.out.println("start up completed");
      for (int i = -warmupIterations; i < iterations; ++i) {
        queue.clear();
        long touchLastModified = project.updateAkkaMain();
        if (queue.poll(30, TimeUnit.SECONDS) != null) {
          long watchFileLastModified = getModifiedTimeOrZero(watchFile);
          long elapsed = watchFileLastModified - touchLastModified;
          if (elapsed > 0) {
            // Discard the first run that includes build tool startup
            if (i >= 0) totalElapsed += elapsed;
          } else {
            i -= 1;
          }
          System.out.println("Took " + elapsed + " ms to run task");
        } else {
          i -= 1;
        }
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
    final AtomicBoolean isClosed = new AtomicBoolean(false);
    final AtomicBoolean isShutdown = new AtomicBoolean(false);

    ForkProcess(final Process process) {
      System.out.println("Managing process with pid " + process.pid());
      this.process = process;
      thread =
          new Thread("process-io-thread") {
            @Override
            public void run() {
              // Reads the input and error streams of a process and dumps them to
              // the main process output streams
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
                      System.err.print(builder.toString());
                    }
                  }
                  Thread.sleep(2);
                }
              } catch (final IOException | InterruptedException e) {
                isShutdown.set(true);
              }
            }
          };
      thread.setDaemon(true);
      thread.start();
    }

    @Override
    public void close() {
      if (isClosed.compareAndSet(false, true)) {
        try {
          process.destroy();
          process.waitFor(10, TimeUnit.SECONDS);
          isShutdown.set(true);
          System.out.println("Waited for process");
          thread.interrupt();
          thread.join();
        } catch (final InterruptedException e) {
          e.printStackTrace();
          // something weird happened
        }
      }
    }
  }

  private static class Project implements AutoCloseable {
    private final Path baseDirectory;
    private final Path projectBaseDirectory;
    private final String akkaMainContent;
    private final Path akkaMainPath;
    private final ForkProcess forkProcess;

    Path getBaseDirectory() {
      return baseDirectory;
    }

    long updateAkkaMain() throws IOException {
      final String append = "\n//" + UUID.randomUUID().toString();
      Files.write(akkaMainPath, (akkaMainContent + append).getBytes());
      return getModifiedTimeOrZero(akkaMainPath);
    }

    Project(
        final Path baseDirectory,
        final Path projectBaseDirectory,
        final String javaHome,
        final String... commands)
        throws IOException, URISyntaxException {
      this.baseDirectory = baseDirectory;
      final var mainSrcDirectory =
          Files.createDirectories(projectBaseDirectory.resolve(srcDirectory("main")));
      akkaMainPath = mainSrcDirectory.resolve("AkkaMain.scala");
      akkaMainContent = loadSourceFile("AkkaMain.scala");
      this.projectBaseDirectory = projectBaseDirectory;
      Files.writeString(akkaMainPath, akkaMainContent);
      final var testSrcDirectory =
          Files.createDirectories(projectBaseDirectory.resolve(srcDirectory("test")));
      Files.writeString(
          testSrcDirectory.resolve("AkkaPerfTest.scala"), loadSourceFile("AkkaPerfTest.scala"));
      if (!javaHome.isEmpty()) {
        final var commandName =
            System.getProperty("os.name").toLowerCase().startsWith("win") ? "java.exe" : "java";
        commands[0] =
            Paths.get(javaHome).resolve("bin").resolve(commandName).normalize().toString();
      }
      System.out.println("Running " + commands[0] + " in " + baseDirectory);
      final var builder = new ProcessBuilder(commands).directory(baseDirectory.toFile());
      if (!javaHome.isEmpty()) builder.environment().put("JAVA_HOME", javaHome);
      this.forkProcess = new ForkProcess(builder.start());
    }

    void genSources(int count) throws IOException {
      final Path src = projectBaseDirectory.resolve(srcDirectory("main")).resolve("blah");
      Files.createDirectories(src);
      for (int i = 1; i <= count; ++i) {
        Files.writeString(src.resolve("Blah" + i + ".scala"), generatedSource(i));
      }
    }

    @Override
    public void close() {
      if (forkProcess != null) {
        System.out.println("should kill " + forkProcess);
        forkProcess.close();
      }
    }
  }

  private static String generatedSource(final int counter) {
    final int lines = 75;
    return "package sbt.benchmark.blah\n\nclass Blah"
        + counter
        + "\n// ******************************************************************".repeat(lines);
  }

  private static String loadSourceFile(final String name) throws IOException, URISyntaxException {
    final ClassLoader loader = Main.class.getClassLoader();
    final URL url = loader.getResource("shared/" + name);
    if (url == null) throw new NullPointerException();
    final URI uri = url.toURI();
    return new String(Files.readAllBytes(Paths.get(uri)));
  }

  private static void setupProject(final String project, final Path tempDir) {
    try {
      final URL url = Main.class.getClassLoader().getResource(project);
      if (url == null) throw new NullPointerException();
      final URI uri = url.toURI();
      Path path;
      if (uri.getScheme().equals("jar")) {
        path = jarFileSystem.getPath("/" + project);
      } else {
        path = Paths.get(uri);
      }
      final var base = path.getParent();
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
                Files.write(
                    tempDir.resolve(base.relativize(file).toString()), Files.readAllBytes(file));
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
    } catch (final NullPointerException | IOException | URISyntaxException e) {
      e.printStackTrace();
    }
  }

  private static class TempDirectory implements AutoCloseable {
    private final Path tempDir;

    Path get() {
      return tempDir;
    }

    private void retryDelete(final Path path) throws IOException {
      var i = 0;
      while (i < 100) {
        try {
          Files.deleteIfExists(path);
          i = 1000;
        } catch (final AccessDeniedException e) {
          i += 1;
          if (i >= 100) throw e;
        }
      }
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
                  if (attrs.isDirectory()) {
                    closeImpl(file);
                  }
                  retryDelete(file);
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
                retryDelete(dir);
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

    TempDirectory() throws IOException {
      final var base =
          Paths.get(System.getProperty("java.io.tmpdir", ""))
              .toRealPath()
              .resolve("build-tool-perf");
      Files.createDirectories(base);
      final var file = base.toFile();
      if (!Files.isWritable(base) && !file.setWritable(true))
        throw new IOException("Couldn't set " + base + " writable.");
      if (!Files.isReadable(base) && !file.setReadable(true))
        throw new IOException("Couldn't set " + base + " readable.");
      final var formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd+HH-mm-ss");
      final var zdt =
          ZonedDateTime.ofInstant(
              Instant.ofEpochMilli(System.currentTimeMillis()),
              Clock.systemDefaultZone().getZone());
      final var text = formatter.format(zdt);
      tempDir = Files.createDirectories(base.resolve(text));
    }
  }

  private static long getModifiedTimeOrZero(final Path path) {
    try {
      return Files.getLastModifiedTime(path).toMillis();
    } catch (final IOException e) {
      return 0;
    }
  }
}
