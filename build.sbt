name := "animerecommender"

version := "0.1.0"

scalaVersion := "2.10.4"

resolvers += "Akka Snapshot Repository" at "http://repo.akka.io/snapshots/"

resolvers ++= Seq("typesafe" at "http://repo.typesafe.com/typesafe/releases/")

libraryDependencies ++= Seq("io.prediction"               % "client"              % "0.6.1",
                            "org.scalatest"              %% "scalatest"           % "2.1.0",
                            "org.seleniumhq.selenium"     % "selenium-java"       % "2.42.2",
                            "com.typesafe.slick"         %% "slick"               % "2.0.1",
                            "org.xerial"                  % "sqlite-jdbc"         % "3.7.2",
                            "org.slf4j"                   % "slf4j-simple"        % "1.7.7",
                            "com.typesafe.scala-logging" %% "scala-logging-slf4j" % "2.1.2",
                            "com.typesafe.akka"          %% "akka-actor"          % "2.4-SNAPSHOT")

fork := true
