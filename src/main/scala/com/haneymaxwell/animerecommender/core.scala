package com.haneymaxwell.animerecommender

import scraper._
import scala.concurrent.duration._
import concurrent.ExecutionContext.Implicits.global
import akka.actor.ActorSystem
import Data._

object Main {
  def main(args: Array[String]): Unit = {
    import QueueUtils.blockingQueueToQueueLike
    import scala.concurrent._
    import java.util.concurrent.ArrayBlockingQueue
    import com.haneymaxwell.animerecommender.scraper.QueueUtils.CompletableQueue
    import com.haneymaxwell.animerecommender.scraper.UsernameManager.Block
    // Database seeding configuration
    lazy val scheduler = ActorSystem().scheduler

    DB.make()

    lazy val underlying = new ArrayBlockingQueue[(Username, Gender)](200)
    lazy val usernameQueue = new CompletableQueue[(Username, Gender)](underlying)
    lazy val scrape = new DriverManager(5)
    RatingsScraper.processUsernames(usernameQueue, scrape)

    lazy val blockUnderlying = new ArrayBlockingQueue[(Block, Gender)](2)
    lazy val blockQueue = new CompletableQueue(blockUnderlying)

    QueueUtils.report(Seq(("UsernameQueue", underlying), ("BlockQueue", blockUnderlying)), 100 seconds)

    Metrics.allMetrics(1.minute)

    UsernameManager.runBlocks(scrape, usernameQueue, blockQueue, Male, 2)
    UsernameManager.runBlocks(scrape, usernameQueue, blockQueue, Female, 2)

/*    sys.addShutdownHook {
      println("Beginning shutdown")
      Future { blocking(Thread.sleep(1000000)); println("Expired graceful shutdown timeout, shutting down"); System.exit(0) }
      println("Draining blockQueue for shutdown")
      blockQueue.finish()
      println("Draining username queue for shutdown")
      usernameQueue.finish()
      println("Username queue drained, shutting down scrapers for shutdown")
      scrape.done()
      println("Scrapers shut down, delaying 100 seconds to allow remaining database queries to finish")
      Thread.sleep(100000)
      println("Shutdown delay over, assuming safe to shut down")
    }*/
  }
}
