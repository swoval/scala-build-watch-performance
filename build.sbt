import java.io.File
import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.{Files, Path}

libraryDependencies += "com.swoval" % "file-tree-views" % "2.1.0"

javacOptions ++= Seq("-source", "11", "-target", "11")

val genBinary = taskKey[Path]("generate an executable binary")
genBinary := {
  val dir = target.value.toPath
  val classPath = (fullClasspathAsJars in Runtime).value.map(_.data).mkString(File.pathSeparator)
  val content =
    s"""
       |#!/bin/bash
       |
       |java=`which java`
       |args="--class-path $classPath build.performance.Main"
       |exec "java" $$args "$$@"
     """.stripMargin

  val binary = Files.write(dir.resolve("watch-test.out"), content.getBytes)
  val permissions = PosixFilePermissions.fromString("rwxrwxrwx")
  Files.setPosixFilePermissions(binary, permissions)
  binary
}

autoScalaLibrary := false
