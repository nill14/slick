import sbt._
import Keys._
import Tests._
import com.jsuereth.sbtsite.SphinxSupport.Sphinx
import com.jsuereth.sbtsite.SitePlugin.site

object SLICKBuild extends Build {

  /** Settings for all projects */
  lazy val commonSettings = Seq[Setting[_]](
    resolvers += Resolver.sonatypeRepo("snapshots"),
    scalacOptions ++= List("-deprecation", "-feature"),
    // Run the Queryable tests (which need macros) on a forked JVM
    // to avoid classloader problems with reification
    testGrouping <<= definedTests in Test map partitionTests,
    parallelExecution in Test := false,
    //concurrentRestrictions += Tags.limitSum(1, Tags.Test, Tags.ForkedTestGroup),
    concurrentRestrictions += Tags.limit(Tags.Test, 1),
    logBuffered := false,
    testOptions += Tests.Argument(TestFrameworks.JUnit, "-q", "-v", "-s", "-a"),
    publishTo <<= (repoKind)(r => Some(Resolver.file("test", file("c:/temp/repo/"+r)))),
    /*publishTo <<= (repoKind){
      case "snapshots" => Some("snapshots" at "https://oss.sonatype.org/content/repositories/snapshots")
      case "releases" =>  Some("releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2")
    },*/
    publishMavenStyle := true,
    pomIncludeRepository := { _ => false },
    // Work around scaladoc problem
    unmanagedClasspath in Compile += Attributed.blank(new java.io.File("doesnotexist")),
    repoKind <<= (version)(v => if(v.trim.endsWith("SNAPSHOT")) "snapshots" else "releases"),
    makePomConfiguration ~= { _.copy(configurations = Some(Seq(Compile, Runtime))) },
    includeFilter in Sphinx := ("*.html" | "*.png" | "*.js" | "*.css" | "*.gif" | "*.txt")
  )

  /* Custom Setting Keys */
  val repoKind = SettingKey[String]("repo-kind", "Maven repository kind (\"snapshots\" or \"releases\")")

  /* Project Definitions */
  lazy val aRootProject = Project(id = "root", base = file("."),
    settings = Project.defaultSettings ++ commonSettings ++ Seq(
      target := file("target/root"),
      sourceDirectory := file("target/root-src"),
      publishArtifact := false
    )).aggregate(slickProject, slickDocsProject)
  lazy val slickDocsProject = Project(id = "docs", base = file("slick-docs"),
    settings = Project.defaultSettings ++ commonSettings ++ Seq(
      publishArtifact := false,
      sourceDirectory in Compile := file("src/sphinx"),
      resourceDirectory in Test := file("src/test/resources")
    )) dependsOn slickProject
  lazy val slickProject = Project(id = "main", base = file("."),
    settings = Project.defaultSettings ++ commonSettings ++ fmppSettings ++ site.settings ++ site.sphinxSupport() ++ Seq(
      target := file("target/main"),
      publishArtifact in Test := false,
      name := "SLICK",
      organizationName := "Typesafe",
      organization := "com.typesafe",
      description := "A type-safe database API for Scala",
      homepage := Some(url("https://github.com/slick/slick/wiki")),
      startYear := Some(2008),
      licenses += ("Two-clause BSD-style license", url("http://github.com/slick/slick/blob/master/LICENSE.txt")),
      pomExtra :=
        <developers>
          <developer>
            <id>szeiger</id>
            <name>Stefan Zeiger</name>
            <timezone>+1</timezone>
            <url>http://szeiger.de</url>
          </developer>
          <developer>
            <id>cvogt</id>
            <name>Jan Christopher Vogt</name>
            <timezone>+1</timezone>
            <url>https://github.com/cvogt/</url>
          </developer>
        </developers>
        <scm>
          <url>git@github.com:slick/slick.git</url>
          <connection>scm:git:git@github.com:slick/slick.git</connection>
        </scm>,
      scalacOptions in doc <++= (version).map(v => Seq("-doc-title", "SLICK", "-doc-version", v))
    ))

  /* Split tests into a group that needs to be forked and another one that can run in-process */
  def partitionTests(tests: Seq[TestDefinition]) = {
    val (fork, notFork) = tests partition (_.name contains ".queryable.")
    Seq(
      new Group("fork", fork, SubProcess(Seq())),
      new Group("inProcess", notFork, InProcess)
    )
  }

  /* FMPP Task */
  lazy val fmpp = TaskKey[Seq[File]]("fmpp")
  lazy val fmppConfig = config("fmpp") hide
  lazy val fmppSettings = inConfig(Compile)(Seq(sourceGenerators <+= fmpp, fmpp <<= fmppTask)) ++ Seq(
    libraryDependencies += "net.sourceforge.fmpp" % "fmpp" % "0.9.14" % fmppConfig.name,
    ivyConfigurations += fmppConfig,
    fullClasspath in fmppConfig <<= update map { _ select configurationFilter(fmppConfig.name) map Attributed.blank },
    //mappings in (Compile, packageSrc) <++= // Add generated sources to sources JAR
    //  (sourceManaged in Compile, managedSources in Compile) map { (b, s) => s x (Path.relativeTo(b) | Path.flat) }
    mappings in (Compile, packageSrc) <++=
      (sourceManaged in Compile, managedSources in Compile, sourceDirectory in Compile) map { (base, srcs, srcDir) =>
        val fmppSrc = srcDir / "scala"
        val inFiles = fmppSrc ** "*.fm"
        (srcs x (Path.relativeTo(base) | Path.flat)) ++ // Add generated sources to sources JAR
          (inFiles x (Path.relativeTo(fmppSrc) | Path.flat)) // Add *.fm files to sources JAR
      }
  )
  lazy val fmppTask =
    (fullClasspath in fmppConfig, runner in fmpp, sourceManaged, streams, cacheDirectory, sourceDirectory) map { (cp, r, output, s, cache, srcDir) =>
      val fmppSrc = srcDir / "scala"
      val inFiles = (fmppSrc ** "*.fm" get).toSet
      val cachedFun = FileFunction.cached(cache / "fmpp", outStyle = FilesInfo.exists) { (in: Set[File]) =>
        IO.delete(output ** "*.scala" get)
        val args = "--expert" :: "-q" :: "-S" :: fmppSrc.getPath :: "-O" :: output.getPath ::
          "--replace-extensions=fm, scala" :: "-M" :: "execute(**/*.fm), ignore(**/*)" :: Nil
        toError(r.run("fmpp.tools.CommandLine", cp.files, args, s.log))
        (output ** "*.scala").get.toSet
      }
      cachedFun(inFiles).toSeq
    }
}
