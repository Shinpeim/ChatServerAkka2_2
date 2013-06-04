import akka.actor.Actor
import akka.io.Tcp

class Client extends Actor {
  def receive: Receive = {
    // データが送られてきたときに
    // clientSocketを管理するActorから
    // 送られてくるメッセージ。
    // 中にByteStringが入っている
    // 単純にエコーバックする
    case Tcp.Received(data) =>
      sender ! Tcp.Write(data)

    // clientSocketが閉じられたときに
    // clientSocketを管理するアクターから
    // 送られてくるメッセージ
    case Tcp.PeerClosed =>
      context stop self
  }
}
