package com.haneymaxwell.animerecommender.scraper

import java.util.concurrent.Executors

import akka.actor.ActorRef
import com.typesafe.scalalogging.slf4j.LazyLogging

import Predef.{any2stringadd => _, _}
import scala.concurrent.{ExecutionContext, blocking, Future}
import scala.util.matching.Regex

object UsernameScraper extends LazyLogging {
  implicit lazy val ec = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  import Data._
  import com.haneymaxwell.animerecommender.scraper.QueueUtils.CompletableQueue

  lazy val BaseUrl = "http://myanimelist.net/users.php?q="

  def genUrl(gender: Gender, index: Long, prefix: String) = s"$BaseUrl$prefix&g=${gender.toInt}&show=$index"

  lazy val NameExtractor: Regex = new Regex( """profile/([^"]+)">\1</a></div>""", "name")

  case class GenerateNamesResult(femaleNames: Set[Username], maleNames: Set[Username])

  def getResults(gender: Gender, index: Long, scrape: DriverManager, prefix: String): Future[String] =
    scrape source genUrl(gender, index, prefix)

  def getNames(gender: Gender, index: Long, scrape: DriverManager, prefix: String): Future[Set[Username]] = {
    import scala.util.matching.Regex.Match
    getResults(gender, index, scrape, prefix) map (NameExtractor.findAllMatchIn(_).toSet.map((m: Match) => Username(m.group("name"))))
  }

  def generateNames(scrape: DriverManager, gender: Gender, prefix: String, offset: Int, sendTo: ActorRef) = {
    println(s"Scraping for new usernames for gender $gender with prefix $prefix, from $offset")

    lazy val result: Future[Set[Username]] = getNames(gender, offset, scrape, prefix)

    def putIfAbsent(username: Username) = {
      if (DB.usernamePresent(username)) {
        println(s"Scraped username $username which was already present in database")
      } else {
        println(s"Scraped username $username which is a new username, enqueueing for processing")
        DB.addUsername(username, gender)
        sendTo ! username
        Metrics.newUsernameProcessed.incrementAndGet()
      }
    }

    result map { names => names foreach putIfAbsent }
  }
}





