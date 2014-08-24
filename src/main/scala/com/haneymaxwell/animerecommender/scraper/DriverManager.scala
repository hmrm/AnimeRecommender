package com.haneymaxwell.animerecommender.scraper

import java.util.concurrent.atomic.AtomicInteger

import akka.util.Timeout
import com.typesafe.scalalogging.slf4j.LazyLogging
import org.scalatest.selenium.Chrome

import Predef.{any2stringadd => _, _}
import java.util.concurrent.{TimeoutException, BlockingQueue, ArrayBlockingQueue, Executors}
import scala.concurrent.{ExecutionContext, blocking}
import com.haneymaxwell.animerecommender.Util._
import scala.concurrent.duration._
import akka.actor._
import akka.pattern.ask
import scala.concurrent._

import scala.util.Try

class DriverManager(nScrapers: Int) extends LazyLogging {
  implicit val ec = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  lazy val system = ActorSystem("my-system", defaultExecutionContext = Some(ec))
  lazy val scheduler = system.scheduler

  case class Source(url: String)
  case class Text(url:String)
  case object Cleanup

  class ScraperActor extends Actor {
    var underlying = new Scraper
    var scrapes = 0
    def receive = {
      case Source(url) => process(() => underlying.source(url))
      case Text(url)   => process(() => underlying.text(url))
      case Cleanup     => underlying.cleanup()
    }

    def process(work: () => String, timeout: Duration = 5.minutes, incapsulaBackoff: Duration = 10.seconds): Try[String] = {
      checkScrapes()
      Try {
        val result = Await.result(Future(work()), timeout)
        if (result.contains("Incapsula incident")) {
          replaceUnderlying()
          if (incapsulaBackoff < 1.hour) {
            Metrics.incidents.incrementAndGet()
            logger.info(s"Incapsula incident detected, backing off for $incapsulaBackoff")
            Thread.sleep(incapsulaBackoff.toMillis)
            process(work, timeout, incapsulaBackoff * 2).get
          } else {
            val msg = "Incapsula backoff exceeded one hour!"
            logger.error(msg)
            throw new Exception(msg)
          }
        } else {
          result
        }
      } recoverWith {
        case e: Throwable => {
          if (timeout < 30.minutes) {
            replaceUnderlying()
            logger.info(s"Failed request, retrying $e")
            process(work, timeout * 2, incapsulaBackoff)
          } else {
            val msg = s"Failed with too high timeout! $e"
            logger.error(msg)
            throw new Exception(msg)
          }
        }
      }
    }

    def checkScrapes() = {
      if (scrapes > 20) {
        replaceUnderlying()
        scrapes = 0
      }
    }

    def replaceUnderlying() = {
      underlying.cleanup()
      underlying = new Scraper
    }
  }

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

  lazy val scrapers = Range(0, nScrapers) map {_ => system.actorOf(Props(new ScraperActor))}


  case class ScrapeRequest(promise: Promise[String], fx: Scraper => String)

  val index = new AtomicInteger(0)

  protected def dispatch(msg: Any): Future[String] = {
    val idx = index.getAndIncrement % scrapers.length
    scrapers(idx).ask(msg)(Timeout(10.hours)).mapTo[Try[String]].map(_.get)
  }

  def source(url: String): Future[String] = {
    dispatch(Source(url))
  }

  def text  (url: String): Future[String] = {
    dispatch(Text(url))
  }

  def done() = {
    scrapers map (_ ! Cleanup)
  }
}
