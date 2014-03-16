package com.haneymaxwell.animerecommender.scraper

import Predef.{any2stringadd => _, _}
import org.scalatest.selenium.Chrome
import java.util.concurrent.{BlockingQueue, ArrayBlockingQueue}

object UsernameScraper {

  import scala.util.matching.Regex
  import scala.concurrent.Future
  import concurrent.ExecutionContext.Implicits.global

  case class Username(get: String)

  case class Gender(toInt: Int)
  object Female extends Gender(2)
  object Male   extends Gender(1)

  lazy val BaseUrl = "http://myanimelist.net/users.php?q="

  def genUrl(gender: Gender, index: Int) = s"$BaseUrl&g=${gender.toInt}&show=$index"

  lazy val NameExtractor: Regex = new Regex("""profile/([^"]+)">\1</a></div>""", "name")

  case class GenerateNamesResult(femaleNames: Set[Username], maleNames: Set[Username])

  def generateNames(lastSuccessful: GenerateNamesResult = GenerateNamesResult(Set(), Set())): (Future[Unit], BlockingQueue[Username]) = {
    lazy val queue = new ArrayBlockingQueue[Username](100)
    def recur(lastSuccessful: GenerateNamesResult): Future[Unit] = {

      lazy val nFemale = lastSuccessful.femaleNames.size
      lazy val nMale   = lastSuccessful.maleNames.size

      lazy val femaleResult = getNames(Female, nFemale)
      lazy val maleResult   = getNames(Male,   nMale)
      lazy val results = femaleResult zip maleResult

      results flatMap { case (newFemaleNames, newMaleNames) =>
        lazy val femaleNames = lastSuccessful.femaleNames ++ newFemaleNames
        lazy val maleNames   = lastSuccessful.maleNames   ++ newMaleNames

        (newFemaleNames -- lastSuccessful.femaleNames) foreach queue.put
        (newMaleNames   -- lastSuccessful.maleNames)   foreach queue.put

        if ((femaleNames.size == nFemale) && (maleNames.size == nMale)) {
          Future(())
        } else {
          println(s"latest: $femaleNames, $maleNames")
          recur(GenerateNamesResult(femaleNames, maleNames))
        }
      }
    }
    (recur(lastSuccessful), queue)
  }

  def getResults(gender: Gender, index: Int): Future[String] = Future {
    lazy val scraper = new Scraper
    try {
      scraper(genUrl(gender, index))
    } finally {
      scraper.cleanup()
    }
  }

  def getNames(gender: Gender, index: Int): Future[Set[Username]] = {
    import scala.util.matching.Regex.Match
    getResults(gender, index) map (NameExtractor.findAllMatchIn(_).toSet.map((m: Match) => Username(m.group("name"))))
  }
}

class Scraper extends Chrome with (String => String) {
  def apply(url: String) = {
    go to url
    pageSource
  }

  def cleanup() = close()
}
