package com.haneymaxwell.animerecommender

import scraper._
import scala.concurrent.duration._
import concurrent.ExecutionContext.Implicits.global

object Main {
  def main(args: Array[String]): Unit = {
    import scala.concurrent.{Future, Await}
    // Database seeding configuration
    DB.make()
    lazy val names = UsernameScraper.generateNames()
    lazy val queue = UsernameScraper.rateLimit(names._2, 25, 2 seconds)
    lazy val scrapers = Range(1, 5) map { _ => UsernameScraper.processUsernames(queue, names._1) }
    Await.result(Future.sequence(scrapers), 60 days)
  }
}
