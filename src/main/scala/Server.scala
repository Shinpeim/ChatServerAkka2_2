import akka.actor.{Props, ActorRef, Actor}
import akka.io.{ IO, Tcp }
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
      // このメッセージの送り手がserverソケットではなく
      // クライアントソケットの管理者であるため、
      // falseが表示される
      println(sender == serverSockSender)

      // 今後クライアントソケットからのメッセージは
      // Clientアクターが受け取るように設定する
      val client = context.actorOf(Props[Client])
      sender ! Tcp.Register(client)
  }
}
