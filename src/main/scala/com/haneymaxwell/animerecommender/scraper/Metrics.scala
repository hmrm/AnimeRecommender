package com.haneymaxwell.animerecommender.scraper

object Metrics {

  import java.util.concurrent.atomic.AtomicLong
  import scala.concurrent.duration.FiniteDuration
  import scala.concurrent.ExecutionContext

  lazy val dbConflicts = new AtomicLong(0)
  lazy val usernameProcessed = new AtomicLong(0)
  lazy val newUsernameProcessed = new AtomicLong(0)
  lazy val rateLimitedQueuePops = new AtomicLong(0)
  lazy val scrapesDone = new AtomicLong(0)
  lazy val incidents = new AtomicLong(0)

  lazy val totalIncidents = new AtomicLong(0)
  lazy val totalScrapes = new AtomicLong(0)

  def report(metric: AtomicLong, message: String, interval: FiniteDuration, total: Option[AtomicLong] = None)(implicit ec: ExecutionContext) {
    import akka.actor.ActorSystem
    lazy val system = ActorSystem()
    system.scheduler.schedule(interval, interval) {
      lazy val result = metric.getAndSet(0)
      total foreach { atom =>
        lazy val totalResult = atom.addAndGet(result)
        println(s"[METRICS TOTALS] $message overall: $totalResult")
      }
      println(s"[METRICS] $message during last $interval: $result")
    }
  }

  def allMetrics(interval: FiniteDuration)(implicit ec: ExecutionContext) {
    report(dbConflicts, "Number of db lock conflicts", interval)
    report(usernameProcessed, "Number of usernames fully processed", interval)
    report(newUsernameProcessed, "Number of new usernames added to system", interval)
    report(rateLimitedQueuePops, "Number of rateLimitedQueuePops", interval)
    report(scrapesDone, "Number of scrapes done", interval, Some(totalScrapes))
    report(incidents, "Encountered incidents", interval, Some(totalIncidents))
  }
}
