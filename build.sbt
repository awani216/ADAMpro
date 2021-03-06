import sbt.{ExclusionRule, _}
import sbt.Keys._
import sbtassembly.AssemblyPlugin.autoImport.{ShadeRule, _}

name := "ADAMpro"

lazy val commonSettings = Seq(
  organization := "org.vitrivr",
  version := "0.1.0",
  scalaVersion := "2.11.8"
)

//projects
lazy val root = (project in file(".")).
  settings(commonSettings: _*)

lazy val grpc = project.
  settings(commonSettings ++ Seq(assemblyOutputPath in assembly := baseDirectory.value / ".." / "lib" / "grpc-assembly-0.1-SNAPSHOT.jar"): _*)

lazy val grpcclient = project.
  settings(commonSettings: _*)

lazy val web = project.dependsOn(grpcclient).
  settings(commonSettings: _*)





//build
lazy val buildSettings = Seq(
  scalaVersion := "2.11.8",
  crossScalaVersions := Seq("2.11.8"),
  ivyScala := ivyScala.value.map(_.copy(overrideScalaVersion = true))
)

mainClass in(Compile, run) := Some("org.vitrivr.adampro.main.Startup")

unmanagedResourceDirectories in Compile += baseDirectory.value / "conf"
javacOptions ++= Seq("-encoding", "UTF-8")
scalacOptions ++= Seq()

//lib resolvers
resolvers ++= Seq(
  "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/",
  "Restlet Repositories" at "http://maven.restlet.org",
  "Hortonworks Repositories" at "http://repo.hortonworks.com/content/repositories/releases/"
)
resolvers += Resolver.sonatypeRepo("snapshots")

//base libs
val baseLibs = Seq(
  "org.scala-lang" % "scala-compiler" % "2.11.8",
  "org.scala-lang" % "scala-reflect" % "2.11.8"
)

//adampro core libs
val coreLibs = Seq(
  "org.apache.spark" %% "spark-core" % "2.2.0" % "provided" excludeAll ExclusionRule("org.apache.hadoop"), //make sure that you use the same spark version as in your deployment!
  "org.apache.spark" %% "spark-sql" % "2.2.0"  % "provided",
  "org.apache.spark" %% "spark-hive" % "2.2.0"  % "provided",
  "org.apache.spark" %% "spark-mllib" % "2.2.0"  % "provided",
  "org.apache.hadoop" % "hadoop-client" % "2.7.3" excludeAll ExclusionRule("javax.servlet")  //make sure that you use the same hadoop version as in your deployment!
).map(
  _.excludeAll(
    ExclusionRule("org.scala-lang"),
    ExclusionRule("io.netty") //remove this line if running locally within IDE
  )
)
//TODO: add multiple configurations to sbt, one which has coreLibs as provided (as they do not have to be submitted to spark)

//secondary libs
val secondaryLibs = Seq(
  "org.scalanlp" %% "breeze" % "0.13.1",
  "org.scalanlp" %% "breeze-natives" % "0.13.1",
  "com.typesafe.slick" %% "slick" % "3.1.1",
  "com.mchange" % "c3p0" % "0.9.5.2",
  "com.h2database" % "h2" % "1.4.196",
  "it.unimi.dsi" % "fastutil" % "7.0.12",
  "commons-io" % "commons-io" % "2.5",
  "org.apache.commons" % "commons-lang3" % "3.4",
  "org.apache.commons" % "commons-math3" % "3.4.1",
  "com.googlecode.javaewah" % "JavaEWAH" % "1.1.6",
  "com.google.guava" % "guava" % "21.0",
  "org.jgrapht" % "jgrapht-core" % "1.0.1"
).map(
  _.excludeAll(
    ExclusionRule("org.scala-lang"),
    ExclusionRule("org.slf4j"),
    ExclusionRule("log4j"),
    ExclusionRule("ch.qos.logback"),
    ExclusionRule("io.netty")
  )
)

//tertiary libs
val tertiaryLibs = Seq(
  "com.lucidworks.spark" % "spark-solr" % "3.3.0",
  "org.postgresql" % "postgresql" % "9.4.1208",
  "com.datastax.spark" %% "spark-cassandra-connector" % "2.0.5",
  "net.postgis" % "postgis-jdbc" % "2.2.1",
  "com.databricks" %% "spark-avro" % "3.1.0",
  "org.iq80.leveldb" % "leveldb" % "0.9",
  "com.linkedin.paldb" % "paldb" % "1.2.0",
  "org.alluxio" % "alluxio-core-client-fs" % "1.5.0",
  "com.hortonworks" % "shc-core" % "1.1.1-2.1-s_2.11",
  "org.apache.hbase" % "hbase-client" % "1.3.1"
).map(
  _.excludeAll(
    ExclusionRule("org.scala-lang"),
    ExclusionRule("org.slf4j"),
    ExclusionRule("log4j"),
    ExclusionRule("ch.qos.logback"),
    ExclusionRule("io.netty")
  )
)

//test libs
val testLibs = Seq(
  "org.scalatest" %% "scalatest" % "2.2.6",
  "io.grpc" % "grpc-netty" % "1.2.0"
).map(
  _.excludeAll(
    ExclusionRule("org.scala-lang"),
    ExclusionRule("org.slf4j"),
    ExclusionRule("log4j"),
    ExclusionRule("ch.qos.logback"),
    ExclusionRule("io.netty")
  )
)

//assembly
assemblyShadeRules in assembly := Seq(
  ShadeRule.rename("io.netty.**" -> "adampro.grpc.shaded.io.netty.@1").inAll,
  ShadeRule.rename("com.google.**" -> "adampro.grpc.shaded.com.google.@1").inAll
)

libraryDependencies := baseLibs ++ coreLibs ++ secondaryLibs ++ tertiaryLibs ++ testLibs

dependencyOverrides ++= Set(
  "org.apache.hadoop" % "hadoop-hdfs" % "2.7.3"
)

unmanagedBase <<= baseDirectory { base => base / "lib" }
unmanagedResourceDirectories in Compile += baseDirectory.value / "conf"

//assembly
assemblyOption in assembly :=
  (assemblyOption in assembly).value.copy(includeScala = true)

val meta = """META.INF(.)*""".r
assemblyMergeStrategy in assembly := {
  case PathList("application.conf") => MergeStrategy.discard
  case PathList("javax", "servlet", xs@_*) => MergeStrategy.last
  case PathList(ps@_*) if ps.last endsWith ".html" => MergeStrategy.last
  case n if n.startsWith("reference.conf") => MergeStrategy.concat
  case n if n.endsWith(".conf") => MergeStrategy.concat
  case meta(_) => MergeStrategy.discard
  case x => MergeStrategy.last
}

mainClass in assembly := Some("org.vitrivr.adampro.main.Startup")

test in assembly := {}

//provided libraries should be included in "run"
run in Compile <<= Defaults.runTask(fullClasspath in Compile, mainClass in(Compile, run), runner in(Compile, run))

parallelExecution in Test := false
concurrentRestrictions in Global += Tags.limit(Tags.Test, 1)

//custom commands
addCommandAlias("proto", "; grpc/assembly")

lazy val setupDocker = taskKey[Unit]("Setup and start docker container to run ADAMpro.")
setupDocker := {
  "./scripts/setupDocker.sh" !
}