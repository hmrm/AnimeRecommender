package com.haneymaxwell.animerecommender

import scala.concurrent.Future

import scala.concurrent.ExecutionContext.Implicits.global

object Util {
  case class FutureWithEscalation[A](escalate: Future[A]) {
    escalate onFailure { case e => throw e }
  }

  implicit object IntellijIsAnnoying

  implicit def toFuture[A](eu: FutureWithEscalation[A]): Future[A] = eu.escalate

  implicit def toFutureWithEscalation[A](f: Future[A]): FutureWithEscalation[A] = FutureWithEscalation(f)
}
