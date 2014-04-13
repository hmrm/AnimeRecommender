package com.haneymaxwell.animerecommender.scraper

import Predef.{any2stringadd => _, _}
import java.util.concurrent.BlockingQueue
import scala.concurrent.blocking
import concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import com.haneymaxwell.animerecommender.Util._

object RatingsScraper {

  import Data._
  import com.haneymaxwell.animerecommender.scraper.QueueUtils.CompletableQueue

  def genUrl(username: Username): String =
    s"view-source:myanimelist.net/malappinfo.php?u=${username.get}&status=all&type=anime"

  def processName(name: Username, scrape: DriverManager): Future[String] = {
    println(s"Scraping for name $name")
    scrape source genUrl(name) map {
      str => str.split('\n').mkString("")
    } // I am not entirely sure why this is necessary, but it is
  }

  // HAAAAACK
  def genXmlRegex(tag: String) = s"""(?<=<span class="webkit-html-tag">&lt;$tag&gt;</span>)((?!<span class="webkit-html-tag">&lt;/$tag&gt;</span>).)*(?=<span class="webkit-html-tag">&lt;/$tag&gt;</span>)""".r

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

  def processUsernames(queue: CompletableQueue[(Username, Gender)], scrape: DriverManager): Future[Unit] = Future {
    println("Ready to process username")
    lazy val (user, gender) = blocking(queue.take())
    println(s"Processing $user")

    lazy val result: Future[String] = processName(user, scrape).escalate

    result onSuccess { case data =>
      lazy val results: (Map[AID, SeriesName], Map[AID, Rating]) = blocking(processData(data))

      results._1 foreach {
        case (aid, name) => blocking(DB.addName(aid, name))
      }

      results._2 foreach {
        case (aid, rating) => blocking(DB.addRating(user, aid, rating))
      }

      blocking(DB.addUsername(user, gender))
      blocking(DB.processUsername(user))
      Metrics.usernameProcessed.incrementAndGet()
    }

    processUsernames(queue, scrape)
    ()
  } escalate
}
