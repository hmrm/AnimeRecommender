package com.haneymaxwell.animerecommender.scraper

import Predef.{any2stringadd => _, _}
import java.util.concurrent.{BlockingQueue, ArrayBlockingQueue}
import scala.concurrent.blocking
import concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration

object QueueUtils {
  def rateLimit[T](queue: BlockingQueue[T], buffer: Int, interval: FiniteDuration): BlockingQueue[T] = {
    import akka.actor.ActorSystem
    lazy val system = ActorSystem()
    lazy val scheduler = system.scheduler
    lazy val rateLimitedQueue = new ArrayBlockingQueue[T](buffer)
    scheduler.schedule(interval, interval) {
      blocking(rateLimitedQueue.put(blocking(queue.take())))
    }
    rateLimitedQueue
  }
}
