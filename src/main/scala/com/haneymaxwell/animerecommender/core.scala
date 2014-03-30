package com.haneymaxwell.animerecommender

import scraper._
import scala.concurrent.duration._
import concurrent.ExecutionContext.Implicits.global
import akka.actor.ActorSystem
import Data._

object Main {
  def main(args: Array[String]): Unit = {
    import scala.concurrent._
    import java.util.concurrent.ArrayBlockingQueue
    import com.haneymaxwell.animerecommender.scraper.QueueUtils.CompletableQueue
    // Database seeding configuration
    lazy val scheduler = ActorSystem().scheduler

    DB.make()

    lazy val underlying = new ArrayBlockingQueue[(Username, Gender)](40)
    lazy val usernameQueue = new CompletableQueue[(Username, Gender)](underlying)
    lazy val scrape = new DriverManager(5)
    RatingsScraper.processUsernames(usernameQueue, scrape)
    QueueUtils.report(Seq(("UsernameQueue", underlying)), 5 seconds)
    UsernameManager.scrapeAndUpdate(scrape, usernameQueue)

    scheduler.schedule(4.hours, 4.hours) {
      Future { blocking(Thread.sleep(1000000)); System.exit(0) }
      usernameQueue.finish()
      scrape.done()
      Thread.sleep(100000)
      System.exit(0)
    }
  }
}
