import ClientMessageStage._
import akka.actor.{ActorRef, Actor}
import akka.io.{Tcp, TcpPipelineHandler}
import akka.io.TcpPipelineHandler.WithinActorContext
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success
import ExecutionContext.Implicits.global

class Client(roomService: ActorRef,
             init: TcpPipelineHandler.Init[WithinActorContext,
                                           ClientMessageStage.Message,
                                           ClientMessageStage.Message],
             pipeLineWorker:Future[ActorRef]) extends Actor {

  override def postStop = {
    println("stopped")
  }

  def receiveFallback: PartialFunction[Any, Unit] = {
    case init.Event(ReceivedInvalidCommand(commandName, message)) =>
      sender ! init.Command(NotifyError("error: invalid command. " + message))

    case init.Event(ReceivedUnsupportedCommand(commandName)) =>
      sender ! init.Command(NotifyError("error: unsupported command. " + commandName))

    case init.Event(_) =>
      sender ! init.Command(NotifyError("error: you can't use that command in this state"))

    case c: ClientMessageStage.Command =>
      pipeLineWorker andThen {
        case Success(a) => a ! init.Command(c)
      }

    case Tcp.PeerClosed =>
      context stop self
  }

  def default: PartialFunction[Any, Unit] = {
    case init.Event(ReceivedName(name: String)) =>
      context.become(named(name) orElse receiveFallback)

    case init.Event(ReceivedExit) =>
      context stop self
  }

  private def named(name: String): PartialFunction[Any, Unit] = {
    case init.Event(ReceivedEnter(room)) =>
      roomService ! RoomService.Enter(room, self)
      roomService ! RoomService.Broadcast(room, ClientMessageStage.SomeoneEntered(name))
      context.become(entered(name, room) orElse receiveFallback)

    case init.Event(ReceivedExit) =>
      context stop self
  }

  private def entered(name: String, room: String):PartialFunction[Any, Unit] = {
    case init.Event(ReceivedChatMessage(message)) =>
      roomService ! RoomService.Broadcast(room, ClientMessageStage.SomeoneSaid(name, message))

    case init.Event(ReceivedExit) =>
      roomService ! RoomService.Exit(room, self)
      roomService ! RoomService.Broadcast(room, ClientMessageStage.SomeoneExited(name))
      context.become(named(name))
  }

  def receive: Receive = (default orElse receiveFallback)
}
