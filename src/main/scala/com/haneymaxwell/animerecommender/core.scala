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

    lazy val usernameQueue = new ArrayBlockingQueue[(Username, Gender)](40)
    lazy val scrape = new DriverManager(6)
    lazy val wontStop = RatingsScraper.processUsernames(usernameQueue, scrape)
    QueueUtils.report(Seq(("UsernameQueue", usernameQueue)), 5 seconds)
    UsernameManager.scrapeAndUpdate(scrape, usernameQueue)
    Await.result(wontStop, 6000 days)
  }
}
