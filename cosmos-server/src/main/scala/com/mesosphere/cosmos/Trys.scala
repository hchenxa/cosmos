package com.mesosphere.cosmos

import com.twitter.util.Try

object Trys {

  def join[A, B](a: Try[A], b: Try[B]): Try[(A, B)] = {
    for {
      aa <- a
      bb <- b
    } yield { aa -> bb }
  }

  def join[A, B, C](a: Try[A], b: Try[B], c: Try[C]): Try[(A, B, C)] = {
    for {
      aa <- a
      bb <- b
      cc <- c
    } yield { (aa, bb, cc) }
  }
  
  def join[A, B, C, D](a: Try[A], b: Try[B], c: Try[C], d: Try[D]): Try[(A, B, C, D)] = {
    for {
      aa <- a
      bb <- b
      cc <- c
      dd <- d
    } yield { (aa, bb, cc, dd) }
  }

}
