package com.haneymaxwell.animerecommender.scraper

import Predef.{any2stringadd => _, _}

object UsernameScraper {

  import scala.util.matching.Regex
  import scala.concurrent.Future
  import spray.client.pipelining._
  import spray.http.HttpRequest
  import akka.actor.ActorSystem
  import spray.http.HttpHeaders.`User-Agent`

  implicit lazy val system = ActorSystem()
  implicit lazy val ec = system.dispatcher

  case class Username(get: String)

  case class Gender(toInt: Int)
  object Female extends Gender(2)
  object Male   extends Gender(1)

  lazy val BaseUrl = "http://myanimelist.net/users.php?q="

  def genUrl(gender: Gender, index: Int) = s"$BaseUrl&g=${gender.toInt}&show=$index"

  lazy val NameExtractor: Regex = new Regex("""profile\/([^"]+)"\1<\/a><\/div>""", "name")

  // ARRRRRRRRRRRRRRRRRRRRRRGGGGGGGGGGGGGGGGGGGG they now are somehow detecting that this is a
  // robot, and blocking me (even though I'm obeying their robots.txt) ARRRRRRRRRGGGGGGGGGGG.
  // This is on hold until I figure out how to do screen scraping easily.
  lazy val pipeline: HttpRequest => Future[String] =
      sendReceive ~>
      unmarshal[String]

  def getResults(gender: Gender, index: Int): Future[String] = {
    pipeline(Get(genUrl(gender, index)))
  }

  def getNames(gender: Gender, index: Int): Future[Set[Username]] = {
    getResults(gender, index) map (NameExtractor.findAllIn(_).toSet.map(Username.apply))
  }
}
