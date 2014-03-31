package com.haneymaxwell.animerecommender.scraper

import java.util.concurrent.BlockingQueue
import Data._
import scala.concurrent._
import com.haneymaxwell.animerecommender.Util._
import scala.concurrent.ExecutionContext.Implicits.global

object UsernameManager {

  import com.haneymaxwell.animerecommender.scraper.QueueUtils.CompletableQueue

  def updateLeastRecent(queue: CompletableQueue[(Username, Gender)], n: Int) = Future {
    println(s"Updating $n least recently updated users")
    DB.usernamesSortedByRecentness(n) foreach { user => blocking(queue.put(user)) }
  } escalate

  def scrapeAndUpdate(scraper: DriverManager,
                      queue: CompletableQueue[(Username, Gender)],
                      gender: Gender,
                      last: Int = -100,
                      wasScrape: Boolean = true,
                      modifierBumps: Int = 0,
                      maxModifierBumps: Int = 30,
                      modifier: Int = 0): Future[Unit] = Future {
    val current = DB.nUsernamesProcessed(gender)

    val start = current + modifier

    if (current - last < 10 && wasScrape && (modifierBumps >= maxModifierBumps)) {
      println(s"Last username scraping run for $gender only generated ${current - last}, and modifier bumps exhausted, updating other users")

      updateLeastRecent(queue, 500) onComplete { _ =>
        blocking(scrapeAndUpdate(scraper, queue, gender, current, wasScrape = false, modifierBumps = modifierBumps, modifier = modifier))
      }
    } else if (current - last < 10 && wasScrape) {
      println(s"Last username scraping run for $gender only generated ${current - last}, bumping modifier for $modifierBumps time")
      UsernameScraper.generateNames(scraper, queue, gender, start + 10) onComplete {
        _ =>
          blocking(scrapeAndUpdate(scraper, queue, gender, current, wasScrape = true, modifierBumps = modifierBumps + 1, modifier = modifier + 10))
      }
    } else {
      println(s"Last username scraping for $gender generated ${current - last}, continuing scraping")

      UsernameScraper.generateNames(scraper, queue, gender, current) onComplete { _ =>
        blocking(scrapeAndUpdate(scraper, queue, gender, current, wasScrape = true, modifierBumps = modifierBumps, modifier = modifier))
      }
    }
  }
}
