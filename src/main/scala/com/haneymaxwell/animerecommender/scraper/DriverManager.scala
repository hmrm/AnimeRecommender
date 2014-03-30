package com.haneymaxwell.animerecommender.scraper

import org.scalatest.selenium.Chrome

import Predef.{any2stringadd => _, _}
import java.util.concurrent.{BlockingQueue, ArrayBlockingQueue}
import scala.concurrent.blocking
import concurrent.ExecutionContext.Implicits.global
import com.haneymaxwell.animerecommender.Util._
import scala.concurrent.duration._

class DriverManager(nScrapers: Int, queueSize: Int = 2) {

  protected class Scraper extends Chrome {
    def source(url: String) = blocking {
      go to url
      pageSource
    }

    def text(url: String) = blocking {
      import org.openqa.selenium.By
      go to url
      webDriver.findElement(By.tagName("body")).getText
    }

    def cleanup() = quit()
  }

  import scala.concurrent.{Promise, Future}

  lazy val queue: BlockingQueue[ScrapeRequest] = new ArrayBlockingQueue(queueSize)

  QueueUtils.report(Seq(("DriverQueue", queue)), 5 seconds)

  lazy val scrapers = Range(0, nScrapers) map {_ => new Scraper}

  scrapers.zipWithIndex map { case (scrape, i) => consume(scrape, i) }

  protected def consume(scrape: Scraper, index: Int, nScrapes: Int = 0): Future[Unit] = Future {
    if (nScrapes > 20) {
      println("Maximum scrapes exhausted for scraper, restarting scraper")
      scrape.cleanup()
      lazy val newScrape = new Scraper
      consume(newScrape, 0)
    } else {
      import java.util.concurrent.TimeoutException
      import scala.concurrent.Await
      import scala.concurrent.duration._

      // println(s"Scraper $index ready to process")
      val request = blocking(queue.take())
      // println(s"Scraper $index got request")

      def getResult(scrape: Scraper, timeout: Duration = 1.minute): (Scraper, String) =
        try {
          (scrape, Await.result(
            Future{ /* println(s"Scraper $index running scraping task") ;*/ request.fx(scrape) }.escalate, timeout))
        } catch {
          case e: TimeoutException => {
            println(s"Timed out on request for scraper $index, restarting scraper")
            scrape.cleanup()
            lazy val newScrape = new Scraper
            getResult(newScrape, timeout * 2)
          }
        }

      lazy val result = getResult(scrape)
      request.promise.success(result._2)
      // println(s"Scraper $index completed request")
      consume(result._1, index, nScrapes + 1)
    }
  }

  case class ScrapeRequest(promise: Promise[String], fx: Scraper => String)

  protected def dispatch(fx: Scraper => String): Future[String] = {
    lazy val promise = Promise[String]()
    blocking(queue.put(ScrapeRequest(promise, fx)))
    promise.future.escalate
  }

  def source(url: String): Future[String] = {
    dispatch(scrape => scrape source url)
  }

  def text  (url: String): Future[String] = {
    dispatch(scrape => scrape text   url)
  }

  def done() = scrapers map (scrape => scrape.cleanup())
}
