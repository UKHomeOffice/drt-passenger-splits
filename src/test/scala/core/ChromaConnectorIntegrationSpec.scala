package core

import java.nio.charset.StandardCharsets
import java.util.zip.{ZipEntry, ZipInputStream, ZipOutputStream}

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import awscala.s3.{S3Object, S3ObjectSummary, Bucket, S3}
import awscala.{BasicCredentialsProvider, Credentials}
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.typesafe.config.ConfigFactory
import org.specs2.mutable.SpecificationLike
import scala.StringBuilder
import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import java.io.{OutputStream, InputStream, File, FileOutputStream}
import java.util.zip

class S3IntegrationSpec extends TestKit(ActorSystem())
  with SpecificationLike {
  test =>

  import awscala._

  implicit val region = Region.EU_WEST_1
  //  val credentials = BasicCredentialsProvider(S3Secrets.accessKeyId, S3Secrets.s3Secret)
  implicit val s3 = S3()(region)
  //  implicit val s3 = S3(credentials)(region)
  //  val bucket: Bucket = s3.createBucket("unique-name-xxx")
  //  bucket.put("sample.txt", new java.io.File("sample.txt"))

  //  import ChromaParser.ChromaParserProtocol._
  //  import scala.concurrent.ExecutionContext.Implicits.global
  import system.dispatcher

  "We can connect to the S3 bucket" >> {
    println("starting")
    val bucketName: String = "drt-deveu-west-1"
    val result = Bucket(bucketName)
    val toList: Stream[Either[String, S3ObjectSummary]] = result.ls("")
    val objects = toList collect {
      //      case Left(keyName) => println("key:", keyName)
      case Right(so) =>
        Future {
          val obj: Option[S3Object] = result.get(so.getKey)
          println("obj is ", obj)
          obj.map {
            o =>
              val buffer = new Array[Byte](4096)
              val zis: ZipInputStream = new ZipInputStream(o.getObjectContent)
              val nextEntry: ZipEntry = zis.getNextEntry

              var ze: ZipEntry = zis.getNextEntry()
              var lb: List[(String, String, String)] = Nil
              def getZipEntry(ze: ZipEntry): String = {
                val sbuilder = new ArrayBuffer[Byte]()

                val fileName = ze.getName()
                var len: Int = zis.read(buffer)

                while (len > 0) {
                  sbuilder ++= buffer
                  len = zis.read(buffer)
                }
                val content: String = new String(sbuilder.toArray,StandardCharsets.UTF_8)
                (content)
              }
              while (ze != null) {
                lb = (so.getKey, nextEntry.getName, getZipEntry(ze)) :: lb

                ze = zis.getNextEntry()
              }

              zis.closeEntry()
              zis.close()
              lb
          }
        }
    }

    val fof: Future[Stream[Option[List[(String, String, String)]]]] = Future.sequence(objects.take(3))
    Await.ready(fof, Duration.Inf) map {
      (stream: Stream[Option[List[(String, String, String)]]]) =>
        stream map {
          case Some(l) => l.foreach{ file =>
            println(file._1)
            println(file._3)
          }
        }
    }
    false
  }
}
