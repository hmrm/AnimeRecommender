package com.haneymaxwell.animerecommender.scraper

import Predef.{any2stringadd => _, _}

object UsernameScraper {

  import scala.util.matching.Regex
  import scala.concurrent.Future
  import org.scalatest.selenium.Chrome

  case class Username(get: String)

  case class Gender(toInt: Int)
  object Female extends Gender(2)
  object Male   extends Gender(1)

  lazy val BaseUrl = "http://myanimelist.net/users.php?q="

  def genUrl(gender: Gender, index: Int) = s"$BaseUrl&g=${gender.toInt}&show=$index"

  lazy val NameExtractor: Regex = new Regex("""profile/([^"]+)">\1</a></div>""", "name")

  class Scraper extends Chrome with (String => String) {
    def apply(url: String) = {
      go to url
      pageSource
    }

    def cleanup() = close()
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
