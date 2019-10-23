val tapirVersion     = "0.11.7"
val kantanCsvVersion = "0.5.1"

name := "api"

version := "0.1"

scalaVersion := "2.12.10"

libraryDependencies ++= Seq(
    "com.softwaremill.tapir" %% "tapir-core"                % tapirVersion
  , "com.softwaremill.tapir" %% "tapir-http4s-server"       % tapirVersion
  , "com.softwaremill.tapir" %% "tapir-json-circe"          % tapirVersion
  , "com.softwaremill.tapir" %% "tapir-openapi-docs"        % tapirVersion
  , "com.softwaremill.tapir" %% "tapir-openapi-circe-yaml"  % tapirVersion
  , "com.softwaremill.tapir" %% "tapir-swagger-ui-http4s"   % tapirVersion
  , "org.http4s"             %% "http4s-blaze-server"       % "0.20.11"
  , "com.outr"               %% "scribe"                    % "2.7.3"
  , "io.scalaland"           %% "chimney"                   % "0.3.3"
  , "com.nrinaudo"           %% "kantan.csv-java8"          % kantanCsvVersion
  , "com.nrinaudo"           %% "kantan.csv-generic"        % kantanCsvVersion
  , "org.typelevel"          %% "cats-core"                 % "2.0.0"
  , "org.typelevel"          %% "cats-effect"               % "2.0.0"
  , "is.cir"                 %% "ciris-core"                % "0.12.1"
  , "com.lihaoyi"            %% "utest"                     % "0.7.1" % "test"
)

scalacOptions ++= Seq(
    "-Ypartial-unification"
  , "-language:higherKinds"
  , "-feature"
)

javaOptions ++= Seq(
  "-Xss4M"
)

run / mainClass := Some("com.tvi.api.Application")

testFrameworks += new TestFramework("utest.runner.Framework")
