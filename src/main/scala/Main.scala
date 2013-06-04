import akka.actor.{Props, ActorSystem}

object Main {
  def main(args: Array[String]): Unit = {
    val system = ActorSystem.create()
    system.actorOf(Props[Server])
  }
}
