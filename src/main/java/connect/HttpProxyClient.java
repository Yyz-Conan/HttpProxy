package connect;

import connect.network.nio.NioClientTask;
import connect.network.nio.NioHPCClientFactory;
import connect.network.nio.NioHPCSender;
import intercept.ProxyFilterManager;
import log.LogDog;

import java.nio.channels.SocketChannel;
import java.util.regex.Pattern;

/**
 * 接收处理客户请求
 */
public class HttpProxyClient extends NioClientTask {
    private String lastHost = null;
    //    private String host = null;
    private ConnectPool connectPool;

    public HttpProxyClient(SocketChannel channel) {
        super(channel);
        setConnectTimeout(0);
        connectPool = new ConnectPool();
        setReceive(new RequestReceive(this, "onReceiveRequestData"));
        setSender(new NioHPCSender());
    }

//    @Override
//    protected void onConnectSocketChannel(boolean isConnect) {
//        LogDog.d("==> isConnect = " + isConnect + " obj = " + HttpProxyClient.this.toString());
//    }

    private void onReceiveRequestData(byte[] data) {
//        LogDog.d("==========================================================================================================");
        String proxyData;
//        if (data.length < 300) {
        proxyData = new String(data);
//        } else {
//            proxyData = new String(data, 0, 100);
//        }

        String[] array = proxyData.split("\r\n");
        String firsLine = array[0];
        String host = null;
        int port = 80;
        for (String tmp : array) {
            if (tmp.startsWith("Host: ")) {
                String urlStr = tmp.split(" ")[1];
                String[] arrayUrl = urlStr.split(":");
                host = arrayUrl[0];
                if (arrayUrl.length > 1) {
                    String portStr = arrayUrl[1];
                    port = Integer.parseInt(portStr);
                }
            }
        }

        if (ProxyFilterManager.getInstance().isIntercept(host)) {
            NioHPCClientFactory.getFactory().removeTask(this);
            return;
        }

        if (Pattern.matches(".* .* HTTP.*", firsLine)) {
//            LogDog.v("==##> HttpProxyClient firsLine = " + firsLine);
//            LogDog.v("Proxy Request host = " + host + " obj = " + toString());
            String[] requestLineCells = firsLine.split(" ");
            String method = requestLineCells[0];
//            String urlStr = requestLineCells[1];
            String protocol = requestLineCells[2];

            NioClientTask clientTask = connectPool.get(host);
            if ("CONNECT".equals(method)) {
                if (clientTask == null) {
                    createNewSSLConnect(host, port, protocol);
                } else {
                    reuseSSLClient((ProxyHttpsConnectClient) clientTask, data);
                }
            } else {
                if (clientTask == null) {
                    createNewConnect(data, host, port);
                } else {
                    reuseClient(clientTask, data);
                }
            }
//            LogDog.d("==> Proxy Request host " + host + " proxyData = " + data.length + " obj = " + toString());
            LogDog.d("==> Proxy Request host " + host + " [ add connect count = " + HttpProxyServer.localConnectCount.get() + " ] " + " obj = " + HttpProxyClient.this.toString());
        } else {
            NioClientTask clientTask = connectPool.get(lastHost);
            if (clientTask instanceof ProxyHttpsConnectClient) {
                ProxyHttpsConnectClient httpsClient = (ProxyHttpsConnectClient) clientTask;
                if (httpsClient == null) {
                    LogDog.d("Last Host Proxy Request  = " + lastHost + " obj = " + HttpProxyClient.this.toString());
                    LogDog.d("复用请求 没有找到对应的链路 " + new String(data));
                    NioHPCClientFactory.getFactory().removeTask(this);
                    return;
                }
                reuseSSLClient(httpsClient, data);
            } else {
                NioHPCClientFactory.getFactory().removeTask(this);
            }
        }
//        LogDog.d("==========================================================================================================");
    }

    private void createNewSSLConnect(String host, int port, String protocol) {
        ProxyHttpsConnectClient sslNioClient = new ProxyHttpsConnectClient(host, port, protocol, getSender());
        sslNioClient.setConnectPool(connectPool);
        NioHPCClientFactory.getFactory().addTask(sslNioClient);
        lastHost = host;
    }

    private void createNewConnect(byte[] data, String host, int port) {
        ProxyHttpConnectClient connectClient = new ProxyHttpConnectClient(data, host, port, getSender());
        connectClient.setConnectPool(connectPool);
        NioHPCClientFactory.getFactory().addTask(connectClient);
        lastHost = host;
    }

    private void reuseSSLClient(ProxyHttpsConnectClient clientTask, byte[] data) {
        if (!clientTask.isCloseing()) {
            clientTask.getSender().sendData(data);
        } else {
            createNewSSLConnect(clientTask.getHost(), clientTask.getPort(), clientTask.getProtocol());
        }
    }

    private void reuseClient(NioClientTask clientTask, byte[] data) {
        if (clientTask != null && !clientTask.isCloseing()) {
            clientTask.getSender().sendData(data);
        } else {
            createNewConnect(data, clientTask.getHost(), clientTask.getPort());
        }
    }


    @Override
    protected void onCloseSocketChannel() {
        connectPool.destroy();
        LogDog.d("==> Connect close " + lastHost + " [ remover connect count = " + HttpProxyServer.localConnectCount.decrementAndGet() + " ] " + " obj = " + HttpProxyClient.this.toString());

//        LogDog.d("==> Proxy Local Client close ing !!! " + host + " obj = " + HttpProxyClient.this.toString());
//        LogDog.d("---------- remover() Connect Count = " + HttpProxyServer.localConnectCount.decrementAndGet());
    }
}
