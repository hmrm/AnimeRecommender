package com.haneymaxwell.animerecommender.scraper

import Predef.{any2stringadd => _, _}
import java.util.concurrent.{BlockingQueue, ArrayBlockingQueue}
import scala.concurrent.blocking
import concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object QueueUtils {
  def rateLimit[T](queue: BlockingQueue[T], buffer: Int, interval: FiniteDuration): QueueLike[T] = {
    import akka.actor.ActorSystem
    lazy val system = ActorSystem()
    lazy val scheduler = system.scheduler
    lazy val rateLimitedQueue = new ArrayBlockingQueue[T](buffer)
    report(Seq(("RateLimitedOutQueue",rateLimitedQueue)), 100.seconds)
    scheduler.schedule(interval, interval) {
      val itemOption = Option(queue.poll())
      itemOption map { item =>
        val res = rateLimitedQueue.offer(item)
        if (res) Metrics.rateLimitedQueuePops.incrementAndGet()
        else blocking(queue.put(item))
      }
    }
    new QueueLike[T] {
      def put(t: T) = blocking(queue.put(t))
      def take(): T = blocking(rateLimitedQueue.take())
      def isEmpty(): Boolean = rateLimitedQueue.isEmpty() && queue.isEmpty()
    }
  }

  def report(queues: Traversable[(String, BlockingQueue[_])], interval: FiniteDuration): Unit = {
    import akka.actor.ActorSystem
    lazy val system = ActorSystem()
    lazy val scheduler = system.scheduler
    scheduler.schedule(interval, interval) {
      queues foreach { case (name, queue) =>
        println(s"[QUEUE CAPACITY] Queue $name has remaining capacity ${queue.remainingCapacity()} with ${queue.size()} items currently queued ")
      }
    }
  }

  trait QueueLike[A] {
    def put(a: A): Unit
    def take(): A
    def isEmpty(): Boolean
  }

  implicit def blockingQueueToQueueLike[A](b: BlockingQueue[A]) = {
    new QueueLike[A] {
      def put(a: A) = b.put(a)
      def take() = b.take()
      def isEmpty() = b.isEmpty()
    }
  }

  class CompletableQueue[A](underlying: QueueLike[A]) {

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

    def block(interval: FiniteDuration) = {
      import akka.actor.ActorSystem
      lazy val system = ActorSystem()
      lazy val scheduler = system.scheduler
      lock.writeLock().lock()
      println(s"Queue locked for $interval")
      scheduler.scheduleOnce(interval) {
        println("Unlocking queue")
        lock.writeLock().unlock()

      }
    }
  }
}
