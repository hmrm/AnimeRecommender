package com.haneymaxwell.animerecommender.scraper

import Predef.{any2stringadd => _, _}
import java.util.concurrent.BlockingQueue
import scala.concurrent.blocking
import concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import com.haneymaxwell.animerecommender.Util._

object RatingsScraper {

  import Data._

  def genUrl(username: Username): String =
    s"http://myanimelist.net/malappinfo.php?u=${username.get}&status=all&type=anime"

  def processName(name: Username, scrape: DriverManager): Future[String] = {
    scrape text genUrl(name) map {
      str => str.split('\n').mkString("")
    } // I am not entirely sure why this is necessary, but it is
  }

  // Note: it would be nicer just to parse this into XML, but it looks like it isn't adequately standards conforming
  def genXmlRegex(tag: String) = s"""(?<=<$tag>)((?!</$tag>).)*(?=</$tag>)""".r

  lazy val GetSeries = genXmlRegex("anime")
  lazy val GetName   = genXmlRegex("series_title")
  lazy val GetAID    = genXmlRegex("series_animedb_id")
  lazy val GetRating = genXmlRegex("my_score")



  def processData(data: String): (Map[AID, SeriesName], Map[AID, Rating]) = {
    lazy val res: Seq[((AID, SeriesName), (AID, Rating))] = (GetSeries.findAllIn(data).toSeq map {
      case text =>
        for {
          name <- GetName.findFirstIn(text)
          aid <- GetAID.findFirstIn(text)
          rating <- GetRating.findFirstIn(text)
        } yield (AID(aid.toInt) -> SeriesName(name), AID(aid.toInt) -> Rating(rating.toInt))
    }).flatten

    (res.map(x => x._1).toMap, res.map(x => x._2).toMap)
  }

  def processUsernames(queue: BlockingQueue[(Username, Gender)], scrape: DriverManager): Future[Unit] = Future {
    lazy val (user, gender) = blocking(queue.take())

    processName(user, scrape) map { data =>

      lazy val results: (Map[AID, SeriesName], Map[AID, Rating]) = blocking(processData(data))

      results._1 foreach {
        case (aid, name) => DB.addName(aid, name)
      }

      results._2 foreach {
        case (aid, rating) => DB.addRating(user, aid, rating)
      }

      DB.addUsername(user, gender)
      DB.processUsername(user)

    } escalate

    processUsernames(queue, scrape)
    ()
  } escalate
}
