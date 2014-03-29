package com.haneymaxwell.animerecommender

import scraper._
import scala.concurrent.duration._
import concurrent.ExecutionContext.Implicits.global

object Main {
  def main(args: Array[String]): Unit = {
    import scala.concurrent.{Future, Await}
    // Database seeding configuration
    DB.make()
    lazy val scrape = new DriverManager(4)
    lazy val names = UsernameScraper.generateNames(scrape)
    lazy val queue = QueueUtils.rateLimit(names._2, 25, 2 seconds)
    lazy val scrapers = Range(0, 4) map { _ => RatingsScraper.processUsernames(queue, names._1, scrape) }
    Await.result(Future.sequence(scrapers), 60 days)
  }
}
