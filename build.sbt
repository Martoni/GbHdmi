// See README.md for license details.

ThisBuild / scalaVersion     := "2.12.13"
ThisBuild / version          := "0.1.0"
ThisBuild / organization     := "eu.fabienm"

lazy val root = (project in file("."))
  .settings(
    name := "GbHdmi",
    libraryDependencies ++= Seq(
      "edu.berkeley.cs" %% "chisel3" % "3.5.1",
      "edu.berkeley.cs" %% "chiseltest" % "0.5.1" % "test",
      "eu.fabienm" %% "gbvga" % "0.1.0",
      "com.armadeus" %% "hdmicore" % "0.1.0",
      "eu.fabienm" %% "chisnespad" % "0.2.0",
      "org.armadeus" %% "fpgamacro" % "0.1.0"
    ),
    scalacOptions ++= Seq(
      "-Xsource:2.11",
      "-language:reflectiveCalls",
      "-deprecation",
      "-feature",
      "-Xcheckinit",
      // Enables autoclonetype2 in 3.4.x (on by default in 3.5)
      "-P:chiselplugin:useBundlePlugin"
    ),
    addCompilerPlugin("edu.berkeley.cs" % "chisel3-plugin" % "3.5.1" cross CrossVersion.full),
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full)
  )

