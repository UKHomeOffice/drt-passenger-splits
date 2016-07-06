package s3

import java.io.InputStream
import java.util.Date
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.contrib.ZipInputStreamSource
import akka.stream.scaladsl.{FileIO, Source}
import akka.testkit.TestKit
import akka.util.ByteString
import com.mfglabs.commons.aws.s3.S3StreamBuilder
import core.{CoreActors, ZipUtils}
import core.ZipUtils.UnzippedFileContent
import org.specs2.mutable.{SpecificationLike, Specification}
import org.specs2.concurrent.ExecutionEnv
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import akka.stream.scaladsl._


class s3AkkaStreams extends TestKit(ActorSystem()) with SpecificationLike {
  test =>

  import com.mfglabs.commons.aws.s3._

  val bucket: String = "drt-deveu-west-1"
  val key = ""
  val prefix = ""
  val builder = S3StreamBuilder(new AmazonS3AsyncClient())
  // contains un-materialized composable Source / Flow / Sink
  implicit val flowMaterializer = ActorMaterializer()

  val fileStream: Source[ByteString, NotUsed] = builder.getFileAsStream(bucket, key)

  val multipartfileStream: Source[ByteString, NotUsed] = builder.getMultipartFileAsStream(bucket, prefix)

  //  someBinaryStream.via(
  //    builder.uploadStreamAsFile(bucket, key, chunkUploadConcurrency = 2)
  //  )
  //
  //  someBinaryStream.via(
  //    builder.uploadStreamAsMultipartFile(
  //      bucket,
  //      prefix,
  //      nbChunkPerFile = 10000,
  //      chunkUploadConcurrency = 2
  //    )
  //  )

  val ops = new builder.MaterializedOps(flowMaterializer) // contains materialized methods on top of S3Stream
  "given an ops" >> {
    //    "we can list files" in {
    //      val files: Future[Seq[(String, Date)]] = ops.listFiles(bucket)
    //      val res = Await.result(files, 2 seconds)
    //      println(res)
    //      res.nonEmpty
    //    }
    //    "can heyey unzip content of a file" in {
    //      val fileNameStream: Source[(String, Date), NotUsed] = builder.listFilesAsStream(bucket)
    //      val res: Future[Done] = fileNameStream
    //        .filterNot(_._1.startsWith("PRE"))
    //        .runWith(Sink.foreach {
    //          println
    //        })
    //      Await.result(res, 3 seconds)
    //      true
    //    }
    //    "flatMap demo" in {
    //      val result = List(1, 2, 3).map(_ * 2)
    //      println(result)
    //      true
    //    }

    "can unzip content of a file" in {
      val poller = new SimpleS3Poller with CoreActors {
        implicit def system = test.system

      }

//      val res = poller.zipFiles.runWith(Sink.foreach {
//        (unzippedFileContent) => println(unzippedFileContent)
//      })
      val res = poller.streamAllThis
//      Await.result(res, 500 seconds)
      Thread.sleep(3000)
      true
    }
  }

  val file: Future[ByteString] = ops.getFile(bucket, key)

}
