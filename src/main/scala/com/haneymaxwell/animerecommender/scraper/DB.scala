package com.haneymaxwell.animerecommender.scraper

import scala.slick.driver.SQLiteDriver.simple._
import java.sql.SQLException

object DB {

  import Data._

  lazy val db = Database.forURL("jdbc:sqlite:/tmp/ardb.db", driver = "org.sqlite.JDBC")

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

  def addName(aid: AID, name: SeriesName): Unit = db withSession { implicit session =>
    try {
      names +=(aid.get, name.get)
      println(s"Added name: $name for series $aid")
    } catch {
      case e: SQLException if e.getMessage.contains("unique") => () //println(s"$name already present, not adding")
      case e: SQLException if e.getMessage.contains("locked") => addName(aid, name)
    }
  }

  def addRating(user: Username, aid: AID, rating: Rating): Unit = db withSession { implicit session =>
    lazy val hash: Int = user.hashCode() ^ aid.hashCode()
    try {
      ratings += Tuple4(hash, user.get, aid.get, rating.get)
      // println(s"Added rating: $rating for anime: $aid for user $user")
    } catch {
      case e: SQLException if e.getMessage.contains("unique") => try {
        lazy val q = for { r <- ratings if r.hash === hash } yield r.rating
        q.update(rating.get)
        // println(s"Updated rating $aid $user to $rating")
      } catch { case e: SQLException if e.getMessage.contains("locked") => addRating(user, aid, rating) }
      case e: SQLException if e.getMessage.contains("locked") => addRating(user, aid, rating)
    }
  }

  def addUsername(user: Username, gender: Gender): Unit = db withSession { implicit session =>
    try { usernames +=(user.get, gender.toInt, 0); println(s"Added username: $user") }
    catch {
      case e: SQLException if e.getMessage.contains("unique") => {
        println(s"$user already present, not adding")
      }
      case e: SQLException if e.getMessage.contains("locked") => addUsername(user, gender)
    }
  }

  def usernamePresent(user: Username): Boolean = db withSession { implicit session =>
    try { usernames.filter(_.username === user.get).exists.run }
    catch { case e: SQLException if e.getMessage.contains("locked") => usernamePresent(user) }
  }

  def processUsername(user: Username): Unit = db withSession { implicit session =>
    try {
      lazy val q = for { u <- usernames if u.username === user.get } yield u.lastProcessed
      q.update(System.currentTimeMillis())
    } catch { case e: SQLException if e.getMessage.contains("locked") => processUsername(user) }
  }

  def nUsernamesProcessed(gender: Gender): Int = db withSession { implicit session =>
    try {
      lazy val q = for {u <- usernames if u.gender === gender.toInt} yield u
      q.list.size
    } catch { case e: SQLException if e.getMessage.contains("locked") => nUsernamesProcessed(gender) }
  }

  /** NOTE: Least recent will be first */
  def usernamesSortedByRecentness(n: Int): Seq[(Username, Gender)] = db withSession { implicit session =>
    try {
      usernames.sortBy(_.lastProcessed).map(u => (u.username, u.gender)).take(n).run.map {
        case (username, gender) => (Username(username), Gender(gender))
      }
    } catch { case e: SQLException if e.getMessage.contains("locked") => usernamesSortedByRecentness(n) }
  }
}
