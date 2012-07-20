version in ThisBuild := "0.11.0-SNAPSHOT"

scalaVersion in ThisBuild := "2.10.0-M5"

crossScalaVersions in ThisBuild ++= "2.10.0-M4" :: Nil
//crossVersion in ThisBuild := CrossVersion.Disabled

//scalaHome in ThisBuild := Some(file("C:/Users/szeiger/code/scala/build/pack"))

//autoScalaLibrary in ThisBuild := false

libraryDependencies in ThisBuild ++= Seq(
  "com.h2database" % "h2" % "1.3.166" % "test",
  "org.xerial" % "sqlite-jdbc" % "3.6.20" % "test",
  "org.apache.derby" % "derby" % "10.9.1.0" % "test",
  "org.hsqldb" % "hsqldb" % "2.2.8" % "test",
  "postgresql" % "postgresql" % "9.1-901.jdbc4" % "test",
  "mysql" % "mysql-connector-java" % "5.1.13" % "test",
  "net.sourceforge.jtds" % "jtds" % "1.2.4" % "test",
  "com.novocode" % "junit-interface" % "0.9-RC2" % "test",
  "org.slf4j" % "slf4j-api" % "1.6.4",
  "ch.qos.logback" % "logback-classic" % "0.9.28" % "test"
)

// Add scala-compiler dependency for scala.reflect.internal
libraryDependencies in ThisBuild <+= scalaVersion(
  "org.scala-lang" % "scala-compiler" % _
)
