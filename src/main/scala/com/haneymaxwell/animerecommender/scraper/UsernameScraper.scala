package com.haneymaxwell.animerecommender.scraper

import Predef.{any2stringadd => _, _}
import org.scalatest.selenium.Chrome
import java.util.concurrent.{BlockingQueue, ArrayBlockingQueue}
import scala.slick.driver.SQLiteDriver.simple._
import scala.concurrent.blocking
import concurrent.ExecutionContext.Implicits.global


object DB {
  import UsernameScraper.{AID, SeriesName, Username, Rating}
  lazy val db = Database.forURL("jdbc:sqlite:/tmp/ardb.db", driver = "org.sqlite.JDBC")

  class Usernames(tag: Tag) extends Table[(String, Boolean)](tag, "USERNAMES") {
    def username  = column[String]("USERNAME")
    def processed = column[Boolean]("PROCESSED")
    def * = (username, processed)
  }
  lazy val usernames = TableQuery[Usernames]

  class Names(tag: Tag) extends Table[(Int, String)](tag, "NAMES") {
    def anime = column[Int]("AID", O.PrimaryKey)
    def name  = column[String]("NAME")
    def * = (anime, name)
  }
  lazy val names = TableQuery[Names]

  class Ratings(tag: Tag) extends Table[(String, Int, Int)](tag, "RATINGS") {
    def user   = column[String]("UID")
    def anime  = column[Int]("AID")
    def rating = column[Int]("RATING")
    def * = (user, anime, rating)
  }
  lazy val ratings = TableQuery[Ratings]

  def make(): Unit = db withSession { implicit session =>
    import java.sql.SQLException

    Seq(ratings.ddl, names.ddl, usernames.ddl) foreach { table =>
      try {
        table.create
      } catch { case e: SQLException => () }
    }
  }

  def addName(aid: AID, name: SeriesName) = db withSession { implicit session =>
    if(!names.filter(_.anime === aid.get).exists.run) {
      println(s"Added name: $name for series $aid")
      names += (aid.get, name.get)
    }
  }

  def addRating(user: Username, aid: AID, rating: Rating) = db withSession { implicit session =>
    println(s"Added rating: $rating for anime: $aid for user $user")
    ratings += Tuple3(user.get, aid.get, rating.get)
  }

  def addUsername(user: Username) = db withSession { implicit session =>
    println(s"Added username: $user")
    usernames += (user.get, false)
  }

  def processUsername(user: Username) = db withSession { implicit session =>
    lazy val q = for { u <- usernames if u.username === user.get } yield u.processed
    q.update(true)
  }
}

object UsernameScraper {

  import scala.util.matching.Regex
  import scala.concurrent.Future
  import scala.concurrent.duration.{FiniteDuration, Duration}

  case class Username(get: String)

  case class Gender(toInt: Int)
  object Female extends Gender(2)
  object Male   extends Gender(1)

  lazy val BaseUrl = "http://myanimelist.net/users.php?q="

  def genUrl(gender: Gender, index: Int) = s"$BaseUrl&g=${gender.toInt}&show=$index"

  lazy val NameExtractor: Regex = new Regex("""profile/([^"]+)">\1</a></div>""", "name")

  case class GenerateNamesResult(femaleNames: Set[Username], maleNames: Set[Username])

  def getResults(gender: Gender, index: Int, scrape: DriverManager): Future[String] =
    scrape source genUrl(gender, index)

  def getNames(gender: Gender, index: Int, scrape: DriverManager): Future[Set[Username]] = {
    import scala.util.matching.Regex.Match
    getResults(gender, index, scrape) map (NameExtractor.findAllMatchIn(_).toSet.map((m: Match) => Username(m.group("name"))))
  }

  def generateNames(scrape: DriverManager, lastSuccessful: GenerateNamesResult = GenerateNamesResult(Set(), Set())): (Future[Unit], BlockingQueue[Username]) = {
    lazy val queue = new ArrayBlockingQueue[Username](100)
    def recur(lastSuccessful: GenerateNamesResult): Future[Unit] = {

      lazy val nFemale = lastSuccessful.femaleNames.size
      lazy val nMale   = lastSuccessful.maleNames.size

      lazy val femaleResult = getNames(Female, nFemale, scrape)
      lazy val maleResult   = getNames(Male,   nMale,   scrape)
      lazy val results = femaleResult zip maleResult

      results flatMap { case (newFemaleNames, newMaleNames) =>
        lazy val femaleNames = lastSuccessful.femaleNames ++ newFemaleNames
        lazy val maleNames   = lastSuccessful.maleNames   ++ newMaleNames

        (newFemaleNames -- lastSuccessful.femaleNames) foreach { name => DB.addUsername(name); blocking(queue.put(name))}
        (newMaleNames   -- lastSuccessful.maleNames)   foreach { name => DB.addUsername(name); blocking(queue.put(name)) }

        if ((femaleNames.size == nFemale) && (maleNames.size == nMale)) {
          Future(())
        } else {
          recur(GenerateNamesResult(femaleNames, maleNames))
        }
      }
    }
    (recur(lastSuccessful), queue)
  }

