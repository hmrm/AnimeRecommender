package com.haneymaxwell.animerecommender.scraper

import org.scalatest.selenium.Chrome

import Predef.{any2stringadd => _, _}
import java.util.concurrent.{BlockingQueue, ArrayBlockingQueue}
import scala.concurrent.blocking
import concurrent.ExecutionContext.Implicits.global
import com.haneymaxwell.animerecommender.Util._

class DriverManager(nScrapers: Int, queueSize: Int = 4) {

  protected class Scraper extends Chrome {
    def source(url: String) = {
      go to url
      pageSource
    }

    def text(url: String) = {
      import org.openqa.selenium.By
      go to url
      webDriver.findElement(By.tagName("body")).getText
    }

    def cleanup() = quit()
  }

  import scala.concurrent.{Promise, Future}

  lazy val queue: BlockingQueue[ScrapeRequest] = new ArrayBlockingQueue(queueSize)

  lazy val scrapers = Range(0, nScrapers) map {_ => new Scraper}

  scrapers map { scrape => consume(scrape) }

  protected def consume(scrape: Scraper, nScrapes: Int = 0): Future[Unit] = Future {
    if (nScrapes > 20) {
      println("Maximum scrapes exhausted for scraper, restarting scraper")
      scrape.cleanup()
      lazy val newScrape = new Scraper
      consume(newScrape, 0)
    } else {
      import java.util.concurrent.TimeoutException
      import scala.concurrent.Await
      import scala.concurrent.duration._

      lazy val request = blocking(queue.take())

      def getResult(scrape: Scraper, timeout: Duration = 1.minute): (Scraper, String) =
        try {
          (scrape, Await.result(Future(request.fx(scrape)).escalate, timeout))
        } catch {
          case e: TimeoutException => {
            println("Timed out on request for scraper, restarting scraper")
            scrape.cleanup()
            lazy val newScrape = new Scraper
            getResult(newScrape, timeout * 2)
          }
        }

      lazy val result = getResult(scrape)
      request.promise.success(result._2)
      consume(result._1, nScrapes + 1)
    }
  }

  case class ScrapeRequest(promise: Promise[String], fx: Scraper => String)

  protected def dispatch(fx: Scraper => String): Future[String] = {
    lazy val promise = Promise[String]()
    (for {
      dispatched <- Future(blocking(queue.put(ScrapeRequest(promise, fx))))
      result     <- promise.future
    } yield result).escalate
  }

  def source(url: String): Future[String] = {
    dispatch(scrape => scrape source url)
  }

  def text  (url: String): Future[String] = {
    dispatch(scrape => scrape text   url)
  }

  def done() = scrapers map (scrape => scrape.cleanup())
}
