package com.haneymaxwell.animerecommender.scraper

object Data {
  case class Rating(get: Int)

  case class AID(get: Int)

  case class SeriesName(get: String)

  case class Username(get: String)

  case class Gender(toInt: Int)
  object Female extends Gender(2)
  object Male extends Gender(1)
}
