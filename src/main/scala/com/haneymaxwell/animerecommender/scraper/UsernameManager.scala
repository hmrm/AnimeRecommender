package com.haneymaxwell.animerecommender.scraper

import java.util.concurrent.Executors

import Data._
import akka.actor.{ActorRef, Actor}
import scala.concurrent._
import com.haneymaxwell.animerecommender.Util._
import duration._
import akka.pattern.pipe

object UsernameManager {
  implicit val ec = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())


  import java.util.concurrent.atomic.AtomicInteger

  lazy val blocksInFlight: AtomicInteger = new AtomicInteger(0)

  class UsernameScrapingActor(prefix: String, gender: Gender, scraper: DriverManager, next: ActorRef) extends Actor {
    case class LastBlock(i: Int)
    case object Done
    def getLastBlockStart: Future[LastBlock] = {
      import scala.util.matching.Regex
      lazy val lastPageRegex: Regex = """(?<=show=)[0-9]*(?=">Last)""".r
      lazy val page: Future[String] =  UsernameScraper.getResults(gender, 0, scraper, prefix)
      page map { p =>
        val res = lastPageRegex.findFirstIn(p).getOrElse("0").toInt
        println(s"Detected last username block for gender $gender at $res")
        LastBlock(res)
      }
    }

    def receive: Unit = {
      case LastBlock(i) => {
        Range(0, i + 1, 25) foreach (self ! _)
        self ! Done
      }
      case j: Int       => UsernameScraper.generateNames(scraper, gender, prefix, j, next)
      case Done         => context.system.scheduler.scheduleOnce(30.days)(getLastBlockStart pipeTo self)
    }

    getLastBlockStart pipeTo self
  }

  val querychars = ('a' to 'z').union('0' to '9').union(Seq('-', '_'))
  val prefixes = for {
    ch1 <- querychars
    ch2 <- querychars
  } yield s"$ch1$ch2"
}
