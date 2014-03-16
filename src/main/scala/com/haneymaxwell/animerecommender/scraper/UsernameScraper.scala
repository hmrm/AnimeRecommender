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

  def getResults(gender: Gender, index: Int): Future[String] = Future {
    lazy val scrape = new Scraper
    try {
      scrape source genUrl(gender, index)
    } finally {
      scrape.cleanup()
    }
  }

  def getNames(gender: Gender, index: Int): Future[Set[Username]] = {
    import scala.util.matching.Regex.Match
    getResults(gender, index) map (NameExtractor.findAllMatchIn(_).toSet.map((m: Match) => Username(m.group("name"))))
  }

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

  def genUrl(username: Username): String =
    s"http://myanimelist.net/malappinfo.php?u=${username.get}&status=all&type=anime"

  def processName(name: Username): Future[String] = {
    lazy val scrape = new Scraper
    lazy val res = Future(scrape text genUrl(name))
    res onComplete { _ => scrape.cleanup() }
    res map { str => str.split('\n').mkString("") } // I am not entirely sure why this is necessary, but it is
  }

  // Note: it would be nicer just to parse this into XML, but it looks like it isn't adequately standards conforming
  def genXmlRegex(tag: String) = s"""(?<=<$tag>)((?!</$tag>).)*(?=</$tag>)""".r

  lazy val GetSeries = genXmlRegex("anime")
  lazy val GetName   = genXmlRegex("series_title")
  lazy val GetAID    = genXmlRegex("series_animedb_id")
  lazy val GetRating = genXmlRegex("my_score")

  case class Rating(get: Int)
  case class AID(get: Int)
  case class SeriesName(get: String)

  def processData(data: String): (Map[AID, SeriesName], Map[AID, Rating]) = {
    lazy val res: Seq[((AID, SeriesName), (AID, Rating))] = (GetSeries.findAllIn(data).toSeq map { case text =>
      for {
        name   <- GetName  .findFirstIn(text)
        aid    <- GetAID   .findFirstIn(text)
        rating <- GetRating.findFirstIn(text)
      } yield ((AID(aid.toInt) -> SeriesName(name)), (AID(aid.toInt) -> Rating(rating.toInt)))
    }).flatten

    (res.map(x => x._1).toMap, res.map(x => x._2).toMap)
  }

}

class Scraper extends Chrome {
  def source(url: String) = {
    go to url
    pageSource
  }

  def text(url: String) = {
    import org.openqa.selenium.By
    go to url
    webDriver.findElement(By.tagName("body")).getText
  }

  def cleanup() = close()
}