  def genUrl(username: Username): String =
    s"http://myanimelist.net/malappinfo.php?u=${username.get}&status=all&type=anime"

  def processName(name: Username, scrape: DriverManager): Future[String] = {
    scrape text genUrl(name) map { str => str.split('\n').mkString("") } // I am not entirely sure why this is necessary, but it is
  }

  // Note: it would be nicer just to parse this into XML, but it looks like it isn't adequately standards conforming
  def genXmlRegex(tag: String) = s"""(?<=<$tag>)((?!</$tag>).)*(?=</$tag>)""".r

  lazy val GetSeries = genXmlRegex("anime")
  lazy val GetName   = genXmlRegex("series_title")
  lazy val GetAID    = genXmlRegex("series_animedb_id")
  lazy val GetRating = genXmlRegex("my_score")

  case class Rating    (get: Int)
  case class AID       (get: Int)
  case class SeriesName(get: String)

  def processData(data: String): (Map[AID, SeriesName], Map[AID, Rating]) = {
    lazy val res: Seq[((AID, SeriesName), (AID, Rating))] = (GetSeries.findAllIn(data).toSeq map { case text =>
      for {
        name   <- GetName  .findFirstIn(text)
        aid    <- GetAID   .findFirstIn(text)
        rating <- GetRating.findFirstIn(text)
      } yield (AID(aid.toInt) -> SeriesName(name), AID(aid.toInt) -> Rating(rating.toInt))
    }).flatten

    (res.map(x => x._1).toMap, res.map(x => x._2).toMap)
  }

  def processUsernames(queue: BlockingQueue[Username], queueDone: Future[Unit], scrape: DriverManager): Future[Unit] = {
    if (queueDone.isCompleted) Future.successful(())
    else {
      Future { blocking(queue.take()) } flatMap { user =>
        processName(user, scrape) map { case data =>
          lazy val results = processData(data)
          results._1 foreach { case (aid, name)   => DB.addName  (aid, name)         }
          results._2 foreach { case (aid, rating) => DB.addRating(user, aid, rating) }
          DB.processUsername(user)
        } flatMap { _ => blocking{processUsernames(queue, queueDone, scrape)} }
      }
    }
  }

  def rateLimit[T](queue: BlockingQueue[T], buffer: Int, interval: FiniteDuration): BlockingQueue[T] = {
    import akka.actor.ActorSystem
    lazy val system = ActorSystem()
    lazy val scheduler = system.scheduler
    lazy val rateLimitedQueue = new ArrayBlockingQueue[T](buffer)
    scheduler.schedule(interval, interval) {
      blocking(rateLimitedQueue.put(blocking(queue.take())))
    }
    rateLimitedQueue
  }
}

class DriverManager(nScrapers: Int) {

  protected class Scraper extends Chrome {
    def source(url: String) = {
      go to url
      pageSource
    }

    def text(url: String) = {
      import org.openqa.selenium.By
      go to url
      webDriver.findElement(By.tagName("body")).getText
    }

    def cleanup() = quit()
  }

  import scala.concurrent.{Promise, Future}

  lazy val queue: BlockingQueue[ScrapeRequest] = new ArrayBlockingQueue(40)

  lazy val scrapers = Range(0, nScrapers) map {_ => new Scraper}

  scrapers map consume

  protected def consume(scrape: Scraper): Future[Unit] = Future {
    import scala.util.Try
    lazy val request = blocking(queue.take())
    request.promise.complete(Try(request.fx(scrape)))
    consume(scrape)
  }

  case class ScrapeRequest(promise: Promise[String], fx: Scraper => String)

  protected def dispatch(fx: Scraper => String): Future[String] = {
    lazy val promise = Promise[String]()
    for {
      dispatched <- Future { blocking(queue.put(ScrapeRequest(promise, fx))) }
      result     <- promise.future
    } yield result
  }

  def source(url: String): Future[String] = {
    dispatch(scrape => scrape source url)
  }

  def text  (url: String): Future[String] = {
    dispatch(scrape => scrape text   url)
  }

  def done() = scrapers map (scrape => scrape.cleanup())
}


