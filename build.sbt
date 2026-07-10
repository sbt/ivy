lazy val copyLicenseFiles = taskKey[Seq[File]]("copies needed files for jar.")
lazy val makeModuleProperties = taskKey[Seq[File]]("Create module.properties file.")

lazy val root = (project in file(".")).
  settings(versionWithGit: _*).
  settings(
    // TODO - Read from version.properties
    git.baseVersion := "2.3.0-sbt",
    name := "ivy",
    scalacOptions ++= Seq("-target:jvm-1.8"),
    Compile / compile / javacOptions ++= Seq("-target", "8", "-source", "8"),
    Compile / unmanagedSourceDirectories := Seq(
      baseDirectory.value / "src" / "java"
    ),
    Compile / unmanagedJars := Seq.empty,
    Compile / unmanagedResourceDirectories :=
      (Compile / unmanagedSourceDirectories).value,
    Compile / unmanagedResources / includeFilter :=
       "*.png" | "*.xml" | "*.properties" | "*.xsl" | "*.xsd" | "*.css" | "*.html" | "*.template" | "*.ent",
    Compile / unmanagedResources / excludeFilter :=
       "*.java",
    Test / unmanagedSourceDirectories := Seq(
      baseDirectory.value / "test" / "java"
    ),
    Test / unmanagedClasspath := Seq.empty,
    compileOrder := CompileOrder.JavaThenScala,
    copyLicenseFiles := {
      val dir = ((Compile / resourceManaged)).value
      val bd = baseDirectory.value
      val copies =
        Map(
          (bd / "LICENSE") -> (dir / "META-INF" / "LICENSE"),
          (bd / "NOTICE") -> (dir / "META-INF" / "NOTICE")
        )
      IO.copy(copies)
      (copies map (_._2))(collection.breakOut)
    },
    makeModuleProperties := {
      val dir = ((Compile / resourceManaged)).value
      val file = dir / "module.properties"
        IO.write(file, s"version=${version.value}\n")
      Seq(file)
    },
    // TODO - copy ivysettings to ivyconf files for backwards compatibility.
    Compile / resourceGenerators += copyLicenseFiles.taskValue,
    Compile / resourceGenerators += makeModuleProperties.taskValue,
    libraryDependencies ++=
      Seq(
        "org.apache.ant" %"ant-nodeps" % "1.7.1" % "provided",
        "commons-httpclient" % "commons-httpclient" % "3.0" % "provided",
        "org.bouncycastle" % "bcpg-jdk14" % "1.45" % "provided",
        "com.jcraft" % "jsch.agentproxy.jsch" % "0.0.6" % "provided",
        "com.jcraft" % "jsch.agentproxy" % "0.0.6" % "provided",
        "com.jcraft" % "jsch.agentproxy.connector-factory" % "0.0.6" % "provided",
        "commons-vfs" % "commons-vfs" % "1.0" % "provided",
        "oro" % "oro" % "2.0.8" % "provided",
        "org.apache.ant" %"ant-testutil" % "1.7.1" % Test,
        "commons-lang" % "commons-lang" % "2.6" % Test,
      ),
    autoScalaLibrary := false,
    crossPaths := false
  )

ThisBuild / organization := "org.scala-sbt.ivy"
ThisBuild / homepage := Some(url("https://github.com/sbt/ivy"))
ThisBuild / description := "patched Ivy for sbt"
ThisBuild / licenses := List("Apache-2.0" -> url("https://github.com/sbt/ivy/blob/2.3.x-sbt/LICENSE"))
ThisBuild / scmInfo := Some(ScmInfo(url("https://github.com/sbt/ivy"), "git@github.com:sbt/ivy.git"))
ThisBuild / developers := List(
  Developer("eed3si9n", "Eugene Yokota", "@eed3si9n", url("https://github.com/eed3si9n"))
)
ThisBuild / pomIncludeRepository := { _ => false }
ThisBuild / publishTo := {
  val centralSnapshots = "https://central.sonatype.com/repository/maven-snapshots/"
  if (version.value.endsWith("-SNAPSHOT")) Some("central-snapshots" at centralSnapshots)
  else localStaging.value
}
