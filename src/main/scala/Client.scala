import akka.actor.Actor
import akka.io._
import akka.io.TcpPipelineHandler.WithinActorContext

class Client(init: TcpPipelineHandler.Init[WithinActorContext, TwoLines, TwoLines]) extends Actor {
  override def postStop = {
    println("stopped")
  }

  def receive: Receive = {
    case init.Event(twoLines) =>
      sender ! init.Command(twoLines)

    case Tcp.PeerClosed =>
      context stop self
  }
}
