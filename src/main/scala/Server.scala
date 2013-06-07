import akka.actor.{Props, ActorRef, Actor}
import akka.event.NoLogging
import akka.io.{TcpReadWriteAdapter, TcpPipelineHandler, IO, Tcp}
import java.net.InetSocketAddress

class Server extends Actor{

  // サーバーソケットを管理してくれるアクターを作る。
  // 第一引数に self を指定することで、
  // サーバーソケットを管理してくれるアクターは
  // このアクターにメッセージを送ってくるようになる
  IO(Tcp)(context.system) ! Tcp.Bind(self, new InetSocketAddress("localhost", 9876))

  var serverSockSender:ActorRef = null

  def receive: Receive = {
    // Bindに成功したときにserverSocketを管理してるactorから送られる
    case Tcp.Bound(localAddress) =>
      // serverSocketを管理してるアクターを保持しておく
      serverSockSender = sender
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
      val pipeLineStages =
        new TwoLinesStage >>
          new ByteStringStage >>
          new TcpReadWriteAdapter

      // pipelineワーカーをつくる。
      // このpipelineワーカーの構成要素は、
      //   * pipeline
      //   * IOワーカー
      //   * ハンドラー
      // である。
      // pipelineワーカーの中では以下のような仕事が行われる
      //   * IOワーカーのメッセージ(Tcp.ReceiveなどのTcp.Event) を
      //     pipeline のイベント側に流し込む(そのためpipelineの一番下は
      //     Tcp.Eventをイベントとして受け取れるようになっていなければならない。)
      //     TcpReadWriteAdapterはTcp.Eventを受け取りByteStringに変換してくれるので、
      //     今回はそれを使っている。
      //
      //   * パイプラインから出てきたイベントをEventというcase classに
      //     包んでハンドラーにメッセージとして渡す
      //     今回パイプラインの一番上はTwoLineStageなので、
      //     Event(TwoLines) がハンドラーに渡される。
      //
      //   * このpipelineワーカーに対してCommand(message:Any)を渡すと、
      //     パイプラインのコマンド側にTを流し込む。
      //     コマンド側のパイプを出て行ったデータは、
      //     IOワーカーに渡される。
      //     そのため、一番したのパイプラインはTcp.Writeなどのコマンドを
      //     吐き出すようになっていなければならない。
      val init = TcpPipelineHandler.withLogger(NoLogging, pipeLineStages)
      val client = context.actorOf(Props(new Client(init))) //ハンドラー。
      val pipelineWorker = context.actorOf(TcpPipelineHandler.props(init, connectionActor, client))

      // IOワーカーがpipelineワーカーにメッセージを送るように設定する
      connectionActor ! Tcp.Register(pipelineWorker)
  }
}
