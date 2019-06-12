
lazy val `trip` =
  project
    .in(file("."))
    .enablePlugins(DockerPlugin, JavaAppPackaging)
    .settings(commonSettings: _*)
    .settings(packageSettings: _*)
    .settings(
      bashScriptExtraDefines ++= IO.readLines(baseDirectory.value / "native_packager_parameter.sh"),
      javaOptions in Universal ++= globalJavaOptions,
      libraryDependencies ++= Seq(
        library.akkaActor,
        library.akkaBootstrapManagement,
        library.akkaBootstrapDiscoveryK8,
        library.akkaClusterSharding,
        library.akkaHttp,
        library.akkaHttpCirce,
        library.akkaLog4j,
        library.akkaStream,
        library.circeGeneric,
        library.disruptor,
        library.log4jApi,
        library.log4jApiScala,
        library.log4jCore,
        library.pureConfig
      ),
      version := "1.0.0"
    )

lazy val library =
  new {
    val akkaActor                  = "com.typesafe.akka"             %% "akka-actor"                        % "2.5.23"
    val akkaBootstrapManagement    = "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % "1.0.1"
    val akkaBootstrapDiscoveryK8   = "com.lightbend.akka.discovery"  %% "akka-discovery-kubernetes-api"     % "1.0.1"
    val akkaCluster                = "com.typesafe.akka"             %% "akka-cluster"                      % "2.5.21"
    val akkaClusterSharding        = "com.typesafe.akka"             %% "akka-cluster-sharding"             % "2.5.23"
    val akkaLog4j                  = "de.heikoseeberger"             %% "akka-log4j"                        % "1.6.1"
    val akkaHttp                   = "com.typesafe.akka"             %% "akka-http"                         % "10.1.7"
    val akkaHttpCirce              = "de.heikoseeberger"             %% "akka-http-circe"                   % "1.26.0"
    val akkaStream                 = "com.typesafe.akka"             %% "akka-stream"                       % "2.5.23"
    val circeGeneric               = "io.circe"                      %% "circe-generic"                     % "0.11.1"
    val disruptor                  = "com.lmax"                      %  "disruptor"                         % "3.4.2"
    val log4jApi                   = "org.apache.logging.log4j"      %  "log4j-api"                         % "2.11.0"
    val log4jApiScala              = "org.apache.logging.log4j"      %% "log4j-api-scala"                   % "11.0"
    val log4jCore                  = "org.apache.logging.log4j"      %  "log4j-core"                        % "2.11.0"
    val pureConfig                 = "com.github.pureconfig"         %% "pureconfig"                        % "0.10.2"
  }

lazy val commonSettings =
  compilerSettings ++
  resolverSettings ++
  sbtSettings ++
  scalaFmtSettings

lazy val compilerSettings =
  Seq(
    scalaVersion := "2.12.6",
    scalacOptions ++= Seq(
      "-unchecked",
      "-deprecation",
      "-language:_",
      "-target:jvm-1.8",
      "-encoding",
      "UTF-8"
    ),
    javacOptions ++= Seq(
      "-source",
      "1.8",
      "-target",
      "1.8"
    )
  )

// Java Options set via sbt have to be added explicitly to native packager
// Maintain the list here and reference in relevant scopes.
lazy val globalJavaOptions = Seq(
  // See https://logging.apache.org/log4j/2.x/manual/async.html for using async logger
  "-Dlog4j2.contextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector",
  // This service is headless.
  "-Djava.awt.headless=true"
)

lazy val resolverSettings = Seq(
  resolvers += Resolver.bintrayRepo("tanukkii007", "maven")
)

lazy val sbtSettings =
  Seq(
    fork := true,
    cancelable in Global := true
  )

lazy val scalaFmtSettings =
  Seq(
    scalafmtOnCompile := true
  )

lazy val packageSettings =
  dockerSettings ++
  organizationSettings

lazy val organizationSettings =
  Seq(
    organization := "com.markusjura"
  )

lazy val dockerSettings =
  Seq(
    packageSummary := "Trip Service",
    packageDescription := "Handling trips",
    dockerExposedPorts := Seq(8080, 8558),
    dockerBaseImage := "openjdk:8-jdk"
  )
