name := "api"

version := "0.1"

scalaVersion := "2.12.10"

libraryDependencies ++= Seq(
    "com.softwaremill.tapir" %% "tapir-core"                % "0.11.7"
  , "com.softwaremill.tapir" %% "tapir-http4s-server"       % "0.11.7"
  , "com.softwaremill.tapir" %% "tapir-json-circe"          % "0.11.7"
  , "com.softwaremill.tapir" %% "tapir-openapi-docs"        % "0.11.7"
  , "com.softwaremill.tapir" %% "tapir-openapi-circe-yaml"  % "0.11.7"
  , "com.softwaremill.tapir" %% "tapir-swagger-ui-http4s"   % "0.11.7"
  , "org.http4s"             %% "http4s-blaze-server"       % "0.20.11"
  , "com.outr"               %% "scribe"                    % "2.7.3"
  , "io.scalaland"           %% "chimney"                   % "0.3.3"
)

javaOptions ++= Seq(
  "-Xss4M"
)

run / mainClass := Some("com.tvi.api.Application")
