import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.{Files, Path}

libraryDependencies += "com.swoval" % "file-tree-views" % "2.1.0"

javacOptions ++= Seq("-source", "11", "-target", "11")

val genBinary = taskKey[Path]("generate an executable binary")
genBinary := {
  val dir = target.value.toPath
  val assembledJar = assembly.value
  val content =
    s"""
       |#!/bin/bash
       |
       |java=`which java`
       |args="-jar $assembledJar build.performance.Main"
       |exec "java" $$args "$$@"
     """.stripMargin

  val binary = Files.write(dir.resolve("watch-test.out"), content.getBytes)
  binary.toFile.setExecutable(true)
  binary.toFile.setReadable(true)
  binary.toFile.setWritable(true)
  val permissions = PosixFilePermissions.fromString("rwxrwxrwx")
  Files.setPosixFilePermissions(binary, permissions)
  binary
}

autoScalaLibrary := false
