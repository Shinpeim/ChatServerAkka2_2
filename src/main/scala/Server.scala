import akka.actor.SupervisorStrategy.Stop
import akka.actor.{OneForOneStrategy, Props, ActorRef, Actor}
import akka.io._
import akka.util.ByteString
import java.net.InetSocketAddress
import scala.concurrent.promise
import akka.event.Logging

class Server extends Actor{
  val roomService = context.actorOf(Props[RoomService])

  // サーバーソケットを管理してくれるアクターを作る。
  // 第一引数に self を指定することで、
  // サーバーソケットを管理してくれるアクターは
  // このアクターにメッセージを送ってくるようになる
  IO(Tcp)(context.system) ! Tcp.Bind(self, new InetSocketAddress("localhost", 9876))

  def receive: Receive = {
    // Bindに成功したときにserverSocketを管理してるactorから送られる
    case Tcp.Bound(localAddress) =>
      // serverSocketを管理してるアクターを保持しておく
      context.become(bound)

    // Bindに失敗したときにserverSocketを管理しているactorから送られる
    case Tcp.CommandFailed(_:Tcp.Bind) => context stop self
  }

  def bound: Receive = {
    // クライアントからの接続があったときに送られてくる
    case Tcp.Connected(remote, local) =>

      // クライアントとのIOをやってくれるIOワーカー
      val connectionActor = sender

      //pipelineを組み立てる
      val pipeLineStages = new ClientMessageStage >>
        new StringByteStringAdapter("UTF-8") >>
        new DelimiterFraming(1024, ByteString("\r\n")) >>
        new TcpReadWriteAdapter

      val p = promise[ActorRef]
      val f = p.future

      val init = TcpPipelineHandler.withLogger(Logging.getLogger(context.system, self), pipeLineStages)
      val client = context.actorOf(Props(new Client(roomService, init, f))) //ハンドラー。
      val pipelineWorker = context.actorOf(TcpPipelineHandler.props(init, connectionActor, client))

      p.success(pipelineWorker)

      // IOワーカーがpipelineワーカーにメッセージを送るように設定する
      connectionActor ! Tcp.Register(pipelineWorker)
  }

  override val supervisorStrategy = OneForOneStrategy() {
    case _ => Stop
  }
}
