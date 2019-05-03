package build.performance;

import com.swoval.files.FileTreeViews.Observer;
import com.swoval.files.PathWatchers.Event;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import com.swoval.files.PathWatcher;
import com.swoval.files.PathWatchers;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class Main {
  private static final Path defaultTouchFile =
      Paths.get("shared", "AkkaMain.scala").toAbsolutePath();
  private static final Path defaultWatchFile = Paths.get("target", "watch.out").toAbsolutePath();

  public static void main(final String[] args) {
    Path touchFile = defaultTouchFile;
    Path watchFile = defaultWatchFile;
    char prev = '\0';
    for (final String arg : args) {
      switch (prev) {
        case 't':
          touchFile = Paths.get(arg);
          prev = '\0';
          break;
        case 'w':
          watchFile = Paths.get(arg);
          prev = '\0';
          break;
        default:
          if (arg.equals("-t") || arg.equals("--touch")) {
            prev = 't';
          } else if (arg.equals("-watch") || arg.equals("--watch")) {
            prev = 'w';
          } else {
            final String[] parts = arg.split("=");
            if (parts.length == 2) {
              if (parts[0].equals("-t") || parts[0].equals("--touch")) {
                touchFile = Paths.get(parts[1]);
              } else if (parts[0].equals("-w") || parts[0].equals("--watch")) {
                watchFile = Paths.get(parts[1]);
              }
            }
            prev = '\0';
          }
          break;
      }
    }
    try (final PathWatcher<PathWatchers.Event> watcher = PathWatchers.get(true)) {
      watcher.register(watchFile, -1);
      final LinkedBlockingQueue<Integer> queue = new LinkedBlockingQueue<>();
      watcher.addObserver(
          new Observer<Event>() {
            @Override
            public void onError(final Throwable t) {}

            @Override
            public void onNext(final Event event) {
              System.out.println("Received event " + event);
              queue.offer(1);
            }
          });
      final byte[] touchContents = Files.readAllBytes(touchFile);
      Files.write(touchFile, touchContents);
      System.out.println("Wrote to " + touchFile);
      queue.poll(5, TimeUnit.SECONDS);
      long watchFileLastModified = Files.getLastModifiedTime(watchFile).toMillis();
      long touchLastModified = Files.getLastModifiedTime(touchFile).toMillis();
      long elapsed = watchFileLastModified - touchLastModified;
      System.out.println("Took " + elapsed + " ms to run task");
    } catch (final IOException | InterruptedException e) {
    }
  }
}
