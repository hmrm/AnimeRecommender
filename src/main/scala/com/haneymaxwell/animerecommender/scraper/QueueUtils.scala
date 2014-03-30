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

  def report(queues: Traversable[(String, BlockingQueue[_])], interval: FiniteDuration): Unit = {
    import akka.actor.ActorSystem
    lazy val system = ActorSystem()
    lazy val scheduler = system.scheduler

    scheduler.schedule(interval, interval) {
      queues foreach { case (name, queue) =>
        println(s"Queue $name has remaining capacity ${queue.remainingCapacity()} with ${queue.size()} items currently queued ")
      }
    }
  }

  class CompletableQueue[A](underlying: BlockingQueue[A]) {

    import java.util.concurrent.locks.ReentrantReadWriteLock

    private val lock = new ReentrantReadWriteLock(true)

    private def read[A](a: => A): A = {
      lock.readLock().lock()
      val res = a
      lock.readLock().unlock()
      res
    }

    def put(a: A) = read(underlying.put(a))

    def take(): A = underlying.take()

    def finish() = {
      lock.writeLock().lock()
      while(!underlying.isEmpty()) {
        println("Draining queue not yet empty, delaying 10 seconds")
        blocking(Thread.sleep(10000))
      }
    }
  }
}
