package connect;

import connect.network.nio.NioClientTask;
import connect.network.nio.NioHPCClientFactory;
import connect.network.nio.NioReceive;
import log.LogDog;
import util.IoEnvoy;
import util.ThreadAnnotation;

public class RequestReceive extends NioReceive {

    protected NioClientTask nioClientTask;

    public RequestReceive(NioClientTask task, String receiveMethodName) {
        super(task, receiveMethodName);
        this.nioClientTask = task;
    }

    @Override
    protected void onRead() {
        if (channel.isConnected() && channel.isOpen()) {
            try {
                byte[] data = IoEnvoy.tryRead(channel);
                if (data != null) {
                    ThreadAnnotation.disposeMessage(mReceiveMethodName, mReceive, data);
                } else {
//                LogDog.e(" ==> 收到空的数据 !!!");
                    NioHPCClientFactory.getFactory().removeTask(nioClientTask);
                }
            } catch (Exception e) {
                LogDog.e(" ==> 接受数据异常 !!!" + e.getMessage() + " host = " + nioClientTask.getHost() + " obj = " + nioClientTask.toString());
                NioHPCClientFactory.getFactory().removeTask(nioClientTask);
            }
        }
    }
}
