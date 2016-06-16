package core

import java.nio.charset.StandardCharsets._
import java.util.zip.{ZipEntry, ZipInputStream}

import scala.collection.mutable.ArrayBuffer

object ZipUtils {
  case class UnzippedFileContent(filename: String, content: String)
  def unzipAllFilesInStream(unzippedStream: ZipInputStream): List[UnzippedFileContent] = {
    var ze: ZipEntry = unzippedStream.getNextEntry()
    var lb: List[UnzippedFileContent] = Nil

    while (ze != null) {
      val name: String = ze.getName
      val entry: String = ZipUtils.getZipEntry(unzippedStream)
      lb = UnzippedFileContent(name, entry) :: lb
      ze = unzippedStream.getNextEntry()
    }

    unzippedStream.closeEntry()
    unzippedStream.close()
    lb
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
