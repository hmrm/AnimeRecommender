name := "animerecommender"

version := "0.1.0"

scalaVersion := "2.10.0"

resolvers ++= Seq("typesafe" at "http://repo.typesafe.com/typesafe/releases/")

libraryDependencies ++= Seq("io.prediction"            % "client"        % "0.6.1",
                            "org.scalatest"           %% "scalatest"     % "2.1.0",
                            "org.seleniumhq.selenium"  % "selenium-java" % "2.25.0",
                            "com.typesafe.slick"      %% "slick"         % "2.0.0",
                            "org.xerial"               % "sqlite-jdbc"   % "3.7.2",
                            "com.typesafe.akka"       %% "akka-actor"    % "2.3.0")

fork := true
