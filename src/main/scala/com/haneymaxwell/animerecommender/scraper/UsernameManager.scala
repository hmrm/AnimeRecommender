package com.haneymaxwell.animerecommender.scraper

import Data._
import scala.concurrent._
import com.haneymaxwell.animerecommender.Util._
import scala.concurrent.ExecutionContext.Implicits.global

object UsernameManager {

  import java.util.concurrent.atomic.AtomicInteger

  lazy val blocksInFlight: AtomicInteger = new AtomicInteger(0)

  import com.haneymaxwell.animerecommender.scraper.QueueUtils.CompletableQueue

  def updateLeastRecent(queue: CompletableQueue[(Username, Gender)], n: Int): Future[Unit] = Future {
    println(s"Updating $n least recently updated users")
    DB.usernamesSortedByRecentness(n) foreach { user => blocking(queue.put(user)) }
  } escalate

  case class Block(get: Int)

  def getLastBlockStart(scraper: DriverManager, gender: Gender): Future[Int] = {
    import scala.util.matching.Regex
    lazy val lastPageRegex: Regex = """(?<=show=)[0-9]*(?=">Last)""".r
    lazy val page: Future[String] =  UsernameScraper.getResults(gender = gender, index = 0, scrape = scraper)
    page map { p =>
      val res = lastPageRegex.findFirstIn(p).get.toInt
      println(s"Detected last username block for gender $gender at $res")
      res
    }
  }

  def generateBlocks(last: Int,
                     gender: Gender,
                     queue: CompletableQueue[(Block, Gender)]): Future[Unit] = Future {
    (for {
      i <- Range.inclusive(0, last, 25)
      if !DB.blockPresent((Block(i), gender))
    } yield (Block(i), gender)) foreach { bg =>
      DB.addBlock(bg)
      blocking(queue.put(bg))
    }
  }

  def consumeBlock(blocks: CompletableQueue[(Block, Gender)],
                   scraper: DriverManager,
                   usernameQueue: CompletableQueue[(Username, Gender)]): Future[Unit] = {
    Future(blocking(blocks.take())) flatMap { case (block, gender) =>
      println(s"Generating names for $block $gender")
      println(s"${blocksInFlight.incrementAndGet()} blocks in flight")
      blocking(UsernameScraper.generateNames(scraper, usernameQueue, gender, block.get)) map { _ =>
        println(s"Finished generating names for $block $gender, ${blocksInFlight.decrementAndGet()} blocks in flight")
        DB.processBlock(block, gender)
      }
    } flatMap(_ => consumeBlock(blocks, scraper, usernameQueue))
  }

  def updateLeastRecentBlocks(blockQueue: CompletableQueue[(Block, Gender)],
                              n: Int,
                              scraper: DriverManager): Future[Unit] = Future {
    println(s"Updating $n old blocks")
    DB.blocksSortedByRecentness(n) foreach blocking(blockQueue.put(_))
  }

  def runBlocks(scraper: DriverManager,
                usernameQueue: CompletableQueue[(Username, Gender)],
                blockQueue: CompletableQueue[(Block, Gender)],
                gender: Gender,
                parallelism: Int): Unit = {
    def generateBlocksFromLast: Future[Unit] = getLastBlockStart(scraper, gender) flatMap { last => generateBlocks(last, gender, blockQueue) }

    def alternateGenerate(): Unit       = generateBlocksFromLast.escalate                             onSuccess {case x => alternateUpdate()}
    def alternateUpdate(): Unit         = updateLeastRecentBlocks(blockQueue, 1000, scraper).escalate onSuccess {case x => alternateUsernameUpdate()}
    def alternateUsernameUpdate(): Unit = updateLeastRecent(usernameQueue, 10000).escalate            onSuccess {case x => alternateGenerate()}

    Range(0, parallelism).par foreach { _ =>
      blocking(consumeBlock(blockQueue, scraper, usernameQueue))
    }

    alternateGenerate()
  }
}
