package com.haneymaxwell.animerecommender.scraper

import Predef.{any2stringadd => _, _}
import java.util.concurrent.{BlockingQueue, ArrayBlockingQueue}
import scala.concurrent.blocking
import concurrent.ExecutionContext.Implicits.global
import scala.util.matching.Regex
import scala.concurrent.Future
import com.haneymaxwell.animerecommender.Util._

object UsernameScraper {

  import Data._
  import com.haneymaxwell.animerecommender.scraper.QueueUtils.CompletableQueue

  lazy val BaseUrl = "http://myanimelist.net/users.php?q="

  def genUrl(gender: Gender, index: Long) = s"$BaseUrl&g=${gender.toInt}&show=$index"

  lazy val NameExtractor: Regex = new Regex( """profile/([^"]+)">\1</a></div>""", "name")

  case class GenerateNamesResult(femaleNames: Set[Username], maleNames: Set[Username])

  def getResults(gender: Gender, index: Long, scrape: DriverManager): Future[String] =
    scrape source genUrl(gender, index)

  def getNames(gender: Gender, index: Long, scrape: DriverManager): Future[Set[Username]] = {
    import scala.util.matching.Regex.Match
    getResults(gender, index, scrape) map (NameExtractor.findAllMatchIn(_).toSet.map((m: Match) => Username(m.group("name"))))
  }

  def generateNames(scrape: DriverManager, queue: CompletableQueue[(Username, Gender)], gender: Gender, start: Int) = {
    println(s"Scraping for new usernames for gender $gender starting from $start")

    lazy val result: Future[Set[Username]] = getNames(gender, start, scrape)

    def putIfAbsent(username: Username) = {
      if (DB.usernamePresent(username)) {
        println(s"Scraped username $username which was already present in database")
      } else {
        println(s"Scraped username $username which is a new username, enqueueing for processing")
        DB.addUsername(username, gender)
        blocking(queue.put((username, gender)))
        Metrics.newUsernameProcessed.incrementAndGet()
      }
    }

    result map { names => names foreach putIfAbsent }
  }
}





