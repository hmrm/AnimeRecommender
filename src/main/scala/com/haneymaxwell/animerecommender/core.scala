package com.haneymaxwell.animerecommender

import scraper._
import scala.concurrent.duration._
import concurrent.ExecutionContext.Implicits.global
import Data._

object Main {
  def main(args: Array[String]): Unit = {
    import scala.concurrent.{Future, Await}
    import java.util.concurrent.ArrayBlockingQueue
    // Database seeding configuration

    DB.make()

    lazy val usernameQueue = new ArrayBlockingQueue[(Username, Gender)](1)
    lazy val scrape = new DriverManager(4)
    lazy val scrapers = Range(0, 4) map { _ => RatingsScraper.processUsernames(usernameQueue, scrape) }
    QueueUtils.report(Seq(("UsernameQueue", usernameQueue)), 5 seconds)
    UsernameManager.scrapeAndUpdate(scrape, usernameQueue)
    Await.result(Future.sequence(scrapers), 60 days)
  }
}
