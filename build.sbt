name := "animerecommender"

version := "0.1.0"

scalaVersion := "2.10.0"

resolvers ++= Seq(
  "spray"    at "http://repo.spray.io",
  "typesafe" at "http://repo.typesafe.com/typesafe/releases/")

libraryDependencies ++= Seq("io.prediction"           % "client"       % "0.6.1",
                            "io.spray"                % "spray-client" % "1.3.0",
                            "com.typesafe.akka"      %% "akka-actor"   % "2.3.0")
