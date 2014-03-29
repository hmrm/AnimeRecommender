package com.haneymaxwell.animerecommender.scraper

import java.util.concurrent.BlockingQueue
import Data._
import scala.concurrent.Future
import com.haneymaxwell.animerecommender.Util._
import scala.concurrent.ExecutionContext.Implicits.global

object UsernameManager {
  def updateLeastRecent(queue: BlockingQueue[(Username, Gender)], n: Int) = Future {
    println(s"Updating $n least recently updated users")
    DB.usernamesSortedByRecentness(n) foreach { user => queue.put(user) }
  } escalate

  def scrapeAndUpdate(scraper: DriverManager,
                      queue: BlockingQueue[(Username, Gender)],
                      last: Int = -100,
                      wasScrape: Boolean = true): Future[Unit] = Future {
    val current = Seq(Male, Female) map DB.nUsernamesProcessed reduce (_ + _)

    if (current - last < 10 && wasScrape) {
      println(s"Last username scraping run only generated ${current - last}, updating other users")

      updateLeastRecent(queue, 1000) onComplete { _ =>
        scrapeAndUpdate(scraper, queue, current, wasScrape = false)
      }
    } else {
      println(s"Last username scraping generated ${current - last}, continuing scraping")

      UsernameScraper.generateNames(scraper, queue) onComplete { _ =>
        scrapeAndUpdate(scraper, queue, current, wasScrape = true)
      }
    }
  }
}
