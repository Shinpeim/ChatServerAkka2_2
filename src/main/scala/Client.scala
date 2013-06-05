import akka.actor.{ActorRef, Actor}
import akka.io.{PipelineContext, PipelineFactory, Tcp}
import akka.util.ByteString
import scala.util.{Success}

class Client(socketActor:ActorRef) extends Actor {

  //pipelineを組み立てる
  val pipeLineStages =
    new TwoLinesStage >>
      new ByteStringStage

  //pipelineの「出入り口」を作る
  val pipeLineInjector = PipelineFactory.buildWithSinkFunctions(new PipelineContext {}, pipeLineStages)(
    self ! _, // pipelineに inject されて command となったものをハンドルするアクターを設定
    self ! _  // pipelineに inject されて event となったものをハンドルするアクターを設定
  )

  def receive: Receive = {
    // 以下,clientSocketを管理するActorから送られてくるイベント

    // データが送られてきたときに
    // 送られてくるメッセージ。
    // 中にByteStringが入っている
    case Tcp.Received(data: ByteString) =>
      // pipelineにinjectEventする
      println("Got ByteString from peer!")
      pipeLineInjector.injectEvent(data)

    // clientSocketが閉じられたときに
    // 送られてくるメッセージ
    case Tcp.PeerClosed =>
      context stop self


    // 以下、pipeLineから送られてくるイベント

    // pipeLineに injectEvent された byteString が TwoLines になって通知される
    case Success(twoLines: TwoLines) =>
      //そのままTwoLinesをPipelineにinjectCommandする
      pipeLineInjector.injectCommand(twoLines) //そのまま今度はパイプラインにコマンドを注入する

    // pipeLineにinjectCommand された TwoLinesがByteStringになって通知される
    case Success(bytes: ByteString) =>
      // socketActorにエコーバックをお願いする
      println("Echo backing!")
      socketActor ! Tcp.Write(bytes)

  }
}
