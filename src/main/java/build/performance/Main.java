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
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class Main {
  private static Set<String> allProjects;
  private static final FileSystem jarFileSystem;
  private static final Random random = new Random();
  private static final boolean isMac;
  private static final boolean isWin;

  static {
    var osName = System.getProperty("os.name", "").toLowerCase();
    isMac = osName.startsWith("mac");
    isWin = osName.startsWith("win");
  }

  static {
    allProjects = new LinkedHashSet<>();
    allProjects.add("sbt-0.13.17");
    allProjects.add("sbt-1.3.0");
    allProjects.add("sbt-1.3.0-fork");
    allProjects.add("sbt-1.3.0-turbo");
    allProjects.add("gradle-5.4.1");
    allProjects.add("mill-0.3.6");
    allProjects.add("bloop-1.3.2");
    try {
      final var url = Main.class.getClassLoader().getResource("sbt-1.3.0");
      if (url == null) throw new NullPointerException();
      final var uri = url.toURI();
      jarFileSystem =
          uri.getScheme().equals("jar")
              ? FileSystems.newFileSystem(uri, Collections.emptyMap())
              : null;
    } catch (final Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  public static void main(final String[] args)
      throws IOException, InterruptedException, URISyntaxException, TimeoutException {
    var prev = '\0';
    var warmupIterations = 3;
    var iterations = 5;
    var baseDirectory = Optional.<Path>empty();
    var extraSources = 5000;
    var timeout = 10;
    var cpuTimeout = 5;
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
          case 'c':
            cpuTimeout = Integer.valueOf(arg);
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
          case 't':
            timeout = Integer.valueOf(arg);
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
            } else if (arg.equals("-c") || arg.equals("--cpu-timeout-seconds")) {
              prev = 'c';
            } else if (arg.equals("-t") || arg.equals("--timeout-minutes")) {
              prev = 't';
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
      final var results = new ArrayList<RunResult>();
      for (final var projectName : projects) {
        final var base = tempDir.get();
        final var projectBase = base.resolve(projectName);
        setupProject(projectName, base);
        final Project project;
        final ProjectLayout layout;
        if (projectName.startsWith("sbt")) {
          final var binary = projectBase.resolve("bin").resolve("sbt-launch.jar").toString();
          layout = new ProjectLayout(projectBase, projectBase);
          final var color = isWin ? "false" : "true";
          final var factory =
              new SimpleServerFactory(
                  projectBase, javaHome, "java", "-Dsbt.supershell=never", "-Dsbt.color=" + color, "-jar", binary, "~test");
          project = new Project(projectName, layout, factory);
        } else if (projectName.startsWith("mill")) {
          final var binary = projectBase.resolve("bin").resolve("mill").toString();
          layout = new ProjectLayout(projectBase, projectBase.resolve("perf"));
          final var factory =
              new SimpleServerFactory(
                  projectBase,
                  javaHome,
                  "java",
                  "-DMILL_CLASSPATH=" + binary,
                  "-DMILL_VERSION=0.3.6",
                  "-Djna.nosys=true",
                  "-cp",
                  binary,
                  "mill.MillMain",
                  "-i",
                  "-w",
                  "perf.test");
          project = new Project(projectName, layout, factory);
        } else if (projectName.startsWith("gradle")) {
          var binaryName = projectName.replaceAll("gradle-", "gradle-launcher-") + ".jar";
          final var binary = projectBase.resolve("lib").resolve(binaryName).toString();
          layout = new ProjectLayout(projectBase, projectBase);
          final var factory =
              new SimpleServerFactory(
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
          project = new Project(projectName, layout, factory);
        } else if (projectName.startsWith("bloop")) {
          installBloop(projectBase, javaHome);
          final var bloopJar = projectBase.resolve("dist").resolve("blp-coursier").toString();
          layout = new ProjectLayout(projectBase, projectBase);
          final var serverFactory =
              new SimpleServerFactory(
                  projectBase,
                  javaHome,
                  "java",
                  "-jar",
                  bloopJar,
                  "launch",
                  "ch.epfl.scala:bloop-frontend_2.12:1.3.2",
                  "-r",
                  "bintray:scalameta/maven",
                  "-r",
                  "bintray:scalacenter/releases",
                  "-r",
                  "https://oss.sonatype.org/content/repository/staging",
                  "--main",
                  "bloop.Server");
          final var bloopBin = projectBase.resolve("dist").resolve("bloop").toString();
          final var factory =
              new ClientServerFactory(
                  serverFactory,
                  bloopUpCheck(projectBase),
                  projectBase,
                  javaHome,
                  "python",
                  bloopBin,
                  "test",
                  "bloop-1-3-2",
                  "-w");
          project = new Project(projectName, layout, factory);
        } else {
          throw new IllegalArgumentException("Cannot create a project from name " + projectName);
        }
        try (final var watcher = PathWatchers.get(true)) {
          watcher.register(layout.getBaseDirectory(), 0);
          results.add(run(project, 0, timeout, iterations, warmupIterations, cpuTimeout, watcher));
          genSources(layout, extraSources);
          System.out.println("generated " + extraSources + " sources");
          results.add(
              run(
                  project,
                  extraSources,
                  timeout,
                  iterations,
                  warmupIterations,
                  cpuTimeout,
                  watcher));
        } catch (final Exception e) {
          System.err.println("Error running tests for " + project.name);
          e.printStackTrace();
        }
      }
      results.sort(
          (left, right) ->
              left.count == right.count
                  ? left.name.compareTo(right.name)
                  : left.count - right.count);
      System.out.println(" project | min (ms) | max (ms) | median (ms) | total (ms) | cpu % |");
      System.out.println(":------- | :------: | :------: | :-------: | :--------: | :---: |");
      for (final var result : results) {
        System.out.println(result.markdownRow());
      }
      final Path src = sourceDirectory.orElse(null);
      baseDirectory.ifPresent(dir -> System.out.println(src));
    }
  }

  private static boolean runProc(final String... commands) throws IOException {
    return runProc(30, TimeUnit.SECONDS, false, commands);
  }

  private static boolean runProc(
      final long duration, final TimeUnit timeUnit, final boolean quiet, final String... commands)
      throws IOException {
    return runProc(duration, timeUnit, quiet, new ProcessBuilder(commands));
  }

  private static boolean runProc(
      final long duration,
      final TimeUnit timeUnit,
      final boolean quiet,
      final ProcessBuilder builder)
      throws IOException {
    final var process = builder.start();
    var thread = quiet ? null : new ProcessIOThread(process);
    try {
      return process.waitFor(duration, timeUnit) && process.exitValue() == 0;
    } catch (final InterruptedException e) {
      return false;
    } finally {
      if (thread != null) thread.interrupt();
    }
  }

  private static final class RunResult {
    private final long[] results;
    private final int count;
    private final String name;
    private final long totalMs;
    private final double cpuUtilization;

    RunResult(
        final String name,
        final int count,
        final long[] results,
        final long totalMs,
        final double cpuUtilization) {
      this.count = count;
      this.name = name;
      this.results = results;
      this.totalMs = totalMs;
      this.cpuUtilization = cpuUtilization;
    }

    String markdownRow() {
      var min = Long.MAX_VALUE;
      var max = Long.MIN_VALUE;
      var avg = 0L;
      var times = new ArrayList<Long>(results.length);
      for (var elapsed : results) {
        min = Math.min(min, elapsed);
        max = Math.max(max, elapsed);
        times.add(elapsed);
      }
      Collections.sort(times);
      if (results.length % 2 == 0) {
        int base = results.length / 2;
        avg = (times.get(base) + times.get(base - 1)) / 2;
      } else {
        avg = times.get(results.length / 2);
      }
      return this.name
          + (" (" + (count + 3) + " source files) | ")
          + (min + " | " + max + " | " + avg + " | " + totalMs + " | " + cpuUtilization);
    }
  }

  @SuppressWarnings("unused")
  private static RunResult run(
      final Project project,
      final int count,
      final int timeoutMinutes,
      final int iterations,
      final int warmupIterations,
      final int cpuTimeout,
      final PathWatcher<PathWatchers.Event> watcher)
      throws TimeoutException {
    final var result = new long[iterations];
    final var start = System.nanoTime();
    try (final var server = project.buildServerFactory.newServer()) {
      long totalElapsed = 0;
      {
        System.out.println("Waiting for startup");
        final var updateResult = project.updateAkkaMain(watcher, count);
        if (!updateResult.latch.await(timeoutMinutes, TimeUnit.MINUTES))
          throw new TimeoutException("Failed to touch expected file");
        System.out.println("Waited for startup");
      }
      // bloop takes a moment to start watching files
      if (project.name.startsWith("bloop")) Thread.sleep(1000 + count / 2);
      for (int i = -warmupIterations; i < iterations; ++i) {
        if (project.name.startsWith("bloop")) Thread.sleep(1000);
        final var updateResult = project.updateAkkaMain(watcher, count);
        if (updateResult.latch.await(30, TimeUnit.SECONDS)) {
          long elapsed = updateResult.elapsed();
          if (elapsed > 0) {
            // Discard the first run that includes build tool startup
            if (i >= 0) {
              totalElapsed += elapsed;
              result[i] = elapsed;
            }
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
      final var end = System.nanoTime();
      final double cpu = getProcessCpuUtilization(server.pid(), cpuTimeout);
      System.out.println(
          "Average cpu utilization percentage over " + cpuTimeout + " seconds was " + cpu);
      return new RunResult(project.name, count, result, (end - start) / 1000000, cpu);
    } catch (final IOException | InterruptedException e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  private static class ForkProcess implements TimeoutAutoCloseable {
    final Process process;
    final Thread thread;
    final AtomicBoolean isClosed = new AtomicBoolean(false);

    ForkProcess(final Process process) {
      System.out.println("Managing process with pid " + process.pid());
      this.process = process;
      thread = new ProcessIOThread(process);
    }

    @Override
    public int pid() {
      return (int) process.pid();
    }

    @Override
    public void close() {
      if (isClosed.compareAndSet(false, true)) {
        try {
          process.destroy();
          process.waitFor(10, TimeUnit.SECONDS);
          thread.interrupt();
          thread.join();
        } catch (final InterruptedException e) {
          e.printStackTrace();
          // something weird happened
        }
      }
    }
  }

  private static class UpdateResult {
    private final CountDownLatch latch;
    private final long lastModifiedTime;
    private final Path watchPath;

    final long elapsed() throws IOException {
      var written = Long.valueOf(Files.readString(watchPath).lines().skip(1).iterator().next());
      return written - lastModifiedTime;
    }

    UpdateResult(final CountDownLatch latch, final Path watchPath, final long lastModifiedTime) {
      this.lastModifiedTime = lastModifiedTime;
      this.latch = latch;
      this.watchPath = watchPath;
    }
  }

  private static class ProjectLayout {
    private final Path baseDirectory;
    private final Path mainSourceDirectory;
    private final Path testSourceDirectory;

    ProjectLayout(final Path baseDirectory, final Path projectBaseDirectory) {
      this.baseDirectory = baseDirectory;
      this.mainSourceDirectory = srcDirectory(projectBaseDirectory, "main");
      this.testSourceDirectory = srcDirectory(projectBaseDirectory, "test");
    }

    Path getMainSourceDirectory() {
      return mainSourceDirectory;
    }

    Path getTestSourceDirectory() {
      return testSourceDirectory;
    }

    Path getBaseDirectory() {
      return baseDirectory;
    }

    Path getOutputPathSourceFile() {
      return getMainSourceDirectory().resolve("WatchFile.scala");
    }

    private static Path srcDirectory(final Path base, final String config) {
      return base.resolve("src")
          .resolve(config)
          .resolve("scala")
          .resolve("sbt")
          .resolve("benchmark");
    }
  }

  private static void initProject(final ProjectLayout projectLayout)
      throws IOException, URISyntaxException {
    Files.createDirectories(projectLayout.getMainSourceDirectory());
    final var akkaMainPath = projectLayout.getMainSourceDirectory().resolve("AkkaMain.scala");
    Files.writeString(akkaMainPath, loadSourceFile("AkkaMain.scala"));

    Files.createDirectories(projectLayout.getTestSourceDirectory());
    final var akkaTestPath = projectLayout.getTestSourceDirectory().resolve("AkkaPerfTest.scala");
    Files.writeString(akkaTestPath, loadSourceFile("AkkaPerfTest.scala"));
  }

  private static void genSources(final ProjectLayout layout, final int count) throws IOException {
    final Path src = Files.createDirectories(layout.getMainSourceDirectory().resolve("blah"));
    for (int i = 1; i <= count; ++i) {
      Files.writeString(src.resolve("Blah" + i + ".scala"), generatedSource(i));
    }
  }

  private interface BuildServerFactory {
    TimeoutAutoCloseable newServer();
  }

  private interface TimeoutAutoCloseable extends AutoCloseable {
    int pid();

    @Override
    void close() throws TimeoutException;
  }

  private static class SimpleServerFactory implements BuildServerFactory {
    private final ProcessBuilder processBuilder;

    SimpleServerFactory(final Path directory, final String javaHome, final String... commands) {
      this.processBuilder = getBuilder(directory, javaHome, commands);
    }

    @Override
    public TimeoutAutoCloseable newServer() {
      try {
        return new ForkProcess(processBuilder.start());
      } catch (final IOException e) {
        throw new IllegalStateException(e);
      }
    }
  }

  private static class ClientServerFactory implements BuildServerFactory {
    private final BuildServerFactory buildServerFactory;
    private final BuildServerFactory clientServerFactory;
    private final Supplier<Boolean> serverUpCheck;

    ClientServerFactory(
        final BuildServerFactory buildServerFactory,
        final Supplier<Boolean> serverUpCheck,
        final Path directory,
        final String javaHome,
        final String... commands) {
      this.buildServerFactory = buildServerFactory;
      this.clientServerFactory = new SimpleServerFactory(directory, javaHome, commands);
      this.serverUpCheck = serverUpCheck;
    }

    @Override
    public TimeoutAutoCloseable newServer() {
      final var server = buildServerFactory.newServer();
      if (!serverUpCheck.get()) throw new IllegalStateException("Server did not respond");
      final var client = clientServerFactory.newServer();
      return new TimeoutAutoCloseable() {
        @Override
        public int pid() {
          return server.pid();
        }

        @Override
        public void close() throws TimeoutException {
          client.close();
          server.close();
        }
      };
    }
  }

  private static ProcessBuilder getBuilder(
      final Path directory, final String javaHome, final String... commands) {
    if (!javaHome.isEmpty() && commands[0].equals("java")) {
      final var commandName =
          System.getProperty("os.name").toLowerCase().startsWith("win") ? "java.exe" : "java";
      commands[0] = Paths.get(javaHome).resolve("bin").resolve(commandName).normalize().toString();
    }
    System.out.println("Running " + commands[0] + " in " + directory);
    final var processBuilder =
        new ProcessBuilder(commands).inheritIO().directory(directory.toFile());
    processBuilder.environment().remove("SBT_OPTS");
    if (!javaHome.isEmpty()) processBuilder.environment().put("JAVA_HOME", javaHome);
    return processBuilder;
  }

  private static class Project {
    private final ProjectLayout projectLayout;
    private final BuildServerFactory buildServerFactory;
    private final String name;

    UpdateResult updateAkkaMain(final PathWatcher<PathWatchers.Event> watcher, final int count)
        throws IOException {
      final long rand = random.nextLong();
      final var newPath =
          projectLayout.getBaseDirectory().resolve("watch-" + (rand < 0 ? -rand : rand) + ".out");
      System.out.println("Waiting for " + newPath);
      final var latch = new CountDownLatch(1);
      watcher.addObserver(
          new Observer<>() {
            @Override
            public void onError(final Throwable t) {}

            @Override
            public void onNext(final Event event) {
              if (event.getTypedPath().getPath().equals(newPath)) {
                try {
                  if (event.getTypedPath().isFile()
                      && Integer.valueOf(Files.readString(newPath).lines().iterator().next())
                          == count) latch.countDown();
                } catch (final NumberFormatException | NoSuchElementException e) {
                  // ignore this, it means the file doesn't exist
                } catch (final Exception e) {
                  e.printStackTrace();
                }
              }
            }
          });
      final var pathString = newPath.toString().replaceAll("\\\\", "\\\\\\\\");
      final var blahString =
          projectLayout
              .getMainSourceDirectory()
              .resolve("blah")
              .toString()
              .replaceAll("\\\\", "\\\\\\\\");
      final String content =
          "package sbt.benchmark\n\n"
              + "import java.nio.file.Paths\n"
              + "object WatchFile {\n"
              + ("  val path = java.nio.file.Paths.get(\"" + pathString + "\")\n")
              + ("  val blahPath = java.nio.file.Paths.get(\"" + blahString + "\")\n")
              + "}";
      final var outputPath = projectLayout.getOutputPathSourceFile();
      Files.writeString(outputPath, content);
      return new UpdateResult(latch, newPath, System.currentTimeMillis());
    }

    Project(
        final String name,
        final ProjectLayout projectLayout,
        final BuildServerFactory buildServerFactory)
        throws IOException, URISyntaxException {
      this.name = name;
      this.projectLayout = projectLayout;
      this.buildServerFactory = buildServerFactory;
      initProject(projectLayout);
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
      if (uri.getScheme().equals("jar") && jarFileSystem != null) {
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
    private Thread shutdownHook;

    Path get() {
      return tempDir;
    }

    private void retryDelete(final Path path) throws IOException {
      var i = 0;
      while (i < 200) {
        try {
          Files.deleteIfExists(path);
          i = 1000;
        } catch (final IOException e) {
          i += 1;
          try {
            Thread.sleep(5);
          } catch (final InterruptedException ex) {
            throw e;
          }
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
      Runtime.getRuntime().removeShutdownHook(shutdownHook);
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
      shutdownHook =
          new Thread("Cleanup-" + tempDir) {
            @Override
            public void run() {
              try {
                closeImpl(tempDir);
              } catch (final Exception e) {
                e.printStackTrace();
              }
            }
          };
      Runtime.getRuntime().addShutdownHook(shutdownHook);
    }
  }

  private static class ProcessIOThread extends Thread {
    private final Process process;
    private final Consumer<String> consumer;

    ProcessIOThread(final Process process, final Consumer<String> consumer) {
      super("process-io-thread-" + process.pid());
      this.process = process;
      this.consumer = consumer;
      setDaemon(true);
      start();
    }

    ProcessIOThread(final Process process) {
      this(process, System.err::print);
    }

    @Override
    public void run() {
      // Reads the input and error streams of a process and dumps them to
      // the main process output streams
      final InputStream is = process.getInputStream();
      final InputStream es = process.getErrorStream();
      try {
        while (true) {
          {
            final StringBuilder builder = new StringBuilder();
            while (is.available() > 0) {
              builder.append((char) is.read());
            }
            if (builder.length() > 0) {
              consumer.accept(builder.toString());
            }
          }
          {
            final StringBuilder builder = new StringBuilder();
            while (es.available() > 0) {
              builder.append((char) es.read());
            }
            if (builder.length() > 0) {
              consumer.accept(builder.toString());
            }
          }
          Thread.sleep(2);
        }
      } catch (final IOException | InterruptedException e) {
        // exit on interrupt
      }
    }
  }

  private static void installBloop(final Path dir, final String javaHome) throws IOException {
    runProc(
        "python", dir.resolve("install.py").toString(), "--dest", dir.resolve("dist").toString());
    final var sbtLaunchJar = dir.resolve("bin").resolve("sbt-launch.jar").toString();
    final var builder = getBuilder(dir, javaHome, "java", "-jar", sbtLaunchJar, "bloopInstall");
    runProc(5, TimeUnit.MINUTES, false, builder);
  }

  private static Supplier<Boolean> bloopUpCheck(final Path dir) {
    final var binary = dir.resolve("dist").resolve("bloop").toString();
    return () -> {
      var up = false;
      while (!up) {
        try {
          up = runProc(5, TimeUnit.SECONDS, false, "python", binary, "about");
          Thread.sleep(100);
        } catch (final IOException | InterruptedException e) {
        }
      }
      return true;
    };
  }

  private static long getModifiedTimeOrZero(final Path path) {
    try {
      return Files.getLastModifiedTime(path).toMillis();
    } catch (final IOException e) {
      return 0;
    }
  }

  private static double getProcessCpuUtilization(final int pid, final int delaySeconds)
      throws IOException, InterruptedException {
    final var s = Integer.toString(delaySeconds);
    final var p = Integer.toString(pid);
    if (isMac) {
      final var proc =
          new ProcessBuilder("top", "-pid", p, "-d", "-s", s, "-l", "2", "-stats", "cpu").start();
      assert (proc.waitFor() == 0);
      // For some reason the process builder doesn't wait for top to actually complete, but
      // reading from the input stream blocks until top is over.
      final var result = new String(proc.getInputStream().readAllBytes());
      final var it = result.lines().iterator();
      while (it.hasNext()) {
        final var cpu = it.next();
        if (!it.hasNext()) {
          try {
            return Double.valueOf(cpu);
          } catch (final NumberFormatException e) {
            return -1;
          }
        }
      }
      return 0.0;
    } else if (isWin) {
      final var builder = new ProcessBuilder("powershell.exe", "Get-Process", "-Id", p);
      final var firstProc = builder.start();
      assert (firstProc.waitFor() == 0);
      // For some reason the process builder doesn't wait for top to actually complete, but
      // reading from the input stream blocks until top is over.
      final var firstResult = new String(firstProc.getInputStream().readAllBytes());
      final var first = firstResult.lines().iterator();
      var startSeconds = -1.0d;
      while (first.hasNext()) {
        final var cpuLine = first.next();
        try {
          startSeconds = Double.valueOf(cpuLine.split("[ ]+")[5]);
        } catch (final Exception e) {
        }
      }
      Thread.sleep(delaySeconds * 1000);
      final var secondProc = builder.start();
      assert (secondProc.waitFor() == 0);
      // For some reason the process builder doesn't wait for top to actually complete, but
      // reading from the input stream blocks until top is over.
      final var secondResult = new String(secondProc.getInputStream().readAllBytes());
      final var second = secondResult.lines().iterator();
      var finishSeconds = -1.0d;
      while (second.hasNext()) {
        final var cpuLine = second.next();
        try {
          finishSeconds = Double.valueOf(cpuLine.split("[ ]+")[5]);
        } catch (final Exception e) {
        }
      }
      if (startSeconds != -1.0d && finishSeconds != -1.0d) {
        return ((int) ((finishSeconds - startSeconds) / delaySeconds * 100 * 100)) / 100.0;
      }
      return -1;
    } else {
      final var proc = new ProcessBuilder("top", "-p", p, "-b", "-d", s, "-n", "2").start();
      assert (proc.waitFor() == 0);
      // For some reason the process builder doesn't wait for top to actually complete, but
      // reading from the input stream blocks until top is over.
      final var result = new String(proc.getInputStream().readAllBytes());
      final var it = result.lines().iterator();
      while (it.hasNext()) {
        final var cpuLine = it.next();
        if (!it.hasNext()) {
          try {
            return Double.valueOf(cpuLine.split("[ ]+")[9]);
          } catch (final NumberFormatException e) {
            return -1;
          }
        }
      }
      return 0.0;
    }
  }
}
