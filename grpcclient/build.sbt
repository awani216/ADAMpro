name := "ADAMpro-grpcclient"

libraryDependencies ++= Seq(
  "com.google.protobuf" % "protobuf-java" % "3.2.0",
  "io.grpc" % "grpc-netty" % "1.2.0",
  "io.grpc" % "grpc-protobuf" % "1.2.0",
  "io.grpc" % "grpc-stub" % "1.2.0",
  "org.slf4j" % "slf4j-simple" % "1.7.21",
  "com.trueaccord.scalapb" %% "scalapb-json4s" % "0.3.2",
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.7.4",
  "com.fasterxml.jackson.module" % "jackson-module-scala_2.10" % "2.7.4"
)

unmanagedBase <<= baseDirectory { base => base / ".." / "lib" }

//assembly
assemblyOption in assembly :=
  (assemblyOption in assembly).value.copy(includeScala = false)

val metaMime = """META.INF(.)mime.types""".r
val meta = """META.INF(.)*""".r
assemblyMergeStrategy in assembly := {
  case PathList("javax", "servlet", xs @ _*) => MergeStrategy.last
  case PathList(ps @ _*) if ps.last endsWith ".html" => MergeStrategy.last
  case n if n.startsWith("reference.conf") => MergeStrategy.concat
  case n if n.endsWith(".conf") => MergeStrategy.concat
  case metaMime(_) => MergeStrategy.deduplicate
  case meta(_) => MergeStrategy.discard
  case x => MergeStrategy.last
}

test in assembly := {}