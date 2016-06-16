package core

import java.nio.charset.StandardCharsets
import java.util.zip.{ZipEntry, ZipInputStream, ZipOutputStream}

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import awscala.s3.{S3Object, S3ObjectSummary, Bucket, S3}
import awscala.{BasicCredentialsProvider, Credentials}
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.typesafe.config.ConfigFactory
import core.ChromaParser.ChromaSingleFlight
import org.specs2.mutable.SpecificationLike
import spray.json.{RootJsonFormat, DefaultJsonProtocol}
import scala.StringBuilder
import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Await
import scala.concurrent.Future
import spray.httpx.SprayJsonSupport._
import scala.concurrent.duration.Duration
import java.io.{OutputStream, InputStream, File, FileOutputStream}
import java.util.zip
import spray.json.DefaultJsonProtocol

import java.nio.charset.StandardCharsets
import java.util.zip.{ZipEntry, ZipInputStream, ZipOutputStream}

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import awscala.s3.{S3Object, S3ObjectSummary, Bucket, S3}
import awscala.{BasicCredentialsProvider, Credentials}
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.typesafe.config.ConfigFactory
import org.specs2.mutable.SpecificationLike
import spray.json.{RootJsonFormat, DefaultJsonProtocol}
import scala.StringBuilder
import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Await
import scala.concurrent.Future
import spray.httpx.SprayJsonSupport._
import scala.concurrent.duration.Duration
import java.io.{OutputStream, InputStream, File, FileOutputStream}
import java.util.zip

import scala.util.Try


object PassengerInfoParser {

  case class PassengerInfo(DocumentIssuingCountryCode: String)

  case class FlightPassengerInfoResponse(ArrivalPortCode: String, PassengerList: List[PassengerInfo])

  object FlightPassengerInfoProtocol extends DefaultJsonProtocol {
    implicit val passengerInfoConverter = jsonFormat1(PassengerInfo)
    implicit val flightPassengerInfoResponseConverter = jsonFormat2(FlightPassengerInfoResponse)
  }

}
