package core

import java.nio.charset.StandardCharsets._
import java.util.zip.{ZipEntry, ZipInputStream}

import scala.collection.mutable.ArrayBuffer

object ZipUtils {

  case class UnzippedFileContent(filename: String, content: String)

  def unzipAllFilesInStream(unzippedStream: ZipInputStream): Stream[UnzippedFileContent] = {
    try {
      unzipAllFilesInStream(unzippedStream, unzippedStream.getNextEntry)
    } finally {
      unzippedStream.closeEntry()
      unzippedStream.close()
    }
  }

  def unzipAllFilesInStream(unzippedStream: ZipInputStream, ze: ZipEntry): Stream[UnzippedFileContent] = {
    if (ze == null) {
      Stream.empty
    }
    else {
      val name: String = ze.getName
      val entry: String = ZipUtils.getZipEntry(unzippedStream)
      UnzippedFileContent(name, entry) #:: unzipAllFilesInStream(unzippedStream, unzippedStream.getNextEntry)
    }
  }

  def getZipEntry(zis: ZipInputStream): String = {
    val buffer = new Array[Byte](4096)
    val stringBuffer = new ArrayBuffer[Byte]()
    var len: Int = zis.read(buffer)

    while (len > 0) {
      stringBuffer ++= buffer.take(len)
      len = zis.read(buffer)
    }
    val content: String = new String(stringBuffer.toArray, UTF_8)
    (content)
  }
}
