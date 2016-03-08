package ch.unibas.dmi.dbis.adam.rpc

import ch.unibas.dmi.dbis.adam.http.grpc.adam.AdamSearchGrpc
import io.grpc.Server
import io.grpc.netty.NettyServerBuilder

import scala.concurrent.ExecutionContext


/**
  * adamtwo
  *
  * Ivan Giangreco
  * March 2016
  */
class RPCServer(executionContext: ExecutionContext) { self =>
  private var server: Server = null
  private val port = 44000

  def start(): Unit = {
    server = NettyServerBuilder.forPort(port).addService(AdamSearchGrpc.bindService(new AdamSearchImpl, executionContext)).build.start
    Runtime.getRuntime.addShutdownHook(new Thread() {
      override def run(): Unit = {
        System.err.println("*** shutting down gRPC server since JVM is shutting down")
        self.stop()
        System.err.println("*** server shut down")
      }
    })
  }

  def stop(): Unit = {
    if (server != null) {
      server.shutdown()
    }
  }

  def blockUntilShutdown(): Unit = {
    while(true){
      Thread.sleep(1000)
    }
  }
}