import akka.io.{SymmetricPipePair, PipelineContext, SymmetricPipelineStage}
import akka.util.{ByteStringBuilder, ByteString}
import scala.annotation.tailrec

case class TwoLines(first: String, second: String)

class TwoLinesStage extends SymmetricPipelineStage[PipelineContext, TwoLines, ByteString] {
  def apply(ctx:PipelineContext) = new SymmetricPipePair[TwoLines, ByteString] {
    override def commandPipeline = {twoLines: TwoLines =>
      twoLines match {
        case TwoLines(first, second) =>
          val bb = new ByteStringBuilder
          bb.append(ByteString(first))
          bb.append(ByteString("\r\n"))
          bb.append(ByteString(second))
          ctx.singleCommand(bb.result)
      }
    }

    override def eventPipeline = {bytes: ByteString =>
      val seq = bytes.decodeString("UTF-8").split("\r\n")
      val twoLines = TwoLines(seq(0), seq(1))
      ctx.singleEvent(twoLines)
    }
  }
}

class ByteStringStage extends SymmetricPipelineStage[PipelineContext, ByteString, ByteString] {
  def apply(ctx: PipelineContext) = new SymmetricPipePair[ByteString, ByteString] {
    // まだ切りのいいところまで受信していない場合、このバッファに
    // データを貯めておく
    var buffer: Option[ByteString] = None;

    override def commandPipeline = {bs: ByteString =>
      // 二行分のByteStringの最後に改行を付与
      val bb = new ByteStringBuilder
      bb.append(bs)
      bb.append(ByteString("\r\n"))
      ctx.singleCommand(bb.result)
    }

    override def eventPipeline = {
      bs: ByteString => {
        val bytes: Option[ByteString] = buffer match {
          case None => Some(bs)
          case Some(buffered) => Some(buffered ++ bs)
        }

        // 得たデータを2行ずつ取り出す
        val (gots: Seq[ByteString], rest: Option[ByteString]) = extract(Seq.empty, bytes)

        // 「あまり」のデータをバッファに格納しておく
        buffer = rest
        gots.map(Left(_))
      }
    }

    @tailrec
    private def indexOfSecondCRLF(bytes: ByteString, crlfCount: Int= 0, byteCount: Int = 0):
      Option[Int] = {
      bytes match {
        case bytes if bytes.size < 2 => None // 見つからなかった
        case bytes if bytes.startsWith(ByteString("\r\n")) =>
          if (crlfCount == 1) Some(byteCount)
          else indexOfSecondCRLF(bytes.drop(2), crlfCount + 1, byteCount + 2)
        case bytes => indexOfSecondCRLF(bytes.drop(1),crlfCount, byteCount + 1)
      }
    }

    @tailrec
    private def extract(gots:Seq[ByteString], rest: Option[ByteString]):
      (Seq[ByteString],Option[ByteString]) = {
      rest match {
        case None => (gots, rest)
        case Some(bytes) => indexOfSecondCRLF(bytes) match {
          case None => (gots, rest)
          case Some(index) =>
            val newGots = gots ++ Seq(bytes.take(index))
            val newRest = Option(bytes.drop(index + 2))
            extract(newGots, newRest)
        }
      }
    }
  }
}
