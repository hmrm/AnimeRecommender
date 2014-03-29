package com.haneymaxwell.animerecommender.scraper

import scala.slick.driver.SQLiteDriver.simple._

object DB {

  import Data._

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
