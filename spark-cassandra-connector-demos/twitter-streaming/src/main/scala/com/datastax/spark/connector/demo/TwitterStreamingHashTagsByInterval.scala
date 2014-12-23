package com.datastax.spark.connector.demo

import com.datastax.driver.scala.core.SomeColumns

import scala.util.matching.Regex
import org.apache.spark.storage.StorageLevel
import org.apache.spark.streaming.{Time, Seconds, StreamingContext}
import org.apache.spark.streaming.twitter.TwitterUtils
import org.joda.time.{DateTimeZone, DateTime}
import twitter4j.auth.Authorization
import com.datastax.spark.connector.streaming._

class TwitterStreamingHashTagsByInterval extends Serializable {

  def start(auth: Option[Authorization], ssc: StreamingContext, filters: Regex, keyspace: String, table: String): Unit = {

    val transform = (cruft: String) => filters.findAllIn(cruft).flatMap(_.stripPrefix("#"))

    val stream = TwitterUtils.createStream(ssc, auth, Nil, StorageLevel.MEMORY_ONLY_SER_2)

    /** Note that Cassandra is doing the sorting for you here. */
    stream.flatMap(_.getText.toLowerCase.split("""\s+"""))
      .map(transform)
      .countByValueAndWindow(Seconds(5), Seconds(5))
      .transform((rdd, time) => rdd.map { case (term, count) => (term, count, now(time))})
      .saveToCassandra(keyspace, table, SomeColumns("hashtag", "mentions", "interval"))

    ssc.checkpoint("./checkpoint")
    ssc.start()
    ssc.awaitTermination()
  }

  private def now(time: Time): String =
    new DateTime(time.milliseconds, DateTimeZone.UTC).toString("yyyyMMddHH:mm:ss.SSS")
}


