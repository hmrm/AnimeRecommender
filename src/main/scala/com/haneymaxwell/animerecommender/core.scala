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

    lazy val underlying = new ArrayBlockingQueue[(Username, Gender)](80)
    lazy val usernameQueue = new CompletableQueue[(Username, Gender)](underlying)
    lazy val scrape = new DriverManager(6)
    RatingsScraper.processUsernames(usernameQueue, scrape)
    QueueUtils.report(Seq(("UsernameQueue", underlying)), 5 seconds)
    UsernameManager.scrapeAndUpdate(scrape, usernameQueue, Female)
    UsernameManager.scrapeAndUpdate(scrape, usernameQueue, Male)

    scheduler.schedule(55.days, 55.days) {
      println("Beginning shutdown")
      Future { blocking(Thread.sleep(1000000)); println("Expired graceful shutdown timeout, shutting down"); System.exit(0) }
      println("Draining username queue for shutdown")
      usernameQueue.finish()
      println("Username queue drained, shutting down scrapers for shutdown")
      scrape.done()
      println("Scrapers shut down, delaying 100 seconds to allow remaining database queries to finish")
      Thread.sleep(100000)
      println("Shutdown delay over, assuming safe to shut down")
      System.exit(0)
    }
  }
}
