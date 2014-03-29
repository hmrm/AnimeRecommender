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

  def generateNames(scrape: DriverManager, queue: BlockingQueue[(Username, Gender)]) = {
    lazy val nFemale: Int = DB.nUsernamesProcessed(Female)
    lazy val nMale:   Int = DB.nUsernamesProcessed(Male)

    println(s"Scraping for new usernames starting from male: $nMale, female: $nFemale")

    lazy val femaleResult: Future[Set[Username]] = getNames(Female, nFemale, scrape)
    lazy val maleResult:   Future[Set[Username]] = getNames(Male, nMale, scrape)

    lazy val results: Future[(Set[Username], Set[Username])] = femaleResult zip maleResult

    def putIfAbsent(username: Username, gender: Gender) = {
      if (DB.usernamePresent(username)) {
        println(s"Scraped username $username which was already present in database")
      } else {
        println(s"Scraped username $username which is a new username, enqueueing for processing")
        DB.addUsername(username, gender)
        blocking(queue.put((username, gender)))
      }
    }

    results map { case (newFemaleNames, newMaleNames) =>
      newFemaleNames foreach (name => putIfAbsent(name, Female))
      newMaleNames   foreach (name => putIfAbsent(name, Male))
    } escalate
  }
}





