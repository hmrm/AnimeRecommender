package com.haneymaxwell.animerecommender.scraper

import scala.slick.driver.SQLiteDriver.simple._
import java.sql.SQLException
import java.util.concurrent.BlockingQueue

object DB {

  import Data._

  lazy val db = Database.forURL("jdbc:sqlite:/home/hmrm/ardb.db", driver = "org.sqlite.JDBC")

  class Usernames(tag: Tag) extends Table[(String, Int, Long)](tag, "USERNAMES") {
    def username  = column[String]("USERNAME", O.PrimaryKey, O.DBType("TEXT"))
    def gender = column[Int]("GENDER")
    def lastProcessed = column[Long]("PROCESSED")
    def * = (username, gender, lastProcessed)
  }
  lazy val usernames = TableQuery[Usernames]

  class Names(tag: Tag) extends Table[(Int, String)](tag, "NAMES") {
    def anime = column[Int]("AID", O.PrimaryKey)
    def name  = column[String]("NAME", O.DBType("TEXT"))
    def * = (anime, name)
  }
  lazy val names = TableQuery[Names]

  class Ratings(tag: Tag) extends Table[(Int, String, Int, Int)](tag, "RATINGS") {
    def hash   = column[Int]("HASH", O.PrimaryKey)
    def user   = column[String]("UID", O.DBType("TEXT"))
    def anime  = column[Int]("AID")
    def rating = column[Int]("RATING")
    def * = (hash, user, anime, rating)
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

  def ignoreUnique[A](f: A => Unit): A => Unit = { a =>
    try { f(a) }
    catch { case e: SQLException if e.getMessage.contains("unique") => () }
  }

  def retryForLocked[A, B](delay: Int)(f: A => B): A => B = { a =>
    try { synchronized(f(a)) }
    catch {
      case e: SQLException if e.getMessage.contains("locked") => { Thread.sleep(1); retryForLocked(delay * 2)(f)(a) }
    }
  }

  lazy val addName: ((AID, SeriesName)) =>  Unit = retryForLocked(1) { ignoreUnique { case (aid, name) =>
    db withSession { implicit session =>
        names += (aid.get, name.get)
        println(s"Added name: $name for series $aid")
    }
  }}

  lazy val addRating: ((Username, AID, Rating)) => Unit = retryForLocked(1) { case (user, aid, rating) =>
    db withSession {
      implicit session =>
        lazy val hash: Int = user.hashCode() ^ aid.hashCode()
        try {
          ratings += Tuple4(hash, user.get, aid.get, rating.get)
          // println(s"Added rating: $rating for anime: $aid for user $user")
        } catch {
          case e: SQLException if e.getMessage.contains("unique") => try {
            lazy val q = for {r <- ratings if r.hash === hash} yield r.rating
            q.update(rating.get)
            // println(s"Updated rating $aid $user to $rating")
          }
        }
    }
  }

  lazy val addUsername: ((Username, Gender)) => Unit = retryForLocked(1) { ignoreUnique { case ((user, gender)) =>
    db withSession { implicit session =>
      usernames +=(user.get, gender.toInt, 0); println(s"Added username: $user")
    }
  }}

  lazy val usernamePresent: Username => Boolean = retryForLocked(1) { user =>
    db withSession { implicit session =>
      usernames.filter(_.username === user.get).exists.run
    }
  }

  lazy val processUsername: Username => Unit = retryForLocked(1) { user =>
    db withSession { implicit session =>
      lazy val q = for {u <- usernames if u.username === user.get} yield u.lastProcessed
      q.update(System.currentTimeMillis())
    }
  }

  lazy val nUsernamesProcessed: Gender => Int = retryForLocked(1) { gender =>
    db withSession { implicit session =>
      lazy val q = for {u <- usernames if u.gender === gender.toInt} yield u
      q.list.size
    }
  }

  /** NOTE: Least recent will be first */
  lazy val usernamesSortedByRecentness: Int => Seq[(Username, Gender)] = retryForLocked(1) { n =>
    db withSession { implicit session =>
      usernames.sortBy(_.lastProcessed).map(u => (u.username, u.gender)).take(n).run.map {
        case (username, gender) => (Username(username), Gender(gender))
      }
    }
  }
}